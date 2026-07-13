package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.core.network.CustomHeader
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.PersistentCookieJar
import com.uzairansar.hermex.core.network.isPrivateNetworkHost
import com.uzairansar.hermex.core.network.requireAllowedServerTransport
import com.uzairansar.hermex.data.secure.ServerAccount
import com.uzairansar.hermex.data.secure.ServerRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

sealed interface AuthState {
    data object Unconfigured : AuthState
    data class LoggedOut(val server: okhttp3.HttpUrl) : AuthState
    data class LoggedIn(val server: okhttp3.HttpUrl, val account: ServerAccount) : AuthState
}

class AuthRepository(
    private val registry: ServerRegistry,
    private val clientFactory: (okhttp3.HttpUrl) -> HermesApiClient,
    private val probeClientFactory: (okhttp3.HttpUrl, List<CustomHeader>) -> HermesApiClient = { url, _ -> clientFactory(url) },
    private val cookieJar: PersistentCookieJar,
    private val clearCachedServer: suspend (String) -> Unit = {},
) {
    private val authGenerations = ConcurrentHashMap<String, AtomicLong>()
    private val authMutationLocks = ConcurrentHashMap<String, Mutex>()
    private val _state = MutableStateFlow(restoreState())
    val state: StateFlow<AuthState> = _state

    val servers = registry.snapshot

    suspend fun testConnection(
        serverUrlString: String,
        customHeaders: List<CustomHeader> = emptyList(),
    ): com.uzairansar.hermex.core.model.AuthStatusResponse {
        val url = normalizeServerUrl(serverUrlString)
        val client = probeClientFactory(url, customHeaders)
        return testConnection(client)
    }

    private suspend fun testConnection(
        client: HermesApiClient,
    ): com.uzairansar.hermex.core.model.AuthStatusResponse {
        val health = client.health()
        if (health.status != "ok") throw ApiError.Http(200, "Unexpected health status.")
        return client.authStatus()
    }

    suspend fun configure(serverUrlString: String, password: String, customHeaders: List<CustomHeader> = emptyList()) {
        val url = normalizeServerUrl(serverUrlString)
        val generation = advanceAuthGeneration(url)
        withAuthMutation(url) {
            ensureCurrentAuthGeneration(url, generation)
            val client = probeClientFactory(url, customHeaders)
            try {
                val status = testConnection(client)
                if (status.authEnabled == true) {
                    if (status.passwordAuthEnabled == false) {
                        throw IllegalStateException("This server signs in with passkeys, which Hermex Android does not support yet.")
                    }
                    if (password.isBlank()) throw IllegalArgumentException("Enter the server password.")
                    val login = client.login(password)
                    if (login.ok != true) throw ApiError.Unauthorized
                }
                ensureCurrentAuthGeneration(url, generation)
                clearCacheAfterAuthentication(url, client)
                ensureCurrentAuthGeneration(url, generation)
                val account = registry.activate(url)
                registry.saveCustomHeaders(account.id, customHeaders)
                registry.setLoggedOut(account.id, false)
                _state.value = AuthState.LoggedIn(url, account)
            } catch (error: Throwable) {
                discardSupersededCookies(url, generation)
                throw error
            }
        }
    }

    suspend fun addServer(
        serverUrlString: String,
        password: String,
        customHeaders: List<CustomHeader> = emptyList(),
        displayName: String? = null,
        initials: String? = null,
        headerLogoColorHex: String? = null,
    ): AddServerResult {
        val url = normalizeServerUrl(serverUrlString)
        val generation = advanceAuthGeneration(url)
        return withAuthMutation(url) {
            ensureCurrentAuthGeneration(url, generation)
            val serverId = ServerRegistry.normalizedId(url)
            if (registry.snapshot.value.servers.any { it.id == serverId }) {
                throw IllegalArgumentException("This server is already configured.")
            }
            val client = probeClientFactory(url, customHeaders)
            try {
                val status = testConnection(client)
                if (status.authEnabled == true) {
                    if (status.passwordAuthEnabled == false) {
                        throw IllegalStateException("This server signs in with passkeys, which Hermex Android does not support yet.")
                    }
                    if (password.isBlank()) return@withAuthMutation AddServerResult.NeedsPassword
                    val login = client.login(password)
                    if (login.ok != true) throw ApiError.Unauthorized
                }
                ensureCurrentAuthGeneration(url, generation)
                clearCacheAfterAuthentication(url, client)
                ensureCurrentAuthGeneration(url, generation)
                val account = registry.activate(
                    url = url,
                    displayName = displayName,
                    initials = initials,
                    headerLogoColorHex = headerLogoColorHex,
                )
                registry.saveCustomHeaders(account.id, customHeaders)
                registry.setLoggedOut(account.id, false)
                _state.value = AuthState.LoggedIn(url, account)
                AddServerResult.Added(account)
            } catch (error: Throwable) {
                discardSupersededCookies(url, generation)
                throw error
            }
        }
    }

    fun activate(id: String) {
        registry.snapshot.value.servers.firstOrNull { it.id == id }
            ?.urlString
            ?.let { runCatching { it.toHttpUrl() }.getOrNull() }
            ?.let(::advanceAuthGeneration)
        registry.setActive(id)
        _state.value = restoreState()
    }

    fun customHeaders(serverId: String): List<CustomHeader> =
        registry.customHeaders(serverId)

    fun saveCustomHeaders(serverId: String, headers: List<CustomHeader>) {
        registry.saveCustomHeaders(serverId, headers)
    }

    fun updateServerIdentity(
        account: ServerAccount,
        displayName: String,
        initials: String,
        headerLogoColorHex: String,
    ): ServerAccount? {
        val updated = registry.update(
            account.copy(
                displayName = displayName,
                initials = initials,
                headerLogoColorHex = headerLogoColorHex,
            ),
        ) ?: return null
        _state.value = restoreState()
        return updated
    }

    suspend fun logout() {
        val server = (_state.value as? AuthState.LoggedIn)?.server ?: return
        val generation = advanceAuthGeneration(server)
        withAuthMutation(server) {
            if (currentAuthGeneration(server) != generation) return@withAuthMutation
            val client = clientFactory(server)
            var cancellation: CancellationException? = null
            try {
                client.logout()
            } catch (error: CancellationException) {
                cancellation = error
            } catch (_: Throwable) {
                // Local sign-out must still complete if the server is unavailable.
            }
            withContext(NonCancellable) {
                if (currentAuthGeneration(server) == generation) {
                    markLoggedOut(server)
                } else {
                    // No newer cookie mutation can run until this origin lock is released.
                    cookieJar.clear(server)
                }
            }
            cancellation?.let { throw it }
        }
    }

    suspend fun forget(id: String) {
        val account = registry.snapshot.value.servers.firstOrNull { it.id == id }
        val url = account?.let { runCatching { it.urlString.toHttpUrl() }.getOrNull() }
        if (url == null) {
            registry.remove(id)
            _state.value = restoreState()
            return
        }
        withContext(NonCancellable) {
            withAuthMutation(url) {
                // Keep the configured account and its session intact if durable cache cleanup fails.
                clearCachedServer(url.toString())
                advanceAuthGeneration(url)
                cookieJar.clear(url)
                registry.remove(id)
                _state.value = restoreState()
            }
        }
        currentCoroutineContext().ensureActive()
    }

    suspend fun validateRestoredSession(): Boolean {
        val restored = _state.value as? AuthState.LoggedIn ?: return false
        val generation = currentAuthGeneration(restored.server)
        val status = try {
            clientFactory(restored.server).authStatus()
        } catch (_: ApiError.Unauthorized) {
            invalidateRestoredSession(restored.server, generation)
            return false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Preserve offline access when the server cannot be reached.
            return true
        }

        if (status.authEnabled != true || status.loggedIn == true) return true
        invalidateRestoredSession(restored.server, generation)
        return false
    }

    fun currentAuthGeneration(server: okhttp3.HttpUrl): Long = authGeneration(server).get()

    suspend fun handleUnauthorized(server: okhttp3.HttpUrl, requestGeneration: Long) {
        if (currentAuthGeneration(server) != requestGeneration) return
        val active = _state.value as? AuthState.LoggedIn ?: return
        if (ServerRegistry.normalizedId(active.server) != ServerRegistry.normalizedId(server)) return
        invalidateRestoredSession(active.server, requestGeneration)
    }

    private suspend fun invalidateRestoredSession(server: okhttp3.HttpUrl, expectedGeneration: Long) {
        if (!authGeneration(server).compareAndSet(expectedGeneration, expectedGeneration + 1)) return
        withAuthMutation(server) {
            withContext(NonCancellable) {
                markLoggedOut(server)
            }
        }
    }

    private suspend fun markLoggedOut(server: okhttp3.HttpUrl) {
        val serverId = ServerRegistry.normalizedId(server)
        registry.setLoggedOut(serverId, true)
        cookieJar.clear(server)
        runCatching { clearCachedServer(server.toString()) }
        val active = registry.activeServer()
        if (active?.id == serverId) {
            _state.value = AuthState.LoggedOut(server)
        }
    }

    private suspend fun clearCacheAfterAuthentication(url: okhttp3.HttpUrl, client: HermesApiClient) {
        try {
            clearCachedServer(url.toString())
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                try {
                    client.logout()
                } catch (_: Throwable) {
                    // The local cookie is the final rollback authority.
                }
                cookieJar.clear(url)
            }
            throw error
        }
    }

    private fun ensureCurrentAuthGeneration(server: okhttp3.HttpUrl, expectedGeneration: Long) {
        check(currentAuthGeneration(server) == expectedGeneration) {
            "Authentication was superseded by another account change."
        }
    }

    private fun discardSupersededCookies(server: okhttp3.HttpUrl, expectedGeneration: Long) {
        if (currentAuthGeneration(server) != expectedGeneration) cookieJar.clear(server)
    }

    private suspend fun <T> withAuthMutation(server: okhttp3.HttpUrl, block: suspend () -> T): T =
        authMutationLock(server).withLock { block() }

    private fun authMutationLock(server: okhttp3.HttpUrl): Mutex =
        authMutationLocks.getOrPut(ServerRegistry.normalizedId(server)) { Mutex() }

    private fun restoreState(): AuthState {
        val account = registry.activeServer() ?: return AuthState.Unconfigured
        val url = runCatching { account.urlString.toHttpUrl() }.getOrNull() ?: return AuthState.Unconfigured
        return if (registry.isLoggedOut(account.id)) AuthState.LoggedOut(url) else AuthState.LoggedIn(url, account)
    }

    private fun advanceAuthGeneration(server: okhttp3.HttpUrl): Long = authGeneration(server).incrementAndGet()

    private fun authGeneration(server: okhttp3.HttpUrl): AtomicLong =
        authGenerations.getOrPut(ServerRegistry.normalizedId(server)) { AtomicLong(0) }

    companion object {
        fun normalizeServerUrl(input: String): okhttp3.HttpUrl {
            val trimmed = input.trim()
            val withScheme = if (trimmed.contains("://")) {
                trimmed
            } else {
                "${defaultScheme(trimmed)}://$trimmed"
            }
            val parsed = withScheme.toHttpUrl()
            val normalized = parsed.newBuilder()
                .host(normalizedHost(parsed.host))
                .encodedPath("/")
                .encodedQuery(null)
                .fragment(null)
                .build()
            requireAllowedServerTransport(normalized)
            return normalized
        }

        private fun normalizedHost(host: String): String =
            if (host.startsWith("www.webui.", ignoreCase = true)) host.drop(4) else host

        private fun defaultScheme(schemalessServer: String): String {
            val host = runCatching { "http://$schemalessServer".toHttpUrl().host.lowercase() }.getOrNull()
            return if (host != null && shouldDefaultToPlainHttp(host)) "http" else "https"
        }

        private fun shouldDefaultToPlainHttp(host: String): Boolean {
            if (host.endsWith(".test")) return false
            return isPrivateNetworkHost(host)
        }
    }
}

sealed interface AddServerResult {
    data class Added(val account: ServerAccount) : AddServerResult
    data object NeedsPassword : AddServerResult
}

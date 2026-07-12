package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.core.network.CustomHeader
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.PersistentCookieJar
import com.uzairansar.hermex.data.secure.ServerAccount
import com.uzairansar.hermex.data.secure.ServerRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrl

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
) {
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
        val client = probeClientFactory(url, customHeaders)
        val status = testConnection(client)
        if (status.authEnabled == true) {
            if (status.passwordAuthEnabled == false) {
                throw IllegalStateException("This server signs in with passkeys, which Hermex Android does not support yet.")
            }
            if (password.isBlank()) throw IllegalArgumentException("Enter the server password.")
            val login = client.login(password)
            if (login.ok != true) throw ApiError.Unauthorized
        }
        val account = registry.activate(url)
        registry.saveCustomHeaders(account.id, customHeaders)
        _state.value = AuthState.LoggedIn(url, account)
    }

    suspend fun addServer(serverUrlString: String, password: String, customHeaders: List<CustomHeader> = emptyList()): AddServerResult {
        val url = normalizeServerUrl(serverUrlString)
        val serverId = ServerRegistry.normalizedId(url)
        if (registry.snapshot.value.servers.any { it.id == serverId }) {
            throw IllegalArgumentException("This server is already configured.")
        }
        val client = probeClientFactory(url, customHeaders)
        val status = testConnection(client)
        if (status.authEnabled == true) {
            if (status.passwordAuthEnabled == false) {
                throw IllegalStateException("This server signs in with passkeys, which Hermex Android does not support yet.")
            }
            if (password.isBlank()) return AddServerResult.NeedsPassword
            val login = client.login(password)
            if (login.ok != true) throw ApiError.Unauthorized
        }
        val account = registry.activate(url)
        registry.saveCustomHeaders(account.id, customHeaders)
        _state.value = AuthState.LoggedIn(url, account)
        return AddServerResult.Added(account)
    }

    fun activate(id: String) {
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
        runCatching { clientFactory(server).logout() }
        cookieJar.clear(server)
        _state.update { AuthState.LoggedOut(server) }
    }

    fun forget(id: String) {
        registry.snapshot.value.servers
            .firstOrNull { it.id == id }
            ?.let { account ->
                runCatching { account.urlString.toHttpUrl() }
                    .getOrNull()
                    ?.let(cookieJar::clear)
            }
        registry.remove(id)
        _state.value = restoreState()
    }

    private fun restoreState(): AuthState {
        val account = registry.activeServer() ?: return AuthState.Unconfigured
        val url = runCatching { account.urlString.toHttpUrl() }.getOrNull() ?: return AuthState.Unconfigured
        return AuthState.LoggedIn(url, account)
    }

    companion object {
        fun normalizeServerUrl(input: String): okhttp3.HttpUrl {
            val trimmed = input.trim()
            val withScheme = if (trimmed.contains("://")) {
                trimmed
            } else {
                "${defaultScheme(trimmed)}://$trimmed"
            }
            val parsed = withScheme.toHttpUrl()
            return parsed.newBuilder()
                .host(normalizedHost(parsed.host))
                .encodedPath("/")
                .encodedQuery(null)
                .fragment(null)
                .build()
        }

        private fun normalizedHost(host: String): String =
            if (host.startsWith("www.webui.", ignoreCase = true)) host.drop(4) else host

        private fun defaultScheme(schemalessServer: String): String {
            val host = runCatching { "http://$schemalessServer".toHttpUrl().host.lowercase() }.getOrNull()
            return if (host != null && shouldDefaultToPlainHttp(host)) "http" else "https"
        }

        private fun shouldDefaultToPlainHttp(host: String): Boolean {
            if (host == "localhost" || host == "127.0.0.1") return true

            val octets = host.split('.').mapNotNull(String::toIntOrNull)
            return octets.size == 4 &&
                octets.all { it in 0..255 } &&
                octets[0] == 100 &&
                octets[1] in 64..127
        }
    }
}

sealed interface AddServerResult {
    data class Added(val account: ServerAccount) : AddServerResult
    data object NeedsPassword : AddServerResult
}

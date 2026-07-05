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
    private val cookieJar: PersistentCookieJar,
) {
    private val _state = MutableStateFlow(restoreState())
    val state: StateFlow<AuthState> = _state

    val servers = registry.snapshot

    suspend fun testConnection(serverUrlString: String): com.uzairansar.hermex.core.model.AuthStatusResponse {
        val url = normalizeServerUrl(serverUrlString)
        val client = clientFactory(url)
        val health = client.health()
        if (health.status != "ok") throw ApiError.Http(200, "Unexpected health status.")
        return client.authStatus()
    }

    suspend fun configure(serverUrlString: String, password: String, customHeaders: List<CustomHeader> = emptyList()) {
        val url = normalizeServerUrl(serverUrlString)
        val account = registry.activate(url)
        registry.saveCustomHeaders(account.id, customHeaders)
        val client = clientFactory(url)
        val status = testConnection(url.toString())
        if (status.authEnabled == true) {
            if (status.passwordAuthEnabled == false) {
                throw IllegalStateException("This server signs in with passkeys, which Hermex Android does not support yet.")
            }
            if (password.isBlank()) throw IllegalArgumentException("Enter the server password.")
            val login = client.login(password)
            if (login.ok != true) throw ApiError.Unauthorized
        }
        _state.value = AuthState.LoggedIn(url, account)
    }

    fun activate(id: String) {
        registry.setActive(id)
        _state.value = restoreState()
    }

    suspend fun logout() {
        val server = (_state.value as? AuthState.LoggedIn)?.server ?: return
        runCatching { clientFactory(server).logout() }
        cookieJar.clear(server)
        _state.update { AuthState.LoggedOut(server) }
    }

    fun forget(id: String) {
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
            val withScheme = when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                else -> "https://$trimmed"
            }
            val parsed = withScheme.toHttpUrl()
            return parsed.newBuilder()
                .encodedPath("/")
                .encodedQuery(null)
                .fragment(null)
                .build()
        }
    }
}

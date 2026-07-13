package com.uzairansar.hermex.data.secure

import com.uzairansar.hermex.core.network.CustomHeader
import com.uzairansar.hermex.core.network.HermesJson
import com.uzairansar.hermex.core.network.sanitized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
data class ServerAccount(
    val id: String,
    val urlString: String,
    val displayName: String,
    val initials: String,
    val headerLogoColorHex: String = "#FFD700",
    val customHeadersRef: String? = id,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
)

@Serializable
data class ServerRegistrySnapshot(
    val servers: List<ServerAccount> = emptyList(),
    val activeServerId: String? = null,
)

class ServerRegistry(
    private val secretStore: SecretStore,
) {
    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<ServerRegistrySnapshot> = _snapshot

    fun activeServer(): ServerAccount? = _snapshot.value.servers.firstOrNull { it.id == _snapshot.value.activeServerId }

    fun activate(
        url: HttpUrl,
        displayName: String? = null,
        initials: String? = null,
        headerLogoColorHex: String? = null,
    ): ServerAccount {
        val id = normalizedId(url)
        var result: ServerAccount? = null
        _snapshot.update { current ->
            val existing = current.servers.firstOrNull { it.id == id }
            if (existing != null) {
                val updated = existing.copy(
                    displayName = displayName?.trim()?.takeIf { it.isNotBlank() } ?: existing.displayName,
                    initials = initials?.trim()?.takeIf { it.isNotBlank() } ?: existing.initials,
                    headerLogoColorHex = headerLogoColorHex?.trim()?.takeIf { it.isNotBlank() }
                        ?: existing.headerLogoColorHex,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
                result = updated
                current.copy(
                    servers = current.servers.map { account -> if (account.id == id) updated else account },
                    activeServerId = id,
                )
            } else {
                val account = ServerAccount(
                    id = id,
                    urlString = id,
                    displayName = displayName?.trim()?.takeIf { it.isNotBlank() } ?: url.host,
                    initials = initials?.trim()?.takeIf { it.isNotBlank() } ?: url.host.take(2).uppercase(),
                    headerLogoColorHex = headerLogoColorHex?.trim()?.takeIf { it.isNotBlank() } ?: "#FFD700",
                )
                result = account
                current.copy(servers = current.servers + account, activeServerId = id)
            }
        }
        persist(_snapshot.value)
        return requireNotNull(result)
    }

    fun setActive(id: String) {
        _snapshot.update { current ->
            if (current.servers.any { it.id == id }) current.copy(activeServerId = id) else current
        }
        persist(_snapshot.value)
    }

    fun update(account: ServerAccount): ServerAccount? {
        var updatedAccount: ServerAccount? = null
        _snapshot.update { current ->
            if (current.servers.none { it.id == account.id }) return@update current
            val updated = account.copy(updatedAtEpochMillis = System.currentTimeMillis())
            updatedAccount = updated
            current.copy(
                servers = current.servers.map { existing ->
                    if (existing.id == account.id) updated else existing
                },
            )
        }
        persist(_snapshot.value)
        return updatedAccount
    }

    fun remove(id: String) {
        _snapshot.update { current ->
            val remaining = current.servers.filterNot { it.id == id }
            current.copy(
                servers = remaining,
                activeServerId = if (current.activeServerId == id) remaining.firstOrNull()?.id else current.activeServerId,
            )
        }
        secretStore.remove(customHeadersKey(id))
        secretStore.remove(loggedOutKey(id))
        persist(_snapshot.value)
    }

    fun isLoggedOut(serverId: String): Boolean =
        secretStore.getString(loggedOutKey(serverId)) == LOGGED_OUT_VALUE

    fun setLoggedOut(serverId: String, loggedOut: Boolean) {
        if (loggedOut) {
            secretStore.putString(loggedOutKey(serverId), LOGGED_OUT_VALUE)
        } else {
            secretStore.remove(loggedOutKey(serverId))
        }
    }

    fun customHeaders(serverId: String): List<CustomHeader> =
        secretStore.getString(customHeadersKey(serverId))
            ?.let { runCatching { HermesJson.decodeFromString<List<CustomHeader>>(it) }.getOrNull() }
            .orEmpty()

    fun saveCustomHeaders(serverId: String, headers: List<CustomHeader>) {
        secretStore.putString(customHeadersKey(serverId), HermesJson.encodeToString(headers.sanitized()))
    }

    private fun load(): ServerRegistrySnapshot =
        secretStore.getString(KEY)
            ?.let { runCatching { HermesJson.decodeFromString<ServerRegistrySnapshot>(it) }.getOrNull() }
            ?: ServerRegistrySnapshot()

    private fun persist(snapshot: ServerRegistrySnapshot) {
        secretStore.putString(KEY, HermesJson.encodeToString(snapshot))
    }

    private fun customHeadersKey(serverId: String) = "custom_headers::$serverId"
    private fun loggedOutKey(serverId: String) = "logged_out::$serverId"

    companion object {
        private const val KEY = "servers"
        private const val LOGGED_OUT_VALUE = "true"

        fun normalizedId(url: HttpUrl): String {
            val builder = url.newBuilder()
                .encodedPath("/")
                .encodedQuery(null)
                .fragment(null)
            return builder.build().toString()
        }
    }
}

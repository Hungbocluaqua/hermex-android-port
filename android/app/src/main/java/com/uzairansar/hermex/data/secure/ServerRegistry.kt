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
    private val loadResult = load()
    val loadFailure: Throwable? = loadResult.exceptionOrNull()
    private val _snapshot = MutableStateFlow(loadResult.getOrDefault(ServerRegistrySnapshot()))
    val snapshot: StateFlow<ServerRegistrySnapshot> = _snapshot

    fun activeServer(): ServerAccount? = _snapshot.value.servers.firstOrNull { it.id == _snapshot.value.activeServerId }

    @Synchronized
    fun activate(
        url: HttpUrl,
        displayName: String? = null,
        initials: String? = null,
        headerLogoColorHex: String? = null,
        customHeaders: List<CustomHeader>? = null,
        loggedOut: Boolean? = null,
    ): ServerAccount {
        ensureWritable()
        val id = normalizedId(url)
        val current = _snapshot.value
        val existing = current.servers.firstOrNull { it.id == id }
        val result: ServerAccount
        val updatedSnapshot = if (existing != null) {
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
        persist(updatedSnapshot, customHeaders?.let { id to it.sanitized() }, loggedOut?.let { id to it })
        _snapshot.value = updatedSnapshot
        return result
    }

    @Synchronized
    fun setActive(id: String) {
        ensureWritable()
        val current = _snapshot.value
        val updated = if (current.servers.any { it.id == id }) current.copy(activeServerId = id) else current
        if (updated == current) return
        persist(updated)
        _snapshot.value = updated
    }

    @Synchronized
    fun update(account: ServerAccount): ServerAccount? {
        ensureWritable()
        val current = _snapshot.value
        if (current.servers.none { it.id == account.id }) return null
        val updatedAccount = account.copy(updatedAtEpochMillis = System.currentTimeMillis())
        val updatedSnapshot = current.copy(
            servers = current.servers.map { existing ->
                if (existing.id == account.id) updatedAccount else existing
            },
        )
        persist(updatedSnapshot)
        _snapshot.value = updatedSnapshot
        return updatedAccount
    }

    @Synchronized
    fun remove(
        id: String,
        additionalSecretMutation: (Map<String, String>) -> Map<String, String> = { it },
    ) {
        ensureWritable()
        val current = _snapshot.value
        val remaining = current.servers.filterNot { it.id == id }
        if (remaining.size == current.servers.size) return
        val updated = current.copy(
            servers = remaining,
            activeServerId = if (current.activeServerId == id) remaining.firstOrNull()?.id else current.activeServerId,
        )
        secretStore.update { current ->
            additionalSecretMutation(current)
                .minus(customHeadersKey(id))
                .minus(loggedOutKey(id))
                .plus(KEY to HermesJson.encodeToString(updated))
        }
        _snapshot.value = updated
    }

    fun isLoggedOut(serverId: String): Boolean =
        secretStore.getString(loggedOutKey(serverId)) == LOGGED_OUT_VALUE

    @Synchronized
    fun setLoggedOut(
        serverId: String,
        loggedOut: Boolean,
        additionalSecretMutation: (Map<String, String>) -> Map<String, String> = { it },
    ) {
        ensureWritable()
        secretStore.update { current ->
            val updated = additionalSecretMutation(current)
            if (loggedOut) {
                updated + (loggedOutKey(serverId) to LOGGED_OUT_VALUE)
            } else {
                updated - loggedOutKey(serverId)
            }
        }
    }

    fun customHeaders(serverId: String): List<CustomHeader> =
        secretStore.getString(customHeadersKey(serverId))
            ?.let { encoded -> HermesJson.decodeFromString<List<CustomHeader>>(encoded) }
            .orEmpty()

    fun saveCustomHeaders(serverId: String, headers: List<CustomHeader>) {
        ensureWritable()
        secretStore.putString(customHeadersKey(serverId), HermesJson.encodeToString(headers.sanitized()))
    }

    private fun load(): Result<ServerRegistrySnapshot> = runCatching {
        val encoded = secretStore.getString(KEY) ?: return@runCatching ServerRegistrySnapshot()
        HermesJson.decodeFromString<ServerRegistrySnapshot>(encoded).also { snapshot ->
            snapshot.servers.forEach { server ->
                secretStore.getString(customHeadersKey(server.id))?.let { headers ->
                    HermesJson.decodeFromString<List<CustomHeader>>(headers)
                }
            }
        }
    }

    private fun ensureWritable() {
        check(loadFailure == null) {
            "The saved server registry is damaged. Reset secure data from the recovery screen."
        }
    }

    private fun persist(
        snapshot: ServerRegistrySnapshot,
        customHeaders: Pair<String, List<CustomHeader>>? = null,
        loggedOut: Pair<String, Boolean>? = null,
    ) {
        secretStore.update { current ->
            var updated = current + (KEY to HermesJson.encodeToString(snapshot))
            customHeaders?.let { (serverId, headers) ->
                updated = updated + (customHeadersKey(serverId) to HermesJson.encodeToString(headers))
            }
            loggedOut?.let { (serverId, isLoggedOut) ->
                updated = if (isLoggedOut) {
                    updated + (loggedOutKey(serverId) to LOGGED_OUT_VALUE)
                } else {
                    updated - loggedOutKey(serverId)
                }
            }
            updated
        }
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

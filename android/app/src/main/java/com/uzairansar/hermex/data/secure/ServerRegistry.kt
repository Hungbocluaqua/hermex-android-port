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
    val headerLogoColorHex: String = "#7DD3FC",
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

    fun activate(url: HttpUrl): ServerAccount {
        val id = normalizedId(url)
        var result: ServerAccount? = null
        _snapshot.update { current ->
            val existing = current.servers.firstOrNull { it.id == id }
            if (existing != null) {
                result = existing
                current.copy(activeServerId = id)
            } else {
                val account = ServerAccount(
                    id = id,
                    urlString = id,
                    displayName = url.host,
                    initials = url.host.take(2).uppercase(),
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

    fun remove(id: String) {
        _snapshot.update { current ->
            val remaining = current.servers.filterNot { it.id == id }
            current.copy(
                servers = remaining,
                activeServerId = if (current.activeServerId == id) remaining.firstOrNull()?.id else current.activeServerId,
            )
        }
        secretStore.remove(customHeadersKey(id))
        persist(_snapshot.value)
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

    companion object {
        private const val KEY = "servers"

        fun normalizedId(url: HttpUrl): String {
            val builder = url.newBuilder()
                .encodedPath("/")
                .encodedQuery(null)
                .fragment(null)
            return builder.build().toString()
        }
    }
}

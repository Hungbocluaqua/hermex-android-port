package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.data.secure.SecretStore
import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(
    private val secretStore: SecretStore,
) : CookieJar {
    private val lock = Any()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val key = keyFor(url)
            val existing = read(key).filterNot { stored ->
                cookies.any { it.name == stored.name && it.domain == stored.domain && it.path == stored.path }
            }
            val updated = existing + cookies.map(CookieRecord::from)
            secretStore.putString(key, HermesJson.encodeToString(updated))
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val key = keyFor(url)
        val now = System.currentTimeMillis()
        val records = read(key)
        val fresh = records.filter { it.expiresAt == null || it.expiresAt > now }
        if (fresh.size != records.size) {
            secretStore.putString(key, HermesJson.encodeToString(fresh))
        }
        fresh.mapNotNull { it.toCookie() }.filter { it.matches(url) }
    }

    fun clear(url: HttpUrl) {
        synchronized(lock) {
            secretStore.remove(keyFor(url))
        }
    }

    private fun read(key: String): List<CookieRecord> =
        secretStore.getString(key)
            ?.let { runCatching { HermesJson.decodeFromString<List<CookieRecord>>(it) }.getOrNull() }
            .orEmpty()

    private fun keyFor(url: HttpUrl): String = "cookies::${ServerOrigin.from(url)}"
}

private object ServerOrigin {
    fun from(url: HttpUrl): String = url.newBuilder()
        .encodedPath("/")
        .encodedQuery(null)
        .fragment(null)
        .build()
        .toString()
}

@Serializable
private data class CookieRecord(
    val name: String,
    val value: String,
    val expiresAt: Long? = null,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
) {
    fun toCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .apply {
                expiresAt?.let { expiresAt(it) }
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                path(path)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()

    companion object {
        fun from(cookie: Cookie): CookieRecord = CookieRecord(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt.takeIf { it != Long.MAX_VALUE },
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
        )
    }
}

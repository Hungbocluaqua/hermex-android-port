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
            val existing = readAll(url).filterNot { stored ->
                cookies.any { it.name == stored.name && it.domain == stored.domain && it.path == stored.path }
            }
            val updated = existing + cookies.map(CookieRecord::from)
            persist(updated)
            secretStore.remove(legacyKeyFor(url))
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val records = readAll(url)
        val fresh = records.filter { it.expiresAt == null || it.expiresAt > now }
        if (fresh.size != records.size || read(legacyKeyFor(url)).isNotEmpty()) {
            persist(fresh)
            secretStore.remove(legacyKeyFor(url))
        }
        fresh.mapNotNull { it.toCookie() }.filter { it.matches(url) }
    }

    fun clear(url: HttpUrl) {
        synchronized(lock) {
            secretStore.update(clearingMutation(url))
        }
    }

    internal fun <T> whileClearing(url: HttpUrl, action: ((Map<String, String>) -> Map<String, String>) -> T): T =
        synchronized(lock) { action(clearingMutation(url)) }

    internal fun clearingMutation(url: HttpUrl): (Map<String, String>) -> Map<String, String> = { current ->
        val legacyKey = legacyKeyFor(url)
        val retained = readAll(current, url).filterNot { record -> record.belongsToServer(url) }
        current
            .minus(legacyKey)
            .let { secrets ->
                if (retained.isEmpty()) secrets - COOKIE_STORE_KEY
                else secrets + (COOKIE_STORE_KEY to HermesJson.encodeToString(retained))
            }
    }

    private fun readAll(url: HttpUrl): List<CookieRecord> =
        (read(COOKIE_STORE_KEY) + read(legacyKeyFor(url)))
            .distinctBy { record -> Triple(record.name, record.domain, record.path) }

    private fun readAll(secrets: Map<String, String>, url: HttpUrl): List<CookieRecord> =
        (decodeRecords(secrets[COOKIE_STORE_KEY]) + decodeRecords(secrets[legacyKeyFor(url)]))
            .distinctBy { record -> Triple(record.name, record.domain, record.path) }

    private fun persist(records: List<CookieRecord>) {
        if (records.isEmpty()) secretStore.remove(COOKIE_STORE_KEY)
        else secretStore.putString(COOKIE_STORE_KEY, HermesJson.encodeToString(records))
    }

    private fun read(key: String): List<CookieRecord> =
        decodeRecords(secretStore.getString(key))

    private fun decodeRecords(encoded: String?): List<CookieRecord> =
        encoded?.let { runCatching { HermesJson.decodeFromString<List<CookieRecord>>(it) }.getOrNull() }.orEmpty()

    private fun legacyKeyFor(url: HttpUrl): String = "cookies::${ServerOrigin.from(url)}"

    private companion object {
        const val COOKIE_STORE_KEY = "cookies::all"
    }
}

private fun CookieRecord.belongsToServer(url: HttpUrl): Boolean {
    val cookie = toCookie() ?: return false
    if (cookie.secure && !url.isHttps) return false
    return if (cookie.hostOnly) {
        cookie.domain.equals(url.host, ignoreCase = true)
    } else {
        url.host.equals(cookie.domain, ignoreCase = true) ||
            url.host.endsWith(".${cookie.domain}", ignoreCase = true)
    }
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

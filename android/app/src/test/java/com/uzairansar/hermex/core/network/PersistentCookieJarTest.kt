package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.data.secure.SecretStore
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class PersistentCookieJarTest {
    @Test
    fun concurrentCookieWritesDoNotLoseCookies() {
        val store = ConcurrentSecretStore()
        val jar = PersistentCookieJar(store)
        val url = "https://cookies.test/".toHttpUrl()
        val workers = 24
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val executor = Executors.newFixedThreadPool(workers)
        try {
            repeat(workers) { index ->
                executor.execute {
                    ready.countDown()
                    start.await()
                    jar.saveFromResponse(
                        url,
                        listOf(Cookie.Builder().name("cookie-$index").value("value-$index").hostOnlyDomain(url.host).path("/").build()),
                    )
                    done.countDown()
                }
            }
            ready.await()
            start.countDown()
            done.await()
            assertEquals(workers, jar.loadForRequest(url).size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun domainCookieIsSharedAcrossSubdomains() {
        val jar = PersistentCookieJar(ConcurrentSecretStore())
        val authUrl = "https://auth.example.test/".toHttpUrl()
        val appUrl = "https://hermes.example.test/".toHttpUrl()
        jar.saveFromResponse(
            authUrl,
            listOf(Cookie.Builder().name("session").value("token").domain("example.test").path("/").build()),
        )

        val cookies = jar.loadForRequest(appUrl)

        assertEquals(1, cookies.size)
        assertEquals("session", cookies.single().name)
    }

    @Test
    fun clearingServerRemovesItsDomainCookies() {
        val jar = PersistentCookieJar(ConcurrentSecretStore())
        val url = "https://hermes.example.test/".toHttpUrl()
        jar.saveFromResponse(
            url,
            listOf(Cookie.Builder().name("session").value("token").domain("example.test").path("/").build()),
        )

        jar.clear(url)

        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun clearingServerRemovesCookiesScopedBelowTheRootPath() {
        val jar = PersistentCookieJar(ConcurrentSecretStore())
        val url = "https://hermes.example.test/".toHttpUrl()
        jar.saveFromResponse(
            url,
            listOf(Cookie.Builder().name("session").value("token").hostOnlyDomain(url.host).path("/api").build()),
        )

        jar.clear(url)

        assertTrue(jar.loadForRequest("https://hermes.example.test/api/session".toHttpUrl()).isEmpty())
    }

    @Test
    fun clearingParentServerPreservesHostOnlySubdomainCookie() {
        val jar = PersistentCookieJar(ConcurrentSecretStore())
        val parentUrl = "https://example.test/".toHttpUrl()
        val childUrl = "https://hermes.example.test/".toHttpUrl()
        jar.saveFromResponse(
            childUrl,
            listOf(Cookie.Builder().name("child-session").value("token").hostOnlyDomain(childUrl.host).path("/").build()),
        )

        jar.clear(parentUrl)

        assertEquals("child-session", jar.loadForRequest(childUrl).single().name)
    }
}

private class ConcurrentSecretStore : SecretStore {
    private val values = ConcurrentHashMap<String, String>()

    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
    override fun clearPrefix(prefix: String) { values.keys.filter { it.startsWith(prefix) }.forEach(values::remove) }
    override fun update(transform: (Map<String, String>) -> Map<String, String>) {
        synchronized(values) {
            val updated = transform(values.toMap())
            values.clear()
            values.putAll(updated)
        }
    }
}

package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.data.secure.SecretStore
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
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
}

private class ConcurrentSecretStore : SecretStore {
    private val values = ConcurrentHashMap<String, String>()

    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
    override fun clearPrefix(prefix: String) { values.keys.filter { it.startsWith(prefix) }.forEach(values::remove) }
}

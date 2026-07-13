package com.uzairansar.hermex.core.network

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CustomHeaderTest {
    @Test
    fun sanitizedHeadersDropBrowserCsrfHeaders() {
        val request = Request.Builder().url("https://hermes.example.com/health")
        listOf(
            CustomHeader("CF-Access-Client-Id", "id"),
            CustomHeader("Origin", "https://evil.example"),
            CustomHeader("Referer", "https://evil.example"),
        ).applyTo(request)

        val headers = request.build().headers
        assertEquals("id", headers["CF-Access-Client-Id"])
        assertNull(headers["Origin"])
        assertNull(headers["Referer"])
    }

    @Test
    fun parserAcceptsColonAndEqualsHeaderLines() {
        val headers = parseCustomHeaderLines(
            """
            CF-Access-Client-Id: id
            CF-Access-Client-Secret=secret
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                CustomHeader("CF-Access-Client-Id", "id"),
                CustomHeader("CF-Access-Client-Secret", "secret"),
            ),
            headers,
        )
    }

    @Test
    fun parserRejectsBrowserManagedHeaders() {
        try {
            parseCustomHeaderLines("Origin: https://evil.example")
            fail("Expected Origin to be rejected.")
        } catch (error: IllegalArgumentException) {
            assertEquals("Origin, Referer, Host, and Content-Length are not allowed.", error.message)
        }
    }

    @Test
    fun parserRejectsInvalidHttpHeaderSyntax() {
        val error = runCatching { parseCustomHeaderLines("Bad Header: value") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Line 1: invalid HTTP header name or value.", error?.message)
    }

    @Test
    fun invalidPersistedHeadersAreDroppedBeforeRequestMutation() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"status":"ok"}"""))
            val api = HermesApiClient(
                baseUrl = server.url("/"),
                client = OkHttpClient(),
                customHeaders = { listOf(CustomHeader("Bad Header", "value")) },
            )

            api.health()

            assertNull(server.takeRequest().headers["Bad Header"])
        } finally {
            server.close()
        }
    }

    @Test
    fun customHeadersRemainOnSameOriginRedirects() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(307)
                    .addHeader("Location", server.url("/health-after-redirect").toString())
                    .build(),
            )
            server.enqueue(json("""{"status":"ok"}"""))

            val api = HermesApiClient(
                baseUrl = server.url("/"),
                client = OkHttpClient(),
                customHeaders = { listOf(CustomHeader("CF-Access-Client-Id", "id")) },
            )

            api.health()

            assertEquals("id", server.takeRequest().headers["CF-Access-Client-Id"])
            assertEquals("id", server.takeRequest().headers["CF-Access-Client-Id"])
        } finally {
            server.close()
        }
    }

    @Test
    fun customHeadersAreStrippedFromCrossOriginRedirects() = runBlocking {
        val server = MockWebServer()
        val redirectedServer = MockWebServer()
        try {
            server.start()
            redirectedServer.start()
            redirectedServer.enqueue(json("""{"status":"ok"}"""))
            server.enqueue(
                MockResponse.Builder()
                    .code(307)
                    .addHeader("Location", redirectedServer.url("/health").toString())
                    .build(),
            )

            val api = HermesApiClient(
                baseUrl = server.url("/"),
                client = OkHttpClient(),
                customHeaders = {
                    listOf(
                        CustomHeader("CF-Access-Client-Id", "id"),
                        CustomHeader("X-Hermex-Secret", "secret"),
                    )
                },
            )

            api.health()

            val originalRequest = server.takeRequest()
            val redirectedRequest = redirectedServer.takeRequest()
            assertEquals("id", originalRequest.headers["CF-Access-Client-Id"])
            assertEquals("secret", originalRequest.headers["X-Hermex-Secret"])
            assertNull(redirectedRequest.headers["CF-Access-Client-Id"])
            assertNull(redirectedRequest.headers["X-Hermex-Secret"])
        } finally {
            server.close()
            redirectedServer.close()
        }
    }

    private fun json(body: String): MockResponse =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}

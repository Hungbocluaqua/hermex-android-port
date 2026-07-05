package com.uzairansar.hermex.core.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}

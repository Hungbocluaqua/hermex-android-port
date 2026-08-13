package com.uzairansar.hermex.core.network

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerSettingsApiTest {
    @Test
    fun sessionVisibilityAndReasoningDisplayUseVerifiedBodies() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(jsonResponse("""{"show_claude_code_sessions":false}"""))
            server.enqueue(jsonResponse("""{"display":"hide"}"""))
            val client = HermesApiClient(server.url("/"), OkHttpClient())

            client.updateClaudeCodeSessionVisibility(false)
            client.setReasoningDisplay("hide")

            val settings = server.takeRequest()
            assertEquals("POST", settings.method)
            assertEquals("/api/settings", settings.url.encodedPath)
            assertEquals("""{"show_claude_code_sessions":false}""", settings.body?.utf8())
            assertNull(settings.headers["Authorization"])
            assertNull(settings.headers["Origin"])
            assertNull(settings.headers["Referer"])

            val reasoning = server.takeRequest()
            assertEquals("POST", reasoning.method)
            assertEquals("/api/reasoning", reasoning.url.encodedPath)
            assertEquals("""{"display":"hide"}""", reasoning.body?.utf8())
        } finally {
            server.close()
        }
    }

    @Test
    fun providersUsesReadOnlyStatusEndpointAndRichResponse() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                jsonResponse(
                    """
                    {"active_provider":"openai","providers":[{"id":"openai","has_key":true,"models":["gpt-5"]}]}
                    """.trimIndent(),
                ),
            )

            val response = HermesApiClient(server.url("/"), OkHttpClient()).providers()

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/providers", request.url.encodedPath)
            assertEquals("openai", response.activeProvider)
            assertEquals("gpt-5", response.providers?.single()?.models?.single()?.id)
        } finally {
            server.close()
        }
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}

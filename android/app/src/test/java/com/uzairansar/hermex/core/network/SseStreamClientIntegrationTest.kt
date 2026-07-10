package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.ui.chat.ChatStreamRecoveryPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.toList
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseStreamClientIntegrationTest {
    @Test
    fun deliversEventsAndClassifiesNormalActiveStreamCloseAsRecoverable() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body("id: stream-1:7\nevent: token\ndata: {\"text\":\"hello\"}\n\n")
                    .build(),
            )

            var lastEventId: String? = null
            val client = SseStreamClient(server.url("/"), OkHttpClient()) { emptyList() }
            val events = withTimeout(5_000) {
                client.stream(server.url("/api/chat/stream?stream_id=stream-1")) {
                    lastEventId = it
                }.toList()
            }

            assertEquals(listOf(SseEvent.Token("hello")), events)
            assertEquals("stream-1:7", lastEventId)
            assertTrue(
                ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                    cause = null,
                    activeStreamId = "stream-1",
                    streamId = "stream-1",
                ),
            )
        } finally {
            server.close()
        }
    }
}

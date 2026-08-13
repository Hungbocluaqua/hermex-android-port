package com.uzairansar.hermex.core.network

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanEventStreamClientTest {
    @Test
    fun helloFrameRequiresNonnegativeCursorAndBoardIdentity() {
        assertEquals(
            KanbanStreamFrame.Hello(cursor = 42, board = "main"),
            KanbanStreamFrameDecoder.decode(
                "hello",
                """{"cursor":42,"board":" main ","future":{"value":true}}""",
                null,
            ),
        )
        assertEquals(KanbanStreamFrame.Malformed, KanbanStreamFrameDecoder.decode("hello", "{}", null))
        assertEquals(
            KanbanStreamFrame.Malformed,
            KanbanStreamFrameDecoder.decode("hello", """{"cursor":-1,"board":"main"}""", null),
        )
    }

    @Test
    fun eventsFrameToleratesUnknownFieldsAndPreservesFrameIdentity() {
        val frame = KanbanStreamFrameDecoder.decode(
            "events",
            """{"events":[{"id":7,"task_id":"CARD-1","kind":"updated","future":true}],"cursor":7,"extra":1}""",
            "7",
        )

        assertTrue(frame is KanbanStreamFrame.Events)
        frame as KanbanStreamFrame.Events
        assertEquals(7, frame.cursor)
        assertEquals(7, frame.frameId)
        assertEquals("CARD-1", frame.events.single().cardId)
    }

    @Test
    fun malformedEventsAndUnknownTypesRemainDistinct() {
        assertEquals(
            KanbanStreamFrame.Malformed,
            KanbanStreamFrameDecoder.decode("events", """{"events":[],"cursor":4}""", "not-an-int"),
        )
        assertEquals(
            KanbanStreamFrame.Malformed,
            KanbanStreamFrameDecoder.decode("events", """{"events":[]}""", null),
        )
        assertEquals(KanbanStreamFrame.Ignored, KanbanStreamFrameDecoder.decode("future", "not-json", null))
    }

    @Test
    fun httpStreamUsesExactHeadersAndDecodesHelloAndEvents() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "text/event-stream")
                    .body(
                        """
                        event: hello
                        data: {"cursor":4,"board":"main"}

                        id: 5
                        event: events
                        data: {"events":[{"id":5,"task_id":"CARD-1","kind":"updated"}],"cursor":5}

                        """.trimIndent(),
                    )
                    .build(),
            )
            val frames = withTimeout(5_000) {
                HttpKanbanEventStreamingClient(
                    baseUrl = server.url("/"),
                    client = OkHttpClient(),
                    customHeaders = { listOf(CustomHeader("X-Hermex-Test", "yes")) },
                ).stream(server.url("/api/kanban/events/stream?board=main&since=4")).toList()
            }

            assertEquals(2, frames.size)
            assertEquals(KanbanStreamFrame.Hello(4, "main"), frames[0])
            assertEquals(5, (frames[1] as KanbanStreamFrame.Events).cursor)
            val request = server.takeRequest()
            assertEquals("/api/kanban/events/stream", request.url.encodedPath)
            assertEquals("main", request.url.queryParameter("board"))
            assertEquals("4", request.url.queryParameter("since"))
            assertEquals("text/event-stream", request.headers["Accept"])
            assertEquals("no-cache, no-transform", request.headers["Cache-Control"])
            assertEquals("identity", request.headers["Accept-Encoding"])
            assertEquals("yes", request.headers["X-Hermex-Test"])
        } finally {
            server.close()
        }
    }

    @Test
    fun sameOriginUnauthorizedStreamTriggersCentralAuthenticationHandling() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(MockResponse.Builder().code(401).body("unauthorized").build())
            var unauthorizedServer: String? = null
            val result = runCatching {
                withTimeout(5_000) {
                    HttpKanbanEventStreamingClient(
                        baseUrl = server.url("/"),
                        client = OkHttpClient(),
                        onUnauthorized = { unauthorizedServer = it.toString() },
                        customHeaders = { emptyList() },
                    ).stream(server.url("/api/kanban/events/stream?board=main&since=0")).toList()
                }
            }

            assertTrue(result.isFailure)
            assertEquals(server.url("/").toString(), unauthorizedServer)
        } finally {
            server.close()
        }
    }
}

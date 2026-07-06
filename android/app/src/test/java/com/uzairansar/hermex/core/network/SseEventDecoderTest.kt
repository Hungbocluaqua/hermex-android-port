package com.uzairansar.hermex.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseEventDecoderTest {
    @Test
    fun decodesTokenEvent() {
        val event = SseEventDecoder.decode("token", """{"text":"hello"}""")

        assertEquals(SseEvent.Token("hello"), event)
    }

    @Test
    fun decodesToolCompleteEvent() {
        val event = SseEventDecoder.decode("tool_complete", """{"name":"shell","duration":1.25,"is_error":false}""")

        assertTrue(event is SseEvent.ToolCompleted)
        assertEquals("shell", (event as SseEvent.ToolCompleted).event.name)
    }

    @Test
    fun decodesCompletedSessionFromDoneEvent() {
        val event = SseEventDecoder.decode(
            "done",
            """
            {
              "usage":{"context_length":128000,"input_tokens":42},
              "session":{
                "session_id":"session-1",
                "messages":[{"role":"assistant","content":"Done.","message_id":"assistant-1"}]
              }
            }
            """.trimIndent(),
        )

        assertTrue(event is SseEvent.Done)
        event as SseEvent.Done
        assertEquals("session-1", event.sessionId)
        assertEquals("Done.", event.session?.messages?.single()?.content)
        assertEquals(128000, event.usage?.contextLength)
    }

    @Test
    fun mapsMalformedJsonToTransportError() {
        val event = SseEventDecoder.decode("token", "{")

        assertTrue(event is SseEvent.TransportError)
    }
}

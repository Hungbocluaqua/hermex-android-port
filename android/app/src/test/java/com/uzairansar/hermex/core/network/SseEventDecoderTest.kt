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
    fun mapsMalformedJsonToTransportError() {
        val event = SseEventDecoder.decode("token", "{")

        assertTrue(event is SseEvent.TransportError)
    }
}

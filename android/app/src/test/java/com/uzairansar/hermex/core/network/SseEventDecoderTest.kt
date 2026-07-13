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

    @Test
    fun preservesTitleInterimAndPendingSteerPayloads() {
        assertEquals(
            SseEvent.Title("session-1", "Fresh title"),
            SseEventDecoder.decode("title", """{"session_id":"session-1","title":"Fresh title"}"""),
        )
        assertEquals(
            SseEvent.InterimAssistant("Interim", alreadyStreamed = true),
            SseEventDecoder.decode("interim_assistant", """{"text":"Interim","already_streamed":true}"""),
        )
        assertEquals(
            SseEvent.PendingSteerLeftover("next turn"),
            SseEventDecoder.decode("pending_steer_leftover", """{"text":"next turn"}"""),
        )
    }

    @Test
    fun decodesApprovalAndClarificationFrames() {
        val approval = SseEventDecoder.decode(
            "approval",
            """{"pending":{"approval_id":"approval-1","command":"git push"},"pending_count":1}""",
        )
        assertTrue(approval is SseEvent.ApprovalPending)
        assertEquals("approval-1", (approval as SseEvent.ApprovalPending).response.pending?.normalizedApprovalId)

        val clarification = SseEventDecoder.decode(
            "clarify",
            """{"pending":{"clarify_id":"clarify-1","question":"Which branch?"},"pending_count":1}""",
        )
        assertTrue(clarification is SseEvent.ClarificationPending)
        assertEquals("clarify-1", (clarification as SseEvent.ClarificationPending).response.pending?.normalizedClarifyId)
    }

    @Test
    fun toleratesUnexpectedOptionalToolMetadataTypes() {
        val event = SseEventDecoder.decode(
            "tool_complete",
            """{"name":42,"args":["not-an-object"],"duration":"12.5","is_error":"true","tid":99}""",
        )

        assertTrue(event is SseEvent.ToolCompleted)
        val tool = (event as SseEvent.ToolCompleted).event
        assertEquals("42", tool.name)
        assertEquals(null, tool.args)
        assertEquals(12.5, tool.duration ?: 0.0, 0.0)
        assertEquals(true, tool.isError)
        assertEquals("99", tool.tid)
    }
}

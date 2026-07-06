package com.uzairansar.hermex.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptRowVisibilityTest {
    @Test
    fun hidesRawToolMessages() {
        val message = ChatMessage(role = "tool", content = "tool output", toolCallId = "tool-1")

        assertFalse(message.shouldRenderTranscriptItem(showThinkingAndToolCards = true))
    }

    @Test
    fun hidesEmptyUserMessagesWithoutAttachments() {
        val message = ChatMessage(role = "user", content = "   ")

        assertFalse(message.shouldRenderTranscriptItem(showThinkingAndToolCards = true))
    }

    @Test
    fun rendersUserAttachmentOnlyMessages() {
        val message = ChatMessage(
            role = "user",
            content = "",
            attachments = listOf(MessageAttachment(name = "photo.png")),
        )

        assertTrue(message.shouldRenderTranscriptItem(showThinkingAndToolCards = true))
    }

    @Test
    fun rendersAssistantAccessoryOnlyRowsWhenEnabled() {
        val message = ChatMessage(
            role = "assistant",
            content = "",
            reasoning = listOf(ReasoningSegment(text = "Thinking about the plan")),
            toolCalls = listOf(ToolCall(id = "tool-1", name = "read_file")),
        )

        assertTrue(message.shouldRenderTranscriptItem(showThinkingAndToolCards = true))
        assertFalse(message.shouldRenderTranscriptItem(showThinkingAndToolCards = false))
    }
}

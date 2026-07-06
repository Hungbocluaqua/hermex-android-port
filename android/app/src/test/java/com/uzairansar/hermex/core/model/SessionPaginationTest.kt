package com.uzairansar.hermex.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPaginationTest {
    @Test
    fun resolvedOffsetPrefersServerOffset() {
        val session = SessionDetail(
            messageCount = 100,
            messagesOffset = 42,
            messagesTruncated = true,
            messages = listOf(ChatMessage(role = "assistant", content = "Latest")),
        )

        assertEquals(42, session.resolvedMessagesOffset(loadedMessageCount = 1))
        assertTrue(session.hasOlderMessages(loadedMessageCount = 1))
    }

    @Test
    fun resolvedOffsetFallsBackToMessageCountWhenTruncated() {
        val session = SessionDetail(
            messageCount = 75,
            messagesTruncated = true,
            messages = List(50) { index -> ChatMessage(id = "m$index", role = "assistant", content = "$index") },
        )

        assertEquals(25, session.resolvedMessagesOffset(loadedMessageCount = 50))
        assertTrue(session.hasOlderMessages(loadedMessageCount = 50))
    }

    @Test
    fun resolvedOffsetClampsMissingOrNegativeState() {
        val session = SessionDetail(
            messageCount = 3,
            messagesTruncated = true,
            messages = List(5) { index -> ChatMessage(id = "m$index", role = "assistant", content = "$index") },
        )

        assertEquals(0, session.resolvedMessagesOffset(loadedMessageCount = 5))
        assertTrue(session.hasOlderMessages(loadedMessageCount = 5))
        assertFalse(SessionDetail(messages = emptyList()).hasOlderMessages(loadedMessageCount = 0))
    }

    @Test
    fun prependOlderMessagesDeduplicatesOverlapByMessageId() {
        val current = listOf(
            ChatMessage(messageId = "m2", role = "user", content = "Current user"),
            ChatMessage(messageId = "m3", role = "assistant", content = "Current assistant"),
        )
        val older = listOf(
            ChatMessage(messageId = "m1", role = "assistant", content = "Older"),
            ChatMessage(messageId = "m2", role = "user", content = "Duplicate overlap"),
        )

        val merged = ChatMessagePageMerger.prependOlderMessages(older, current)

        assertEquals(listOf("m1", "m2", "m3"), merged.map { it.messageId })
        assertEquals("Current user", merged[1].content)
    }
}

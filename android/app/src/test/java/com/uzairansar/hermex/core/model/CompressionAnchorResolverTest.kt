package com.uzairansar.hermex.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompressionAnchorResolverTest {
    @Test
    fun resolvesReferenceAfterLatestMatchingAnchorKey() {
        val messages = listOf(
            ChatMessage(role = "user", content = "Before"),
            ChatMessage(
                role = "assistant",
                content = "Keep this response",
                timestamp = 1710000000.0,
            ),
            ChatMessage(role = "user", content = "After"),
        )
        val metadata = CompressionAnchorMetadata(
            messageKey = CompressionAnchorMessageKey(
                role = "assistant",
                ts = 1710000000.0,
                text = "Keep this response",
                attachments = 0,
            ),
            summary = "Reference summary",
        )

        val card = CompressionAnchorResolver.resolve(
            messages = messages,
            messagesOffset = 0,
            metadata = metadata,
        )

        assertEquals("Reference summary", card?.referenceText)
        assertEquals(1, card?.afterMessageIndex)
    }

    @Test
    fun clampsVisibleIndexBeforeLoadedWindowToTopCard() {
        val messages = listOf(
            ChatMessage(role = "user", content = "Loaded tail"),
            ChatMessage(role = "assistant", content = "Current context"),
        )
        val metadata = CompressionAnchorMetadata(
            visibleIdx = 2,
            summary = "Earlier context summary",
        )

        val card = CompressionAnchorResolver.resolve(
            messages = messages,
            messagesOffset = 10,
            metadata = metadata,
        )

        assertEquals("Earlier context summary", card?.referenceText)
        assertNull(card?.afterMessageIndex)
    }

    @Test
    fun doesNotDuplicateLiteralContextCompactionMarker() {
        val messages = listOf(
            ChatMessage(role = "assistant", content = "[context compaction] Earlier context summary"),
            ChatMessage(role = "assistant", content = "Current context"),
        )
        val metadata = CompressionAnchorMetadata(
            visibleIdx = 0,
            summary = "Earlier context summary",
        )

        val card = CompressionAnchorResolver.resolve(
            messages = messages,
            messagesOffset = 0,
            metadata = metadata,
        )

        assertNull(card)
    }
}

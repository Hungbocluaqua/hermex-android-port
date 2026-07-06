package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptLinkPreviewTest {
    @Test
    fun extractsFirstPlainWebUrl() {
        val url = TranscriptLinkPreviewExtractor.firstWebUrl("Open https://example.com/docs.")

        assertEquals("https://example.com/docs", url.toString())
    }

    @Test
    fun ignoresUrlsInsideInlineAndFencedCode() {
        val text = """
            `https://example.com/inline`
            ```sh
            echo https://example.com/fenced
            ```
            Real: https://example.com/visible
        """.trimIndent()

        assertEquals("https://example.com/visible", TranscriptLinkPreviewExtractor.firstWebUrl(text).toString())
    }

    @Test
    fun skipsStreamingAndNonChatRoles() {
        val message = ChatMessage(role = "assistant", content = "https://example.com")

        assertNull(TranscriptLinkPreviewEligibility.previewUrlFor(message, isStreaming = true))
        assertNull(TranscriptLinkPreviewEligibility.previewUrlFor(message.copy(role = "tool"), isStreaming = false))
        assertEquals("https://example.com/", TranscriptLinkPreviewEligibility.previewUrlFor(message, isStreaming = false).toString())
    }
}

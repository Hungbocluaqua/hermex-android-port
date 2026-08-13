package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMarkdownBlockSplitterTest {
    @Test
    fun shortTextStaysInActiveMarkdown() {
        val text = "Hello from Hermes."

        val segments = StreamingMarkdownBlockSplitter.split(text)

        assertTrue(segments.stableChunks.isEmpty())
        assertEquals(text, segments.activeMarkdown)
    }

    @Test
    fun completedFenceSealsStableChunk() {
        val stableBody = "A".repeat(6_100)
        val text = "$stableBody\n```swift\nlet answer = 42\n```\nStill streaming"

        val segments = StreamingMarkdownBlockSplitter.split(text)

        assertEquals(1, segments.stableChunks.size)
        assertTrue(segments.stableChunks.single().text.contains(stableBody))
        assertTrue(segments.activeMarkdown.contains("Still streaming"))
    }

    @Test
    fun headingBoundaryCanSealWithoutFence() {
        val prose = "Line of prose.\n".repeat(500)

        val segments = StreamingMarkdownBlockSplitter.split(prose + "## Next section\nMore text")

        assertFalse(segments.stableChunks.isEmpty())
        assertTrue(segments.activeMarkdown.contains("More text"))
    }

    @Test
    fun tabSeparatedHeadingCountsAsStableBoundary() {
        val prose = "Line of prose.\n".repeat(500)

        val segments = StreamingMarkdownBlockSplitter.split(prose + "##\tTab heading\nMore text")

        assertFalse(segments.stableChunks.isEmpty())
        assertTrue(segments.activeMarkdown.contains("More text"))
    }

    @Test
    fun endOfTextIsNeverSealed() {
        val text = "Paragraph.\n\n".repeat(700)

        val segments = StreamingMarkdownBlockSplitter.split(text)

        assertTrue(segments.activeMarkdown.isNotEmpty())
        assertEquals(text, segments.stableChunks.joinToString("") { it.text } + segments.activeMarkdown)
    }
}

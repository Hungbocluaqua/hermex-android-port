package com.uzairansar.hermex.ui.chat

internal data class StreamingMarkdownChunk(
    val id: Int,
    val text: String,
)

internal data class StreamingMarkdownBlockSegments(
    val stableChunks: List<StreamingMarkdownChunk>,
    val activeMarkdown: String,
)

internal object StreamingMarkdownBlockSplitter {
    const val STABLE_CHUNK_TARGET_CHARACTER_COUNT = 6_000

    fun split(text: String): StreamingMarkdownBlockSegments {
        var lineStart = 0
        var chunkStart = 0
        var isInsideFence = false
        val stableChunks = mutableListOf<StreamingMarkdownChunk>()

        while (lineStart < text.length) {
            val lineEnd = text.indexOf('\n', lineStart).takeIf { it >= 0 } ?: text.length
            val nextLineStart = if (lineEnd < text.length) lineEnd + 1 else text.length
            val hasLineBreak = lineEnd < text.length
            val trimmedLine = text.substring(lineStart, lineEnd).trim()

            var stableBoundary: Int? = null
            if (isFenceDelimiter(trimmedLine)) {
                isInsideFence = !isInsideFence
                if (!isInsideFence) stableBoundary = nextLineStart
            } else if (!isInsideFence && hasLineBreak) {
                if (trimmedLine.isEmpty() || isStableSingleLineBlock(trimmedLine)) {
                    stableBoundary = nextLineStart
                }
            }

            if (
                stableBoundary != null &&
                stableBoundary < text.length &&
                stableBoundary - chunkStart >= STABLE_CHUNK_TARGET_CHARACTER_COUNT
            ) {
                val chunkText = text.substring(chunkStart, stableBoundary)
                if (chunkText.isNotBlank()) {
                    stableChunks += StreamingMarkdownChunk(stableChunks.size, chunkText)
                }
                chunkStart = stableBoundary
            }

            lineStart = nextLineStart
        }

        return StreamingMarkdownBlockSegments(
            stableChunks = stableChunks,
            activeMarkdown = text.substring(chunkStart),
        )
    }

    private fun isFenceDelimiter(trimmedLine: String): Boolean =
        trimmedLine.startsWith("```") || trimmedLine.startsWith("~~~")

    private fun isStableSingleLineBlock(trimmedLine: String): Boolean {
        val headingMarkerCount = trimmedLine.takeWhile { it == '#' }.length
        val isHeading = headingMarkerCount in 1..6 &&
            trimmedLine.drop(headingMarkerCount).firstOrNull()?.isWhitespace() == true
        return isHeading || trimmedLine == "---" || trimmedLine == "***"
    }
}

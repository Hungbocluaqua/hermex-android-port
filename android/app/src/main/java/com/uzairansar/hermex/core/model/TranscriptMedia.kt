package com.uzairansar.hermex.core.model

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface TranscriptMediaSource {
    data class LocalPath(val path: String) : TranscriptMediaSource
    data class RemoteUrl(val url: HttpUrl) : TranscriptMediaSource
}

data class TranscriptMediaReference(
    val rawReference: String,
) {
    val id: String
        get() = rawReference

    val source: TranscriptMediaSource
        get() {
            val trimmed = rawReference.trim()
            val url = trimmed.toHttpUrlOrNull()
            return if (url != null && (url.scheme == "http" || url.scheme == "https")) {
                TranscriptMediaSource.RemoteUrl(url)
            } else {
                TranscriptMediaSource.LocalPath(trimmed)
            }
        }

    val displayName: String
        get() {
            val trimmed = rawReference.trim()
            if (trimmed.isEmpty()) return "Media"
            return when (val resolvedSource = source) {
                is TranscriptMediaSource.RemoteUrl -> resolvedSource.url.pathSegments
                    .lastOrNull { it.isNotBlank() }
                    ?.ifBlank { null }
                    ?: "Image"
                is TranscriptMediaSource.LocalPath -> trimmed.lastPathComponent().ifBlank { trimmed }
            }
        }

    val isRasterImageCandidate: Boolean
        get() {
            val ext = pathExtension
            if (ext in rasterImageExtensions) return true
            return source is TranscriptMediaSource.RemoteUrl && ext.isEmpty()
        }

    private val pathExtension: String
        get() {
            val path = when (val resolvedSource = source) {
                is TranscriptMediaSource.RemoteUrl -> resolvedSource.url.encodedPath
                is TranscriptMediaSource.LocalPath -> resolvedSource.path
            }
            return path.substringBefore('?')
                .substringBefore('#')
                .lastPathComponent()
                .substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
        }

    private companion object {
        private val rasterImageExtensions = setOf(
            "bmp",
            "gif",
            "heic",
            "heif",
            "ico",
            "jpg",
            "jpeg",
            "png",
            "tif",
            "tiff",
            "webp",
        )
    }
}

sealed interface TranscriptMediaSegment {
    data class Text(val text: String) : TranscriptMediaSegment
    data class Media(val reference: TranscriptMediaReference) : TranscriptMediaSegment
}

object TranscriptMediaParser {
    fun segments(markdown: String): List<TranscriptMediaSegment> {
        if (markdown.isEmpty()) return emptyList()

        val segments = mutableListOf<TranscriptMediaSegment>()
        var cursor = 0
        var isInFence = false
        var fenceCharacter: Char? = null

        while (cursor < markdown.length) {
            val newlineIndex = markdown.indexOf('\n', startIndex = cursor)
            val lineEnd = if (newlineIndex >= 0) newlineIndex + 1 else markdown.length
            val line = markdown.substring(cursor, lineEnd)

            if (isInFence) {
                appendText(line, segments)
                if (fenceMarker(line) == fenceCharacter) {
                    isInFence = false
                    fenceCharacter = null
                }
            } else {
                val marker = fenceMarker(line)
                if (marker != null) {
                    appendText(line, segments)
                    isInFence = true
                    fenceCharacter = marker
                } else {
                    appendMediaSegments(line, segments)
                }
            }

            cursor = lineEnd
        }

        return segments
    }

    private fun appendMediaSegments(line: String, segments: MutableList<TranscriptMediaSegment>) {
        var cursor = 0
        var textStart = 0

        while (cursor < line.length) {
            if (line.startsWith("MEDIA:", cursor)) {
                val range = referenceRange(line, cursor + 6)
                if (range != null) {
                    appendText(line.substring(textStart, cursor), segments)
                    segments += TranscriptMediaSegment.Media(
                        TranscriptMediaReference(line.substring(range.first, range.last)),
                    )
                    cursor = range.last
                    textStart = cursor
                    continue
                }
            }
            cursor += 1
        }

        appendText(line.substring(textStart), segments)
    }

    private fun appendText(text: String, segments: MutableList<TranscriptMediaSegment>) {
        if (text.isEmpty()) return
        val last = segments.lastOrNull()
        if (last is TranscriptMediaSegment.Text) {
            segments[segments.lastIndex] = last.copy(text = last.text + text)
        } else {
            segments += TranscriptMediaSegment.Text(text)
        }
    }

    private fun referenceRange(line: String, start: Int): IntRangeBounds? {
        if (start >= line.length) return null
        var end = start
        while (end < line.length && !isReferenceTerminator(line[end])) {
            end += 1
        }

        var trimmedEnd = end
        while (trimmedEnd > start && line[trimmedEnd - 1] in trailingPunctuation) {
            trimmedEnd -= 1
        }

        return if (trimmedEnd > start) IntRangeBounds(start, trimmedEnd) else null
    }

    private fun isReferenceTerminator(character: Char): Boolean =
        character.isWhitespace() || character == ')' || character == ']'

    private fun fenceMarker(line: String): Char? {
        var index = 0
        var leadingSpaces = 0
        while (index < line.length && line[index] == ' ' && leadingSpaces < 4) {
            leadingSpaces += 1
            index += 1
        }
        if (leadingSpaces > 3 || index >= line.length) return null
        return when {
            line.startsWith("```", index) -> '`'
            line.startsWith("~~~", index) -> '~'
            else -> null
        }
    }

    private data class IntRangeBounds(val first: Int, val last: Int)

    private val trailingPunctuation = setOf('.', ',', ';', ':', '!', '?')
}

private fun String.lastPathComponent(): String =
    trim().trimEnd('/', '\\').replace('\\', '/').substringAfterLast('/')

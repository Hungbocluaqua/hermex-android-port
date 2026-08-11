package com.uzairansar.hermex.core.model

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

sealed interface TranscriptMediaSource {
    data class LocalPath(val path: String) : TranscriptMediaSource
    data class RemoteUrl(val url: HttpUrl) : TranscriptMediaSource
}

enum class TranscriptMediaKind {
    Image,
    Audio,
    Video,
    Unsupported,
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
        get() = mediaKind == TranscriptMediaKind.Image

    val isAudioCandidate: Boolean
        get() = mediaKind == TranscriptMediaKind.Audio

    val isVideoCandidate: Boolean
        get() = mediaKind == TranscriptMediaKind.Video

    val isExtensionlessRemoteMediaCandidate: Boolean
        get() = source is TranscriptMediaSource.RemoteUrl && pathExtension.isEmpty()

    val mediaKind: TranscriptMediaKind
        get() = when {
            pathExtension in rasterImageExtensions -> TranscriptMediaKind.Image
            pathExtension in audioExtensions -> TranscriptMediaKind.Audio
            pathExtension in videoExtensions -> TranscriptMediaKind.Video
            isExtensionlessRemoteMediaCandidate -> TranscriptMediaKind.Image
            else -> TranscriptMediaKind.Unsupported
        }

    val fileExtension: String?
        get() = pathExtension.takeIf { it.isNotEmpty() }

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
        private val audioExtensions = setOf("aac", "caf", "m4a", "mp3", "wav")
        private val videoExtensions = setOf("m4v", "mov", "mp4")
    }
}

object TranscriptMediaDataClassifier {
    fun resolvedKind(reference: TranscriptMediaReference, data: ByteArray): TranscriptMediaKind {
        val declared = reference.mediaKind
        if (!reference.isExtensionlessRemoteMediaCandidate && declared != TranscriptMediaKind.Unsupported) {
            return declared
        }
        return when {
            data.hasPngSignature() || data.hasJpegSignature() || data.hasGifSignature() || data.hasWebpSignature() ->
                TranscriptMediaKind.Image
            data.hasWaveSignature() || data.hasMp3Signature() || data.hasCafSignature() || data.hasAacSignature() ->
                TranscriptMediaKind.Audio
            data.hasIsoBaseMediaSignature() -> TranscriptMediaKind.Video
            declared == TranscriptMediaKind.Unsupported -> TranscriptMediaKind.Unsupported
            else -> TranscriptMediaKind.Video
        }
    }

    fun suggestedExtension(reference: TranscriptMediaReference, data: ByteArray): String =
        reference.fileExtension ?: when (resolvedKind(reference, data)) {
            TranscriptMediaKind.Image -> when {
                data.hasJpegSignature() -> "jpg"
                data.hasGifSignature() -> "gif"
                data.hasWebpSignature() -> "webp"
                else -> "png"
            }
            TranscriptMediaKind.Audio -> when {
                data.hasWaveSignature() -> "wav"
                data.hasCafSignature() -> "caf"
                data.hasAacSignature() -> "aac"
                else -> "mp3"
            }
            TranscriptMediaKind.Video -> "mp4"
            TranscriptMediaKind.Unsupported -> "bin"
        }

    private fun ByteArray.hasPngSignature(): Boolean = startsWith(0x89, 0x50, 0x4E, 0x47)
    private fun ByteArray.hasJpegSignature(): Boolean = startsWith(0xFF, 0xD8, 0xFF)
    private fun ByteArray.hasGifSignature(): Boolean = startsWithAscii("GIF8")
    private fun ByteArray.hasWebpSignature(): Boolean = startsWithAscii("RIFF") && asciiAt(8, "WEBP")
    private fun ByteArray.hasWaveSignature(): Boolean = startsWithAscii("RIFF") && asciiAt(8, "WAVE")
    private fun ByteArray.hasMp3Signature(): Boolean = startsWithAscii("ID3") ||
        (size >= 2 && this[0].unsigned == 0xFF && (this[1].unsigned and 0xE0) == 0xE0)
    private fun ByteArray.hasCafSignature(): Boolean = startsWithAscii("caff")
    private fun ByteArray.hasAacSignature(): Boolean =
        size >= 2 && this[0].unsigned == 0xFF && (this[1].unsigned and 0xF6) == 0xF0
    private fun ByteArray.hasIsoBaseMediaSignature(): Boolean = asciiAt(4, "ftyp")

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { index -> this[index].unsigned == bytes[index] }

    private fun ByteArray.startsWithAscii(value: String): Boolean = asciiAt(0, value)

    private fun ByteArray.asciiAt(offset: Int, value: String): Boolean =
        size >= offset + value.length && value.indices.all { index -> this[offset + index].toInt().toChar() == value[index] }

    private val Byte.unsigned: Int
        get() = toInt() and 0xFF
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
        var fenceLength = 0

        while (cursor < markdown.length) {
            val newlineIndex = markdown.indexOf('\n', startIndex = cursor)
            val lineEnd = if (newlineIndex >= 0) newlineIndex + 1 else markdown.length
            val line = markdown.substring(cursor, lineEnd)

            if (isInFence) {
                appendText(line, segments)
                val marker = fenceMarker(line)
                if (
                    marker != null &&
                    marker.character == fenceCharacter &&
                    marker.length >= fenceLength &&
                    marker.trailingText.isBlank()
                ) {
                    isInFence = false
                    fenceCharacter = null
                    fenceLength = 0
                }
            } else {
                val marker = fenceMarker(line)
                if (marker != null) {
                    appendText(line, segments)
                    isInFence = true
                    fenceCharacter = marker.character
                    fenceLength = marker.length
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
        val inlineCodeRanges = inlineCodeRanges(line)

        while (cursor < line.length) {
            if (line.startsWith("MEDIA:", cursor)) {
                val range = referenceRange(
                    line = line,
                    markerStart = cursor,
                    start = cursor + 6,
                    syntax = ReferenceSyntax.MediaToken,
                )
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
            if (
                line.startsWith(FILE_URL_MARKER, cursor) &&
                isBareFileUrlStart(cursor, line) &&
                inlineCodeRanges.none { range -> cursor >= range.first && cursor < range.last }
            ) {
                val range = referenceRange(
                    line = line,
                    markerStart = cursor,
                    start = cursor + FILE_URL_MARKER.length,
                    syntax = ReferenceSyntax.FileUrl,
                )
                if (range != null) {
                    appendText(line.substring(textStart, cursor), segments)
                    val rawUrl = line.substring(cursor, range.last)
                    segments += TranscriptMediaSegment.Media(
                        TranscriptMediaReference(normalizedLocalPath(rawUrl)),
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

    private fun referenceRange(
        line: String,
        markerStart: Int,
        start: Int,
        syntax: ReferenceSyntax,
    ): IntRangeBounds? {
        if (start >= line.length) return null
        var end = start
        while (end < line.length && !isReferenceTerminator(line[end], syntax)) {
            end += 1
        }

        var trimmedEnd = end
        while (trimmedEnd > start && line[trimmedEnd - 1] in trailingPunctuation) {
            trimmedEnd -= 1
        }
        if (syntax == ReferenceSyntax.MediaToken) {
            emphasisDelimiter(line, markerStart)?.let { delimiter ->
                if (
                    trimmedEnd - delimiter.length >= start &&
                    line.regionMatches(trimmedEnd - delimiter.length, delimiter, 0, delimiter.length)
                ) {
                    trimmedEnd -= delimiter.length
                }
            }
        }

        return if (trimmedEnd > start) IntRangeBounds(start, trimmedEnd) else null
    }

    private fun emphasisDelimiter(line: String, markerStart: Int): String? =
        emphasisDelimiters.firstOrNull { delimiter ->
            markerStart >= delimiter.length &&
                line.regionMatches(markerStart - delimiter.length, delimiter, 0, delimiter.length)
        }

    private fun isReferenceTerminator(character: Char, syntax: ReferenceSyntax): Boolean =
        character.isWhitespace() ||
            character == ')' ||
            character == ']' ||
            (syntax == ReferenceSyntax.FileUrl && character in fileUrlTerminators)

    private fun isBareFileUrlStart(index: Int, line: String): Boolean =
        index == 0 || line[index - 1].isWhitespace()

    private fun normalizedLocalPath(rawUrl: String): String {
        val decodedPath = runCatching { URI(rawUrl).path }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
        return decodedPath ?: rawUrl.removePrefix(FILE_URL_MARKER)
    }

    private fun inlineCodeRanges(line: String): List<IntRangeBounds> {
        val ranges = mutableListOf<IntRangeBounds>()
        var cursor = 0
        while (cursor < line.length) {
            if (line[cursor] != '`') {
                cursor += 1
                continue
            }
            val openingStart = cursor
            val openingEnd = backtickRunEnd(line, cursor)
            val delimiterLength = openingEnd - openingStart
            var search = openingEnd
            var closingEnd: Int? = null
            while (search < line.length) {
                if (line[search] != '`') {
                    search += 1
                    continue
                }
                val candidateEnd = backtickRunEnd(line, search)
                if (candidateEnd - search == delimiterLength) {
                    closingEnd = candidateEnd
                    break
                }
                search = candidateEnd
            }
            if (closingEnd == null) break
            ranges += IntRangeBounds(openingStart, closingEnd)
            cursor = closingEnd
        }
        return ranges
    }

    private fun backtickRunEnd(line: String, start: Int): Int {
        var end = start
        while (end < line.length && line[end] == '`') end += 1
        return end
    }

    private fun fenceMarker(line: String): FenceMarker? {
        var index = 0
        var leadingSpaces = 0
        while (index < line.length && line[index] == ' ' && leadingSpaces < 4) {
            leadingSpaces += 1
            index += 1
        }
        if (leadingSpaces > 3 || index >= line.length) return null
        val character = line[index].takeIf { it == '`' || it == '~' } ?: return null
        var end = index
        while (end < line.length && line[end] == character) end += 1
        val length = end - index
        if (length < 3) return null
        return FenceMarker(character, length, line.substring(end).trimEnd('\r', '\n'))
    }

    private data class FenceMarker(val character: Char, val length: Int, val trailingText: String)
    private data class IntRangeBounds(val first: Int, val last: Int)

    private enum class ReferenceSyntax {
        MediaToken,
        FileUrl,
    }

    private val trailingPunctuation = setOf('.', ',', ';', ':', '!', '?')
    private val fileUrlTerminators = setOf('<', '>', '"', '\'')
    private val emphasisDelimiters = listOf("***", "___", "**", "__", "*", "_")
    private const val FILE_URL_MARKER = "file://"
}

private fun String.lastPathComponent(): String =
    trim().trimEnd('/', '\\').replace('\\', '/').substringAfterLast('/')

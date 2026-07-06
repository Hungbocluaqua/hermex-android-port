package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ChatMessage
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object TranscriptLinkPreviewExtractor {
    fun firstWebUrl(text: String): HttpUrl? {
        searchableSegments(text).forEach { segment ->
            firstWebUrlInSearchableSegment(segment)?.let { return it }
        }
        return null
    }

    private fun searchableSegments(markdown: String): List<String> {
        if (markdown.isEmpty()) return emptyList()
        val segments = mutableListOf<String>()
        var cursor = 0
        var isInFence = false
        var fenceCharacter: Char? = null

        while (cursor < markdown.length) {
            val newlineIndex = markdown.indexOf('\n', startIndex = cursor)
            val lineEnd = if (newlineIndex >= 0) newlineIndex + 1 else markdown.length
            val line = markdown.substring(cursor, lineEnd)

            if (isInFence) {
                if (fenceMarker(line) == fenceCharacter) {
                    isInFence = false
                    fenceCharacter = null
                }
            } else {
                val marker = fenceMarker(line)
                if (marker != null) {
                    isInFence = true
                    fenceCharacter = marker
                } else if (!shouldSkipLine(line)) {
                    segments += nonInlineCodeSegments(line)
                }
            }

            cursor = lineEnd
        }

        return segments
    }

    private fun firstWebUrlInSearchableSegment(segment: String): HttpUrl? {
        return webUrlRegex.findAll(segment)
            .mapNotNull { match -> match.value.trimTrailingUrlPunctuation().toHttpUrlOrNull() }
            .firstOrNull { it.scheme == "http" || it.scheme == "https" }
    }

    private fun nonInlineCodeSegments(line: String): List<String> {
        val segments = mutableListOf<String>()
        var cursor = 0
        var textStart = 0

        while (cursor < line.length) {
            if (line[cursor] != '`') {
                cursor += 1
                continue
            }

            val opening = backtickRun(line, cursor)
            val closing = closingBacktickRun(
                count = opening.last - opening.first,
                line = line,
                start = opening.last,
            )
            if (closing == null) {
                cursor = opening.last
                continue
            }

            if (textStart < opening.first) {
                segments += line.substring(textStart, opening.first)
            }
            cursor = closing.last
            textStart = cursor
        }

        if (textStart < line.length) {
            segments += line.substring(textStart)
        }
        return segments
    }

    private fun shouldSkipLine(line: String): Boolean {
        if (!line.contains(webUrlPresenceRegex)) return false
        if (line.startsWith('\t') || line.startsWith("    ")) return true
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return true
        if (isLikelyJsonLine(trimmed)) return true
        return codeOrLogPatterns.any { it.containsMatchIn(trimmed) }
    }

    private fun isLikelyJsonLine(trimmedLine: String): Boolean {
        if (trimmedLine.startsWith("{") || trimmedLine.startsWith("}")) return true
        if (trimmedLine.startsWith("[") && trimmedLine.contains('"')) return true
        return Regex("^\"[^\"]+\"\\s*:\\s*").containsMatchIn(trimmedLine)
    }

    private fun backtickRun(line: String, start: Int): IntRangeBounds {
        var end = start
        while (end < line.length && line[end] == '`') end += 1
        return IntRangeBounds(start, end)
    }

    private fun closingBacktickRun(count: Int, line: String, start: Int): IntRangeBounds? {
        var cursor = start
        while (cursor < line.length) {
            if (line[cursor] != '`') {
                cursor += 1
                continue
            }
            val candidate = backtickRun(line, cursor)
            if (candidate.last - candidate.first == count) return candidate
            cursor = candidate.last
        }
        return null
    }

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

    private fun String.trimTrailingUrlPunctuation(): String =
        trimEnd('.', ',', ';', ':', '!', '?', ')', ']')

    private data class IntRangeBounds(val first: Int, val last: Int)

    private val webUrlPresenceRegex = Regex("https?://", RegexOption.IGNORE_CASE)
    private val webUrlRegex = Regex("https?://[^\\s<>()\\[\\]\"]+", RegexOption.IGNORE_CASE)
    private val codeOrLogPatterns = listOf(
        Regex("^(\\$|%)\\s+.*https?://", RegexOption.IGNORE_CASE),
        Regex("^(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|NOTICE)\\b.*https?://", RegexOption.IGNORE_CASE),
        Regex("^\\d{4}-\\d{2}-\\d{2}[T\\s].*https?://", RegexOption.IGNORE_CASE),
        Regex("^(\\d{2}:\\d{2}:\\d{2}|\\[\\d{2}:\\d{2}:\\d{2}\\]).*https?://", RegexOption.IGNORE_CASE),
        Regex("^(at\\s+\\S+|#\\d+\\s+|Thread\\s+\\d+|Caused by:|Traceback\\b|File\\s+\"[^\"]+\",\\s+line\\s+\\d+).*https?://", RegexOption.IGNORE_CASE),
        Regex("^(let|var|const|final|static|private|public|return)\\b.*https?://", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z_][A-Za-z0-9_.<>]*\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*.*https?://", RegexOption.IGNORE_CASE),
        Regex("^(curl|wget|git|ssh|scp)\\b.*https?://", RegexOption.IGNORE_CASE),
    )
}

object TranscriptLinkPreviewEligibility {
    fun previewUrlFor(message: ChatMessage, isStreaming: Boolean): HttpUrl? {
        if (isStreaming) return null
        val role = message.role ?: return null
        if (role != "user" && role != "assistant") return null
        val content = message.content ?: return null
        return TranscriptLinkPreviewExtractor.firstWebUrl(content)
    }
}

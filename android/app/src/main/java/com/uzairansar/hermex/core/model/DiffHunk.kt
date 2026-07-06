package com.uzairansar.hermex.core.model

data class DiffHunk(
    val id: Int,
    val header: String,
    val lines: List<DiffLine>,
    val isSynthetic: Boolean,
    val patchNumber: Int,
    val patchCount: Int,
    val newStart: Int?,
    val newCount: Int?,
) {
    val additions: Int get() = lines.count { it.kind == DiffLineKind.Addition }
    val deletions: Int get() = lines.count { it.kind == DiffLineKind.Deletion }

    val displayLabel: String
        get() {
            if (isSynthetic) return "Patch $patchNumber of $patchCount"
            val start = newStart ?: return header
            val count = (newCount ?: 1).coerceAtLeast(1)
            return if (count == 1) "Line $start" else "Lines $start-${start + count - 1}"
        }
}

data class DiffLine(
    val id: Int,
    val kind: DiffLineKind,
    val text: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
) {
    val gutterLabel: String
        get() = when (kind) {
            DiffLineKind.Deletion -> oldLineNumber
            else -> newLineNumber
        }?.toString().orEmpty()
}

enum class DiffLineKind {
    Addition,
    Deletion,
    Context,
}

object DiffHunkParser {
    fun parse(raw: String): List<DiffHunk> {
        if (raw.isEmpty()) return emptyList()
        val allLines = raw.split('\n')
        val headerIndexes = allLines.indices.filter { allLines[it].startsWith("@@") }

        if (headerIndexes.isEmpty()) {
            val groups = mutableListOf<List<String>>()
            var current = mutableListOf<String>()
            allLines.forEach { line ->
                if (line.startsWith("diff --git")) {
                    if (current.isNotEmpty()) groups += current
                    current = mutableListOf()
                } else if (isPatchLine(line)) {
                    current += line
                }
            }
            if (current.isNotEmpty()) groups += current
            return groups.mapIndexed { index, lines ->
                makeHunk(
                    id = index,
                    header = "",
                    rawLines = lines,
                    synthetic = true,
                    patchNumber = index + 1,
                    patchCount = groups.size,
                )
            }
        }

        return headerIndexes.mapIndexed { offset, index ->
            val end = if (offset + 1 < headerIndexes.size) headerIndexes[offset + 1] else allLines.size
            makeHunk(
                id = offset,
                header = allLines[index],
                rawLines = allLines.subList(index + 1, end),
                synthetic = false,
                patchNumber = offset + 1,
                patchCount = headerIndexes.size,
            )
        }
    }

    private fun makeHunk(
        id: Int,
        header: String,
        rawLines: List<String>,
        synthetic: Boolean,
        patchNumber: Int,
        patchCount: Int,
    ): DiffHunk {
        val range = parseRange(header)
        var oldLine = range.oldStart
        var newLine = range.newStart
        val lines = rawLines.mapIndexed { index, rawLine ->
            val kind = rawLine.kind()
            val isMarker = rawLine.startsWith("\\")
            val line = DiffLine(
                id = index,
                kind = kind,
                text = rawLine,
                oldLineNumber = if (isMarker || kind == DiffLineKind.Addition) null else oldLine,
                newLineNumber = if (isMarker || kind == DiffLineKind.Deletion) null else newLine,
            )
            if (!isMarker && kind != DiffLineKind.Addition) oldLine = oldLine?.plus(1)
            if (!isMarker && kind != DiffLineKind.Deletion) newLine = newLine?.plus(1)
            line
        }
        return DiffHunk(
            id = id,
            header = header,
            lines = lines,
            isSynthetic = synthetic,
            patchNumber = patchNumber,
            patchCount = patchCount,
            newStart = range.newStart,
            newCount = range.newCount,
        )
    }

    private fun parseRange(header: String): DiffRange {
        val pieces = header.split(' ')
        if (pieces.size < 3) return DiffRange(null, null, null)
        val old = parseRangeToken(pieces[1])
        val new = parseRangeToken(pieces[2])
        return DiffRange(old.first, new.first, new.second)
    }

    private fun parseRangeToken(token: String): Pair<Int?, Int?> {
        val cleaned = token.drop(1)
        val values = cleaned.split(',', limit = 2).mapNotNull { it.toIntOrNull() }
        return values.firstOrNull() to (values.getOrNull(1) ?: 1)
    }

    private fun isPatchLine(line: String): Boolean {
        val first = line.firstOrNull() ?: return false
        if (line.startsWith("+++ b/") || line == "+++ /dev/null") return false
        if (line.startsWith("--- a/") || line == "--- /dev/null") return false
        return first == '+' || first == '-' || first == ' ' || first == '\\'
    }

    private fun String.kind(): DiffLineKind =
        when (firstOrNull()) {
            '+' -> DiffLineKind.Addition
            '-' -> DiffLineKind.Deletion
            else -> DiffLineKind.Context
        }

    private data class DiffRange(
        val oldStart: Int?,
        val newStart: Int?,
        val newCount: Int?,
    )
}

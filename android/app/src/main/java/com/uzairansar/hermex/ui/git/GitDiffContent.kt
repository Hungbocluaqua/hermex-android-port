package com.uzairansar.hermex.ui.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uzairansar.hermex.core.model.DiffHunk
import com.uzairansar.hermex.core.model.DiffHunkParser
import com.uzairansar.hermex.core.model.DiffLine
import com.uzairansar.hermex.core.model.DiffLineKind
import com.uzairansar.hermex.core.model.GitDiffResponse
import com.uzairansar.hermex.ui.theme.HermexPillButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HermexGitDiffContent(diff: GitDiffResponse) {
    when {
        diff.binary == true -> Text("Binary file changed.", style = MaterialTheme.typography.bodySmall)
        diff.isTooLarge -> Text("Diff too large to show.", style = MaterialTheme.typography.bodySmall)
        diff.diff.isNullOrBlank() -> Text("No changes.", style = MaterialTheme.typography.bodySmall)
        else -> {
            val hunks by produceState<List<DiffHunk>?>(initialValue = null, diff.diff) {
                value = withContext(Dispatchers.Default) { DiffHunkParser.parse(diff.diff.orEmpty()) }
            }
            val resolvedHunks = hunks
            if (resolvedHunks == null) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else if (resolvedHunks.isEmpty()) {
                Text("No changes.", style = MaterialTheme.typography.bodySmall)
            } else {
                var collapsedHunks by remember(diff.diff) { mutableStateOf(emptySet<Int>()) }
                val additions = diff.additions ?: resolvedHunks.sumOf { it.additions }
                val deletions = diff.deletions ?: resolvedHunks.sumOf { it.deletions }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("1 file changed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text("+$additions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    Text("-$deletions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    HermexPillButton(
                        if (collapsedHunks.size == resolvedHunks.size) "Expand all" else "Collapse all",
                        onClick = {
                            collapsedHunks = if (collapsedHunks.size == resolvedHunks.size) {
                                emptySet()
                            } else {
                                resolvedHunks.map { it.id }.toSet()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    var renderedLineCount = 0
                    resolvedHunks.forEach { hunk ->
                        HermexDiffHunkHeader(
                            hunk = hunk,
                            collapsed = hunk.id in collapsedHunks,
                            onToggle = {
                                collapsedHunks = if (hunk.id in collapsedHunks) {
                                    collapsedHunks - hunk.id
                                } else {
                                    collapsedHunks + hunk.id
                                }
                            },
                        )
                        if (hunk.id !in collapsedHunks) {
                            val remaining = (MAX_RENDERED_DIFF_LINES - renderedLineCount).coerceAtLeast(0)
                            hunk.lines.take(remaining).forEach { line -> HermexDiffLineRow(line) }
                            renderedLineCount += minOf(hunk.lines.size, remaining)
                        }
                    }
                    if (resolvedHunks.sumOf { it.lines.size } > MAX_RENDERED_DIFF_LINES) {
                        Text(
                            "Diff display limited to $MAX_RENDERED_DIFF_LINES lines.",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HermexDiffHunkHeader(
    hunk: DiffHunk,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = hunk.displayLabel
                stateDescription = if (collapsed) "Collapsed" else "Expanded"
            }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (collapsed) ">" else "v", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
        Text(hunk.displayLabel, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
        Text("+${hunk.additions}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
        Text("-${hunk.deletions}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
    }
}

private const val MAX_RENDERED_DIFF_LINES = 2_000

@Composable
private fun HermexDiffLineRow(line: DiffLine) {
    val rowColor = when (line.kind) {
        DiffLineKind.Addition -> Color(0xFF34C759).copy(alpha = 0.16f)
        DiffLineKind.Deletion -> Color(0xFFFF3B30).copy(alpha = 0.16f)
        DiffLineKind.Context -> MaterialTheme.colorScheme.background
    }
    val gutterColor = when (line.kind) {
        DiffLineKind.Addition -> Color(0xFF34C759).copy(alpha = 0.24f)
        DiffLineKind.Deletion -> Color(0xFFFF3B30).copy(alpha = 0.24f)
        DiffLineKind.Context -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.gutterLabel,
            modifier = Modifier
                .width(48.dp)
                .background(gutterColor)
                .padding(end = 8.dp, top = 3.dp, bottom = 3.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
        )
        Text(
            text = line.text.ifEmpty { " " },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = if (line.kind == DiffLineKind.Context) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}

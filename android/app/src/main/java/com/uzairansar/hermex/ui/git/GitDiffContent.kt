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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uzairansar.hermex.core.model.DiffHunk
import com.uzairansar.hermex.core.model.DiffHunkParser
import com.uzairansar.hermex.core.model.DiffLine
import com.uzairansar.hermex.core.model.DiffLineKind
import com.uzairansar.hermex.core.model.GitDiffResponse
import com.uzairansar.hermex.ui.theme.HermexPillButton

@Composable
fun HermexGitDiffContent(diff: GitDiffResponse) {
    when {
        diff.binary == true -> Text("Binary file changed.", style = MaterialTheme.typography.bodySmall)
        diff.isTooLarge -> Text("Diff too large to show.", style = MaterialTheme.typography.bodySmall)
        diff.diff.isNullOrBlank() -> Text("No changes.", style = MaterialTheme.typography.bodySmall)
        else -> {
            val hunks = remember(diff.diff) { DiffHunkParser.parse(diff.diff.orEmpty()) }
            if (hunks.isEmpty()) {
                Text("No changes.", style = MaterialTheme.typography.bodySmall)
            } else {
                var collapsedHunks by remember(diff.diff) { mutableStateOf(emptySet<Int>()) }
                val additions = diff.additions ?: hunks.sumOf { it.additions }
                val deletions = diff.deletions ?: hunks.sumOf { it.deletions }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("1 file changed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text("+$additions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    Text("-$deletions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    HermexPillButton(
                        if (collapsedHunks.size == hunks.size) "Expand all" else "Collapse all",
                        onClick = {
                            collapsedHunks = if (collapsedHunks.size == hunks.size) {
                                emptySet()
                            } else {
                                hunks.map { it.id }.toSet()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    hunks.forEach { hunk ->
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
                            hunk.lines.forEach { line -> HermexDiffLineRow(line) }
                        }
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
            .clickable(onClick = onToggle)
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

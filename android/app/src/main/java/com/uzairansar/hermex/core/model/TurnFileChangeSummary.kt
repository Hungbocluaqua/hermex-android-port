package com.uzairansar.hermex.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class TurnFileChange(
    val path: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val action: TurnFileAction = TurnFileAction.Edited,
    val status: String? = null,
    val gitFile: GitFileChange? = null,
) {
    val displayStatus: String
        get() = status?.takeIf { it.isNotBlank() } ?: action.label
}

enum class TurnFileAction(val label: String) {
    Edited("Modified"),
    Added("Added"),
    Deleted("Deleted"),
    Renamed("Renamed"),
}

data class TurnFileChangeSummary(
    val changes: List<TurnFileChange> = emptyList(),
) {
    val fileCount: Int get() = changes.size
    val hasChanges: Boolean get() = changes.isNotEmpty()
    val totalAdditions: Int get() = changes.sumOf { it.additions }
    val totalDeletions: Int get() = changes.sumOf { it.deletions }
    val diffFiles: List<GitFileChange> get() = changes.mapNotNull { it.gitFile }
}

object TurnFileChangeAggregator {
    fun summarize(toolCalls: List<ToolCall>, statusFiles: List<GitFileChange>): TurnFileChangeSummary {
        if (toolCalls.isEmpty()) return TurnFileChangeSummary()

        val orderedPaths = mutableListOf<String>()
        val actionByPath = linkedMapOf<String, TurnFileAction>()
        toolCalls.forEach { tool ->
            candidates(tool).forEach { candidate ->
                val path = normalize(candidate.path) ?: return@forEach
                val existing = actionByPath[path]
                if (existing == null) {
                    orderedPaths += path
                    actionByPath[path] = candidate.action
                } else if (existing == TurnFileAction.Edited && candidate.action != TurnFileAction.Edited) {
                    actionByPath[path] = candidate.action
                }
            }
        }

        return TurnFileChangeSummary(
            changes = orderedPaths.map { path ->
                val action = actionByPath[path] ?: TurnFileAction.Edited
                val match = matchingFile(path, statusFiles)
                TurnFileChange(
                    path = path,
                    additions = match?.additions ?: 0,
                    deletions = match?.deletions ?: 0,
                    action = action,
                    status = match?.status,
                    gitFile = match,
                )
            },
        )
    }

    fun latestTurnToolCalls(messages: List<ChatMessage>, completedGroups: List<ToolCallGroup>): List<ToolCall> {
        if (completedGroups.isEmpty()) return emptyList()
        val lastUserIndex = messages.indexOfLast { message ->
            message.role == "user" && (message.displayText.isNotBlank() || message.attachments?.isNotEmpty() == true)
        }
        val assistantIndices = messages.indices
            .filter { index -> index > lastUserIndex && messages[index].role == "assistant" }
            .toSet()
        if (assistantIndices.isEmpty()) return emptyList()
        return completedGroups
            .filter { it.afterMessageIndex in assistantIndices }
            .flatMap { it.tools }
    }

    fun latestAssistantIndex(messages: List<ChatMessage>): Int? =
        messages.indexOfLast { it.role == "assistant" }.takeIf { it >= 0 }

    fun normalize(raw: String): String? {
        var path = raw.trim()
            .trim('`', '"', '\'', '<', '>', '(', ')', '[', ']', '{', '}')
            .trim()
        if (path.isBlank() || path.length > 240 || "://" in path) return null
        if (path.startsWith("~/")) path = path.drop(2)
        while (path.startsWith("./")) path = path.drop(2)
        path = path.trim()
        if (path.isBlank() || isIgnored(path)) return null
        return path
    }

    private fun candidates(tool: ToolCall): List<PathCandidate> {
        val name = tool.name?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val action = actionForTool(name) ?: return emptyList()
        val args = tool.args.orEmpty()

        if (action == TurnFileAction.Renamed) {
            val destination = firstString(args, "destination", "path", "file_path", "filename")
            return destination?.let { listOf(PathCandidate(it, action)) }.orEmpty()
        }

        val candidates = mutableListOf<PathCandidate>()
        listOf("path", "file_path", "filename").forEach { key ->
            args[key].stringOrNull()?.let { candidates += PathCandidate(it, action) }
        }

        (args["paths"] as? JsonArray)?.forEach { item ->
            item.stringOrNull()?.let { candidates += PathCandidate(it, action) }
        }

        (args["edits"] as? JsonArray)?.forEach { item ->
            val path = (item as? JsonObject)?.get("path").stringOrNull()
            if (path != null) candidates += PathCandidate(path, action)
        }

        return candidates
    }

    private fun actionForTool(name: String): TurnFileAction? =
        when (name) {
            "create_file" -> TurnFileAction.Added
            "remove_file", "delete_file", "mcp_filesystem_remove_file" -> TurnFileAction.Deleted
            "move_file", "rename_file", "mcp_filesystem_move_file" -> TurnFileAction.Renamed
            "write_file", "patch", "edit_file", "mcp_filesystem_write_file", "mcp_filesystem_edit_file" -> TurnFileAction.Edited
            else -> null
        }

    private fun firstString(args: Map<String, JsonElement>, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> args[key].stringOrNull() }

    private fun matchingFile(path: String, files: List<GitFileChange>): GitFileChange? =
        files.firstOrNull { it.displayPath() == path }
            ?: files.firstOrNull { representsSameFile(it.displayPath(), path) }

    private fun representsSameFile(lhs: String, rhs: String): Boolean {
        if (lhs.isBlank() || rhs.isBlank()) return false
        if (lhs == rhs) return true
        if (lhs.startsWith("/") && lhs.endsWith("/$rhs")) return true
        if (rhs.startsWith("/") && rhs.endsWith("/$lhs")) return true
        return false
    }

    private fun isIgnored(path: String): Boolean =
        path.split('/').any { it in ignoredComponents }

    private val ignoredComponents = setOf(
        ".git",
        ".hg",
        ".svn",
        "node_modules",
        ".venv",
        "venv",
        "__pycache__",
        "dist",
        "build",
        ".next",
        ".cache",
    )

    private data class PathCandidate(
        val path: String,
        val action: TurnFileAction,
    )
}

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun GitFileChange.displayPath(): String =
    TurnFileChangeAggregator.normalize(path ?: workspacePath.orEmpty()) ?: path ?: workspacePath.orEmpty()

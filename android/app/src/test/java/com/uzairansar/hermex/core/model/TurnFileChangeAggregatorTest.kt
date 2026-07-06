package com.uzairansar.hermex.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnFileChangeAggregatorTest {
    @Test
    fun extractsMutatingToolPathsAndJoinsGitStatus() {
        val summary = TurnFileChangeAggregator.summarize(
            toolCalls = listOf(
                ToolCall(
                    name = "edit_file",
                    args = mapOf("path" to JsonPrimitive("./app/src/Main.kt")),
                ),
                ToolCall(
                    name = "create_file",
                    args = mapOf("filename" to JsonPrimitive("docs/new.md")),
                ),
            ),
            statusFiles = listOf(
                GitFileChange(path = "app/src/Main.kt", status = "modified", additions = 4, deletions = 1),
                GitFileChange(path = "docs/new.md", status = "untracked", additions = 12, deletions = 0),
            ),
        )

        assertEquals(2, summary.fileCount)
        assertEquals(16, summary.totalAdditions)
        assertEquals(1, summary.totalDeletions)
        assertEquals("app/src/Main.kt", summary.changes[0].path)
        assertEquals("modified", summary.changes[0].displayStatus)
        assertEquals("docs/new.md", summary.changes[1].path)
        assertEquals("untracked", summary.changes[1].displayStatus)
    }

    @Test
    fun moveToolUsesDestinationOnly() {
        val summary = TurnFileChangeAggregator.summarize(
            toolCalls = listOf(
                ToolCall(
                    name = "rename_file",
                    args = mapOf(
                        "source" to JsonPrimitive("old.kt"),
                        "destination" to JsonPrimitive("new.kt"),
                    ),
                ),
            ),
            statusFiles = listOf(GitFileChange(path = "new.kt", status = "renamed")),
        )

        assertEquals(listOf("new.kt"), summary.changes.map { it.path })
        assertEquals(TurnFileAction.Renamed, summary.changes.single().action)
    }

    @Test
    fun extractsPathsArrayAndEditObjects() {
        val summary = TurnFileChangeAggregator.summarize(
            toolCalls = listOf(
                ToolCall(
                    name = "patch",
                    args = mapOf(
                        "paths" to JsonArray(listOf(JsonPrimitive("a.kt"), JsonPrimitive("b.kt"))),
                        "edits" to JsonArray(
                            listOf(
                                buildJsonObject { put("path", "c.kt") },
                            ),
                        ),
                    ),
                ),
            ),
            statusFiles = emptyList(),
        )

        assertEquals(listOf("a.kt", "b.kt", "c.kt"), summary.changes.map { it.path })
    }

    @Test
    fun ignoresReadToolsUrlsAndVendorPaths() {
        val summary = TurnFileChangeAggregator.summarize(
            toolCalls = listOf(
                ToolCall(name = "read_file", args = mapOf("path" to JsonPrimitive("src/A.kt"))),
                ToolCall(name = "edit_file", args = mapOf("path" to JsonPrimitive("https://example.com/file.kt"))),
                ToolCall(name = "edit_file", args = mapOf("path" to JsonPrimitive("node_modules/pkg/index.js"))),
            ),
            statusFiles = emptyList(),
        )

        assertEquals(false, summary.hasChanges)
    }

    @Test
    fun matchesAbsoluteAndRelativeStatusPaths() {
        val summary = TurnFileChangeAggregator.summarize(
            toolCalls = listOf(ToolCall(name = "edit_file", args = mapOf("path" to JsonPrimitive("src/A.kt")))),
            statusFiles = listOf(
                GitFileChange(path = "/Users/me/project/src/A.kt", status = "modified", additions = 2, deletions = 3),
            ),
        )

        assertEquals(2, summary.changes.single().additions)
        assertEquals(3, summary.changes.single().deletions)
    }

    @Test
    fun latestTurnToolCallsUsesAssistantGroupsAfterLastUserMessage() {
        val messages = listOf(
            ChatMessage(role = "user", content = "first"),
            ChatMessage(role = "assistant", content = "old"),
            ChatMessage(role = "user", content = "new"),
            ChatMessage(role = "assistant", content = "working"),
            ChatMessage(role = "assistant", content = "done"),
        )
        val oldTool = ToolCall(name = "edit_file", args = mapOf("path" to JsonPrimitive("old.kt")))
        val newTool = ToolCall(name = "edit_file", args = mapOf("path" to JsonPrimitive("new.kt")))

        val calls = TurnFileChangeAggregator.latestTurnToolCalls(
            messages = messages,
            completedGroups = listOf(
                ToolCallGroup(afterMessageIndex = 1, tools = listOf(oldTool)),
                ToolCallGroup(afterMessageIndex = 3, tools = listOf(newTool)),
            ),
        )

        assertEquals(listOf(newTool), calls)
        assertEquals(4, TurnFileChangeAggregator.latestAssistantIndex(messages))
        assertNull(TurnFileChangeAggregator.latestAssistantIndex(listOf(ChatMessage(role = "user", content = "only"))))
    }
}

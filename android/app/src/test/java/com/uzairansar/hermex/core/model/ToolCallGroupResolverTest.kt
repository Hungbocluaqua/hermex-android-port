package com.uzairansar.hermex.core.model

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolCallGroupResolverTest {
    @Test
    fun anchorsPersistedToolCallsToAssistantMessage() {
        val messages = listOf(
            ChatMessage(role = "user", content = "Please inspect the repo"),
            ChatMessage(role = "assistant", content = "I'll check."),
            ChatMessage(role = "tool", content = "git status output", toolCallId = "tool-1"),
        )

        val groups = ToolCallGroupResolver.groups(
            messages = messages,
            messagesOffset = 0,
            persistedToolCalls = listOf(
                PersistedToolCall(
                    name = "git status",
                    snippet = "clean",
                    tid = "tool-1",
                    assistantMsgIdx = 1,
                    args = mapOf("short" to JsonPrimitive(true)),
                ),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(1, groups.single().afterMessageIndex)
        assertEquals("tool-1", groups.single().tools.single().id)
        assertEquals("git status", groups.single().tools.single().name)
        assertEquals("clean", groups.single().tools.single().preview)
    }

    @Test
    fun anchorsToolResultIndexToPreviousAssistantInTurn() {
        val messages = listOf(
            ChatMessage(role = "user", content = "Run tests"),
            ChatMessage(role = "assistant", content = "Running tests."),
            ChatMessage(role = "tool", content = "Tests passed", toolCallId = "tool-1"),
            ChatMessage(role = "assistant", content = "Done."),
        )

        val groups = ToolCallGroupResolver.groups(
            messages = messages,
            messagesOffset = 10,
            persistedToolCalls = listOf(
                PersistedToolCall(
                    name = "gradle test",
                    snippet = "BUILD SUCCESSFUL",
                    tid = "tool-1",
                    assistantMsgIdx = 12,
                ),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(1, groups.single().afterMessageIndex)
        assertEquals("gradle test", groups.single().tools.single().name)
    }
}

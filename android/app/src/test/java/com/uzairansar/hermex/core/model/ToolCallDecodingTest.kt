package com.uzairansar.hermex.core.model

import com.uzairansar.hermex.core.network.HermesJson
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCallDecodingTest {

    @Test
    fun decodesOpenAiStandardNestedFunctionEnvelope() {
        val json = """
            {
              "id": "call_abc12345",
              "type": "function",
              "function": {
                "name": "run_shell_command",
                "arguments": "{\"command\":\"git status -s\",\"timeout\":30}"
              }
            }
        """.trimIndent()

        val toolCall = HermesJson.decodeFromString<ToolCall>(json)

        assertEquals("call_abc12345", toolCall.id)
        assertEquals("function", toolCall.type)
        assertNotNull(toolCall.function)
        assertEquals("run_shell_command", toolCall.function?.name)
        assertEquals("{\"command\":\"git status -s\",\"timeout\":30}", toolCall.function?.arguments)
        assertNull(toolCall.name)
        assertEquals("run_shell_command", toolCall.displayName)

        // Parsed structured arguments
        assertNotNull(toolCall.args)
        assertEquals(JsonPrimitive("git status -s"), toolCall.args?.get("command"))
        assertEquals(JsonPrimitive(30), toolCall.args?.get("timeout"))
    }

    @Test
    fun preservesCompatibilityWithHermesInternalShape() {
        val json = """
            {
              "id": "tool-1",
              "name": "edit_file",
              "preview": "Replaced lines 10-20 in Dto.kt",
              "args": {
                "path": "Dto.kt",
                "replace": true
              },
              "result": "Success",
              "is_error": false
            }
        """.trimIndent()

        val toolCall = HermesJson.decodeFromString<ToolCall>(json)

        assertEquals("tool-1", toolCall.id)
        assertEquals("edit_file", toolCall.name)
        assertEquals("Replaced lines 10-20 in Dto.kt", toolCall.preview)
        assertEquals("edit_file", toolCall.displayName)
        assertNotNull(toolCall.args)
        assertEquals(JsonPrimitive("Dto.kt"), toolCall.args?.get("path"))
        assertEquals(JsonPrimitive(true), toolCall.args?.get("replace"))
        assertEquals(JsonPrimitive("Success"), toolCall.result)
        assertEquals(false, toolCall.isError)
    }

    @Test
    fun displayNameFollowsPrecedenceHierarchy() {
        // 1. Top-level Hermes name remains authoritative when both shapes are present
        val nestedWithTopLevel = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_1","name":"legacy_name","preview":"preview text","function":{"name":"nested_func"}}""",
        )
        assertEquals("legacy_name", nestedWithTopLevel.displayName)

        // 2. Nested function name is used when top-level name is absent/blank
        val topLevelOnly = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_2","preview":"preview text","function":{"name":"nested_func"}}""",
        )
        assertEquals("nested_func", topLevelOnly.displayName)

        // 3. Preview takes precedence over id when both name shapes are absent/blank
        val previewOnly = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_3","preview":"Line 1 of preview\nLine 2"}""",
        )
        assertEquals("Line 1 of preview", previewOnly.displayName)

        // 4. Preview longer than 48 chars is truncated to 48 chars
        val longPreview = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_4","preview":"This is a very long preview line that exceeds forty-eight characters total"}""",
        )
        assertEquals("This is a very long preview line that exceeds fo", longPreview.displayName)

        // 5. id is used when function.name, name, and preview are absent
        val idOnly = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_opaque_id_123"}""",
        )
        assertEquals("call_opaque_id_123", idOnly.displayName)

        // 6. Default "Tool" when all are absent or blank
        val emptyTool = HermesJson.decodeFromString<ToolCall>("""{}""")
        assertEquals("Tool", emptyTool.displayName)
    }

    @Test
    fun parsesFunctionArgumentsWhenTopLevelArgsAbsent() {
        // Valid JSON string object
        val stringArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"c1","function":{"name":"f","arguments":"{\"key\":\"value\",\"num\":42,\"flag\":true}"}}""",
        )
        assertEquals(JsonPrimitive("value"), stringArgs.args?.get("key"))
        assertEquals(JsonPrimitive(42), stringArgs.args?.get("num"))
        assertEquals(JsonPrimitive(true), stringArgs.args?.get("flag"))

        // Direct JSON object (in case server sends object directly)
        val objectArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"c2","function":{"name":"f","arguments":{"direct":"object"}}}""",
        )
        assertEquals(JsonPrimitive("object"), objectArgs.args?.get("direct"))

        // Top-level args take precedence over nested function arguments
        val bothArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"c3","args":{"source":"top"},"function":{"name":"f","arguments":"{\"source\":\"nested\"}"}}""",
        )
        assertEquals(JsonPrimitive("top"), bothArgs.args?.get("source"))
    }

    @Test
    fun handlesMalformedAndNonObjectFunctionArgumentsWithoutCrashing() {
        // Malformed JSON string
        val malformed = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_1","function":{"name":"search","arguments":"{\"query\": unclosed string..."}}""",
        )
        assertEquals("search", malformed.displayName)
        assertEquals("{\"query\": unclosed string...", malformed.function?.arguments)
        assertNull(malformed.args)

        // Non-object JSON: array
        val arrayArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_2","function":{"name":"search","arguments":"[1, 2, 3]"}}""",
        )
        assertEquals("search", arrayArgs.displayName)
        assertNull(arrayArgs.args)

        // Non-object JSON: primitive string
        val stringPrimitiveArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_3","function":{"name":"search","arguments":"\"plain text string\""}}""",
        )
        assertEquals("search", stringPrimitiveArgs.displayName)
        assertNull(stringPrimitiveArgs.args)

        // Non-object JSON: number
        val numberArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_4","function":{"name":"search","arguments":"12345"}}""",
        )
        assertEquals("search", numberArgs.displayName)
        assertNull(numberArgs.args)

        // Non-object JSON: boolean
        val boolArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_5","function":{"name":"search","arguments":"true"}}""",
        )
        assertEquals("search", boolArgs.displayName)
        assertNull(boolArgs.args)

        // Empty string
        val emptyArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_6","function":{"name":"search","arguments":""}}""",
        )
        assertEquals("search", emptyArgs.displayName)
        assertNull(emptyArgs.args)

        // Whitespace-only string
        val whitespaceArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_7","function":{"name":"search","arguments":"   "}}""",
        )
        assertEquals("search", whitespaceArgs.displayName)
        assertNull(whitespaceArgs.args)

        // Null arguments
        val nullArgs = HermesJson.decodeFromString<ToolCall>(
            """{"id":"call_8","function":{"name":"search","arguments":null}}""",
        )
        assertEquals("search", nullArgs.displayName)
        assertNull(nullArgs.args)
    }

    @Test
    fun decodesChatMessageWithToolCallsAndRoundTrips() {
        val json = """
            {
              "role": "assistant",
              "content": "Running the tool now...",
              "tool_calls": [
                {
                  "id": "call_fn_1",
                  "type": "function",
                  "function": {
                    "name": "get_weather",
                    "arguments": "{\"location\":\"San Francisco\",\"unit\":\"celsius\"}"
                  },
                  "extra_future_field": "tolerant"
                },
                {
                  "id": "call_fn_2",
                  "name": "legacy_tool",
                  "preview": "Legacy preview",
                  "args": {"count": 5}
                }
              ]
            }
        """.trimIndent()

        val message = HermesJson.decodeFromString<ChatMessage>(json)

        assertEquals(2, message.toolCalls?.size)

        val first = message.toolCalls?.get(0)
        assertEquals("call_fn_1", first?.id)
        assertEquals("function", first?.type)
        assertEquals("get_weather", first?.function?.name)
        assertEquals("get_weather", first?.displayName)
        assertEquals(JsonPrimitive("San Francisco"), first?.args?.get("location"))

        val second = message.toolCalls?.get(1)
        assertEquals("call_fn_2", second?.id)
        assertEquals("legacy_tool", second?.name)
        assertEquals("legacy_tool", second?.displayName)
        assertEquals("Legacy preview", second?.preview)
        assertEquals(JsonPrimitive(5), second?.args?.get("count"))

        // Roundtrip serialization
        val encoded = HermesJson.encodeToString(message)
        val decodedRoundtrip = HermesJson.decodeFromString<ChatMessage>(encoded)

        assertEquals("get_weather", decodedRoundtrip.toolCalls?.get(0)?.displayName)
        assertEquals(JsonPrimitive("San Francisco"), decodedRoundtrip.toolCalls?.get(0)?.args?.get("location"))
        assertEquals("legacy_tool", decodedRoundtrip.toolCalls?.get(1)?.displayName)
    }

    @Test
    fun persistedToolCallConvertsToToolCallWithDisplayName() {
        val persisted = PersistedToolCall(
            name = "read_file",
            snippet = "fun main() {}",
            tid = "tid-123",
            assistantMsgIdx = 4,
            args = mapOf("path" to JsonPrimitive("main.kt")),
        )

        val toolCall = persisted.toToolCall(0)

        assertEquals("tid-123", toolCall.id)
        assertEquals("read_file", toolCall.name)
        assertEquals("fun main() {}", toolCall.preview)
        assertEquals("read_file", toolCall.displayName)
        assertEquals(JsonPrimitive("main.kt"), toolCall.args?.get("path"))
    }

    @Test
    fun turnFileChangeAggregatorRecognizesNestedFunctionShape() {
        val toolCall = HermesJson.decodeFromString<ToolCall>(
            """
            {
              "id": "call_edit",
              "type": "function",
              "function": {
                "name": "edit_file",
                "arguments": "{\"path\":\"src/Main.kt\"}"
              }
            }
            """.trimIndent(),
        )

        val summary = TurnFileChangeAggregator.summarize(
            toolCalls = listOf(toolCall),
            statusFiles = listOf(
                GitFileChange(path = "src/Main.kt", status = "modified", additions = 5, deletions = 2),
            ),
        )

        assertEquals(1, summary.fileCount)
        assertEquals("src/Main.kt", summary.changes.single().path)
        assertEquals(5, summary.totalAdditions)
        assertEquals(2, summary.totalDeletions)
        assertEquals(TurnFileAction.Edited, summary.changes.single().action)
    }
}

package com.uzairansar.hermex.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageActionContextResolverTest {
    @Test
    fun userContextTracksFullHistoryIndexAndKeepCount() {
        val context = MessageActionContextResolver.contextFor(
            message = ChatMessage(id = "local-1", role = "user", content = "Hello"),
            visibleIndex = 3,
            messagesOffset = 8,
        )

        requireNotNull(context)
        assertEquals(MessageActionRole.User, context.role)
        assertEquals(3, context.visibleIndex)
        assertEquals(11, context.fullHistoryIndex)
        assertEquals(12, context.keepCountThroughMessage)
        assertEquals("local-1", context.messageId)
        assertEquals("Hello", context.copyText)
        assertNull(context.listenText)
    }

    @Test
    fun assistantContextNormalizesListenText() {
        val context = MessageActionContextResolver.contextFor(
            message = ChatMessage(
                id = "assistant-1",
                role = "assistant",
                content = """
                    ## Result
                    - See [the docs](https://example.com)
                    > quoted `code`
                """.trimIndent(),
            ),
            visibleIndex = 1,
            messagesOffset = 0,
        )

        requireNotNull(context)
        assertEquals(MessageActionRole.Assistant, context.role)
        assertEquals("Result\nSee the docs\nquoted code", context.listenText)
    }

    @Test
    fun unsupportedRowsDoNotExposeActions() {
        assertNull(
            MessageActionContextResolver.contextFor(
                message = ChatMessage(role = "tool", content = "output"),
                visibleIndex = 0,
                messagesOffset = 0,
            ),
        )
        assertNull(
            MessageActionContextResolver.contextFor(
                message = ChatMessage(role = "assistant", content = "   "),
                visibleIndex = 0,
                messagesOffset = 0,
            ),
        )
    }

    @Test
    fun precedingUserMessageSkipsAssistantAndBlankUserRows() {
        val messages = listOf(
            ChatMessage(role = "user", content = "first"),
            ChatMessage(role = "assistant", content = "answer"),
            ChatMessage(role = "user", content = "   "),
            ChatMessage(role = "assistant", content = "retry me"),
        )

        assertEquals(
            "first",
            MessageActionContextResolver.precedingUserMessageText(messages, beforeVisibleIndex = 3),
        )
    }
}

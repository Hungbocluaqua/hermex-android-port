package com.uzairansar.hermex.core.model

enum class MessageActionRole {
    User,
    Assistant,
}

data class MessageActionContext(
    val role: MessageActionRole,
    val visibleIndex: Int,
    val fullHistoryIndex: Int,
    val keepCountThroughMessage: Int,
    val messageId: String,
    val copyText: String,
    val listenText: String?,
)

object MessageActionContextResolver {
    fun contextFor(
        message: ChatMessage,
        visibleIndex: Int,
        messagesOffset: Int,
    ): MessageActionContext? {
        if (visibleIndex < 0) return null
        val role = when (message.role) {
            "user" -> MessageActionRole.User
            "assistant" -> MessageActionRole.Assistant
            else -> return null
        }
        val content = message.displayText.trim()
        if (content.isEmpty()) return null

        val fullHistoryIndex = messagesOffset.coerceAtLeast(0) + visibleIndex
        return MessageActionContext(
            role = role,
            visibleIndex = visibleIndex,
            fullHistoryIndex = fullHistoryIndex,
            keepCountThroughMessage = fullHistoryIndex + 1,
            messageId = message.messageId?.takeIf { it.isNotBlank() }
                ?: message.id?.takeIf { it.isNotBlank() }
                ?: "$role-$fullHistoryIndex",
            copyText = content,
            listenText = if (role == MessageActionRole.Assistant) {
                SpeechTextNormalizer.normalizedAssistantText(content)
            } else {
                null
            },
        )
    }

    fun precedingUserMessageText(
        messages: List<ChatMessage>,
        beforeVisibleIndex: Int,
    ): String? {
        if (messages.isEmpty() || beforeVisibleIndex <= 0) return null
        val startIndex = (beforeVisibleIndex - 1).coerceAtMost(messages.lastIndex)
        for (index in startIndex downTo 0) {
            val message = messages[index]
            if (message.role != "user") continue
            val text = message.displayText.trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }
}

object SpeechTextNormalizer {
    fun normalizedAssistantText(text: String): String? {
        val normalized = text
            .replace("`", "")
            .lineSequence()
            .map { line ->
                line
                    .replace(headingRegex, "")
                    .replace(listRegex, "")
                    .replace(blockquoteRegex, "")
                    .replace(markdownLinkRegex, "$1")
            }
            .joinToString("\n")
            .replace(excessiveBlankLinesRegex, "\n\n")
            .trim()

        return normalized.takeIf { it.isNotEmpty() }
    }

    private val headingRegex = Regex("""^\s{0,3}#{1,6}\s*""")
    private val listRegex = Regex("""^\s{0,3}[-*+]\s+""")
    private val blockquoteRegex = Regex("""^\s{0,3}>\s?""")
    private val markdownLinkRegex = Regex("""\[([^\]]+)]\([^)]+\)""")
    private val excessiveBlankLinesRegex = Regex("""\n{3,}""")
}

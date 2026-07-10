package com.uzairansar.hermex.ui.chat

enum class ChatHapticEvent {
    None,
    MessageSent,
    ResponseCompleted,
    StreamCancelled,
}

object ChatHapticPolicy {
    fun eventForTransition(
        previousIsStreaming: Boolean,
        currentIsStreaming: Boolean,
        previousCompletionTrigger: Int,
        currentCompletionTrigger: Int,
        hasError: Boolean,
    ): ChatHapticEvent = when {
        !previousIsStreaming && currentIsStreaming -> ChatHapticEvent.MessageSent
        previousIsStreaming && !currentIsStreaming && currentCompletionTrigger > previousCompletionTrigger ->
            ChatHapticEvent.ResponseCompleted
        previousIsStreaming && !currentIsStreaming && !hasError -> ChatHapticEvent.StreamCancelled
        else -> ChatHapticEvent.None
    }
}

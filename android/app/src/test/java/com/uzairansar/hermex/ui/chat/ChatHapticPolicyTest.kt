package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHapticPolicyTest {
    @Test
    fun reportsMessageSentWhenStreamingStarts() {
        assertEquals(
            ChatHapticEvent.MessageSent,
            ChatHapticPolicy.eventForTransition(
                previousIsStreaming = false,
                currentIsStreaming = true,
                previousCompletionTrigger = 0,
                currentCompletionTrigger = 0,
                hasError = false,
            ),
        )
    }

    @Test
    fun reportsResponseCompletionWhenCompletionTriggerAdvances() {
        assertEquals(
            ChatHapticEvent.ResponseCompleted,
            ChatHapticPolicy.eventForTransition(
                previousIsStreaming = true,
                currentIsStreaming = false,
                previousCompletionTrigger = 2,
                currentCompletionTrigger = 3,
                hasError = false,
            ),
        )
    }

    @Test
    fun reportsCancellationWithoutCompletionOrError() {
        assertEquals(
            ChatHapticEvent.StreamCancelled,
            ChatHapticPolicy.eventForTransition(
                previousIsStreaming = true,
                currentIsStreaming = false,
                previousCompletionTrigger = 2,
                currentCompletionTrigger = 2,
                hasError = false,
            ),
        )
    }

    @Test
    fun suppressesErrorAndUnrelatedTransitions() {
        assertEquals(
            ChatHapticEvent.None,
            ChatHapticPolicy.eventForTransition(
                previousIsStreaming = true,
                currentIsStreaming = false,
                previousCompletionTrigger = 2,
                currentCompletionTrigger = 2,
                hasError = true,
            ),
        )
        assertEquals(
            ChatHapticEvent.None,
            ChatHapticPolicy.eventForTransition(
                previousIsStreaming = false,
                currentIsStreaming = false,
                previousCompletionTrigger = 2,
                currentCompletionTrigger = 3,
                hasError = false,
            ),
        )
    }
}

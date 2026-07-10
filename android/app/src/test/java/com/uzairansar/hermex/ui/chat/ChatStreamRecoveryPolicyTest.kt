package com.uzairansar.hermex.ui.chat

import java.util.concurrent.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamRecoveryPolicyTest {
    @Test
    fun recoversWhenActiveStreamFlowClosesWithoutCause() {
        assertTrue(
            ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                cause = null,
                activeStreamId = "stream-1",
                streamId = "stream-1",
            ),
        )
    }

    @Test
    fun ignoresIntentionalCancellationAndInactiveStreams() {
        assertFalse(
            ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                cause = CancellationException("replaced by a replay connection"),
                activeStreamId = "stream-1",
                streamId = "stream-1",
            ),
        )
        assertFalse(
            ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                cause = null,
                activeStreamId = null,
                streamId = "stream-1",
            ),
        )
        assertFalse(
            ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                cause = null,
                activeStreamId = "stream-2",
                streamId = "stream-1",
            ),
        )
    }
}

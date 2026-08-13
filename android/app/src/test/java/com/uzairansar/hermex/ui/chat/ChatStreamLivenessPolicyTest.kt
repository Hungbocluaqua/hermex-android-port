package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatStreamLivenessPolicyTest {
    @Test
    fun heartbeatFreshSemanticPauseStaysIdleWithoutPolling() {
        assertEquals(
            ChatStreamLivenessAction.None,
            action(now = 20_000L, progress = 1_000L, transport = 19_000L),
        )
    }

    @Test
    fun transportQuietThresholdPollsThenHonorsCooldown() {
        assertEquals(
            ChatStreamLivenessAction.CheckStatus,
            action(now = 13_100L, progress = 1_000L, transport = 1_000L),
        )
        assertEquals(
            ChatStreamLivenessAction.None,
            action(now = 15_000L, progress = 1_000L, transport = 1_000L, status = 13_100L),
        )
        assertEquals(
            ChatStreamLivenessAction.CheckStatus,
            action(now = 17_100L, progress = 1_000L, transport = 1_000L, status = 13_100L),
        )
    }

    @Test
    fun quietTransportForcesReconnectAtNormalAndRunningToolThresholds() {
        assertEquals(
            ChatStreamLivenessAction.ForceReconnect,
            action(now = 19_100L, progress = 1_000L, transport = 1_000L),
        )
        assertEquals(
            ChatStreamLivenessAction.CheckStatus,
            action(now = 19_100L, progress = 1_000L, transport = 1_000L, runningTool = true),
        )
        assertEquals(
            ChatStreamLivenessAction.ForceReconnect,
            action(now = 26_100L, progress = 1_000L, transport = 1_000L, runningTool = true),
        )
    }

    @Test
    fun silentInitialConnectionReconnectsButFreshHeartbeatsKeepItAlive() {
        assertEquals(
            ChatStreamLivenessAction.None,
            action(now = 17_999L, progress = null, transport = 0L),
        )
        assertEquals(
            ChatStreamLivenessAction.ForceReconnect,
            action(now = 18_000L, progress = null, transport = 0L),
        )
        assertEquals(
            ChatStreamLivenessAction.None,
            action(now = 60_000L, progress = null, transport = 59_000L),
        )
    }

    @Test
    fun pendingPromptSuppressesRecoveryAndHeartbeatNeverDemotesReconnect() {
        assertEquals(
            ChatStreamLivenessAction.None,
            action(now = 30_000L, progress = 1_000L, transport = 1_000L, pendingPrompt = true),
        )
        assertEquals(
            ActiveStreamRecoveryState.Idle,
            ChatStreamLivenessPolicy.stateAfterHeartbeat(ActiveStreamRecoveryState.Checking),
        )
        assertEquals(
            ActiveStreamRecoveryState.Reconnecting,
            ChatStreamLivenessPolicy.stateAfterHeartbeat(ActiveStreamRecoveryState.Reconnecting),
        )
    }

    private fun action(
        now: Long,
        progress: Long?,
        transport: Long?,
        status: Long? = null,
        pendingPrompt: Boolean = false,
        runningTool: Boolean = false,
    ): ChatStreamLivenessAction = ChatStreamLivenessPolicy.action(
        nowMillis = now,
        connectionStartedAtMillis = 0L,
        lastProgressAtMillis = progress,
        lastTransportActivityAtMillis = transport,
        lastStatusCheckAtMillis = status,
        hasPendingPrompt = pendingPrompt,
        hasRunningTool = runningTool,
    )
}

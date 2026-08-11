package com.uzairansar.hermex.ui.chat

internal data class ChatStreamLivenessTiming(
    val checkingIntervalMillis: Long = 5_000L,
    // The server emits SSE comment heartbeats about every five seconds. Twelve seconds
    // tolerates a delayed heartbeat while remaining below the normal reconnect threshold.
    val transportFreshIntervalMillis: Long = 12_000L,
    val reconnectIntervalMillis: Long = 18_000L,
    val runningToolReconnectIntervalMillis: Long = 25_000L,
    val statusPollCooldownMillis: Long = 4_000L,
)

internal enum class ChatStreamLivenessAction {
    None,
    CheckStatus,
    ForceReconnect,
}

internal object ChatStreamLivenessPolicy {
    fun action(
        nowMillis: Long,
        connectionStartedAtMillis: Long,
        lastProgressAtMillis: Long?,
        lastTransportActivityAtMillis: Long?,
        lastStatusCheckAtMillis: Long?,
        hasPendingPrompt: Boolean,
        hasRunningTool: Boolean,
        timing: ChatStreamLivenessTiming = ChatStreamLivenessTiming(),
    ): ChatStreamLivenessAction {
        if (hasPendingPrompt) return ChatStreamLivenessAction.None

        val reconnectInterval = if (hasRunningTool) {
            timing.runningToolReconnectIntervalMillis
        } else {
            timing.reconnectIntervalMillis
        }
        val transportAt = lastTransportActivityAtMillis ?: connectionStartedAtMillis
        val transportElapsed = (nowMillis - transportAt).coerceAtLeast(0L)

        if (lastProgressAtMillis == null) {
            return if (transportElapsed >= reconnectInterval) {
                ChatStreamLivenessAction.ForceReconnect
            } else {
                ChatStreamLivenessAction.None
            }
        }

        val progressElapsed = (nowMillis - lastProgressAtMillis).coerceAtLeast(0L)
        if (progressElapsed < timing.checkingIntervalMillis) return ChatStreamLivenessAction.None
        if (transportElapsed < timing.transportFreshIntervalMillis) return ChatStreamLivenessAction.None
        if (
            lastStatusCheckAtMillis != null &&
            nowMillis - lastStatusCheckAtMillis < timing.statusPollCooldownMillis
        ) {
            return ChatStreamLivenessAction.None
        }

        return if (transportElapsed >= reconnectInterval) {
            ChatStreamLivenessAction.ForceReconnect
        } else {
            ChatStreamLivenessAction.CheckStatus
        }
    }

    fun stateAfterHeartbeat(current: ActiveStreamRecoveryState): ActiveStreamRecoveryState =
        if (current == ActiveStreamRecoveryState.Checking) ActiveStreamRecoveryState.Idle else current
}

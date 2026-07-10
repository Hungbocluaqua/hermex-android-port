package com.uzairansar.hermex.ui.chat

object ChatStreamRecoveryPolicy {
    fun shouldRecoverAfterFlowCompletion(
        cause: Throwable?,
        activeStreamId: String?,
        streamId: String,
    ): Boolean = cause == null && activeStreamId == streamId
}

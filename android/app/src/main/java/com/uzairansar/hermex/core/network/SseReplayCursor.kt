package com.uzairansar.hermex.core.network

object SseReplayCursor {
    fun afterSeqFromEventId(eventId: String?): Int? {
        val trimmed = eventId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val sequenceText = trimmed.substringAfterLast(':')
        val sequence = sequenceText.toIntOrNull() ?: return null
        return sequence.coerceAtLeast(0)
    }
}

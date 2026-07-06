package com.uzairansar.hermex.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SseReplayCursorTest {
    @Test
    fun parsesPlainEventSequence() {
        assertEquals(42, SseReplayCursor.afterSeqFromEventId("42"))
    }

    @Test
    fun parsesSequenceAfterLastColon() {
        assertEquals(42, SseReplayCursor.afterSeqFromEventId("run:journal:42"))
    }

    @Test
    fun clampsNegativeSequenceToZero() {
        assertEquals(0, SseReplayCursor.afterSeqFromEventId("run:-7"))
    }

    @Test
    fun ignoresBlankOrNonNumericEventIds() {
        assertEquals(null, SseReplayCursor.afterSeqFromEventId(null))
        assertEquals(null, SseReplayCursor.afterSeqFromEventId("  "))
        assertEquals(null, SseReplayCursor.afterSeqFromEventId("run:abc"))
    }
}

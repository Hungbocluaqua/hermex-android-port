package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.data.repository.withLatestAssistantResponseSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

class ResponseSpeedTest {
    @Test
    fun formatterRejectsMissingInvalidAndNonPositiveValues() {
        assertNull(responseSpeedText(null, Locale.US))
        assertNull(responseSpeedText(Double.NaN, Locale.US))
        assertNull(responseSpeedText(Double.POSITIVE_INFINITY, Locale.US))
        assertNull(responseSpeedText(0.0, Locale.US))
    }

    @Test
    fun formatterUsesOneLocalizedDecimal() {
        assertEquals("18.2 t/s", responseSpeedText(18.25, Locale.US))
    }

    @Test
    fun completedSpeedAttachesOnlyToLatestVisibleAssistantMessage() {
        val messages = listOf(
            ChatMessage(role = "assistant", content = "Earlier"),
            ChatMessage(role = "tool", content = "tool result"),
            ChatMessage(role = "assistant", content = "Latest"),
        )

        val updated = messages.withLatestAssistantResponseSpeed(24.5)

        assertNull(updated[0].turnTokensPerSecond)
        assertNull(updated[1].turnTokensPerSecond)
        assertEquals(24.5, updated[2].turnTokensPerSecond ?: 0.0, 0.0)
        assertSame(messages, messages.withLatestAssistantResponseSpeed(Double.NaN))
    }
}

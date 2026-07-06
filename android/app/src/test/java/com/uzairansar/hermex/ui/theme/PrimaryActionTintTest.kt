package com.uzairansar.hermex.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryActionTintTest {
    @Test
    fun primaryActionTintAppliesOnlyWhenEnabledAndInteractive() {
        assertTrue(primaryActionTintApplies(preferenceEnabled = true, controlEnabled = true))
        assertFalse(primaryActionTintApplies(preferenceEnabled = false, controlEnabled = true))
        assertFalse(primaryActionTintApplies(preferenceEnabled = true, controlEnabled = false))
    }

    @Test
    fun colorFromHexParsesSixDigitValuesOnly() {
        assertEquals(Color(0xFF7DD3FC), hermexColorFromHex("#7DD3FC"))
        assertEquals(Color(0xFF7DD3FC), hermexColorFromHex("7dd3fc"))
        assertNull(hermexColorFromHex("#ABC"))
        assertNull(hermexColorFromHex("not-a-color"))
        assertNull(hermexColorFromHex(null))
    }

    @Test
    fun primaryActionTintChoosesReadableForeground() {
        assertEquals(Color.Black, primaryActionContentColorFor(Color(0xFFFFD700)))
        assertEquals(Color.White, primaryActionContentColorFor(Color(0xFF5B7CFF)))
    }
}

package com.uzairansar.hermex.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermexSurfaceTokensTest {
    @Test
    fun darkBackdropMatchesIosBlack() {
        assertEquals(Color.Black, DarkHermexSurfaceTokens.background)
    }

    @Test
    fun darkGlassTintIsNeutralAndTranslucent() {
        val tint = DarkHermexSurfaceTokens.glassTint

        assertEquals(tint.red, tint.green, 0.0001f)
        assertEquals(tint.green, tint.blue, 0.0001f)
        assertTrue(tint.alpha in 0.05f..0.12f)
    }
}

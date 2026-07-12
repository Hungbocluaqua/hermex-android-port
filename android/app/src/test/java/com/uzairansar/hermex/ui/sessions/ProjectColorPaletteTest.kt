package com.uzairansar.hermex.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectColorPaletteTest {
    @Test
    fun paletteMatchesIosApprovedProjectColors() {
        assertEquals(
            listOf(
                "#7cb9ff",
                "#f5c542",
                "#e94560",
                "#50c878",
                "#c084fc",
                "#fb923c",
                "#67e8f9",
                "#f472b6",
            ),
            ProjectColorPalette.map { it.hex },
        )
    }

    @Test
    fun defaultProjectColorRotatesThroughPalette() {
        assertEquals("#7cb9ff", defaultProjectColorHex(existingProjectCount = 0))
        assertEquals("#f5c542", defaultProjectColorHex(existingProjectCount = 1))
        assertEquals("#7cb9ff", defaultProjectColorHex(existingProjectCount = ProjectColorPalette.size))
    }
}

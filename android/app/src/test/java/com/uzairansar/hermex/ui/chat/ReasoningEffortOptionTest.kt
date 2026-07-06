package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningEffortOptionTest {
    @Test
    fun nilSupportedEffortsFallBackToFullStaticList() {
        val options = ReasoningEffortOption.optionsForSupportedEfforts(null)

        assertEquals(listOf("none", "minimal", "low", "medium", "high", "xhigh"), options.map { it.id })
    }

    @Test
    fun supportedEffortsAreNormalizedDeduplicatedAndKeepUnknownIds() {
        val options = ReasoningEffortOption.optionsForSupportedEfforts(
            listOf(" Low ", "LOW", "turbo", "", "Minimal"),
        )

        assertEquals(listOf("low", "turbo", "minimal"), options.map { it.id })
        assertEquals(listOf("Low", "Turbo", "Minimal"), options.map { it.title })
    }

    @Test
    fun effortControlVisibilityMatchesIosFallbackRules() {
        assertFalse(ReasoningEffortOption.showsEffortControl(supportsReasoningEffort = false, supportedEfforts = null))
        assertTrue(ReasoningEffortOption.showsEffortControl(supportsReasoningEffort = true, supportedEfforts = emptyList()))
        assertFalse(ReasoningEffortOption.showsEffortControl(supportsReasoningEffort = null, supportedEfforts = emptyList()))
        assertTrue(ReasoningEffortOption.showsEffortControl(supportsReasoningEffort = null, supportedEfforts = null))
    }

    @Test
    fun titleForUsesStaticTitlesAndCapitalizesUnknownIds() {
        assertEquals("XHigh", ReasoningEffortOption.titleFor("xhigh"))
        assertEquals("Turbo", ReasoningEffortOption.titleFor("turbo"))
        assertEquals("Reasoning", ReasoningEffortOption.titleFor(null))
    }
}

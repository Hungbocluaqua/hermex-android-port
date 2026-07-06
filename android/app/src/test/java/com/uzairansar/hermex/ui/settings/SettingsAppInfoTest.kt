package com.uzairansar.hermex.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAppInfoTest {
    @Test
    fun appLinksMirrorIosSettingsDestinations() {
        assertEquals("https://www.uzairansar.com/hermes-mobile/privacy", HermexAppLinks.PRIVACY_POLICY_URL)
        assertEquals("https://www.uzairansar.com/hermes-mobile", HermexAppLinks.SUPPORT_URL)
    }

    @Test
    fun appInfoLabelsUseUnknownFallbacks() {
        assertEquals("Unknown", displayAppVersion(null))
        assertEquals("Unknown", displayAppVersion("  "))
        assertEquals("0.1.0", displayAppVersion("0.1.0"))

        assertEquals("Unknown", displayAppBuild(null))
        assertEquals("Unknown", displayAppBuild(-1))
        assertTrue(displayAppBuild(42) == "42")
    }
}

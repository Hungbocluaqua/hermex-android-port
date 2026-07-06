package com.uzairansar.hermex

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesIntoKnownRootSurface() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasText("Control your Hermes agent from Android.") || hasText("Sessions")
        }

        assertTrue(hasText("Control your Hermes agent from Android.") || hasText("Sessions"))
    }

    private fun hasText(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
}

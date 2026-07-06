package com.uzairansar.hermex.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowPolicyTest {
    @Test
    fun primaryButtonTitlesMatchIosFlow() {
        assertEquals("Get Started", OnboardingFlowPolicy.primaryButtonTitle(0))
        assertEquals("Set Up", OnboardingFlowPolicy.primaryButtonTitle(1))
        assertEquals("Continue", OnboardingFlowPolicy.primaryButtonTitle(2))
        assertEquals("Continue", OnboardingFlowPolicy.primaryButtonTitle(3))
        assertEquals("Connect", OnboardingFlowPolicy.primaryButtonTitle(4))
    }

    @Test
    fun setupPromptMustBeCopiedBeforeMovingForwardUnlessBypassed() {
        assertTrue(OnboardingFlowPolicy.shouldShowCopyReminder(2, hasCopiedAgentPrompt = false))
        assertFalse(OnboardingFlowPolicy.shouldShowCopyReminder(2, hasCopiedAgentPrompt = true))
        assertFalse(
            OnboardingFlowPolicy.shouldShowCopyReminder(
                page = 2,
                hasCopiedAgentPrompt = false,
                hasBypassedCopyReminder = true,
            ),
        )
        assertTrue(
            OnboardingFlowPolicy.shouldInterceptForwardNavigationFromAgentPrompt(
                oldPage = 2,
                newPage = 3,
                hasCopiedAgentPrompt = false,
            ),
        )
    }

    @Test
    fun promptAndTailscaleTargetAreAndroidSpecific() {
        assertTrue(OnboardingFlowPolicy.AgentSetupPrompt.contains("Android phone"))
        assertTrue(OnboardingFlowPolicy.AgentSetupPrompt.contains("HERMES_WEBUI_PASSWORD"))
        assertTrue(OnboardingFlowPolicy.AgentSetupPrompt.contains("tailscale serve --bg 8787"))
        assertTrue(OnboardingFlowPolicy.TailscalePlayStoreUri.contains("com.tailscale.ipn"))
        assertEquals(OnboardingFlowPolicy.PageCount - 1, OnboardingFlowPolicy.ConnectPageIndex)
    }
}

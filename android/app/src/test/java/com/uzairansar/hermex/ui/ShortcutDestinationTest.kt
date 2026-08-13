package com.uzairansar.hermex.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutDestinationTest {
    @Test
    fun newSessionUriUsesSupportedAction() {
        assertEquals(
            "hermes-agent://sessions?shortcutAction=new",
            ShortcutDestination.NewSessionUri,
        )
        assertEquals(
            "hermes-agent://new-chat",
            ShortcutDestination.NewChatUri,
        )
        assertEquals(
            ShortcutDestination.NewSessionAction,
            ShortcutDestination.supportedAction("new"),
        )
    }

    @Test
    fun newVoiceSessionUriUsesSupportedAction() {
        assertEquals(
            "hermes-agent://new-chat-voice",
            ShortcutDestination.NewVoiceSessionUri,
        )
        assertEquals(
            ShortcutDestination.NewVoiceSessionAction,
            ShortcutDestination.supportedAction("newVoice"),
        )
    }

    @Test
    fun newProfileSessionUriUsesSupportedAction() {
        assertEquals(
            "hermes-agent://new-chat-profile",
            ShortcutDestination.NewProfileSessionUri,
        )
        assertEquals(
            ShortcutDestination.NewProfileSessionAction,
            ShortcutDestination.supportedAction("newProfile"),
        )
    }

    @Test
    fun shareUriUsesSupportedAction() {
        assertEquals("hermes-agent://share", ShortcutDestination.ShareUri)
        assertEquals(
            ShortcutDestination.ShareAction,
            ShortcutDestination.supportedAction("share"),
        )
    }

    @Test
    fun unsupportedShortcutActionIsIgnored() {
        assertNull(ShortcutDestination.supportedAction(null))
        assertNull(ShortcutDestination.supportedAction("settings"))
        assertNull(ShortcutDestination.supportedAction("delete"))
    }

    @Test
    fun sessionUriMatchesTheCrossPlatformDeepLinkContract() {
        assertEquals(
            "hermes-agent://session?id=session%20%2F%20one",
            ShortcutDestination.sessionUri(" session / one "),
        )
        assertNull(ShortcutDestination.sessionUri(null))
        assertNull(ShortcutDestination.sessionUri("  "))
    }
}

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
            ShortcutDestination.NewSessionAction,
            ShortcutDestination.supportedAction("new"),
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
}

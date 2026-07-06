package com.uzairansar.hermex.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ChatDisplaySettingsTest {
    @Test
    fun streamingSendBehaviorDefaultsToSteerForUnknownStorageValue() {
        assertTrue(StreamingSendBehavior.fromStorageValue(null) == StreamingSendBehavior.Steer)
        assertTrue(StreamingSendBehavior.fromStorageValue("queue") == StreamingSendBehavior.Queue)
        assertTrue(StreamingSendBehavior.fromStorageValue("interrupt") == StreamingSendBehavior.Interrupt)
        assertTrue(StreamingSendBehavior.fromStorageValue("future") == StreamingSendBehavior.Steer)
    }

    @Test
    fun rtlChatLayoutDefaultFollowsPrimaryRtlLanguage() {
        assertTrue(defaultRtlChatLayoutEnabled(Locale.forLanguageTag("ar")))
        assertTrue(defaultRtlChatLayoutEnabled(Locale.forLanguageTag("he")))
        assertTrue(defaultRtlChatLayoutEnabled(Locale.forLanguageTag("ur")))
        assertFalse(defaultRtlChatLayoutEnabled(Locale.forLanguageTag("en")))
    }

    @Test
    fun sessionRowDisplaySettingsDefaultToIosVisibleRows() {
        val settings = SessionRowDisplaySettings()

        assertTrue(settings.showMessageCount)
        assertTrue(settings.showWorkspace)
        assertTrue(settings.showCronSessions)
    }

    @Test
    fun statusNotificationResponseExcerptsDefaultToPrivate() {
        assertFalse(ChatDisplaySettings().showsStatusNotificationResponseExcerpts)
    }
}

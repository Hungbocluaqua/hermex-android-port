package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseCompletionNotificationPolicyTest {
    @Test
    fun schedulesOnlyWhenEnabledAllowedNormalAndInactive() {
        assertTrue(
            ResponseCompletionNotificationPolicy.shouldSchedule(
                preferenceEnabled = true,
                canPostNotifications = true,
                completedNormally = true,
                appIsActive = false,
            ),
        )

        assertFalse(
            ResponseCompletionNotificationPolicy.shouldSchedule(
                preferenceEnabled = false,
                canPostNotifications = true,
                completedNormally = true,
                appIsActive = false,
            ),
        )
        assertFalse(
            ResponseCompletionNotificationPolicy.shouldSchedule(
                preferenceEnabled = true,
                canPostNotifications = false,
                completedNormally = true,
                appIsActive = false,
            ),
        )
        assertFalse(
            ResponseCompletionNotificationPolicy.shouldSchedule(
                preferenceEnabled = true,
                canPostNotifications = true,
                completedNormally = false,
                appIsActive = false,
            ),
        )
        assertFalse(
            ResponseCompletionNotificationPolicy.shouldSchedule(
                preferenceEnabled = true,
                canPostNotifications = true,
                completedNormally = true,
                appIsActive = true,
            ),
        )
    }
}

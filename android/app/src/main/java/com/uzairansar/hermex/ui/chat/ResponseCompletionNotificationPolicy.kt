package com.uzairansar.hermex.ui.chat

object ResponseCompletionNotificationPolicy {
    fun shouldSchedule(
        preferenceEnabled: Boolean,
        canPostNotifications: Boolean,
        completedNormally: Boolean,
        appIsActive: Boolean,
    ): Boolean =
        preferenceEnabled &&
            canPostNotifications &&
            completedNormally &&
            !appIsActive
}

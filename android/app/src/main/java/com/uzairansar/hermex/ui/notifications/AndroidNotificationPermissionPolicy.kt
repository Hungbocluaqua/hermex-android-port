package com.uzairansar.hermex.ui.notifications

object AndroidNotificationPermissionPolicy {
    private const val POST_NOTIFICATIONS_SDK = 33

    fun canPostNotifications(sdkInt: Int, permissionGranted: Boolean): Boolean =
        sdkInt < POST_NOTIFICATIONS_SDK || permissionGranted
}

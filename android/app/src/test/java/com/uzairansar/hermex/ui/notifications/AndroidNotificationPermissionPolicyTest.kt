package com.uzairansar.hermex.ui.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNotificationPermissionPolicyTest {
    @Test
    fun preAndroid13DoesNotRequireRuntimePermission() {
        assertTrue(AndroidNotificationPermissionPolicy.canPostNotifications(32, permissionGranted = false))
    }

    @Test
    fun Android13AndLaterRequireGrantedPermission() {
        assertFalse(AndroidNotificationPermissionPolicy.canPostNotifications(33, permissionGranted = false))
        assertTrue(AndroidNotificationPermissionPolicy.canPostNotifications(34, permissionGranted = true))
    }
}

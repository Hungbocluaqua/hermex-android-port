package com.uzairansar.hermex.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun parsesStoredThemeValues() {
        assertEquals(AppThemeMode.System, AppThemeMode.fromStorageValue("system"))
        assertEquals(AppThemeMode.Light, AppThemeMode.fromStorageValue("light"))
        assertEquals(AppThemeMode.Dark, AppThemeMode.fromStorageValue("dark"))
    }

    @Test
    fun unknownStoredThemeFallsBackToSystem() {
        assertEquals(AppThemeMode.System, AppThemeMode.fromStorageValue(null))
        assertEquals(AppThemeMode.System, AppThemeMode.fromStorageValue("amoled"))
    }
}

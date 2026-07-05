package com.uzairansar.hermex.data.preferences

enum class AppThemeMode(val storageValue: String, val label: String) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromStorageValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

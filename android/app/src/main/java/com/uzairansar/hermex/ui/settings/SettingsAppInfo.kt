package com.uzairansar.hermex.ui.settings

object HermexAppLinks {
    const val PRIVACY_POLICY_URL = "https://www.uzairansar.com/hermes-mobile/privacy"
    const val SUPPORT_URL = "https://www.uzairansar.com/hermes-mobile"
}

data class AndroidAppInfo(
    val version: String,
    val build: String,
)

fun displayAppVersion(versionName: String?): String =
    versionName?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"

fun displayAppBuild(versionCode: Long?): String =
    versionCode?.takeIf { it >= 0 }?.toString() ?: "Unknown"

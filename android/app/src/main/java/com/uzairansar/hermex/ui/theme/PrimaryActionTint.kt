package com.uzairansar.hermex.ui.theme

import androidx.compose.ui.graphics.Color

fun hermexColorFromHex(value: String?): Color? {
    val raw = value?.trim()?.removePrefix("#") ?: return null
    if (!raw.matches(Regex("[0-9a-fA-F]{6}"))) return null
    return Color(0xFF000000L or raw.toLong(16))
}

fun primaryActionTintApplies(preferenceEnabled: Boolean, controlEnabled: Boolean): Boolean =
    preferenceEnabled && controlEnabled

fun primaryActionContentColorFor(background: Color): Color =
    if (background.luminanceValue() > 0.68f) Color.Black else Color.White

private fun Color.luminanceValue(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue

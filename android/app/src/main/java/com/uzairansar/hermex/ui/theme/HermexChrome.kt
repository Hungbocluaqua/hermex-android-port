package com.uzairansar.hermex.ui.theme

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uzairansar.hermex.R

object HermexColors {
    val Gold = Color(0xFFE8A030)
    val HeaderGold = Color(0xFFFFD700)
    val GoldBright = Color(0xFFFFC24A)
    val AccentBlue = Color(0xFF007AFF)
    val AccentBlueDark = Color(0xFF0A84FF)
    val Ink = Color(0xFF09090B)
    val Paper = Color(0xFFFBFBFD)
    val SystemGray6Light = Color(0xFFF2F2F7)
    val SystemGray5Light = Color(0xFFE5E5EA)
    val SecondarySystemLight = Color(0xFFF2F2F7)
    val SecondarySystemDark = Color(0xFF1C1C1E)
    val TertiarySystemDark = Color(0xFF2C2C2E)
    val SoftLineLight = Color(0x1F000000)
    val SoftLineDark = Color(0x2EFFFFFF)
}

val HermexCardShape: RoundedCornerShape = RoundedCornerShape(12.dp)
val HermexGlassShape: RoundedCornerShape = RoundedCornerShape(22.dp)
val HermexPillShape: RoundedCornerShape = RoundedCornerShape(999.dp)
private const val HermesLogoAspectRatio = 643f / 185f
val LocalHermexHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun HermesHeaderLogo(
    modifier: Modifier = Modifier,
    tint: Color = HermexColors.HeaderGold,
) {
    Box(
        modifier = modifier
            .aspectRatio(HermesLogoAspectRatio)
            .semantics { contentDescription = "HERMEX" },
    ) {
        Image(
            painter = painterResource(R.drawable.hermes_fill_mask),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tint),
        )
        Image(
            painter = painterResource(R.drawable.hermes_shading_overlay),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Image(
            painter = painterResource(R.drawable.hermes_highlight),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Image(
            painter = painterResource(R.drawable.hermes_outline_shadow),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun hermexGlassBrush(): Brush {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.13f),
                Color.White.copy(alpha = 0.07f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.92f),
                Color.White.copy(alpha = 0.72f),
            ),
        )
    }
}

@Composable
fun Modifier.hermexGlass(
    shape: Shape = HermexGlassShape,
    castsShadow: Boolean = true,
): Modifier {
    val dark = isSystemInDarkTheme()
    val borderColor = if (dark) HermexColors.SoftLineDark else HermexColors.SoftLineLight
    val shadowColor = Color.Black.copy(alpha = if (dark) 0.35f else 0.14f)
    val base = if (castsShadow) {
        shadow(18.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
    } else {
        this
    }
    return base
        .clip(shape)
        .background(hermexGlassBrush())
        .border(0.6.dp, borderColor, shape)
}

@Composable
fun Modifier.hermexHairline(shape: Shape = HermexCardShape): Modifier {
    val dark = isSystemInDarkTheme()
    return border(
        width = 0.6.dp,
        color = if (dark) HermexColors.SoftLineDark else HermexColors.SoftLineLight,
        shape = shape,
    )
}

@Composable
fun hermexPrimaryActionContainerColor(
    enabled: Boolean = true,
    tintColor: Color? = null,
): Color {
    if (enabled && tintColor != null) return tintColor
    val dark = isSystemInDarkTheme()
    val base = if (dark) Color.White else Color.Black
    return if (enabled) base else base.copy(alpha = 0.14f)
}

@Composable
fun hermexPrimaryActionContentColor(
    enabled: Boolean = true,
    tintColor: Color? = null,
): Color {
    if (enabled && tintColor != null) return primaryActionContentColorFor(tintColor)
    val dark = isSystemInDarkTheme()
    return if (enabled) {
        if (dark) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.secondary
    }
}

@Composable
fun HermexPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    filledContainerColor: Color? = null,
    filledContentColor: Color? = null,
    outlinedContainerColor: Color? = null,
    outlinedContentColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val view = LocalView.current
    val hapticsEnabled = LocalHermexHapticsEnabled.current
    val hapticClick = {
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onClick()
    }
    val filledContainer = filledContainerColor ?: MaterialTheme.colorScheme.primary
    val filledContent = filledContentColor ?: MaterialTheme.colorScheme.onPrimary
    val outlinedContainer = outlinedContainerColor ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
    val outlinedContent = outlinedContentColor ?: MaterialTheme.colorScheme.onSurface

    if (filled) {
        androidx.compose.material3.Button(
            onClick = hapticClick,
            enabled = enabled,
            modifier = modifier.defaultMinSize(minHeight = 0.dp),
            shape = HermexPillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = filledContainer,
                contentColor = filledContent,
            ),
            contentPadding = contentPadding,
        ) {
            if (leading != null) leading()
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = hapticClick,
            enabled = enabled,
            modifier = modifier.defaultMinSize(minHeight = 0.dp),
            shape = HermexPillShape,
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = outlinedContainer,
                contentColor = outlinedContent,
            ),
            contentPadding = contentPadding,
        ) {
            if (leading != null) leading()
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun HermexSelectorPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
    minWidth: Dp? = null,
    maxWidth: Dp? = null,
    lineLimit: Int = 1,
    glassed: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
) {
    val view = LocalView.current
    val hapticsEnabled = LocalHermexHapticsEnabled.current
    val content = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.82f else 0.42f)
    val shape = HermexPillShape
    val baseModifier = modifier
        .widthIn(
            min = minWidth ?: Dp.Unspecified,
            max = maxWidth ?: Dp.Unspecified,
        )
        .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
        .semantics { contentDescription = label }
        .clip(shape)
    val styledModifier = if (glassed) {
        baseModifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
            .hermexHairline(shape)
    } else {
        baseModifier
    }

    TextButton(
        onClick = {
            if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        enabled = enabled,
        modifier = styledModifier,
        shape = shape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.textButtonColors(
            contentColor = content,
            disabledContentColor = content,
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
    ) {
        if (leadingIcon != null) {
            Image(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                colorFilter = ColorFilter.tint(content),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            maxLines = lineLimit,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(5.dp))
        Image(
            painter = painterResource(R.drawable.ic_hermex_chevron_down),
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            colorFilter = ColorFilter.tint(content),
        )
    }
}

@Composable
fun HermexIconButton(
    label: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    filledContainerColor: Color? = null,
    filledContentColor: Color? = null,
    tonalContainerColor: Color? = null,
    tonalContentColor: Color? = null,
) {
    val view = LocalView.current
    val hapticsEnabled = LocalHermexHapticsEnabled.current
    val container = if (filled) {
        filledContainerColor ?: MaterialTheme.colorScheme.primary
    } else {
        tonalContainerColor ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val content = if (filled) {
        filledContentColor ?: MaterialTheme.colorScheme.onPrimary
    } else {
        tonalContentColor ?: MaterialTheme.colorScheme.onSurface
    }
    val iconResource = hermexIconResource(label, symbol)
    val displaySymbol = normalizedHermexIconSymbol(label, symbol)
    TextButton(
        onClick = {
            if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .background(container)
            .hermexHairline(CircleShape),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = content),
    ) {
        Box(Modifier.padding(bottom = 1.dp)) {
            if (iconResource != null) {
                Image(
                    painter = painterResource(iconResource),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(content),
                )
            } else {
                Text(displaySymbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@DrawableRes
private fun hermexIconResource(label: String, symbol: String): Int? =
    when {
        label == "Back" -> R.drawable.ic_hermex_chevron_left
        label == "Refresh" -> R.drawable.ic_hermex_refresh
        label == "Send" -> R.drawable.ic_hermex_arrow_up
        label == "Stop" -> R.drawable.ic_hermex_stop_fill
        label == "Voice" -> R.drawable.ic_hermex_waveform
        label == "Files" -> R.drawable.ic_lucide_folder
        label == "Git" -> R.drawable.ic_hermex_git_branch
        label == "Clear" || label == "Close search" -> R.drawable.ic_hermex_xmark
        label == "Search sessions" || label == "Go" -> R.drawable.ic_hermex_search
        label == "Up" -> R.drawable.ic_hermex_arrow_up
        label == "Panels" -> R.drawable.ic_hermex_ellipsis
        label == "Attach" || label == "Add project" -> R.drawable.ic_hermex_plus
        label == "Session actions" || label.startsWith("Project actions") -> R.drawable.ic_hermex_ellipsis
        label == "Settings" && !symbol.looksLikeInitials() -> null
        else -> null
    }

private fun normalizedHermexIconSymbol(label: String, symbol: String): String =
    when (label) {
        "Back" -> "\u2039"
        "Refresh" -> "\u21bb"
        "Send" -> "\u2191"
        "Stop" -> "\u25a0"
        "Voice" -> "\u266a"
        "Files" -> "\u2302"
        "Settings" -> "\u2699"
        "Clear" -> "\u00d7"
        "Up" -> "\u2191"
        "Panels" -> "\u22ef"
        "Go" -> "\u2315"
        else -> symbol
    }

private fun String.looksLikeInitials(): Boolean {
    val trimmed = trim()
    return trimmed.length in 1..3 && trimmed.all { it.isLetterOrDigit() }
}

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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

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

enum class HermexSurfaceLevel {
    Base,
    Raised,
    Floating,
}

@Immutable
data class HermexSurfaceTokens(
    val background: Color,
    val base: Color,
    val raised: Color,
    val floating: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val glassShadow: Color,
    val fallbackScrimAlpha: Float,
)

internal val LightHermexSurfaceTokens = HermexSurfaceTokens(
    background = Color(0xFFF2F2F7),
    base = Color(0xFFF2F2F7),
    raised = Color(0xFFFAFAFC),
    floating = Color(0xFFFFFFFF),
    glassTint = Color(0x8AFFFFFF),
    glassBorder = Color(0x1F000000),
    glassShadow = Color(0x24000000),
    fallbackScrimAlpha = 0.86f,
)

internal val DarkHermexSurfaceTokens = HermexSurfaceTokens(
    background = Color.Black,
    base = Color(0xFF08090A),
    raised = Color(0xFF111214),
    floating = Color(0xFF17191C),
    glassTint = Color(0x16FFFFFF),
    glassBorder = Color(0x2BFFFFFF),
    glassShadow = Color(0xB3000000),
    fallbackScrimAlpha = 0.80f,
)

val LocalHermexSurfaceTokens = staticCompositionLocalOf { DarkHermexSurfaceTokens }
val LocalHermexHazeState = staticCompositionLocalOf<HazeState?> { null }

object HermexGlassTokens {
    val BaseBlurRadius: Dp = 18.dp
    val RaisedBlurRadius: Dp = 21.dp
    val FloatingBlurRadius: Dp = 24.dp
    val ShadowElevation: Dp = 18.dp
    val BorderWidth: Dp = 0.6.dp
    const val NoiseFactor: Float = 0.06f
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
fun hermexBackgroundColor(): Color = LocalHermexSurfaceTokens.current.background

@Composable
fun hermexSurfaceColor(level: HermexSurfaceLevel): Color =
    LocalHermexSurfaceTokens.current.colorFor(level)

@Composable
fun Modifier.hermexHazeSource(
    zIndex: Float = 0f,
    key: Any? = null,
): Modifier {
    val state = LocalHermexHazeState.current ?: return this
    return hazeSource(state = state, zIndex = zIndex, key = key)
}

@Composable
fun Modifier.hermexGlass(
    shape: Shape = HermexGlassShape,
    castsShadow: Boolean = true,
    surfaceLevel: HermexSurfaceLevel = if (castsShadow) HermexSurfaceLevel.Floating else HermexSurfaceLevel.Raised,
    tintEnabled: Boolean = true,
): Modifier {
    val state = LocalHermexHazeState.current
    val tokens = LocalHermexSurfaceTokens.current
    val surfaceColor = tokens.colorFor(surfaceLevel)
    val glassTint = remember(tokens, surfaceLevel) {
        tokens.glassTint.copy(
            alpha = (tokens.glassTint.alpha * surfaceLevel.tintAlphaMultiplier).coerceAtMost(1f),
        )
    }
    val hazeStyle = remember(tokens, surfaceLevel, tintEnabled, glassTint) {
        HazeStyle(
            backgroundColor = tokens.background,
            tint = if (tintEnabled) HazeTint(glassTint) else HazeTint(Color.Transparent),
            blurRadius = surfaceLevel.blurRadius,
            noiseFactor = HermexGlassTokens.NoiseFactor,
            fallbackTint = HazeTint(surfaceColor.copy(alpha = tokens.fallbackScrimAlpha)),
        )
    }
    val base = if (castsShadow) {
        shadow(
            elevation = HermexGlassTokens.ShadowElevation,
            shape = shape,
            ambientColor = tokens.glassShadow,
            spotColor = tokens.glassShadow,
        )
    } else {
        this
    }
    val clipped = base.clip(shape)
    val glass = if (state != null) {
        clipped.hazeEffect(state = state, style = hazeStyle)
    } else {
        clipped
            .background(surfaceColor.copy(alpha = tokens.fallbackScrimAlpha))
            .then(if (tintEnabled) Modifier.background(glassTint) else Modifier)
    }
    return glass.border(HermexGlassTokens.BorderWidth, tokens.glassBorder, shape)
}

@Composable
fun Modifier.hermexHairline(shape: Shape = HermexCardShape): Modifier {
    return border(
        width = HermexGlassTokens.BorderWidth,
        color = LocalHermexSurfaceTokens.current.glassBorder,
        shape = shape,
    )
}

private fun HermexSurfaceTokens.colorFor(level: HermexSurfaceLevel): Color = when (level) {
    HermexSurfaceLevel.Base -> base
    HermexSurfaceLevel.Raised -> raised
    HermexSurfaceLevel.Floating -> floating
}

private val HermexSurfaceLevel.blurRadius: Dp
    get() = when (this) {
        HermexSurfaceLevel.Base -> HermexGlassTokens.BaseBlurRadius
        HermexSurfaceLevel.Raised -> HermexGlassTokens.RaisedBlurRadius
        HermexSurfaceLevel.Floating -> HermexGlassTokens.FloatingBlurRadius
    }

private val HermexSurfaceLevel.tintAlphaMultiplier: Float
    get() = when (this) {
        HermexSurfaceLevel.Base -> 0.82f
        HermexSurfaceLevel.Raised -> 1f
        HermexSurfaceLevel.Floating -> 1.22f
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

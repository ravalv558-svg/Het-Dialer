package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PersonalizationManager
import com.example.model.AppThemeMode
import com.example.model.PersonalizationConfig

val LocalPersonalizationConfig = compositionLocalOf { PersonalizationConfig() }

private val ColorOsLightColorScheme = lightColorScheme(
    primary = ColorOsGreen,
    onPrimary = ColorOsWhite,
    secondary = AvatarBlue,
    onSecondary = ColorOsWhite,
    tertiary = ColorOsIncomingGreen,
    background = ColorOsWhite,
    onBackground = ColorOsTextPrimary,
    surface = ColorOsCardBg,
    onSurface = ColorOsTextPrimary,
    surfaceVariant = ColorOsPageBg,
    onSurfaceVariant = ColorOsTextSecondary,
    error = ColorOsRed,
    onError = ColorOsWhite,
    outline = ColorOsDivider
)

private val ColorOsDarkColorScheme = darkColorScheme(
    primary = ColorOsGreen,
    onPrimary = ColorOsWhite,
    secondary = AvatarBlue,
    onSecondary = ColorOsWhite,
    tertiary = ColorOsIncomingGreen,
    background = ColorOsDarkBg,
    onBackground = ColorOsDarkTextPrimary,
    surface = ColorOsDarkSurface,
    onSurface = ColorOsDarkTextPrimary,
    surfaceVariant = ColorOsDarkCard,
    onSurfaceVariant = ColorOsDarkTextSecondary,
    error = ColorOsRed,
    onError = ColorOsWhite,
    outline = ColorOsDarkDivider
)

fun contrastingOnColor(bg: Color): Color {
    return if (bg.luminance() > 0.45f) Color(0xFF0E131B) else Color.White
}

private fun ensureReadableTextColor(candidate: Color, isDarkBackground: Boolean, fallback: Color): Color {
    val lum = candidate.luminance()
    return if (isDarkBackground) {
        if (lum >= 0.35f) candidate else lerp(candidate, Color.White, 0.72f)
    } else {
        if (lum <= 0.40f) candidate else lerp(candidate, fallback, 0.75f)
    }
}

fun buildPersonalizedColorScheme(
    config: PersonalizationConfig,
    useDark: Boolean
): ColorScheme {
    val base = if (useDark) ColorOsDarkColorScheme else ColorOsLightColorScheme
    if (!config.useCustomColors && !config.useCustomTextColor) {
        return base
    }

    val primary = if (config.useCustomColors) config.primaryColor else base.primary
    val secondary = if (config.useCustomColors) config.secondaryColor else base.secondary
    val tertiary = if (config.useCustomColors) config.accentColor else base.tertiary

    val tintedBackground = if (config.useCustomColors) {
        if (useDark) {
            lerp(Color(0xFF070C14), primary, 0.08f)
        } else {
            lerp(Color(0xFFF6F9FC), primary, 0.04f)
        }
    } else {
        base.background
    }

    val tintedSurface = if (config.useCustomColors) {
        if (useDark) {
            lerp(Color(0xFF101826), primary, 0.12f)
        } else {
            lerp(Color(0xFFFFFFFF), secondary, 0.05f)
        }
    } else {
        base.surface
    }

    val tintedSurfaceVariant = if (config.useCustomColors) {
        if (useDark) {
            lerp(Color(0xFF162133), secondary, 0.14f)
        } else {
            lerp(Color(0xFFEEF4FA), primary, 0.07f)
        }
    } else {
        base.surfaceVariant
    }

    val primaryText = if (config.useCustomTextColor) {
        ensureReadableTextColor(config.customTextColor, useDark, base.onBackground)
    } else {
        base.onBackground
    }

    val secondaryText = if (config.useCustomTextColor) {
        primaryText.copy(alpha = 0.72f)
    } else {
        base.onSurfaceVariant
    }

    return base.copy(
        primary = primary,
        onPrimary = contrastingOnColor(primary),
        secondary = secondary,
        onSecondary = contrastingOnColor(secondary),
        tertiary = tertiary,
        onTertiary = contrastingOnColor(tertiary),
        background = tintedBackground,
        onBackground = primaryText,
        surface = tintedSurface,
        onSurface = primaryText,
        surfaceVariant = tintedSurfaceVariant,
        onSurfaceVariant = secondaryText,
        error = config.missedCallColor,
        outline = if (config.useCustomColors) {
            primary.copy(alpha = if (useDark) 0.28f else 0.22f)
        } else {
            base.outline
        }
    )
}

@Composable
private fun animateColor(target: Color, durationMs: Int, label: String): Color {
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = durationMs),
        label = label
    )
    return animated
}

@Composable
private fun ColorScheme.animated(durationMs: Int): ColorScheme {
    return copy(
        primary = animateColor(primary, durationMs, "primary"),
        onPrimary = animateColor(onPrimary, durationMs, "onPrimary"),
        secondary = animateColor(secondary, durationMs, "secondary"),
        onSecondary = animateColor(onSecondary, durationMs, "onSecondary"),
        tertiary = animateColor(tertiary, durationMs, "tertiary"),
        background = animateColor(background, durationMs, "background"),
        onBackground = animateColor(onBackground, durationMs, "onBackground"),
        surface = animateColor(surface, durationMs, "surface"),
        onSurface = animateColor(onSurface, durationMs, "onSurface"),
        surfaceVariant = animateColor(surfaceVariant, durationMs, "surfaceVariant"),
        onSurfaceVariant = animateColor(onSurfaceVariant, durationMs, "onSurfaceVariant"),
        outline = animateColor(outline, durationMs, "outline"),
        error = animateColor(error, durationMs, "error")
    )
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode? = null,
    personalization: PersonalizationConfig? = null,
    content: @Composable () -> Unit
) {
    val appliedFromManager by PersonalizationManager.appliedConfig.collectAsStateWithLifecycle()
    val effectivePersonalization = personalization ?: appliedFromManager
    val resolvedThemeMode = themeMode ?: effectivePersonalization.themeMode

    val useDark = when (resolvedThemeMode) {
        AppThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val targetScheme = remember(effectivePersonalization, useDark) {
        buildPersonalizedColorScheme(effectivePersonalization, useDark)
    }
    val customTypography = remember(effectivePersonalization.typography) {
        buildPersonalizedTypography(effectivePersonalization.typography)
    }
    val animDuration = remember(effectivePersonalization.animations) {
        PersonalizationManager.resolveDurationMs(220, effectivePersonalization.animations)
    }

    CompositionLocalProvider(LocalPersonalizationConfig provides effectivePersonalization) {
        MaterialTheme(
            colorScheme = targetScheme.animated(animDuration),
            typography = customTypography,
            content = content
        )
    }
}

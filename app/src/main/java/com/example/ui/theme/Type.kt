package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.StudioFontFamily
import com.example.model.TypographyConfig

fun resolveComposeFontFamily(family: StudioFontFamily): FontFamily {
    return when (family) {
        StudioFontFamily.SYSTEM -> FontFamily.Default
        StudioFontFamily.INTER -> FontFamily(
            Font(R.font.inter, FontWeight.Light),
            Font(R.font.inter, FontWeight.Normal),
            Font(R.font.inter, FontWeight.Medium),
            Font(R.font.inter, FontWeight.SemiBold),
            Font(R.font.inter, FontWeight.Bold)
        )
        StudioFontFamily.PLUS_JAKARTA_SANS -> FontFamily(
            Font(R.font.plus_jakarta_sans, FontWeight.Normal),
            Font(R.font.plus_jakarta_sans, FontWeight.Medium),
            Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
            Font(R.font.plus_jakarta_sans, FontWeight.Bold)
        )
        StudioFontFamily.SPACE_GROTESK -> FontFamily(
            Font(R.font.space_grotesk, FontWeight.Normal),
            Font(R.font.space_grotesk, FontWeight.Medium),
            Font(R.font.space_grotesk, FontWeight.SemiBold),
            Font(R.font.space_grotesk, FontWeight.Bold)
        )
        StudioFontFamily.OUTFIT -> FontFamily(
            Font(R.font.outfit, FontWeight.Normal),
            Font(R.font.outfit, FontWeight.Medium),
            Font(R.font.outfit, FontWeight.SemiBold),
            Font(R.font.outfit, FontWeight.Bold)
        )
        StudioFontFamily.PLAYFAIR_DISPLAY -> FontFamily(
            Font(R.font.playfair_display, FontWeight.Normal),
            Font(R.font.playfair_display, FontWeight.Medium),
            Font(R.font.playfair_display, FontWeight.SemiBold),
            Font(R.font.playfair_display, FontWeight.Bold)
        )
        StudioFontFamily.JETBRAINS_MONO -> FontFamily(
            Font(R.font.jetbrains_mono, FontWeight.Normal),
            Font(R.font.jetbrains_mono, FontWeight.Medium),
            Font(R.font.jetbrains_mono, FontWeight.SemiBold),
            Font(R.font.jetbrains_mono, FontWeight.Bold)
        )
    }
}

fun buildPersonalizedTypography(config: TypographyConfig): Typography {
    val fontFamily = resolveComposeFontFamily(config.fontFamily)
    val scale = config.fontScale.coerceIn(0.85f, 1.25f)
    val extraSpacing = config.letterSpacingSp.coerceIn(-0.5f, 1.5f)

    fun scaledStyle(
        baseSizeSp: Float,
        lineHeightSp: Float,
        weight: FontWeight,
        baseSpacingSp: Float = 0f
    ): TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = (baseSizeSp * scale).sp,
        lineHeight = (lineHeightSp * scale).sp,
        letterSpacing = (baseSpacingSp + extraSpacing).sp
    )

    return Typography(
        displayLarge = scaledStyle(36f, 44f, FontWeight.Bold, -0.25f),
        headlineLarge = scaledStyle(32f, 38f, FontWeight.Bold, -0.15f),
        headlineMedium = scaledStyle(26f, 32f, FontWeight.SemiBold),
        headlineSmall = scaledStyle(22f, 28f, FontWeight.SemiBold),
        titleLarge = scaledStyle(20f, 26f, FontWeight.SemiBold),
        titleMedium = scaledStyle(17f, 23f, FontWeight.Medium, 0.1f),
        titleSmall = scaledStyle(15f, 20f, FontWeight.Medium, 0.1f),
        bodyLarge = scaledStyle(16f, 24f, FontWeight.Normal, 0.25f),
        bodyMedium = scaledStyle(14f, 20f, FontWeight.Normal, 0.2f),
        bodySmall = scaledStyle(12.5f, 17f, FontWeight.Normal, 0.2f),
        labelLarge = scaledStyle(14f, 20f, FontWeight.SemiBold, 0.1f),
        labelMedium = scaledStyle(12f, 16f, FontWeight.Medium, 0.3f),
        labelSmall = scaledStyle(11f, 15f, FontWeight.Medium, 0.4f)
    )
}

val Typography = buildPersonalizedTypography(TypographyConfig())

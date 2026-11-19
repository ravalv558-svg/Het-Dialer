package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ColorOS 16 Light Surfaces & Text
val ColorOsWhite = Color(0xFFFFFFFF)
val ColorOsPageBg = Color(0xFFF4F5F7)
val ColorOsCardBg = Color(0xFFFFFFFF)
val ColorOsTextPrimary = Color(0xFF141414)
val ColorOsTextSecondary = Color(0xFF757575)
val ColorOsTextHint = Color(0xFFA8A8A8)
val ColorOsDivider = Color(0xFFEAEAEA)
val ColorOsPillBg = Color(0xFFECEEF1)

// ColorOS 16 Dark Mode Surfaces & Text
val ColorOsDarkBg = Color(0xFF0D0F12)
val ColorOsDarkSurface = Color(0xFF181B20)
val ColorOsDarkCard = Color(0xFF22262E)
val ColorOsDarkTextPrimary = Color(0xFFF5F6F8)
val ColorOsDarkTextSecondary = Color(0xFF9AA0AA)
val ColorOsDarkDivider = Color(0xFF2A2F3A)

// ColorOS 16 Signature Accents
val ColorOsGreen = Color(0xFF12C43C)
val ColorOsGreenLight = Color(0xFFE5F9EA)
val ColorOsIncomingGreen = Color(0xFF28C94E)
val ColorOsRed = Color(0xFFE53935)
val ColorOsStarGold = Color(0xFFF5B400)
val ColorOsHdBadgeBg = Color(0xFFB8B8B8)

// Deep Atmospheric Blurred Call Background Colors
val CallBgDeepNavy = Color(0xFF0A1020)
val CallBgMidBlue = Color(0xFF1F3368)
val CallBgSoftBlue = Color(0xFF2E4988)
val CallBgDarkCorner = Color(0xFF080C18)
val CallButtonTranslucent = Color(0x33FFFFFF)
val CallButtonActiveWhite = Color(0xFFFFFFFF)

// Contact Avatar Circle Palette
val AvatarBlue = Color(0xFF2B65EC)
val AvatarCyan = Color(0xFF14B8C4)
val AvatarPurple = Color(0xFF6C3CE9)
val AvatarGreen = Color(0xFF18B24E)
val AvatarMagenta = Color(0xFFB238B0)
val AvatarOrange = Color(0xFFE68A1E)

val ColorOsAvatarPalette = listOf(
    AvatarBlue,
    AvatarCyan,
    AvatarPurple,
    AvatarGreen,
    AvatarMagenta,
    AvatarOrange
)

fun avatarColorForName(name: String): Color {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return AvatarBlue
    return when (trimmed.first().uppercaseChar()) {
        'S' -> AvatarBlue
        'A' -> if (trimmed.length > 7) AvatarPurple else AvatarCyan
        'B' -> if (trimmed.length <= 4) AvatarMagenta else AvatarGreen
        'C', 'D', 'E' -> AvatarOrange
        'F', 'G', 'H', 'I' -> AvatarBlue
        'J', 'K', 'L', 'M' -> AvatarCyan
        'N', 'O', 'P', 'Q' -> AvatarPurple
        'R', 'T', 'U', 'V' -> AvatarGreen
        else -> ColorOsAvatarPalette[(trimmed.hashCode() and 0x7FFFFFFF) % ColorOsAvatarPalette.size]
    }
}

package com.example.ui.personalization

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PersonalizationManager
import com.example.model.CallDirectionIconStyle
import com.example.model.CallHistoryLayoutStyle
import com.example.model.DialpadButtonVisualStyle
import com.example.model.OngoingButtonVisualStyle
import com.example.model.PersonalizationCategory
import com.example.model.PersonalizationConfig
import com.example.model.WaveformAccentStyle
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.resolveComposeFontFamily

enum class PreviewMode(val label: String, val mappedCategory: PersonalizationCategory) {
    DIALER("Dialer", PersonalizationCategory.HOME_DIALER),
    CONTACTS("Contacts", PersonalizationCategory.CONTACTS),
    HISTORY("History", PersonalizationCategory.CALL_HISTORY),
    INCOMING("Incoming", PersonalizationCategory.INCOMING_CALL),
    OUTGOING("Outgoing", PersonalizationCategory.OUTGOING_CALL),
    ONGOING("Ongoing", PersonalizationCategory.ONGOING_CALL)
}

/**
 * Interactive real-time Live Theme Preview card shown on the Personalization Studio Dashboard
 * and category screens. Reflects colors, font family, scale, letter spacing, button shapes,
 * glass transparency, blur/glow, and layout choices immediately.
 */
@Composable
fun PersonalizationLiveThemePreviewCard(
    config: PersonalizationConfig,
    modifier: Modifier = Modifier,
    initialMode: PreviewMode = PreviewMode.DIALER,
    showCustomizeButton: Boolean = true,
    onCustomizeClick: ((PersonalizationCategory) -> Unit)? = null
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    val isDark = isPersonalizationDark(config)
    val fontFamily = remember(config.typography.fontFamily) {
        resolveComposeFontFamily(config.typography.fontFamily)
    }
    val fontScale = config.typography.fontScale.coerceIn(0.85f, 1.25f)
    val letterSpacing = config.typography.letterSpacingSp.sp

    val accent = if (config.useCustomColors) config.primaryColor else Color(0xFF0A84FF)
    val secondary = if (config.useCustomColors) config.secondaryColor else Color(0xFF38BDF8)
    val tertiary = if (config.useCustomColors) config.accentColor else Color(0xFF00D4FF)

    val textPrimary = if (config.useCustomTextColor) {
        config.customTextColor
    } else if (isDark) {
        Color(0xFFF4F7FC)
    } else {
        Color(0xFF121824)
    }
    val textSecondary = textPrimary.copy(alpha = 0.68f)

    LiquidGlassCard(
        config = config,
        cornerRadius = 26.dp,
        highlightAccent = accent,
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_theme_preview_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LIVE THEME PREVIEW",
                    color = accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = config.activeThemeName,
                    color = textPrimary,
                    fontFamily = fontFamily,
                    fontSize = (17f * fontScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = letterSpacing,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("live_preview_theme_name")
                )
            }
            if (showCustomizeButton && onCustomizeClick != null) {
                LiquidGlassActionChip(
                    label = "Customize",
                    icon = Icons.Default.Tune,
                    config = config,
                    isPrimary = true,
                    onClick = { onCustomizeClick(mode.mappedCategory) },
                    modifier = Modifier.testTag("preview_customize_button")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preview Screen Selector Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PreviewMode.entries.forEach { item ->
                val selected = item == mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) accent.copy(alpha = if (isDark) 0.34f else 0.18f)
                            else Color.Transparent
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) accent else textSecondary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { mode = item }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("preview_mode_${item.name.lowercase()}")
                ) {
                    Text(
                        text = item.label,
                        color = if (selected) (if (isDark) Color.White else accent) else textSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Preview Stage
        val stageBgBrush = when (mode) {
            PreviewMode.INCOMING, PreviewMode.OUTGOING, PreviewMode.ONGOING -> Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A1428),
                    accent.copy(alpha = 0.34f),
                    Color(0xFF070C18)
                )
            )
            else -> if (isDark) {
                Brush.verticalGradient(listOf(Color(0xFF09111E), Color(0xFF101F36)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFFF6FAFF), Color(0xFFEAF3FC)))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(stageBgBrush)
                .border(
                    1.dp,
                    Color.White.copy(alpha = if (isDark) 0.18f else 0.72f),
                    RoundedCornerShape(20.dp)
                )
                .padding(14.dp)
        ) {
            when (mode) {
                PreviewMode.DIALER -> {
                    DialerMiniPreview(config, accent, secondary, textPrimary, textSecondary, isDark)
                }
                PreviewMode.CONTACTS -> {
                    ContactsMiniPreview(config, accent, secondary, textPrimary, textSecondary, isDark)
                }
                PreviewMode.HISTORY -> {
                    CallHistoryMiniPreview(config, accent, textPrimary, textSecondary, isDark)
                }
                PreviewMode.INCOMING -> {
                    IncomingMiniPreview(config, accent, tertiary)
                }
                PreviewMode.OUTGOING -> {
                    OutgoingMiniPreview(config, accent, secondary)
                }
                PreviewMode.ONGOING -> {
                    OngoingMiniPreview(config, accent, secondary, tertiary)
                }
            }
        }
    }
}

@Composable
private fun DialerMiniPreview(
    config: PersonalizationConfig,
    accent: Color,
    secondary: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean
) {
    val fontFamily = resolveComposeFontFamily(config.typography.fontFamily)
    val scale = config.typography.fontScale
    val keyShape = PersonalizationManager.shapeFor(config.homeDialer.dialpadButtonShape, 40f)
    val sizeMult = config.homeDialer.dialpadButtonSize.multiplier
    val keySize = (38f * sizeMult).dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "+1 (415) 890-2410",
            color = textPrimary,
            fontFamily = fontFamily,
            fontSize = (18f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = config.typography.letterSpacingSp.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("1" to " ", "2" to "ABC", "3" to "DEF").forEach { (d, sub) ->
                val keyBg = when (config.homeDialer.dialpadButtonStyle) {
                    DialpadButtonVisualStyle.CLEAN_SOLID ->
                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.85f)
                    DialpadButtonVisualStyle.LIQUID_GLASS ->
                        accent.copy(alpha = if (isDark) 0.24f else 0.14f)
                    DialpadButtonVisualStyle.OUTLINED_GLOW ->
                        Color.Transparent
                    DialpadButtonVisualStyle.SOFT_TINT ->
                        secondary.copy(alpha = if (isDark) 0.22f else 0.16f)
                }
                val keyBorder = if (config.homeDialer.dialpadButtonStyle == DialpadButtonVisualStyle.OUTLINED_GLOW) {
                    accent.copy(alpha = 0.72f)
                } else {
                    Color.White.copy(alpha = if (isDark) 0.16f else 0.6f)
                }

                Column(
                    modifier = Modifier
                        .size(width = keySize * 1.45f, height = keySize)
                        .clip(keyShape)
                        .background(keyBg)
                        .border(1.dp, keyBorder, keyShape),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = d,
                        color = textPrimary,
                        fontFamily = fontFamily,
                        fontSize = (15f * scale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            color = textSecondary,
                            fontFamily = fontFamily,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (config.useCustomColors) accent else ColorOsGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Preview Call Button",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ContactsMiniPreview(
    config: PersonalizationConfig,
    accent: Color,
    secondary: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean
) {
    val fontFamily = resolveComposeFontFamily(config.typography.fontFamily)
    val scale = config.typography.fontScale
    val avatarShape = PersonalizationManager.shapeFor(config.contacts.avatarShape, 42f)
    val cardRadius = config.contacts.cardCornerRadiusDp.dp

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Sophia Sterling" to "Product Design", "Aarav Patel" to "Mobile Lead").forEachIndexed { idx, (name, role) ->
            val cardBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.80f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardRadius))
                    .background(cardBg)
                    .border(
                        width = if (config.contacts.showAvatarGlowRing) 1.dp else 0.5.dp,
                        color = if (config.contacts.showAvatarGlowRing) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(cardRadius)
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(avatarShape)
                        .background(if (idx == 0) accent else secondary)
                        .then(
                            if (config.contacts.showAvatarGlowRing) {
                                Modifier.border(1.5.dp, Color.White.copy(alpha = 0.7f), avatarShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.first().toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = name,
                        color = textPrimary,
                        fontFamily = fontFamily,
                        fontSize = (14.5f * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = config.typography.letterSpacingSp.sp
                    )
                    if (config.contacts.showCompanySubtitle) {
                        Text(
                            text = role,
                            color = textSecondary,
                            fontFamily = fontFamily,
                            fontSize = (11.5f * scale).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryMiniPreview(
    config: PersonalizationConfig,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean
) {
    val fontFamily = resolveComposeFontFamily(config.typography.fontFamily)
    val scale = config.typography.fontScale
    val isCards = config.callHistory.layoutStyle == CallHistoryLayoutStyle.GLASS_CARDS
    val isCompact = config.callHistory.layoutStyle == CallHistoryLayoutStyle.COMPACT_DENSE
    val corner = config.callHistory.rowCornerRadiusDp.dp

    Column(verticalArrangement = Arrangement.spacedBy(if (isCards) 8.dp else 4.dp)) {
        listOf(
            Triple("Maya Lin", false, "10:42 AM · 2m 14s"),
            Triple("David Ross", true, "09:15 AM · Missed")
        ).forEach { (name, isMissed, sub) ->
            val rowBg = if (isCards) {
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.82f)
            } else {
                Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corner))
                    .background(rowBg)
                    .padding(horizontal = 10.dp, vertical = if (isCompact) 5.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dirColor = if (isMissed) config.missedCallColor else accent
                    when (config.callHistory.directionIconStyle) {
                        CallDirectionIconStyle.CLASSIC_ARROWS -> Text(
                            text = if (isMissed) "↙" else "↗",
                            color = dirColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        CallDirectionIconStyle.BADGE_PILLS -> Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(dirColor.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isMissed) "Missed" else "Out",
                                color = dirColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        CallDirectionIconStyle.MINIMAL_DOTS -> Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dirColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = name,
                            color = if (isMissed) config.missedCallColor else textPrimary,
                            fontFamily = fontFamily,
                            fontSize = (14f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = config.typography.letterSpacingSp.sp
                        )
                        if (config.callHistory.showCallDuration) {
                            Text(
                                text = sub,
                                color = textSecondary,
                                fontFamily = fontFamily,
                                fontSize = (11f * scale).sp
                            )
                        }
                    }
                }
                if (config.callHistory.showVoLteAndHdBadges) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("HD", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingMiniPreview(
    config: PersonalizationConfig,
    accent: Color,
    tertiary: Color
) {
    val fontFamily = resolveComposeFontFamily(config.typography.fontFamily)
    val scale = config.typography.fontScale
    val btnShape = PersonalizationManager.shapeFor(config.incomingCall.buttonShape, 42f)
    val btnSize = (38f * config.incomingCall.buttonSize.multiplier).dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Incoming Call",
            color = tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Elena Vance",
            color = Color.White,
            fontFamily = fontFamily,
            fontSize = (19f * scale).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = config.typography.letterSpacingSp.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(btnShape)
                    .background(ColorOsRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            if (config.incomingCall.showSmsReplyPill) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(0.8.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("SMS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(btnShape)
                    .background(ColorOsGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun OutgoingMiniPreview(
    config: PersonalizationConfig,
    accent: Color,
    secondary: Color
) {
    val fontFamily = resolveComposeFontFamily(config.typography.fontFamily)
    val scale = config.typography.fontScale

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Calling...",
                color = secondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Marcus Cole",
                color = Color.White,
                fontFamily = fontFamily,
                fontSize = (18f * scale).sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "+1 415 555 0198",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.5.sp
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.25f + config.outgoingCall.waveIntensity * 0.25f))
                .border(1.5.dp, secondary.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun OngoingMiniPreview(
    config: PersonalizationConfig,
    accent: Color,
    secondary: Color,
    tertiary: Color
) {
    val btnShape = PersonalizationManager.shapeFor(config.ongoingCall.buttonShape, 38f)
    val btnSize = (36f * config.ongoingCall.buttonSize.multiplier).dp
    val btnBg = when (config.ongoingCall.buttonStyle) {
        OngoingButtonVisualStyle.DEFAULT_PURPLE_GLASS -> Color(0xFF5D4D82).copy(alpha = 0.65f)
        OngoingButtonVisualStyle.LIQUID_GLASS -> accent.copy(alpha = 0.30f)
        OngoingButtonVisualStyle.FROSTED_SOLID -> Color.White.copy(alpha = 0.24f)
        OngoingButtonVisualStyle.NEON_RIM -> Color(0xFF0D1D35).copy(alpha = 0.80f)
    }
    val btnBorder = when (config.ongoingCall.buttonStyle) {
        OngoingButtonVisualStyle.NEON_RIM -> tertiary
        else -> Color.White.copy(alpha = 0.36f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "02:18 · HD Voice",
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        if (config.ongoingCall.showLiveWaveform) {
            Spacer(modifier = Modifier.height(6.dp))
            val waveTop = when (config.ongoingCall.waveformAccentStyle) {
                WaveformAccentStyle.BLUE_CYAN -> Color(0xFF00F2FE)
                WaveformAccentStyle.MATCH_THEME_ACCENT -> tertiary
                WaveformAccentStyle.AURORA_SPECTRUM -> secondary
            }
            val waveBottom = when (config.ongoingCall.waveformAccentStyle) {
                WaveformAccentStyle.BLUE_CYAN -> Color(0xFF2979FF)
                WaveformAccentStyle.MATCH_THEME_ACCENT -> accent
                WaveformAccentStyle.AURORA_SPECTRUM -> Color(0xFFA5F3FC)
            }
            Canvas(
                modifier = Modifier
                    .width(120.dp)
                    .height(22.dp)
            ) {
                val heights = listOf(0.3f, 0.55f, 0.85f, 0.6f, 0.95f, 0.7f, 0.45f, 0.78f, 0.4f)
                val slot = size.width / heights.size
                val bw = slot * 0.5f
                heights.forEachIndexed { idx, frac ->
                    val bh = size.height * frac
                    val left = idx * slot + (slot - bw) / 2f
                    val top = (size.height - bh) / 2f
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(waveTop, waveBottom)),
                        topLeft = Offset(left, top),
                        size = Size(bw, bh),
                        cornerRadius = CornerRadius(bw / 2f, bw / 2f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.82f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(btnShape)
                    .background(btnBg)
                    .border(1.dp, btnBorder, btnShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.MicOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Box(
                modifier = Modifier
                    .size(width = btnSize * 1.45f, height = btnSize)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ColorOsRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(btnShape)
                    .background(btnBg)
                    .border(1.dp, btnBorder, btnShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

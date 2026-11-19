package com.example.ui.personalization

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.PersonalizationManager
import com.example.model.AnimationMotionStyle
import com.example.model.AppThemeMode
import com.example.model.BuiltInPersonalizationPresets
import com.example.model.ButtonShapeStyle
import com.example.model.ButtonSizeScale
import com.example.model.OngoingButtonVisualStyle
import com.example.model.PersonalizationConfig
import com.example.model.SavedCustomTheme
import com.example.model.StudioFontFamily
import com.example.model.WaveformAccentStyle
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.resolveComposeFontFamily
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OngoingCallCategoryContent(
    config: PersonalizationConfig,
    onShowMessage: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PersonalizationLiveThemePreviewCard(
            config = config,
            initialMode = PreviewMode.ONGOING,
            showCustomizeButton = false
        )

        LiquidGlassCard(config = config) {
            LiquidGlassOptionSelector(
                title = "Call Control Button Shape",
                options = ButtonShapeStyle.entries,
                selected = config.ongoingCall.buttonShape,
                labelOf = { it.label },
                config = config,
                onSelect = { shape ->
                    PersonalizationManager.updateDraft {
                        it.copy(ongoingCall = it.ongoingCall.copy(buttonShape = shape))
                    }
                },
                testTagPrefix = "ongoing_btn_shape"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassOptionSelector(
                title = "Call Button Glass Style",
                options = OngoingButtonVisualStyle.entries,
                selected = config.ongoingCall.buttonStyle,
                labelOf = { it.label },
                config = config,
                onSelect = { style ->
                    PersonalizationManager.updateDraft {
                        it.copy(ongoingCall = it.ongoingCall.copy(buttonStyle = style))
                    }
                },
                testTagPrefix = "ongoing_btn_style"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassOptionSelector(
                title = "Control Button Size",
                options = ButtonSizeScale.entries,
                selected = config.ongoingCall.buttonSize,
                labelOf = { it.label },
                config = config,
                onSelect = { size ->
                    PersonalizationManager.updateDraft {
                        it.copy(ongoingCall = it.ongoingCall.copy(buttonSize = size))
                    }
                },
                testTagPrefix = "ongoing_btn_size"
            )
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassToggleRow(
                title = "Show Live Voice Waveform",
                subtitle = "Display centered acoustic bars during active calls",
                checked = config.ongoingCall.showLiveWaveform,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(ongoingCall = it.ongoingCall.copy(showLiveWaveform = enabled))
                    }
                },
                config = config,
                testTag = "toggle_ongoing_waveform"
            )
            if (config.ongoingCall.showLiveWaveform) {
                Spacer(modifier = Modifier.height(8.dp))
                LiquidGlassOptionSelector(
                    title = "Waveform Accent Palette",
                    options = WaveformAccentStyle.entries,
                    selected = config.ongoingCall.waveformAccentStyle,
                    labelOf = { it.label },
                    config = config,
                    onSelect = { accentStyle ->
                        PersonalizationManager.updateDraft {
                            it.copy(ongoingCall = it.ongoingCall.copy(waveformAccentStyle = accentStyle))
                        }
                    },
                    testTagPrefix = "ongoing_wave_accent"
                )
            }
        }

        CallScreenWallpaperSyncCard(config = config, onShowMessage = onShowMessage)
    }
}

@Composable
fun TypographyCategoryContent(config: PersonalizationConfig) {
    val activeFont = resolveComposeFontFamily(config.typography.fontFamily)
    val scale = config.typography.fontScale
    val spacing = config.typography.letterSpacingSp.sp

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Live Typography Specimen Card
        LiquidGlassCard(config = config) {
            Text(
                text = "TYPOGRAPHY LIVE SPECIMEN",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Make Every Call Yours.",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = activeFont,
                fontSize = (24f * scale).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = spacing,
                modifier = Modifier.testTag("typography_live_specimen_title")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sophia Sterling · +1 (415) 890-2410 · 02:18 HD Voice",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = activeFont,
                fontSize = (14.5f * scale).sp,
                letterSpacing = spacing
            )
        }

        // Bundled Google Font Cards
        LiquidGlassCard(config = config) {
            Text(
                text = "Font Family",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioFontFamily.entries.forEachIndexed { idx, family ->
                    val selected = config.typography.fontFamily == family
                    val previewFamily = resolveComposeFontFamily(family)
                    val accent = MaterialTheme.colorScheme.primary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) accent.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) accent else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                PersonalizationManager.updateDraft {
                                    it.copy(typography = it.typography.copy(fontFamily = family))
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("font_option_${family.name.lowercase()}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = family.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = previewFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = family.previewTagline,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = previewFamily,
                                fontSize = 12.sp
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected font",
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        LiquidGlassCard(config = config) {
            LiquidGlassSliderRow(
                title = "Font Size Scale",
                valueLabel = String.format(Locale.US, "%.2fx", config.typography.fontScale),
                value = config.typography.fontScale,
                valueRange = 0.85f..1.25f,
                onValueChange = { scaleVal ->
                    PersonalizationManager.updateDraft {
                        it.copy(typography = it.typography.copy(fontScale = scaleVal))
                    }
                },
                config = config,
                sliderTestTag = "typography_scale_slider"
            )
            Spacer(modifier = Modifier.height(6.dp))
            LiquidGlassSliderRow(
                title = "Letter Spacing",
                valueLabel = String.format(Locale.US, "%.2f sp", config.typography.letterSpacingSp),
                value = config.typography.letterSpacingSp,
                valueRange = -0.5f..1.5f,
                onValueChange = { spacingVal ->
                    PersonalizationManager.updateDraft {
                        it.copy(typography = it.typography.copy(letterSpacingSp = spacingVal))
                    }
                },
                config = config,
                sliderTestTag = "typography_spacing_slider"
            )
        }
    }
}

@Composable
fun AnimationsCategoryContent(config: PersonalizationConfig) {
    var pulseTrigger by remember { mutableStateOf(false) }
    val demoScale by animateFloatAsState(
        targetValue = if (pulseTrigger) 1.07f else 1.0f,
        animationSpec = PersonalizationManager.resolveAnimationSpec(320, config.animations),
        label = "anim_playground_scale"
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Interactive Live Animation Playground
        LiquidGlassCard(config = config) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interactive Motion Playground",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tap the glass pill to feel ${config.animations.style.label}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .scale(demoScale)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .clickable { pulseTrigger = !pulseTrigger }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("animation_demo_trigger"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Animation,
                            contentDescription = "Test animation",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        LiquidGlassCard(config = config) {
            LiquidGlassOptionSelector(
                title = "Animation Style",
                options = AnimationMotionStyle.entries,
                selected = config.animations.style,
                labelOf = { it.label },
                config = config,
                onSelect = { style ->
                    PersonalizationManager.updateDraft {
                        it.copy(animations = it.animations.copy(style = style))
                    }
                },
                testTagPrefix = "animation_style"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = config.animations.style.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            LiquidGlassSliderRow(
                title = "Transition Speed",
                valueLabel = String.format(Locale.US, "%.2fx", config.animations.speedMultiplier),
                value = config.animations.speedMultiplier,
                valueRange = 0.5f..1.75f,
                onValueChange = { speed ->
                    PersonalizationManager.updateDraft {
                        it.copy(animations = it.animations.copy(speedMultiplier = speed))
                    }
                },
                config = config,
                sliderTestTag = "animation_speed_slider"
            )
            Spacer(modifier = Modifier.height(6.dp))
            LiquidGlassToggleRow(
                title = "Micro-Interaction Press Bounce",
                subtitle = "Subtle fluid scale response when tapping cards and buttons",
                checked = config.animations.microInteractionsEnabled,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(animations = it.animations.copy(microInteractionsEnabled = enabled))
                    }
                },
                config = config,
                testTag = "toggle_micro_interactions"
            )
        }
    }
}

@Composable
fun VisualEffectsCategoryContent(config: PersonalizationConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PersonalizationLiveThemePreviewCard(
            config = config,
            initialMode = PreviewMode.DIALER,
            showCustomizeButton = false
        )

        LiquidGlassCard(config = config) {
            LiquidGlassToggleRow(
                title = "Liquid Glassmorphism",
                subtitle = "Layered translucent glass cards with specular reflections",
                checked = config.visualEffects.glassmorphismEnabled,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(visualEffects = it.visualEffects.copy(glassmorphismEnabled = enabled))
                    }
                },
                config = config,
                testTag = "toggle_glassmorphism"
            )
            Spacer(modifier = Modifier.height(8.dp))
            LiquidGlassSliderRow(
                title = "Background Blur Intensity",
                valueLabel = "${config.visualEffects.blurIntensityDp.roundToInt()} dp",
                value = config.visualEffects.blurIntensityDp,
                valueRange = 0f..28f,
                onValueChange = { blur ->
                    PersonalizationManager.updateDraft {
                        it.copy(visualEffects = it.visualEffects.copy(blurIntensityDp = blur))
                    }
                },
                config = config,
                sliderTestTag = "effects_blur_slider"
            )
            LiquidGlassSliderRow(
                title = "Glass Transparency",
                valueLabel = "${(config.visualEffects.glassTransparency * 100).roundToInt()}%",
                value = config.visualEffects.glassTransparency,
                valueRange = 0.10f..0.85f,
                onValueChange = { alpha ->
                    PersonalizationManager.updateDraft {
                        it.copy(visualEffects = it.visualEffects.copy(glassTransparency = alpha))
                    }
                },
                config = config,
                sliderTestTag = "effects_transparency_slider"
            )
            LiquidGlassSliderRow(
                title = "Specular Highlight Reflection",
                valueLabel = "${(config.visualEffects.highlightReflection * 100).roundToInt()}%",
                value = config.visualEffects.highlightReflection,
                valueRange = 0f..1f,
                onValueChange = { refl ->
                    PersonalizationManager.updateDraft {
                        it.copy(visualEffects = it.visualEffects.copy(highlightReflection = refl))
                    }
                },
                config = config,
                sliderTestTag = "effects_reflection_slider"
            )
            LiquidGlassSliderRow(
                title = "Ambient Depth Glow",
                valueLabel = "${(config.visualEffects.glowIntensity * 100).roundToInt()}%",
                value = config.visualEffects.glowIntensity,
                valueRange = 0f..1f,
                onValueChange = { glow ->
                    PersonalizationManager.updateDraft {
                        it.copy(visualEffects = it.visualEffects.copy(glowIntensity = glow))
                    }
                },
                config = config,
                sliderTestTag = "effects_glow_slider"
            )
        }
    }
}

@Composable
fun ThemesCategoryContent(
    config: PersonalizationConfig,
    savedThemes: List<SavedCustomTheme>,
    onOpenSaveDialog: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var renamingTheme by remember { mutableStateOf<SavedCustomTheme?>(null) }
    var deletingTheme by remember { mutableStateOf<SavedCustomTheme?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Appearance Mode (Light / Dark / Follow System)
        LiquidGlassCard(config = config) {
            LiquidGlassOptionSelector(
                title = "Appearance Mode",
                options = AppThemeMode.entries,
                selected = config.themeMode,
                labelOf = { it.label },
                config = config,
                onSelect = { mode ->
                    PersonalizationManager.updateDraft { it.copy(themeMode = mode) }
                },
                testTagPrefix = "themes_mode"
            )
        }

        // Saved Custom Themes Section
        LiquidGlassCard(config = config) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Saved Themes",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${savedThemes.size} custom theme(s) saved",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp
                    )
                }
                LiquidGlassActionChip(
                    label = "Save Current",
                    icon = Icons.Default.Add,
                    config = config,
                    isPrimary = true,
                    onClick = onOpenSaveDialog,
                    modifier = Modifier.testTag("themes_save_current_btn")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (savedThemes.isEmpty()) {
                Text(
                    text = "No custom themes saved yet. Customize colors, fonts, or glass effects and tap Save Current.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    savedThemes.forEach { saved ->
                        ThemePresetItemRow(
                            theme = saved,
                            isCurrent = config.activeThemeId == saved.id,
                            onPreview = {
                                PersonalizationManager.previewSavedOrPresetTheme(saved)
                                onShowMessage("Loaded \"${saved.name}\" into preview")
                            },
                            onApply = {
                                PersonalizationManager.applySavedOrPresetTheme(context, saved.id)
                                onShowMessage("Applied \"${saved.name}\" everywhere")
                            },
                            onRename = { renamingTheme = saved },
                            onDelete = { deletingTheme = saved }
                        )
                    }
                }
            }
        }

        // Curated Built-in Presets
        LiquidGlassCard(config = config) {
            Text(
                text = "Curated Liquid Glass Presets",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BuiltInPersonalizationPresets.all.forEach { preset ->
                    ThemePresetItemRow(
                        theme = preset,
                        isCurrent = config.activeThemeId == preset.id,
                        onPreview = {
                            PersonalizationManager.previewSavedOrPresetTheme(preset)
                            onShowMessage("Previewing \"${preset.name}\"")
                        },
                        onApply = {
                            PersonalizationManager.applySavedOrPresetTheme(context, preset.id)
                            onShowMessage("Applied \"${preset.name}\" everywhere")
                        },
                        onRename = null,
                        onDelete = null
                    )
                }
            }
        }
    }

    renamingTheme?.let { target ->
        var newNameDraft by remember(target) { mutableStateOf(target.name) }
        var renameError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { renamingTheme = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Rename Theme") },
            text = {
                OutlinedTextField(
                    value = newNameDraft,
                    onValueChange = {
                        newNameDraft = it
                        renameError = null
                    },
                    label = { Text("Theme name") },
                    isError = renameError != null,
                    supportingText = renameError?.let { err -> { Text(err) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_theme_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val res = PersonalizationManager.renameCustomTheme(context, target.id, newNameDraft)
                        res.onSuccess {
                            renamingTheme = null
                            onShowMessage("Renamed theme to \"${it.name}\"")
                        }.onFailure { err ->
                            renameError = err.localizedMessage ?: "Invalid name"
                        }
                    },
                    modifier = Modifier.testTag("rename_theme_confirm_btn")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTheme = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deletingTheme?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingTheme = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete Saved Theme?") },
            text = { Text("Remove \"${target.name}\" from your saved custom themes?") },
            confirmButton = {
                Button(
                    onClick = {
                        PersonalizationManager.deleteCustomTheme(context, target.id)
                        deletingTheme = null
                        onShowMessage("Deleted \"${target.name}\"")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOsRed),
                    modifier = Modifier.testTag("delete_theme_confirm_btn")
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTheme = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ThemePresetItemRow(
    theme: SavedCustomTheme,
    isCurrent: Boolean,
    onPreview: () -> Unit,
    onApply: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val accent = MaterialTheme.colorScheme.primary
    val primarySwatch = theme.config.primaryColor
    val secondarySwatch = theme.config.secondaryColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isCurrent) accent.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .border(
                width = 1.dp,
                color = if (isCurrent) accent else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onPreview)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("theme_item_${theme.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(primarySwatch, secondarySwatch)))
                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${theme.config.themeMode.label} · ${theme.config.typography.fontFamily.label}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
            )
        }
        if (onRename != null) {
            IconButton(
                onClick = onRename,
                modifier = Modifier.testTag("rename_theme_btn_${theme.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename ${theme.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_theme_btn_${theme.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete ${theme.name}",
                    tint = ColorOsRed,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.18f))
                .clickable(onClick = onApply)
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("apply_theme_btn_${theme.id}")
        ) {
            Text(
                text = if (isCurrent) "Active" else "Apply",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

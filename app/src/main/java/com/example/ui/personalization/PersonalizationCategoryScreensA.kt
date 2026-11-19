package com.example.ui.personalization

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CallScreenWallpaperManager
import com.example.data.CallWallpaperType
import com.example.data.PersonalizationManager
import com.example.model.AppBackgroundStyle
import com.example.model.ButtonShapeStyle
import com.example.model.ButtonSizeScale
import com.example.model.CallDirectionIconStyle
import com.example.model.CallHistoryLayoutStyle
import com.example.model.ContactHeaderStyle
import com.example.model.DialpadButtonVisualStyle
import com.example.model.IncomingCallLayoutStyle
import com.example.model.OutgoingCallLayoutStyle
import com.example.model.PersonalizationConfig
import kotlin.math.roundToInt

private enum class ActiveColorTarget(val label: String) {
    PRIMARY("Primary Color"),
    SECONDARY("Secondary Color"),
    ACCENT("Accent Color"),
    TEXT("Text Color"),
    MISSED_CALL("Missed Call Highlight")
}

@Composable
fun HomeDialerCategoryContent(
    config: PersonalizationConfig,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var activeColorTarget by remember { mutableStateOf<ActiveColorTarget?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val result = PersonalizationManager.importAppBackgroundPhotoUri(context, uri)
            result.onSuccess { persistedUri ->
                PersonalizationManager.updateDraft {
                    it.copy(
                        homeDialer = it.homeDialer.copy(
                            backgroundStyle = AppBackgroundStyle.CUSTOM_PHOTO,
                            customBackgroundPhotoUri = persistedUri
                        )
                    )
                }
                onShowMessage("Background photo updated in preview")
            }.onFailure { err ->
                onShowMessage(err.localizedMessage ?: "Failed to load selected photo")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PersonalizationLiveThemePreviewCard(
            config = config,
            initialMode = PreviewMode.DIALER,
            showCustomizeButton = false
        )

        LiquidGlassCard(config = config) {
            Text(
                text = "App Background & Wallpaper",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassOptionSelector(
                title = "Background Style",
                options = AppBackgroundStyle.entries,
                selected = config.homeDialer.backgroundStyle,
                labelOf = { it.label },
                config = config,
                onSelect = { style ->
                    if (style == AppBackgroundStyle.CUSTOM_PHOTO &&
                        config.homeDialer.customBackgroundPhotoUri.isBlank()
                    ) {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } else {
                        PersonalizationManager.updateDraft {
                            it.copy(homeDialer = it.homeDialer.copy(backgroundStyle = style))
                        }
                    }
                },
                testTagPrefix = "home_bg_style"
            )
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassActionChip(
                label = if (config.homeDialer.customBackgroundPhotoUri.isNotBlank()) {
                    "Change Background Photo"
                } else {
                    "Pick Photo Wallpaper"
                },
                icon = Icons.Default.Image,
                config = config,
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_pick_wallpaper_photo_btn")
            )
        }

        LiquidGlassCard(config = config) {
            Text(
                text = "Theme Color Palette",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            LiquidGlassToggleRow(
                title = "Enable Custom Theme Colors",
                subtitle = "Override default accent & surface tints with your palette",
                checked = config.useCustomColors,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft { it.copy(useCustomColors = enabled) }
                },
                config = config,
                testTag = "toggle_use_custom_colors"
            )
            ColorSwatchRow(
                label = "Primary Color",
                color = config.primaryColor,
                onClick = { activeColorTarget = ActiveColorTarget.PRIMARY },
                testTag = "swatch_primary_color"
            )
            ColorSwatchRow(
                label = "Secondary Color",
                color = config.secondaryColor,
                onClick = { activeColorTarget = ActiveColorTarget.SECONDARY },
                testTag = "swatch_secondary_color"
            )
            ColorSwatchRow(
                label = "Accent Color",
                color = config.accentColor,
                onClick = { activeColorTarget = ActiveColorTarget.ACCENT },
                testTag = "swatch_accent_color"
            )
            LiquidGlassToggleRow(
                title = "Custom Text Color",
                subtitle = "Override primary text ink across lists and dialer",
                checked = config.useCustomTextColor,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft { it.copy(useCustomTextColor = enabled) }
                },
                config = config,
                testTag = "toggle_custom_text_color"
            )
            if (config.useCustomTextColor) {
                ColorSwatchRow(
                    label = "Primary Text Color",
                    color = config.customTextColor,
                    onClick = { activeColorTarget = ActiveColorTarget.TEXT },
                    testTag = "swatch_text_color"
                )
            }
        }

        LiquidGlassCard(config = config) {
            Text(
                text = "Dialpad Keys & Sheet",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassOptionSelector(
                title = "Button Shape",
                options = ButtonShapeStyle.entries,
                selected = config.homeDialer.dialpadButtonShape,
                labelOf = { it.label },
                config = config,
                onSelect = { shape ->
                    PersonalizationManager.updateDraft {
                        it.copy(homeDialer = it.homeDialer.copy(dialpadButtonShape = shape))
                    }
                },
                testTagPrefix = "dialpad_shape"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassOptionSelector(
                title = "Button Style",
                options = DialpadButtonVisualStyle.entries,
                selected = config.homeDialer.dialpadButtonStyle,
                labelOf = { it.label },
                config = config,
                onSelect = { style ->
                    PersonalizationManager.updateDraft {
                        it.copy(homeDialer = it.homeDialer.copy(dialpadButtonStyle = style))
                    }
                },
                testTagPrefix = "dialpad_style"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassOptionSelector(
                title = "Button Size",
                options = ButtonSizeScale.entries,
                selected = config.homeDialer.dialpadButtonSize,
                labelOf = { it.label },
                config = config,
                onSelect = { scale ->
                    PersonalizationManager.updateDraft {
                        it.copy(homeDialer = it.homeDialer.copy(dialpadButtonSize = scale))
                    }
                },
                testTagPrefix = "dialpad_size"
            )
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassToggleRow(
                title = "Liquid Glass Dialpad Sheet",
                subtitle = "Apply translucent frosted glass surface to the dialpad drawer",
                checked = config.homeDialer.dialpadGlassSheet,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(homeDialer = it.homeDialer.copy(dialpadGlassSheet = enabled))
                    }
                },
                config = config,
                testTag = "toggle_dialpad_glass_sheet"
            )
        }
    }

    activeColorTarget?.let { target ->
        val initial = when (target) {
            ActiveColorTarget.PRIMARY -> config.primaryColor
            ActiveColorTarget.SECONDARY -> config.secondaryColor
            ActiveColorTarget.ACCENT -> config.accentColor
            ActiveColorTarget.TEXT -> config.customTextColor
            ActiveColorTarget.MISSED_CALL -> config.missedCallColor
        }
        LiquidGlassColorPickerDialog(
            title = "Pick ${target.label}",
            initialColor = initial,
            onDismiss = { activeColorTarget = null },
            onColorSelected = { picked ->
                val argb = picked.toArgb().toLong() and 0xFFFFFFFFL
                PersonalizationManager.updateDraft { current ->
                    when (target) {
                        ActiveColorTarget.PRIMARY -> current.copy(useCustomColors = true, primaryColorArgb = argb)
                        ActiveColorTarget.SECONDARY -> current.copy(useCustomColors = true, secondaryColorArgb = argb)
                        ActiveColorTarget.ACCENT -> current.copy(useCustomColors = true, accentColorArgb = argb)
                        ActiveColorTarget.TEXT -> current.copy(useCustomTextColor = true, textColorArgb = argb)
                        ActiveColorTarget.MISSED_CALL -> current.copy(
                            callHistory = current.callHistory.copy(missedCallColorArgb = argb)
                        )
                    }
                }
                activeColorTarget = null
            }
        )
    }
}

@Composable
fun ContactsCategoryContent(config: PersonalizationConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PersonalizationLiveThemePreviewCard(
            config = config,
            initialMode = PreviewMode.CONTACTS,
            showCustomizeButton = false
        )

        LiquidGlassCard(config = config) {
            LiquidGlassOptionSelector(
                title = "Contact Avatar Shape",
                options = ButtonShapeStyle.entries,
                selected = config.contacts.avatarShape,
                labelOf = { it.label },
                config = config,
                onSelect = { shape ->
                    PersonalizationManager.updateDraft {
                        it.copy(contacts = it.contacts.copy(avatarShape = shape))
                    }
                },
                testTagPrefix = "contacts_avatar_shape"
            )
            Spacer(modifier = Modifier.height(14.dp))
            LiquidGlassSliderRow(
                title = "Card Corner Radius",
                valueLabel = "${config.contacts.cardCornerRadiusDp.roundToInt()} dp",
                value = config.contacts.cardCornerRadiusDp,
                valueRange = 8f..32f,
                onValueChange = { v ->
                    PersonalizationManager.updateDraft {
                        it.copy(contacts = it.contacts.copy(cardCornerRadiusDp = v))
                    }
                },
                config = config,
                sliderTestTag = "contacts_corner_slider"
            )
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassOptionSelector(
                title = "Contact Profile Header Style",
                options = ContactHeaderStyle.entries,
                selected = config.contacts.profileHeaderStyle,
                labelOf = { it.label },
                config = config,
                onSelect = { header ->
                    PersonalizationManager.updateDraft {
                        it.copy(contacts = it.contacts.copy(profileHeaderStyle = header))
                    }
                },
                testTagPrefix = "contacts_header_style"
            )
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassToggleRow(
                title = "Show Company & Job Subtitle",
                subtitle = "Display organization details under contact names",
                checked = config.contacts.showCompanySubtitle,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(contacts = it.contacts.copy(showCompanySubtitle = enabled))
                    }
                },
                config = config,
                testTag = "toggle_contacts_subtitle"
            )
            LiquidGlassToggleRow(
                title = "Luminous Avatar Rim",
                subtitle = "Add a subtle glass accent ring around contact avatars",
                checked = config.contacts.showAvatarGlowRing,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(contacts = it.contacts.copy(showAvatarGlowRing = enabled))
                    }
                },
                config = config,
                testTag = "toggle_contacts_avatar_ring"
            )
        }
    }
}

@Composable
fun CallHistoryCategoryContent(config: PersonalizationConfig) {
    var showMissedPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PersonalizationLiveThemePreviewCard(
            config = config,
            initialMode = PreviewMode.HISTORY,
            showCustomizeButton = false
        )

        LiquidGlassCard(config = config) {
            LiquidGlassOptionSelector(
                title = "Call History Layout",
                options = CallHistoryLayoutStyle.entries,
                selected = config.callHistory.layoutStyle,
                labelOf = { it.label },
                config = config,
                onSelect = { style ->
                    PersonalizationManager.updateDraft {
                        it.copy(callHistory = it.callHistory.copy(layoutStyle = style))
                    }
                },
                testTagPrefix = "history_layout"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassOptionSelector(
                title = "Direction Icon Style",
                options = CallDirectionIconStyle.entries,
                selected = config.callHistory.directionIconStyle,
                labelOf = { it.label },
                config = config,
                onSelect = { iconStyle ->
                    PersonalizationManager.updateDraft {
                        it.copy(callHistory = it.callHistory.copy(directionIconStyle = iconStyle))
                    }
                },
                testTagPrefix = "history_dir_style"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassSliderRow(
                title = "Row Card Radius",
                valueLabel = "${config.callHistory.rowCornerRadiusDp.roundToInt()} dp",
                value = config.callHistory.rowCornerRadiusDp,
                valueRange = 8f..28f,
                onValueChange = { v ->
                    PersonalizationManager.updateDraft {
                        it.copy(callHistory = it.callHistory.copy(rowCornerRadiusDp = v))
                    }
                },
                config = config,
                sliderTestTag = "history_radius_slider"
            )
            LiquidGlassToggleRow(
                title = "Show VoLTE & HD Badges",
                subtitle = "Display carrier voice quality badges in call rows",
                checked = config.callHistory.showVoLteAndHdBadges,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(callHistory = it.callHistory.copy(showVoLteAndHdBadges = enabled))
                    }
                },
                config = config,
                testTag = "toggle_history_badges"
            )
            LiquidGlassToggleRow(
                title = "Show Call Duration",
                subtitle = "Append call duration next to number or location",
                checked = config.callHistory.showCallDuration,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(callHistory = it.callHistory.copy(showCallDuration = enabled))
                    }
                },
                config = config,
                testTag = "toggle_history_duration"
            )
            ColorSwatchRow(
                label = "Missed Call Color",
                color = config.missedCallColor,
                onClick = { showMissedPicker = true },
                testTag = "swatch_missed_call_color"
            )
        }
    }

    if (showMissedPicker) {
        LiquidGlassColorPickerDialog(
            title = "Missed Call Highlight Color",
            initialColor = config.missedCallColor,
            onDismiss = { showMissedPicker = false },
            onColorSelected = { picked ->
                val argb = picked.toArgb().toLong() and 0xFFFFFFFFL
                PersonalizationManager.updateDraft {
                    it.copy(callHistory = it.callHistory.copy(missedCallColorArgb = argb))
                }
                showMissedPicker = false
            }
        )
    }
}

@Composable
fun IncomingCallCategoryContent(
    config: PersonalizationConfig,
    onShowMessage: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PersonalizationLiveThemePreviewCard(
            config = config,
            initialMode = PreviewMode.INCOMING,
            showCustomizeButton = false
        )

        LiquidGlassCard(config = config) {
            LiquidGlassOptionSelector(
                title = "Incoming Screen Layout",
                options = IncomingCallLayoutStyle.entries,
                selected = config.incomingCall.layoutStyle,
                labelOf = { it.label },
                config = config,
                onSelect = { style ->
                    PersonalizationManager.updateDraft {
                        it.copy(incomingCall = it.incomingCall.copy(layoutStyle = style))
                    }
                },
                testTagPrefix = "incoming_layout"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassOptionSelector(
                title = "Answer & Decline Shape",
                options = ButtonShapeStyle.entries,
                selected = config.incomingCall.buttonShape,
                labelOf = { it.label },
                config = config,
                onSelect = { shape ->
                    PersonalizationManager.updateDraft {
                        it.copy(incomingCall = it.incomingCall.copy(buttonShape = shape))
                    }
                },
                testTagPrefix = "incoming_btn_shape"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassOptionSelector(
                title = "Control Button Size",
                options = ButtonSizeScale.entries,
                selected = config.incomingCall.buttonSize,
                labelOf = { it.label },
                config = config,
                onSelect = { size ->
                    PersonalizationManager.updateDraft {
                        it.copy(incomingCall = it.incomingCall.copy(buttonSize = size))
                    }
                },
                testTagPrefix = "incoming_btn_size"
            )
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassToggleRow(
                title = "Caller Avatar Wave Glow",
                subtitle = "Animate concentric rings around incoming caller avatar",
                checked = config.incomingCall.showPulseGlow,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(incomingCall = it.incomingCall.copy(showPulseGlow = enabled))
                    }
                },
                config = config,
                testTag = "toggle_incoming_pulse"
            )
            LiquidGlassToggleRow(
                title = "Show Quick SMS Reply Pill",
                subtitle = "Display one-tap reject with SMS action while ringing",
                checked = config.incomingCall.showSmsReplyPill,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(incomingCall = it.incomingCall.copy(showSmsReplyPill = enabled))
                    }
                },
                config = config,
                testTag = "toggle_incoming_sms_pill"
            )
        }

        CallScreenWallpaperSyncCard(config = config, onShowMessage = onShowMessage)
    }
}

@Composable
fun OutgoingCallCategoryContent(
    config: PersonalizationConfig,
    onShowMessage: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PersonalizationLiveThemePreviewCard(
            config = config,
            initialMode = PreviewMode.OUTGOING,
            showCustomizeButton = false
        )

        LiquidGlassCard(config = config) {
            LiquidGlassOptionSelector(
                title = "Outgoing Screen Style",
                options = OutgoingCallLayoutStyle.entries,
                selected = config.outgoingCall.layoutStyle,
                labelOf = { it.label },
                config = config,
                onSelect = { style ->
                    PersonalizationManager.updateDraft {
                        it.copy(outgoingCall = it.outgoingCall.copy(layoutStyle = style))
                    }
                },
                testTagPrefix = "outgoing_layout"
            )
            Spacer(modifier = Modifier.height(12.dp))
            LiquidGlassSliderRow(
                title = "Calling Wave Glow Intensity",
                valueLabel = "${(config.outgoingCall.waveIntensity * 100).roundToInt()}%",
                value = config.outgoingCall.waveIntensity,
                valueRange = 0f..1f,
                onValueChange = { v ->
                    PersonalizationManager.updateDraft {
                        it.copy(outgoingCall = it.outgoingCall.copy(waveIntensity = v))
                    }
                },
                config = config,
                sliderTestTag = "outgoing_wave_slider"
            )
            LiquidGlassToggleRow(
                title = "Liquid Glass Caller Header Card",
                subtitle = "Frame caller info and SIM status inside a frosted glass panel",
                checked = config.outgoingCall.useGlassHeaderCard,
                onCheckedChange = { enabled ->
                    PersonalizationManager.updateDraft {
                        it.copy(outgoingCall = it.outgoingCall.copy(useGlassHeaderCard = enabled))
                    }
                },
                config = config,
                testTag = "toggle_outgoing_glass_header"
            )
        }

        CallScreenWallpaperSyncCard(config = config, onShowMessage = onShowMessage)
    }
}

@Composable
fun CallScreenWallpaperSyncCard(
    config: PersonalizationConfig,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val wallpaperConfig by CallScreenWallpaperManager.appliedConfig.collectAsState()

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            CallScreenWallpaperManager.importAndPersistPhotoUri(context, uri)
                .onSuccess { persisted ->
                    CallScreenWallpaperManager.applyWallpaperToAllCalls(
                        context,
                        wallpaperConfig.copy(
                            type = CallWallpaperType.PHOTO,
                            mediaUri = persisted
                        )
                    )
                    onShowMessage("Call wallpaper photo applied to all calls")
                }
                .onFailure { err ->
                    onShowMessage(err.localizedMessage ?: "Failed to load call wallpaper")
                }
        }
    }

    LiquidGlassCard(config = config) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Call Screen Wallpaper",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = wallpaperConfig.summarySubtitle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset call wallpaper",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable {
                        CallScreenWallpaperManager.resetToDefault(context)
                        onShowMessage("Call screen wallpaper reset to System Default")
                    }
                    .padding(4.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CallScreenWallpaperManager.BUILT_IN_WALLPAPERS.forEach { preset ->
                val selected = wallpaperConfig.type == CallWallpaperType.BUILT_IN &&
                    wallpaperConfig.builtInId == preset.id
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.verticalGradient(preset.gradientStops))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            CallScreenWallpaperManager.applyWallpaperToAllCalls(
                                context,
                                wallpaperConfig.copy(
                                    type = CallWallpaperType.BUILT_IN,
                                    builtInId = preset.id
                                )
                            )
                            onShowMessage("Applied ${preset.title} to call screens")
                        }
                        .padding(6.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = preset.title,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        LiquidGlassSliderRow(
            title = "Call Wallpaper Blur",
            valueLabel = "${wallpaperConfig.blurRadiusDp.roundToInt()} dp",
            value = wallpaperConfig.blurRadiusDp,
            valueRange = CallScreenWallpaperManager.MIN_BLUR_DP..CallScreenWallpaperManager.MAX_BLUR_DP,
            onValueChange = { blur ->
                CallScreenWallpaperManager.applyWallpaperToAllCalls(
                    context,
                    wallpaperConfig.copy(blurRadiusDp = blur)
                )
            },
            config = config
        )
        LiquidGlassSliderRow(
            title = "Call Wallpaper Darken",
            valueLabel = "${(wallpaperConfig.darkenAlpha * 100).roundToInt()}%",
            value = wallpaperConfig.darkenAlpha,
            valueRange = CallScreenWallpaperManager.MIN_DARKEN_ALPHA..CallScreenWallpaperManager.MAX_DARKEN_ALPHA,
            onValueChange = { alpha ->
                CallScreenWallpaperManager.applyWallpaperToAllCalls(
                    context,
                    wallpaperConfig.copy(darkenAlpha = alpha)
                )
            },
            config = config
        )
        Spacer(modifier = Modifier.height(6.dp))
        LiquidGlassActionChip(
            label = "Choose Photo for Call Screens",
            icon = Icons.Default.Image,
            config = config,
            onClick = {
                photoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

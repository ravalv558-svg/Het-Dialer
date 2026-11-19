package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecom.CallRecordingManager
import com.example.telecom.CallRecordingQuality
import com.example.telecom.CallRecordingSessionState
import com.example.telecom.CallRecordingStorageLocation
import com.example.telecom.RecordingAvailabilityLevel
import com.example.telecom.SavedCallRecording
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.ColorOsWhite

/**
 * Full OPPO ColorOS 16 Call Recording Settings & Recording Library Screen.
 * Replaces the old informational restriction popup with:
 * 1. Clear ON/OFF Call Recording toggle.
 * 2. Live Device & Android 17 Capability Status card (Motorola G57 Power / CAPTURE_AUDIO_OUTPUT / RECORD_AUDIO).
 * 3. Participant Notification & Speakerphone Acoustic Coupling settings.
 * 4. Recording Audio Quality & Storage Location settings.
 * 5. Saved Call Recordings Library with real MediaPlayer playback, Rename, Share, and Delete.
 */
@Composable
fun CallRecordingSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        CallRecordingManager.initialize(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            CallRecordingManager.stopPlayback()
        }
    }

    val sessionState by CallRecordingManager.sessionState.collectAsState()
    var capabilityRefreshTick by remember { mutableIntStateOf(0) }

    val capabilityReport = remember(
        sessionState.isFeatureEnabled,
        sessionState.selectedStorage,
        capabilityRefreshTick
    ) {
        CallRecordingManager.inspectDeviceCapabilities(context)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        capabilityRefreshTick++
    }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var recordingToRename by remember { mutableStateOf<SavedCallRecording?>(null) }
    var recordingToDelete by remember { mutableStateOf<SavedCallRecording?>(null) }

    val pageBg = MaterialTheme.colorScheme.surfaceVariant
    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("call_recording_settings_screen")
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("call_recording_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Call recording",
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Master Call Recording ON/OFF Toggle
            item(key = "card_master_toggle") {
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                CallRecordingManager.setFeatureEnabled(
                                    context,
                                    !sessionState.isFeatureEnabled
                                )
                            }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable call recording",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (sessionState.isFeatureEnabled) {
                                    "ON · Manual in-call recording with participant consent disclosure"
                                } else {
                                    "OFF · In-call Record button will not start audio capture"
                                },
                                color = textSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = sessionState.isFeatureEnabled,
                            onCheckedChange = { enabled ->
                                CallRecordingManager.setFeatureEnabled(context, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorOsWhite,
                                checkedTrackColor = ColorOsGreen
                            ),
                            modifier = Modifier.testTag("call_recording_master_toggle")
                        )
                    }
                }
            }

            // Card 2: Actual Device & Android 17 Capability Status
            item(key = "card_capability_status") {
                val badgeColor = when (capabilityReport.availabilityLevel) {
                    RecordingAvailabilityLevel.OEM_PRIVILEGED_TWO_WAY -> ColorOsGreen
                    RecordingAvailabilityLevel.ACOUSTIC_VOICE_COMMUNICATION_SUPPORTED -> Color(0xFFD97706)
                    RecordingAvailabilityLevel.PERMISSION_REQUIRED -> ColorOsRed
                    RecordingAvailabilityLevel.DISABLED_IN_SETTINGS -> textSecondary
                }

                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("call_recording_capability_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device & Android Capability",
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                color = badgeColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = capabilityReport.statusBadgeText,
                                    color = badgeColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = capabilityReport.statusHeadline,
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = capabilityReport.technicalExplanation,
                            color = textSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )

                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                        // Capability matrix rows
                        CapabilityCheckRow(
                            label = "Target Device / OS",
                            value = "${capabilityReport.deviceManufacturer} ${capabilityReport.deviceModel} · Android ${capabilityReport.androidRelease} (API ${capabilityReport.sdkInt})",
                            isOk = true,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        CapabilityCheckRow(
                            label = "Microphone Permission (RECORD_AUDIO)",
                            value = if (capabilityReport.hasRecordAudioPermission) "Granted" else "Not granted",
                            isOk = capabilityReport.hasRecordAudioPermission,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        CapabilityCheckRow(
                            label = "Default Phone App Role (ROLE_DIALER)",
                            value = if (capabilityReport.isDefaultDialer) "Active" else "Not default (InCallService requires role)",
                            isOk = capabilityReport.isDefaultDialer,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        CapabilityCheckRow(
                            label = "OEM Cellular Stream Privilege (CAPTURE_AUDIO_OUTPUT)",
                            value = if (capabilityReport.hasCaptureAudioOutputPrivilege) {
                                "Granted (System / OEM app)"
                            } else {
                                "Restricted by Android 17 (Uses Acoustic / Mic path)"
                            },
                            isOk = capabilityReport.hasCaptureAudioOutputPrivilege,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )

                        if (!capabilityReport.hasRecordAudioPermission) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("call_recording_grant_mic_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = ColorOsWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Grant Microphone Permission",
                                    color = ColorOsWhite,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Card 3: Silent Call Recording, Instant Start & Acoustic Routing Settings
            item(key = "card_consent_and_routing") {
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Row 1: Silent Call Recording (No beep or voice announcement)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = sessionState.isFeatureEnabled) {
                                    CallRecordingManager.setAnnounceConsentEnabled(
                                        context,
                                        !sessionState.announceConsentEnabled
                                    )
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Record calls silently",
                                    color = textPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (!sessionState.announceConsentEnabled) {
                                        "ON · Records silently with no beep tone and no spoken voice announcement."
                                    } else {
                                        "OFF · Plays an audible announcement before recording starts."
                                    },
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = !sessionState.announceConsentEnabled,
                                enabled = sessionState.isFeatureEnabled,
                                onCheckedChange = { silentModeOn ->
                                    CallRecordingManager.setAnnounceConsentEnabled(
                                        context,
                                        !silentModeOn
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ColorOsWhite,
                                    checkedTrackColor = ColorOsGreen
                                ),
                                modifier = Modifier.testTag("call_recording_silent_mode_toggle")
                            )
                        }

                        HorizontalDivider(
                            color = dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )

                        // Row 2: Instant 1-tap silent recording (skip confirmation dialog)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = sessionState.isFeatureEnabled) {
                                    CallRecordingManager.setSilentInstantStartEnabled(
                                        context,
                                        !sessionState.silentInstantStartEnabled
                                    )
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Instant 1-tap recording",
                                    color = textPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Start recording immediately when tapping Record during a call without showing a confirmation dialog.",
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = sessionState.silentInstantStartEnabled,
                                enabled = sessionState.isFeatureEnabled,
                                onCheckedChange = {
                                    CallRecordingManager.setSilentInstantStartEnabled(context, it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ColorOsWhite,
                                    checkedTrackColor = ColorOsGreen
                                ),
                                modifier = Modifier.testTag("call_recording_instant_start_toggle")
                            )
                        }

                        HorizontalDivider(
                            color = dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )

                        // Row 3: Auto-enable Speakerphone
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = sessionState.isFeatureEnabled) {
                                    CallRecordingManager.setAutoSpeakerEnabled(
                                        context,
                                        !sessionState.autoSpeakerForAcousticCapture
                                    )
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-enable Speakerphone for acoustic capture",
                                    color = textPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Keep OFF for silent earpiece/headset recording, or turn ON to route call audio to Speakerphone.",
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = sessionState.autoSpeakerForAcousticCapture,
                                enabled = sessionState.isFeatureEnabled,
                                onCheckedChange = {
                                    CallRecordingManager.setAutoSpeakerEnabled(context, it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ColorOsWhite,
                                    checkedTrackColor = ColorOsGreen
                                )
                            )
                        }
                    }
                }
            }

            // Card 4: Audio Quality & Storage Location Settings
            item(key = "card_quality_and_storage") {
                val totalUsedBytes = remember(sessionState.savedRecordings) {
                    sessionState.savedRecordings.sumOf { it.fileSizeBytes }
                }
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = sessionState.isFeatureEnabled) {
                                    showQualityDialog = true
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                                .testTag("call_recording_quality_row")
                        ) {
                            Text(
                                text = "Audio quality & format",
                                color = textPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${sessionState.selectedQuality.label} · ${sessionState.selectedQuality.subtitle}",
                                color = textSecondary,
                                fontSize = 12.5.sp
                            )
                        }

                        HorizontalDivider(
                            color = dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = sessionState.isFeatureEnabled) {
                                    showStorageDialog = true
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                                .testTag("call_recording_storage_row")
                        ) {
                            Text(
                                text = "Recording storage location",
                                color = textPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${sessionState.selectedStorage.label} · Used: ${CallRecordingManager.formatFileSize(totalUsedBytes)} · Free: ${CallRecordingManager.formatFileSize(capabilityReport.freeStorageBytes)}",
                                color = textSecondary,
                                fontSize = 12.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = capabilityReport.storageDirectoryPath,
                                color = textSecondary.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Section Header: Recording Library
            item(key = "header_recording_library") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recording Library (${sessionState.savedRecordings.size})",
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Verified .m4a audio only",
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            if (sessionState.savedRecordings.isEmpty()) {
                item(key = "empty_recordings_card") {
                    Surface(
                        color = cardBg,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_recording_empty_library")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = textSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No saved call recordings yet",
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "During an active call, tap Record and confirm participant notification. Only genuine, non-empty audio recordings are saved here—fake or 0-byte recordings are never created.",
                                color = textSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                items(
                    items = sessionState.savedRecordings,
                    key = { it.id }
                ) { recording ->
                    SavedRecordingItemCard(
                        recording = recording,
                        isPlayingThis = sessionState.playingRecordingId == recording.id && sessionState.isPlaybackPlaying,
                        isSelectedForPlayback = sessionState.playingRecordingId == recording.id,
                        playbackPositionMs = sessionState.playbackPositionMs,
                        playbackDurationMs = sessionState.playbackDurationMs,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onTogglePlay = { CallRecordingManager.togglePlayback(recording) },
                        onStopPlay = { CallRecordingManager.stopPlayback() },
                        onRename = { recordingToRename = recording },
                        onShare = { CallRecordingManager.shareRecording(context, recording) },
                        onDelete = { recordingToDelete = recording }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Audio Quality Picker Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Select audio quality", color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CallRecordingQuality.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    CallRecordingManager.setRecordingQuality(context, option)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sessionState.selectedQuality == option,
                                onClick = {
                                    CallRecordingManager.setRecordingQuality(context, option)
                                    showQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ColorOsGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(option.label, color = textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                                Text(option.subtitle, color = textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close", color = ColorOsGreen)
                }
            }
        )
    }

    // Storage Location Picker Dialog
    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Recording storage location", color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CallRecordingStorageLocation.entries.forEach { loc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    CallRecordingManager.setStorageLocation(context, loc)
                                    showStorageDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sessionState.selectedStorage == loc,
                                onClick = {
                                    CallRecordingManager.setStorageLocation(context, loc)
                                    showStorageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ColorOsGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(loc.label, color = textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                                Text(loc.description, color = textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Close", color = ColorOsGreen)
                }
            }
        )
    }

    // Rename Recording Dialog
    val targetRename = recordingToRename
    if (targetRename != null) {
        var renameDraft by remember(targetRename) { mutableStateOf(targetRename.title) }
        AlertDialog(
            onDismissRequest = { recordingToRename = null },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Rename recording", color = textPrimary) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Recording title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CallRecordingManager.renameRecording(context, targetRename.id, renameDraft)
                        recordingToRename = null
                    }
                ) {
                    Text("Save", color = ColorOsGreen, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToRename = null }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    // Delete Recording Confirmation Dialog
    val targetDelete = recordingToDelete
    if (targetDelete != null) {
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Delete recording?", color = textPrimary) },
            text = {
                Text(
                    text = "Permanently delete \"${targetDelete.title}\" (${CallRecordingManager.formatDurationShort(targetDelete.durationSeconds)}) from device storage?",
                    color = textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        CallRecordingManager.deleteRecording(context, targetDelete.id)
                        recordingToDelete = null
                    }
                ) {
                    Text("Delete", color = ColorOsRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }
}

@Composable
private fun CapabilityCheckRow(
    label: String,
    value: String,
    isOk: Boolean,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(0.48f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = if (isOk) textPrimary else Color(0xFFD97706),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.52f)
        )
    }
}

@Composable
private fun SavedRecordingItemCard(
    recording: SavedCallRecording,
    isPlayingThis: Boolean,
    isSelectedForPlayback: Boolean,
    playbackPositionMs: Int,
    playbackDurationMs: Int,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onTogglePlay: () -> Unit,
    onStopPlay: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = cardBg,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_recording_card_${recording.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (recording.phoneNumber.isNotBlank() && recording.phoneNumber != recording.title) {
                        Text(
                            text = recording.phoneNumber,
                            color = textSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${recording.callDirection} · ${recording.simLabel} · ${CallRecordingManager.formatTimestamp(recording.timestampMillis)}",
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }

                // Play / Pause Circular Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ColorOsGreen)
                        .clickable { onTogglePlay() }
                        .testTag("recording_play_pause_${recording.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlayingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlayingThis) "Pause" else "Play",
                        tint = ColorOsWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Playback Progress Bar when active
            if (isSelectedForPlayback) {
                val progress = (playbackPositionMs.toFloat() / playbackDurationMs.coerceAtLeast(1).toFloat())
                    .coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ColorOsGreen,
                        trackColor = textSecondary.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${CallRecordingManager.formatDurationShort((playbackPositionMs / 1000).toLong())} / ${CallRecordingManager.formatDurationShort(recording.durationSeconds)}",
                            color = textSecondary,
                            fontSize = 11.5.sp
                        )
                        TextButton(onClick = onStopPlay) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop playback",
                                tint = ColorOsRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", color = ColorOsRed, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Bottom Metadata + Action Row (Rename, Share, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${CallRecordingManager.formatDurationShort(recording.durationSeconds)} · ${CallRecordingManager.formatFileSize(recording.fileSizeBytes)} · ${recording.audioSourceUsed}",
                    color = textSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("recording_rename_${recording.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Rename recording",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("recording_share_${recording.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share recording",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("recording_delete_${recording.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete recording",
                            tint = ColorOsRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * In-Call Consent & Disclosure Confirmation Dialog displayed before starting genuine call recording.
 * Adheres to Android privacy guidelines: never secretly records calls or bypasses Android security rules,
 * and saves strictly to the app's secure internal directory (`files/CallRecordings`).
 */
@Composable
fun CallRecordingConsentDialog(
    callerDisplay: String,
    announceEnabled: Boolean,
    autoSpeakerEnabled: Boolean,
    hasCapturePrivilege: Boolean,
    onConfirmStartRecording: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F1736),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Start Local Call Recording?",
                color = ColorOsWhite,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You are about to record the call with $callerDisplay.",
                    color = Color(0xFFE8E2F4),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "• Participant Consent & Privacy Notice: ${if (announceEnabled) "An audible announcement (\"This call is now being recorded\") will play so all participants are notified." else "Ensure you verbally notify all participants and obtain their consent before recording."}",
                            color = Color(0xFFD8D0E8),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                        Text(
                            text = "• Secure Internal Storage: Audio is saved strictly to this app's sandboxed private internal directory (files/CallRecordings) with owner-only permissions and excluded from cloud auto-backup.",
                            color = Color(0xFF98F5B1),
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp
                        )
                        if (!hasCapturePrivilege) {
                            Text(
                                text = "• Android Audio Privacy Policy: Direct cellular line capture requires OEM system privilege (CAPTURE_AUDIO_OUTPUT). Recording uses standard MediaRecorder (${if (autoSpeakerEnabled) "with Speakerphone enabled for acoustic pickup" else "via microphone"}) and stops automatically when the call ends.",
                                color = Color(0xFFC9C0DE),
                                fontSize = 12.sp,
                                lineHeight = 16.5.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmStartRecording,
                colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("record_consent_confirm_btn")
            ) {
                Text(
                    text = "Confirm & Record",
                    color = ColorOsWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("record_consent_cancel_btn")
            ) {
                Text("Cancel", color = Color(0xFFD0CADC))
            }
        }
    )
}

/**
 * Subtle, non-intrusive 'Recording' status badge and pulsing animation displayed at the
 * top center of the ongoing call screen ([ActiveCallScreen]) when a call is being recorded.
 */
@Composable
fun TopCallRecordingStatusBadge(
    isVisible: Boolean,
    isAnnouncingConsent: Boolean = false,
    elapsedSeconds: Long = 0L,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(220)) + scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(220)
        ),
        exit = fadeOut(animationSpec = tween(180)) + scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(180)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "top_rec_status_transition")
        val pulseProgress by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850),
                repeatMode = RepeatMode.Reverse
            ),
            label = "top_rec_dot_pulse"
        )
        val wavePhase by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 620),
                repeatMode = RepeatMode.Reverse
            ),
            label = "top_rec_wave_phase"
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = Color(0x662B1220),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(
                        width = 0.8.dp,
                        color = ColorOsRed.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(50)
                    )
                    .clickable { onClick() }
                    .semantics {
                        contentDescription = "Call recording active status indicator"
                    }
                    .testTag("top_call_recording_status_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Subtle pulsing red recording dot with dual concentric halo rings
                    Canvas(
                        modifier = Modifier
                            .size(13.dp)
                            .testTag("top_call_recording_pulse_icon")
                    ) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.minDimension / 2f
                        drawCircle(
                            color = ColorOsRed.copy(alpha = 0.24f * pulseProgress),
                            radius = maxRadius * (0.78f + 0.22f * pulseProgress),
                            center = center
                        )
                        drawCircle(
                            color = ColorOsRed.copy(alpha = 0.42f * (1.2f - 0.5f * pulseProgress)),
                            radius = maxRadius * 0.64f,
                            center = center
                        )
                        drawCircle(
                            color = ColorOsRed.copy(alpha = 0.78f + 0.22f * pulseProgress),
                            radius = maxRadius * 0.44f,
                            center = center
                        )
                    }

                    Text(
                        text = "Recording",
                        color = Color(0xFFFFE4E8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )

                    if (!isAnnouncingConsent && elapsedSeconds > 0L) {
                        Text(
                            text = "· ${CallRecordingManager.formatDurationShort(elapsedSeconds)}",
                            color = Color(0xFFE6C8D0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Subtle 3-bar mini animated audio waveform icon
                    Canvas(
                        modifier = Modifier.size(width = 11.dp, height = 10.dp)
                    ) {
                        val barWidth = size.width / 5f
                        val gap = barWidth
                        val heights = floatArrayOf(
                            size.height * (0.38f + 0.45f * wavePhase),
                            size.height * (0.95f - 0.40f * wavePhase),
                            size.height * (0.45f + 0.50f * (1f - wavePhase * 0.5f))
                        )
                        heights.forEachIndexed { index, barHeight ->
                            val h = barHeight.coerceIn(size.height * 0.28f, size.height)
                            val left = index * (barWidth + gap)
                            val top = (size.height - h) / 2f
                            drawRoundRect(
                                color = Color(0xFFFF8A9B).copy(alpha = 0.88f),
                                topLeft = Offset(left, top),
                                size = Size(barWidth, h),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live In-Call Recording Indicator Pill shown on [ActiveCallScreen] while consent is being
 * announced or while genuine recording is active, with privacy & secure internal storage badge,
 * 1-tap Vault access, and 1-tap Stop button.
 */
@Composable
fun LiveCallRecordingBanner(
    recordingState: CallRecordingSessionState,
    onStopRecording: () -> Unit,
    onOpenRecordingsVault: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = recordingState.isAnnouncingConsent || recordingState.isRecordingActive,
        modifier = modifier
    ) {
        val pulseTransition = rememberInfiniteTransition(label = "rec_dot_pulse")
        val dotAlpha by pulseTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rec_dot_alpha"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xE63B1626),
                            Color(0xE626142E)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = ColorOsRed.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("live_call_recording_indicator")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ColorOsRed.copy(alpha = dotAlpha))
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (recordingState.isAnnouncingConsent) {
                                    "Announcing recording consent..."
                                } else {
                                    "REC ${CallRecordingManager.formatDurationShort(recordingState.elapsedSeconds)}"
                                },
                                color = ColorOsWhite,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = ColorOsGreen.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Internal Vault",
                                    color = Color(0xFF98F5B1),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (recordingState.audioSourceLabel.isNotBlank()) {
                                "${recordingState.audioSourceLabel} · files/CallRecordings"
                            } else {
                                "Saving locally to secure internal directory (files/CallRecordings)"
                            },
                            color = Color(0xFFD8CAD8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenRecordingsVault() }
                            .testTag("call_btn_open_recordings_vault")
                    ) {
                        Text(
                            text = "Vault",
                            color = ColorOsWhite,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        color = ColorOsRed,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onStopRecording() }
                            .testTag("call_btn_stop_recording")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop recording",
                                tint = ColorOsWhite,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Stop",
                                color = ColorOsWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * In-Call Local Recordings & Privacy Vault Sheet content accessible directly from the
 * Ongoing Call interface (`ActiveCallScreen`).
 * Displays Android privacy compliance status, the secure internal storage directory path
 * (`files/CallRecordings`), participant consent controls, and saved local call recordings.
 */
@Composable
fun InCallLocalRecordingsVaultContent(
    recordingState: CallRecordingSessionState,
    onToggleAnnounceConsent: (Boolean) -> Unit,
    onToggleSilentInstantStart: (Boolean) -> Unit,
    onTogglePlayRecording: (SavedCallRecording) -> Unit,
    onStopPlayback: () -> Unit,
    onShareRecording: (SavedCallRecording) -> Unit,
    onDeleteRecording: (SavedCallRecording) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("in_call_recordings_vault_sheet"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Local Call Recording Vault",
                    color = ColorOsWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Secure Internal Directory · files/CallRecordings",
                    color = Color(0xFF98F5B1),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("in_call_vault_internal_dir_label")
                )
            }
            TextButton(onClick = onClose) {
                Text("Close", color = Color(0xFFD0CADC))
            }
        }

        // Privacy & Security Compliance Card
        Surface(
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("in_call_recording_privacy_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Android Privacy & Internal Storage Protection",
                    color = ColorOsWhite,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• Files are saved exclusively in this app's private internal directory (files/CallRecordings) with owner-only file permissions.\n• Excluded from cloud auto-backup and external media scanning (.nomedia).\n• Recording requires explicit user action, RECORD_AUDIO permission, and stops automatically when the call ends.",
                    color = Color(0xFFD8D0E8),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Play audible consent announcement",
                            color = ColorOsWhite,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Announces \"This call is now being recorded\" before capture",
                            color = Color(0xFFC2BBD1),
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = recordingState.announceConsentEnabled,
                        onCheckedChange = onToggleAnnounceConsent,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorOsWhite,
                            checkedTrackColor = ColorOsGreen
                        ),
                        modifier = Modifier.testTag("in_call_vault_consent_toggle")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Require pre-recording confirmation dialog",
                            color = ColorOsWhite,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Show privacy notice dialog before starting recording",
                            color = Color(0xFFC2BBD1),
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = !recordingState.silentInstantStartEnabled,
                        onCheckedChange = { requireDialog ->
                            onToggleSilentInstantStart(!requireDialog)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorOsWhite,
                            checkedTrackColor = ColorOsGreen
                        ),
                        modifier = Modifier.testTag("in_call_vault_confirm_dialog_toggle")
                    )
                }
            }
        }

        // Saved Recordings in Secure Internal Vault
        Text(
            text = "Saved Internal Recordings (${recordingState.savedRecordings.size})",
            color = ColorOsWhite,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (recordingState.savedRecordings.isEmpty()) {
            Surface(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No recordings saved in files/CallRecordings yet. Tap Record during an active call to capture audio locally.",
                    color = Color(0xFFC2BBD1),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recordingState.savedRecordings.take(5).forEach { rec ->
                    val isPlaying = recordingState.playingRecordingId == rec.id && recordingState.isPlaybackPlaying
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("in_call_vault_recording_${rec.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.title,
                                    color = ColorOsWhite,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${CallRecordingManager.formatDurationShort(rec.durationSeconds)} · ${CallRecordingManager.formatFileSize(rec.fileSizeBytes)} · Internal Vault",
                                    color = Color(0xFF98F5B1),
                                    fontSize = 11.5.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { onTogglePlayRecording(rec) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = ColorOsGreen
                                    )
                                }
                                IconButton(
                                    onClick = { onShareRecording(rec) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Share",
                                        tint = ColorOsWhite
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteRecording(rec) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = ColorOsRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}


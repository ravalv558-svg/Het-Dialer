package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppThemeMode
import com.example.model.BlockedNumberEntry
import com.example.model.DeletedContactEntry
import com.example.model.DuplicateContactGroup
import com.example.model.RingtoneOption
import com.example.model.SimAccountInfo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.example.data.CallScreenWallpaperManager
import com.example.telecom.AiAssistantStatus
import com.example.telecom.AiCallAssistantManager
import com.example.telecom.CallRecordingManager
import com.example.ui.components.CallScreenBackgroundSettingsScreen
import com.example.ui.components.LiveAiCallAssistantPanel
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.ColorOsWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OPPO ColorOS 16 Settings Screen (matches IMG-20261107-WA0021):
 * - Clean rounded cards on soft grey/elevated background
 * - Card 1: Speed dial, Answer/End calls
 * - Card 2: Call recording, Block & filter
 * - Card 3: Operator-related settings, More settings
 * - Card 4: Edge lighting for incoming calls (toggle), Lighting settings
 * - Card 5: Theme (Light / Dark / Follow system), Phone ringtone (SIM 1), Vibrate when ringing, Notification preferences
 * - Card 6: Contacts management (Import/Export .vcf, Duplicate contacts merge, Google Account backup & manual sync, Deleted contacts Recycle Bin)
 * - Card 7: Android permissions & Default Phone role, App information, and "You might be looking for: Mobile network"
 */
@Composable
fun SettingsScreen(
    simAccounts: List<SimAccountInfo>,
    themeMode: AppThemeMode,
    ringtones: List<RingtoneOption>,
    selectedRingtoneTitle: String,
    vibrationEnabled: Boolean,
    blockedCount: Int,
    recycleBinCount: Int,
    duplicateGroups: List<DuplicateContactGroup>,
    lastBackupTimestamp: Long,
    isDefaultDialer: Boolean,
    hasAllCorePermissions: Boolean,
    onBack: () -> Unit,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectRingtone: (RingtoneOption) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onOpenBlockedNumbers: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onImportContactsVcf: () -> Unit,
    onExportContactsVcf: () -> Unit,
    onMergeDuplicates: () -> Unit,
    onManualSyncContacts: () -> Unit,
    onRequestPermissionsAndRole: () -> Unit,
    onOpenPersonalizationStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("color_dialer_prefs_v1", Context.MODE_PRIVATE) }

    var edgeLightingEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_edge_lighting", false))
    }
    var powerButtonEndsCall by remember {
        mutableStateOf(prefs.getBoolean("pref_power_ends_call", false))
    }
    var vibrateWhenAnswered by remember {
        mutableStateOf(prefs.getBoolean("pref_vibrate_answered", true))
    }
    var lightingColorLabel by remember {
        mutableStateOf(prefs.getString("pref_lighting_color", "OPPO Emerald Green") ?: "OPPO Emerald Green")
    }

    val speedDialMap = remember {
        mutableStateMapOf<Int, String>().apply {
            for (digit in 2..9) {
                val saved = prefs.getString("pref_speed_dial_$digit", "").orEmpty()
                if (saved.isNotBlank()) put(digit, saved)
            }
        }
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showRingtoneDialog by remember { mutableStateOf(false) }
    var showBackupConfirmDialog by remember { mutableStateOf(false) }
    var showSpeedDialDialog by remember { mutableStateOf(false) }
    var showAnswerEndDialog by remember { mutableStateOf(false) }
    var showCallRecordingScreen by rememberSaveable { mutableStateOf(false) }
    var showCallWallpaperScreen by rememberSaveable { mutableStateOf(false) }
    var showAiAssistantSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var aiInstructionSetting by rememberSaveable {
        mutableStateOf(AiCallAssistantManager.loadSavedInstruction(context))
    }
    var showOperatorSettingsDialog by remember { mutableStateOf(false) }
    var showMoreSettingsDialog by remember { mutableStateOf(false) }
    var showLightingSettingsDialog by remember { mutableStateOf(false) }
    var activePreviewRingtone by remember { mutableStateOf<Ringtone?>(null) }

    LaunchedEffect(context) {
        CallScreenWallpaperManager.ensureInitialized(context)
    }
    val appliedCallWallpaper by CallScreenWallpaperManager.appliedConfig.collectAsState()

    BackHandler(enabled = !showAiAssistantSettingsDialog) { onBack() }

    if (showCallRecordingScreen) {
        CallRecordingSettingsScreen(
            onBack = { showCallRecordingScreen = false },
            modifier = modifier
        )
        return
    }

    if (showCallWallpaperScreen) {
        CallScreenBackgroundSettingsScreen(
            onBack = { showCallWallpaperScreen = false },
            modifier = modifier
        )
        return
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activePreviewRingtone?.stop()
            } catch (_: Exception) {
            }
        }
    }

    val pageBg = MaterialTheme.colorScheme.surfaceVariant
    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)

    val sim1Label = simAccounts.firstOrNull()?.let { "SIM 1 · ${it.carrierName}" } ?: "SIM 1 (Primary)"
    val lastSyncFormatted = remember(lastBackupTimestamp) {
        if (lastBackupTimestamp <= 0L) {
            "Manual only · Never synced"
        } else {
            val fmt = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            "Last manual sync: ${fmt.format(Date(lastBackupTimestamp))}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Top App Bar (matches IMG-20261107-WA0021)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Settings",
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
            item(key = "card_personalization_studio") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenPersonalizationStudio)
                        .testTag("settings_personalization_studio_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0A84FF).copy(alpha = 0.22f),
                                        Color(0xFF00C2FF).copy(alpha = 0.14f)
                                    )
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0A84FF).copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HET Personalization Studio",
                                color = textPrimary,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Make Every Call Yours · Liquid Glass themes, fonts & effects",
                                color = textSecondary,
                                fontSize = 12.5.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFF0A84FF)
                        )
                    }
                }
            }

            // Card 1: Speed dial & Answer/End calls (matches IMG-20261107-WA0021)
            item(key = "card_speed_answer") {
                ColorOsSettingsCard(cardBg = cardBg) {
                    ColorOsSettingsSimpleRow(
                        title = "Speed dial",
                        subtitle = if (speedDialMap.isEmpty()) null else "${speedDialMap.size} key(s) configured",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showSpeedDialDialog = true }
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Answer/End calls",
                        subtitle = null,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showAnswerEndDialog = true }
                    )
                }
            }

            // Card 2: Call recording, AI Call Assistant (Gujarati), & Block & filter
            item(key = "card_record_block") {
                ColorOsSettingsCard(cardBg = cardBg) {
                    ColorOsSettingsSimpleRow(
                        title = "Call recording",
                        subtitle = "Settings, device capability & saved recordings",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showCallRecordingScreen = true },
                        testTag = "settings_call_recording_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "AI Call Assistant (ગુજરાતી)",
                        subtitle = aiInstructionSetting,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showAiAssistantSettingsDialog = true },
                        testTag = "settings_ai_assistant_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Block & filter",
                        subtitle = if (blockedCount > 0) "$blockedCount blocked number(s)" else null,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = onOpenBlockedNumbers,
                        testTag = "settings_blocked_numbers_row"
                    )
                }
            }

            // Card 3: Operator-related settings & More settings (matches IMG-20261107-WA0021)
            item(key = "card_operator_more") {
                ColorOsSettingsCard(cardBg = cardBg) {
                    ColorOsSettingsSimpleRow(
                        title = "Operator-related settings",
                        subtitle = sim1Label,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showOperatorSettingsDialog = true }
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "More settings",
                        subtitle = null,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showMoreSettingsDialog = true }
                    )
                }
            }

            // Card 4: Edge lighting for incoming calls & Lighting settings (matches IMG-20261107-WA0021)
            item(key = "card_edge_lighting") {
                ColorOsSettingsCard(cardBg = cardBg) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !edgeLightingEnabled
                                edgeLightingEnabled = next
                                prefs.edit().putBoolean("pref_edge_lighting", next).apply()
                            }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Edge lighting for incoming calls",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "When the screen is facing up, the edges of the screen will light up for incoming calls.",
                                color = textSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = edgeLightingEnabled,
                            onCheckedChange = { next ->
                                edgeLightingEnabled = next
                                prefs.edit().putBoolean("pref_edge_lighting", next).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorOsWhite,
                                checkedTrackColor = ColorOsGreen
                            )
                        )
                    }
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Lighting settings",
                        subtitle = lightingColorLabel,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showLightingSettingsDialog = true }
                    )
                }
            }

            // Card 5: Theme, Call Screen Background, Ringtone, Vibration, Notifications
            item(key = "card_theme_sound") {
                ColorOsSettingsCard(cardBg = cardBg) {
                    ColorOsSettingsSimpleRow(
                        title = "Theme",
                        subtitle = themeMode.label,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showThemeDialog = true },
                        testTag = "settings_theme_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Call Screen Background",
                        subtitle = appliedCallWallpaper.summarySubtitle(),
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showCallWallpaperScreen = true },
                        testTag = "settings_call_screen_background_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Phone ringtone (SIM 1)",
                        subtitle = "$selectedRingtoneTitle · Tap to select & preview",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showRingtoneDialog = true },
                        testTag = "settings_ringtone_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vibrate when ringing",
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = "Haptic vibration during incoming SIM 1 calls",
                                color = textSecondary,
                                fontSize = 12.5.sp
                            )
                        }
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = onToggleVibration,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorOsWhite,
                                checkedTrackColor = ColorOsGreen
                            ),
                            modifier = Modifier.testTag("settings_vibration_switch")
                        )
                    }
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Notification preferences",
                        subtitle = "Manage incoming call & missed call alerts",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        testTag = "settings_notifications_row"
                    )
                }
            }

            // Card 6: Contacts Management & Backup
            item(key = "card_contacts_mgmt") {
                ColorOsSettingsCard(cardBg = cardBg) {
                    ColorOsSettingsSimpleRow(
                        title = "Import contacts (.vcf)",
                        subtitle = "Import vCard contacts from device storage",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = onImportContactsVcf,
                        testTag = "settings_import_contacts_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Export contacts (.vcf)",
                        subtitle = "Back up all contacts to a local .vcf file",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = onExportContactsVcf,
                        testTag = "settings_export_contacts_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Duplicate contact identification",
                        subtitle = if (duplicateGroups.isEmpty()) {
                            "No duplicate contacts detected"
                        } else {
                            "${duplicateGroups.size} duplicate group(s) found · Tap to merge"
                        },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = onMergeDuplicates,
                        testTag = "settings_merge_duplicates_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Google Account backup & manual sync",
                        subtitle = lastSyncFormatted,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { showBackupConfirmDialog = true },
                        testTag = "settings_manual_sync_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "Deleted contacts (Recycle Bin)",
                        subtitle = "$recycleBinCount contact${if (recycleBinCount == 1) "" else "s"} in Recycle Bin",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = onOpenRecycleBin,
                        testTag = "settings_recycle_bin_row"
                    )
                }
            }

            // Card 7: Permissions & App Info
            item(key = "card_permissions_info") {
                ColorOsSettingsCard(cardBg = cardBg) {
                    ColorOsSettingsSimpleRow(
                        title = "Android permissions & Default Phone role",
                        subtitle = if (isDefaultDialer && hasAllCorePermissions) {
                            "Default Phone App active · All core permissions granted"
                        } else {
                            "Tap to grant missing permissions or Default Phone role"
                        },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = onRequestPermissionsAndRole,
                        testTag = "settings_permissions_row"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    ColorOsSettingsSimpleRow(
                        title = "App information",
                        subtitle = "Phone · OPPO ColorOS 16 Design · Motorola G57 Power (Android 17)",
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = {},
                        showChevron = false
                    )
                }
            }

            // Bottom "You might be looking for: Mobile network" link (matches IMG-20261107-WA0021)
            item(key = "bottom_mobile_network_link") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "You might be looking for",
                        color = textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mobile network",
                        color = ColorOsGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    // Speed Dial Configuration Dialog
    if (showSpeedDialDialog) {
        var selectedDigit by remember { mutableStateOf(2) }
        var assignedNumber by remember(selectedDigit) {
            mutableStateOf(speedDialMap[selectedDigit].orEmpty())
        }
        AlertDialog(
            onDismissRequest = { showSpeedDialDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Speed dial (Keys 2–9)", color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Assign a quick-dial phone number to numeric keys 2 through 9.",
                        color = textSecondary,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        (2..9).forEach { d ->
                            val isSel = d == selectedDigit
                            Surface(
                                color = if (isSel) ColorOsGreen else pageBg,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable {
                                        selectedDigit = d
                                        assignedNumber = speedDialMap[d].orEmpty()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = d.toString(),
                                        color = if (isSel) ColorOsWhite else textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = assignedNumber,
                        onValueChange = { assignedNumber = it },
                        label = { Text("Phone number for key $selectedDigit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = assignedNumber.trim()
                        if (trimmed.isEmpty()) {
                            speedDialMap.remove(selectedDigit)
                            prefs.edit().remove("pref_speed_dial_$selectedDigit").apply()
                        } else {
                            speedDialMap[selectedDigit] = trimmed
                            prefs.edit().putString("pref_speed_dial_$selectedDigit", trimmed).apply()
                        }
                        showSpeedDialDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen)
                ) {
                    Text("Save", color = ColorOsWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialDialog = false }) {
                    Text("Close", color = textSecondary)
                }
            }
        )
    }

    // Answer/End Calls Settings Dialog
    if (showAnswerEndDialog) {
        AlertDialog(
            onDismissRequest = { showAnswerEndDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Answer/End calls", color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Press Power button to end calls", color = textPrimary, fontSize = 15.sp)
                            Text("Quickly end active calls when screen is on", color = textSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = powerButtonEndsCall,
                            onCheckedChange = {
                                powerButtonEndsCall = it
                                prefs.edit().putBoolean("pref_power_ends_call", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorOsWhite,
                                checkedTrackColor = ColorOsGreen
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibrate when call is answered or ended", color = textPrimary, fontSize = 15.sp)
                            Text("Subtle haptic feedback when connected", color = textSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = vibrateWhenAnswered,
                            onCheckedChange = {
                                vibrateWhenAnswered = it
                                prefs.edit().putBoolean("pref_vibrate_answered", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorOsWhite,
                                checkedTrackColor = ColorOsGreen
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnswerEndDialog = false }) {
                    Text("Done", color = ColorOsGreen)
                }
            }
        )
    }

    // Operator-related Settings Dialog
    if (showOperatorSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showOperatorSettingsDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Operator-related settings", color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Primary SIM: $sim1Label",
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "VoLTE HD voice calling, Call forwarding, Call waiting, and Caller ID are managed by your mobile carrier.",
                        color = textSecondary,
                        fontSize = 13.5.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOperatorSettingsDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }
                ) {
                    Text("Carrier settings", color = ColorOsGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOperatorSettingsDialog = false }) {
                    Text("Close", color = textSecondary)
                }
            }
        )
    }

    // More Settings Dialog
    if (showMoreSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showMoreSettingsDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("More settings", color = textPrimary) },
            text = {
                Text(
                    text = "Includes DTMF dialpad tones, proximity sensor screen-off during active calls, and live caller number formatting.",
                    color = textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showMoreSettingsDialog = false }) {
                    Text("Done", color = ColorOsGreen)
                }
            }
        )
    }

    // Gujarati AI Call Assistant Default Instructions & Live Pipeline Diagnostic Dialog
    if (showAiAssistantSettingsDialog) {
        var draft by rememberSaveable(aiInstructionSetting) { mutableStateOf(aiInstructionSetting) }
        val aiDiagnosticState by AiCallAssistantManager.uiState.collectAsState()
        val dialogScrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = {
                // Intentionally no-op: keep the test panel open across all SpeechRecognizer / TTS
                // window focus events, outside touches, and state transitions.
                // The dialog closes ONLY when the user explicitly taps Cancel or Save below.
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            ),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
                .testTag("ai_assistant_settings_dialog"),
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("AI Call Assistant (ગુજરાતી)", color = textPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "જ્યારે ચાલુ કૉલ દરમિયાન AI Call Assistant સક્રિય કરવામાં આવે ત્યારે તે ગુજરાતીમાં પોતાની ઓળખ આપશે, સામેની વ્યક્તિની સંમતિ માંગશે અને નીચે મુજબ તમારી સૂચના જણાવશે:",
                        color = textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Prominent Continuous Gujarati Conversation Button (Normal Mode Multi-Turn Loop)
                    Surface(
                        color = ColorOsGreen,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                AiCallAssistantManager.startContinuousConversation(context, draft)
                            }
                            .testTag("settings_start_continuous_gujarati_conversation_btn")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (aiDiagnosticState.continuousConversation.isRunning) {
                                    "સતત ગુજરાતી વાતચીત ચાલુ છે (${aiDiagnosticState.continuousConversation.stage.labelEnglish}) — ફરી શરૂ કરો"
                                } else {
                                    "સતત ગુજરાતી AI વાતચીત શરૂ કરો (Start Continuous Gujarati Conversation)"
                                },
                                color = ColorOsWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Normal Mode (gu-IN): Speak Gujarati → AI Responds via TTS → Mic Auto-Restarts until Pause/Stop",
                                color = ColorOsWhite.copy(alpha = 0.88f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFF0288D1).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    AiCallAssistantManager.startStandaloneNormalModeGujaratiMicTest(context)
                                }
                                .testTag("settings_normal_mode_gujarati_mic_test_btn")
                        ) {
                            Text(
                                text = if (aiDiagnosticState.normalModeMicTest.isRunning) {
                                    "1. નોર્મલ-મોડ માઇક ટેસ્ટ (ચાલુ...)"
                                } else {
                                    "1. નોર્મલ-મોડ ગુજરાતી માઇક ટેસ્ટ"
                                },
                                color = Color(0xFF0277BD),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }

                        Surface(
                            color = ColorOsGreen.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    AiCallAssistantManager.startSession(context, draft)
                                }
                                .testTag("settings_test_ai_pipeline_btn")
                        ) {
                            Text(
                                text = if (aiDiagnosticState.ttsAndMicTest.isRunning) {
                                    "2. TTS + માઇક ટેસ્ટ (ચાલુ...)"
                                } else {
                                    "2. TTS + માઇક ટેસ્ટ"
                                },
                                color = ColorOsGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (aiDiagnosticState.isActiveSession) {
                        Surface(
                            color = ColorOsRed.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (aiDiagnosticState.normalModeMicTest.isRunning) {
                                        AiCallAssistantManager.stopStandaloneNormalModeGujaratiMicTest()
                                    } else {
                                        AiCallAssistantManager.stopSession()
                                    }
                                }
                                .testTag("settings_stop_active_test_btn")
                        ) {
                            Text(
                                text = "ચાલુ ટેસ્ટ / વાતચીત રોકો (Stop Active Session — Keep Result Open)",
                                color = ColorOsRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Always keep the test & diagnostic panel visible inside the Settings dialog
                    // so final Gujarati transcripts, status stages, and error explanations never disappear.
                    LiveAiCallAssistantPanel(
                        aiState = aiDiagnosticState,
                        onPauseOrResume = {
                            if (aiDiagnosticState.status == AiAssistantStatus.PAUSED ||
                                aiDiagnosticState.continuousConversation.stage == com.example.telecom.ContinuousConversationStage.PAUSED
                            ) {
                                AiCallAssistantManager.resumeSession()
                            } else {
                                AiCallAssistantManager.pauseSession()
                            }
                        },
                        onRetryMic = {
                            if (aiDiagnosticState.normalModeMicTest.stage != com.example.telecom.NormalModeMicTestStage.NOT_RUN &&
                                !aiDiagnosticState.ttsAndMicTest.isRunning &&
                                !aiDiagnosticState.continuousConversation.isRunning
                            ) {
                                AiCallAssistantManager.startStandaloneNormalModeGujaratiMicTest(context)
                            } else {
                                AiCallAssistantManager.retryListening()
                            }
                        },
                        onStopAi = {
                            if (aiDiagnosticState.normalModeMicTest.isRunning) {
                                AiCallAssistantManager.stopStandaloneNormalModeGujaratiMicTest()
                            } else {
                                AiCallAssistantManager.stopSession()
                            }
                        },
                        onSimulateCallerSpeech = { phrase ->
                            AiCallAssistantManager.handleRecognizedCallerSpeech(phrase)
                        },
                        onSaveTranscriptToNotes = {}
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (aiDiagnosticState.continuousConversation.isRunning) {
                        TextButton(
                            onClick = {
                                AiCallAssistantManager.updateUserInstruction(context, draft)
                                aiInstructionSetting = AiCallAssistantManager.loadSavedInstruction(context)
                                showAiAssistantSettingsDialog = false
                            },
                            modifier = Modifier.testTag("settings_ai_dialog_minimise_btn")
                        ) {
                            Text("Minimise (ચાલુ રાખો)", color = Color(0xFF0288D1), fontWeight = FontWeight.Medium)
                        }
                    }
                    TextButton(
                        onClick = {
                            if (aiDiagnosticState.isActiveSession) {
                                AiCallAssistantManager.stopSession()
                            }
                            AiCallAssistantManager.updateUserInstruction(context, draft)
                            aiInstructionSetting = AiCallAssistantManager.loadSavedInstruction(context)
                            showAiAssistantSettingsDialog = false
                        },
                        modifier = Modifier.testTag("settings_ai_dialog_save_btn")
                    ) {
                        Text("Save", color = ColorOsGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (aiDiagnosticState.isActiveSession) {
                            AiCallAssistantManager.stopSession()
                        }
                        showAiAssistantSettingsDialog = false
                    },
                    modifier = Modifier.testTag("settings_ai_dialog_cancel_btn")
                ) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    // Lighting Settings Dialog
    if (showLightingSettingsDialog) {
        val colors = listOf("OPPO Emerald Green", "Ocean Blue", "Sunset Amber", "Royal Purple")
        AlertDialog(
            onDismissRequest = { showLightingSettingsDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Lighting settings", color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    colors.forEach { option ->
                        Surface(
                            color = if (lightingColorLabel == option) ColorOsGreen.copy(alpha = 0.14f) else pageBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    lightingColorLabel = option
                                    prefs.edit().putString("pref_lighting_color", option).apply()
                                    showLightingSettingsDialog = false
                                }
                        ) {
                            Text(
                                text = if (lightingColorLabel == option) "✓ $option" else option,
                                color = textPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLightingSettingsDialog = false }) {
                    Text("Close", color = ColorOsGreen)
                }
            }
        )
    }

    // Theme Picker Dialog (Light Mode / Dark Mode / Follow System)
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "Choose theme",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    AppThemeMode.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTheme(option)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option == themeMode,
                                onClick = {
                                    onSelectTheme(option)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ColorOsGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.label,
                                color = textPrimary,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = ColorOsGreen)
                }
            }
        )
    }

    // Ringtone Selection + Live Preview Dialog
    if (showRingtoneDialog) {
        AlertDialog(
            onDismissRequest = {
                try {
                    activePreviewRingtone?.stop()
                } catch (_: Exception) {
                }
                showRingtoneDialog = false
            },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "Select & preview ringtone",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(ringtones) { item ->
                        val isSelected = item.title == selectedRingtoneTitle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectRingtone(item)
                                    try {
                                        activePreviewRingtone?.stop()
                                        val ringtone = RingtoneManager.getRingtone(context, Uri.parse(item.uriString))
                                        activePreviewRingtone = ringtone
                                        ringtone?.play()
                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectRingtone(item)
                                    try {
                                        activePreviewRingtone?.stop()
                                        val ringtone = RingtoneManager.getRingtone(context, Uri.parse(item.uriString))
                                        activePreviewRingtone = ringtone
                                        ringtone?.play()
                                    } catch (_: Exception) {
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ColorOsGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.title,
                                color = textPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            activePreviewRingtone?.stop()
                        } catch (_: Exception) {
                        }
                        showRingtoneDialog = false
                    }
                ) {
                    Text("Done", color = ColorOsGreen)
                }
            }
        )
    }

    // Google Account Contacts Backup & Manual Sync Confirmation Dialog
    if (showBackupConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackupConfirmDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "Manual Contacts Sync & Backup",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Contacts sync is strictly manual and never runs automatically in the background. Would you like to sync your contacts with your device's configured Google Account now or export a backup file?",
                    color = textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackupConfirmDialog = false
                        onManualSyncContacts()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen)
                ) {
                    Text("Sync now", color = ColorOsWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupConfirmDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }
}

@Composable
private fun ColorOsSettingsCard(
    cardBg: Color,
    content: @Composable () -> Unit
) {
    Surface(
        color = cardBg,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun ColorOsSettingsSimpleRow(
    title: String,
    subtitle: String?,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    testTag: String? = null
) {
    val rowMod = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(horizontal = 18.dp, vertical = 16.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = textSecondary,
                    fontSize = 12.5.sp
                )
            }
        }
        if (showChevron) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = textSecondary.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * OPPO ColorOS 16 Blocked Numbers Screen (Block & filter):
 * - Separate blocked numbers list
 * - Block number
 * - Unblock number
 */
@Composable
fun BlockedNumbersScreen(
    blockedNumbers: List<BlockedNumberEntry>,
    onBack: () -> Unit,
    onBlockNumber: (String, String) -> Unit,
    onUnblockNumber: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var showAddBlockDialog by remember { mutableStateOf(false) }
    var numberInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }

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
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("blocked_numbers_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Blocked numbers",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(
                onClick = {
                    numberInput = ""
                    labelInput = ""
                    showAddBlockDialog = true
                },
                modifier = Modifier.testTag("add_blocked_number_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add blocked number",
                    tint = ColorOsGreen
                )
            }
        }

        Text(
            text = "Calls and texts from numbers on this blocklist will be blocked automatically.",
            color = textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        if (blockedNumbers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No blocked numbers",
                        color = textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap + in the top right to block a phone number.",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                item {
                    Surface(
                        color = cardBg,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            blockedNumbers.forEachIndexed { idx, entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.number,
                                            color = textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (entry.label.isNotBlank()) {
                                            Text(
                                                text = entry.label,
                                                color = textSecondary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { onUnblockNumber(entry.number) },
                                        modifier = Modifier.testTag("unblock_btn_${entry.number}")
                                    ) {
                                        Text(
                                            text = "Unblock",
                                            color = ColorOsRed,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                if (idx < blockedNumbers.lastIndex) {
                                    HorizontalDivider(
                                        color = dividerColor,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(horizontal = 18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddBlockDialog) {
        AlertDialog(
            onDismissRequest = { showAddBlockDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "Block phone number",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = numberInput,
                        onValueChange = { numberInput = it },
                        label = { Text("Phone number") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_block_phone_number")
                    )
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Note / label (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (numberInput.isNotBlank()) {
                            onBlockNumber(numberInput.trim(), labelInput.trim())
                            showAddBlockDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOsRed)
                ) {
                    Text("Block", color = ColorOsWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBlockDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }
}

/**
 * OPPO ColorOS 16 Deleted Contacts / Recycle Bin Screen:
 * - Separate Recycle Bin for deleted contacts.
 * - Deleted contacts remain until manually removed.
 * - Restore contacts or Permanently delete contacts.
 */
@Composable
fun RecycleBinScreen(
    deletedContacts: List<DeletedContactEntry>,
    onBack: () -> Unit,
    onRestoreContact: (String) -> Unit,
    onPermanentlyDeleteContact: (String) -> Unit,
    onEmptyRecycleBin: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var showEmptyConfirm by remember { mutableStateOf(false) }

    val pageBg = MaterialTheme.colorScheme.surfaceVariant
    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("recycle_bin_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Deleted contacts",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (deletedContacts.isNotEmpty()) {
                TextButton(
                    onClick = { showEmptyConfirm = true },
                    modifier = Modifier.testTag("empty_recycle_bin_button")
                ) {
                    Text(
                        text = "Empty all",
                        color = ColorOsRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Text(
            text = "Deleted contacts remain in the Recycle Bin until you manually restore or permanently delete them.",
            color = textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        if (deletedContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.RestoreFromTrash,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Recycle Bin is empty",
                        color = textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Contacts you delete will appear here so you can restore them anytime.",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = deletedContacts, key = { it.deletedId }) { item ->
                    Surface(
                        color = cardBg,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ColorOsGreen.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
                                    color = ColorOsGreen,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.displayName,
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (item.primaryNumber.isNotBlank()) {
                                    Text(
                                        text = item.primaryNumber,
                                        color = textSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            TextButton(
                                onClick = { onRestoreContact(item.deletedId) },
                                modifier = Modifier.testTag("restore_contact_${item.deletedId}")
                            ) {
                                Text("Restore", color = ColorOsGreen, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(
                                onClick = { onPermanentlyDeleteContact(item.deletedId) },
                                modifier = Modifier.testTag("perm_delete_contact_${item.deletedId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete permanently",
                                    tint = ColorOsRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "Empty Recycle Bin?",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "All ${deletedContacts.size} deleted contacts will be permanently removed and cannot be recovered.",
                    color = textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyConfirm = false
                        onEmptyRecycleBin()
                    }
                ) {
                    Text("Delete permanently", color = ColorOsRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }
}

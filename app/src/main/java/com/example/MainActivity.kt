package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PersonalizationManager
import com.example.model.ContactFormData
import com.example.telecom.AiCallAssistantManager
import com.example.telecom.CallRecordingManager
import com.example.telecom.CallSmartIslandManager
import com.example.telecom.CallStateManager
import com.example.telecom.ContinuousConversationStage
import com.example.telecom.InCallActivity
import com.example.telecom.SmartIslandExpansionMode
import com.example.ui.components.CallSmartIslandHost
import com.example.ui.components.ColorOsBottomNavigationBar
import com.example.ui.components.ColorOsOpening3DSplash
import com.example.ui.components.SmartIslandOverlayPermissionBanner
import com.example.ui.personalization.PersonalizationStudioHost
import com.example.ui.screens.BlockedNumbersScreen
import com.example.ui.screens.CallsTabScreen
import com.example.ui.screens.ContactDetailScreen
import com.example.ui.screens.ContactsTabScreen
import com.example.ui.screens.IncomingCallBannerCard
import com.example.ui.screens.NewContactScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.formatIncomingBannerSubtitle
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.ColorOsWhite
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DialerSubScreen
import com.example.viewmodel.DialerViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        com.example.telecom.FirebaseAppCheckHelper.configureAppCheck(this, intent)
        com.example.telecom.ColorInCallService.ensureNotificationChannels(applicationContext)
        com.example.data.CallScreenWallpaperManager.ensureInitialized(applicationContext)
        PersonalizationManager.ensureInitialized(applicationContext)
        handleIncomingDialIntent(intent)

        setContent {
            val appliedPersonalization by PersonalizationManager.appliedConfig.collectAsStateWithLifecycle()
            MyApplicationTheme(
                themeMode = appliedPersonalization.themeMode,
                personalization = appliedPersonalization
            ) {
                ColorDialerRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDialIntent(intent)
    }

    private fun handleIncomingDialIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null && data.scheme == "tel") {
                val number = data.schemeSpecificPart.orEmpty()
                if (number.isNotBlank()) {
                    viewModel.setDialInputFromIntent(number)
                }
            }
        }
    }
}

@Composable
fun ColorDialerRoot(
    viewModel: DialerViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeCall by CallStateManager.activeCallUiState.collectAsStateWithLifecycle()
    val recordingState by CallRecordingManager.sessionState.collectAsStateWithLifecycle()
    val smartIslandState by CallSmartIslandManager.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingSaveContactForm by remember { mutableStateOf<ContactFormData?>(null) }

    val aiAssistantState by AiCallAssistantManager.uiState.collectAsStateWithLifecycle()
    val isBannerDismissed by CallStateManager.isBannerDismissedBySwipe.collectAsStateWithLifecycle()

    // Keep CallSmartIslandManager synchronized with live call, recording, and AI Assistant states
    LaunchedEffect(activeCall, recordingState, aiAssistantState, isBannerDismissed) {
        while (true) {
            val call = activeCall
            val elapsedSec = if (call != null && call.connectTimeMillis > 0L) {
                ((System.currentTimeMillis() - call.connectTimeMillis) / 1000L).coerceAtLeast(0L)
            } else {
                0L
            }
            CallSmartIslandManager.onExternalStatesUpdated(
                callState = call,
                recordingState = recordingState,
                aiState = aiAssistantState,
                isBannerDismissedBySwipe = isBannerDismissed,
                elapsedCallSeconds = elapsedSec
            )
            if (call == null && !CallSmartIslandManager.uiState.value.isClosingAnimationActive) {
                break
            }
            delay(500L)
        }
    }

    // Refresh permissions & data whenever activity resumes, and handle Continuous Conversation background/foreground safely
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.refreshAllData()
                    CallSmartIslandManager.refreshOverlayPermissionState(context)
                    AiCallAssistantManager.onAppReturnedToForeground()
                }
                Lifecycle.Event.ON_STOP -> {
                    AiCallAssistantManager.onAppMovedToBackground()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Track screen switches during an active Continuous Conversation session
    LaunchedEffect(uiState.subScreen, uiState.selectedBottomTab) {
        val screenName = when (uiState.subScreen) {
            is DialerSubScreen.ContactDetail -> "ContactDetail"
            is DialerSubScreen.ContactForm -> "ContactForm"
            is DialerSubScreen.PersonalizationStudio -> "PersonalizationStudio"
            DialerSubScreen.Settings -> "Settings"
            DialerSubScreen.BlockedNumbers -> "BlockedNumbers"
            DialerSubScreen.RecycleBin -> "RecycleBin"
            DialerSubScreen.None -> if (uiState.selectedBottomTab == 0) "Dialpad" else "Contacts"
        }
        AiCallAssistantManager.onScreenSwitched(screenName)
    }

    // Show snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    // Core permissions launcher
    val corePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshAllData()
    }

    var hasCheckedStartupCallPermissions by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.isDefaultDialer, uiState.showFirstLaunchRoleDialog) {
        if (!hasCheckedStartupCallPermissions &&
            (uiState.isDefaultDialer || !uiState.showFirstLaunchRoleDialog)
        ) {
            hasCheckedStartupCallPermissions = true
            val requiredPerms = mutableListOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requiredPerms.add(Manifest.permission.READ_PHONE_NUMBERS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requiredPerms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requiredPerms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val missingPerms = requiredPerms.filter { perm ->
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    perm
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missingPerms.isNotEmpty()) {
                corePermissionsLauncher.launch(missingPerms.toTypedArray())
            }
        }
    }

    // Call Log permission launcher
    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.refreshAllData()
        val granted = result[Manifest.permission.READ_CALL_LOG] == true
        if (!granted) {
            viewModel.showMessage("Call Log permission was denied. Grant it to view your call history.")
        }
    }

    // Contacts permission launcher
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.refreshAllData()
        val granted = result[Manifest.permission.READ_CONTACTS] == true
        if (!granted) {
            viewModel.showMessage("Contacts permission was denied. Grant it to load device contacts.")
        }
    }

    // Write Contacts permission launcher for saving/editing contacts
    val writeContactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.refreshAllData()
        val canWrite = result[Manifest.permission.WRITE_CONTACTS] == true
        val pendingForm = pendingSaveContactForm
        pendingSaveContactForm = null
        if (canWrite && pendingForm != null) {
            viewModel.saveContact(pendingForm)
        } else if (!canWrite) {
            viewModel.showMessage("Contacts write permission is required to save or edit contacts.")
        }
    }

    // vCard (.vcf) Export SAF Launcher
    val exportVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri ->
        if (uri != null) {
            viewModel.exportContactsToVcfUri(uri)
        }
    }

    // vCard (.vcf) Import SAF Launcher
    val importVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importContactsFromVcfUri(uri)
        }
    }

    // CALL_PHONE permission launcher for placing real calls
    val callPhonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.syncPermissionsAndRoleStatus()
        val canCall = result[Manifest.permission.CALL_PHONE] == true
        val numberToCall = pendingCallNumber
        pendingCallNumber = null
        if (canCall && !numberToCall.isNullOrBlank()) {
            val selectedSim = uiState.simAccounts.firstOrNull()?.handle
            CallStateManager.placeRealCall(
                context = context,
                rawNumber = numberToCall,
                phoneAccountHandle = selectedSim,
                onNeedPermission = {},
                onError = { err -> viewModel.showMessage(err) }
            )
        } else if (!canCall) {
            viewModel.showMessage("Phone call permission is required to place calls directly.")
        }
    }

    // Default Phone App RoleManager launcher
    val defaultDialerRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.dismissFirstLaunchRoleDialog()
        viewModel.refreshAllData()
        val nowDefault = CallStateManager.isDefaultDialer(context)
        if (nowDefault) {
            viewModel.showMessage("Phone is now set as your Default Phone App.")
        } else {
            viewModel.showMessage("Default Phone App role not granted. Full incoming/active call screen requires Default Phone App status.")
        }
        val permsToRequest = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permsToRequest.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        corePermissionsLauncher.launch(permsToRequest.toTypedArray())
    }

    fun requestDefaultDialerRole() {
        val roleIntent = CallStateManager.createRoleRequestIntent(context)
        if (roleIntent != null) {
            try {
                defaultDialerRoleLauncher.launch(roleIntent)
            } catch (e: Exception) {
                viewModel.showMessage("Unable to open Default Phone App settings: ${e.localizedMessage ?: "error"}")
            }
        } else {
            viewModel.showMessage("Default Phone App role management is not supported on this device.")
        }
    }

    fun initiateCall(rawNumber: String) {
        val selectedSim = uiState.simAccounts.firstOrNull()?.handle
        CallStateManager.placeRealCall(
            context = context,
            rawNumber = rawNumber,
            phoneAccountHandle = selectedSim,
            onNeedPermission = {
                pendingCallNumber = rawNumber
                callPhonePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.ANSWER_PHONE_CALLS
                    )
                )
            },
            onError = { err -> viewModel.showMessage(err) }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Ongoing Active/Dialing Call return strip
            val isBannerDismissed by CallStateManager.isBannerDismissedBySwipe.collectAsStateWithLifecycle()
            val currentCall = activeCall
            if (currentCall != null && !currentCall.isRinging) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorOsGreen)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .clickable {
                            CallSmartIslandManager.openFullScreenCallUi(context)
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("ongoing_call_return_banner"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            currentCall.isRinging -> "Incoming call: ${currentCall.displayName ?: currentCall.number}"
                            currentCall.isOutgoingDialing -> "Calling: ${currentCall.displayName ?: currentCall.number}"
                            else -> "Active call: ${currentCall.displayName ?: currentCall.number}"
                        },
                        color = ColorOsWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (currentCall.isRinging) "Tap to open" else "Tap to return",
                        color = ColorOsWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Compact Floating Continuous Gujarati Conversation Bar across app screens
            val contState = aiAssistantState.continuousConversation
            if (contState.isRunning || contState.stage == ContinuousConversationStage.PAUSED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF172A3A))
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("continuous_conversation_floating_bar"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ગુજરાતી AI વાતચીત • ${contState.stage.labelEnglish} (${contState.stage.labelGujarati})",
                            color = Color(0xFF7CE8A6),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val latestLine = contState.partialGujaratiText
                            .ifBlank { contState.lastAiGujaratiResponse }
                            .ifBlank { contState.lastRecognizedGujaratiText }
                            .ifBlank { contState.statusDetailGujarati }
                        Text(
                            text = latestLine,
                            color = ColorOsWhite,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = if (contState.stage == ContinuousConversationStage.PAUSED) {
                                ColorOsGreen
                            } else {
                                Color.White.copy(alpha = 0.16f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (contState.stage == ContinuousConversationStage.PAUSED) {
                                        AiCallAssistantManager.resumeSession()
                                    } else {
                                        AiCallAssistantManager.pauseSession()
                                    }
                                }
                                .testTag("floating_bar_pause_resume_btn")
                        ) {
                            Text(
                                text = if (contState.stage == ContinuousConversationStage.PAUSED) "Resume" else "Pause",
                                color = ColorOsWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = ColorOsRed,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    AiCallAssistantManager.stopSession(
                                        reasonNote = "વપરાશકર્તા દ્વારા સતત ગુજરાતી વાતચીત બંધ કરવામાં આવી."
                                    )
                                }
                                .testTag("floating_bar_stop_btn")
                        ) {
                            Text(
                                text = "Stop",
                                color = ColorOsWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Main Screen & Sub-Screens Routing
            when (val sub = uiState.subScreen) {
                is DialerSubScreen.ContactDetail -> {
                    val (resolvedContact, isSaved) = remember(
                        sub.contactId,
                        sub.fallbackCallLog,
                        uiState.contacts
                    ) {
                        viewModel.resolveDetailContact(sub.contactId, sub.fallbackCallLog)
                    }

                    if (resolvedContact != null) {
                        val contactLogs = remember(resolvedContact, uiState.callLogs) {
                            viewModel.filterCallLogsForContact(resolvedContact)
                        }
                        val isBlocked = remember(resolvedContact.primaryPhone, uiState.blockedNumbers) {
                            viewModel.isNumberBlocked(resolvedContact.primaryPhone.orEmpty())
                        }
                        ContactDetailScreen(
                            contact = resolvedContact,
                            contactCallLogs = contactLogs,
                            isSavedContact = isSaved,
                            isBlockedNumber = isBlocked,
                            onBack = { viewModel.navigateBackToMain() },
                            onPlaceCall = { number -> initiateCall(number) },
                            onSendSms = { number -> CallStateManager.openSmsComposer(context, number) },
                            onStartVideoCall = { number, onMsg ->
                                CallStateManager.launchVideoCall(
                                    context = context,
                                    rawNumber = number,
                                    onNeedCallPermission = {
                                        pendingCallNumber = number
                                        callPhonePermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.CALL_PHONE,
                                                Manifest.permission.READ_PHONE_STATE
                                            )
                                        )
                                    },
                                    onMessage = onMsg
                                )
                            },
                            onToggleFavorite = {
                                if (uiState.hasWriteContactsPermission) {
                                    viewModel.toggleFavorite(resolvedContact)
                                } else {
                                    writeContactsLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.WRITE_CONTACTS,
                                            Manifest.permission.READ_CONTACTS
                                        )
                                    )
                                }
                            },
                            onEditContact = {
                                if (isSaved) {
                                    viewModel.openEditContactForm(resolvedContact)
                                } else {
                                    viewModel.openNewContactForm(
                                        prefilledNumber = resolvedContact.primaryPhone.orEmpty()
                                    )
                                }
                            },
                            onSetDefaultNumber = { phoneDataId ->
                                viewModel.setDefaultPhoneNumber(resolvedContact.id, phoneDataId)
                            },
                            onDeleteContact = {
                                viewModel.moveContactToRecycleBin(resolvedContact)
                            },
                            onToggleBlockNumber = { phone ->
                                if (viewModel.isNumberBlocked(phone)) {
                                    viewModel.unblockNumber(phone)
                                } else {
                                    viewModel.blockNumber(phone, resolvedContact.displayName)
                                }
                            }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            viewModel.navigateBackToMain()
                        }
                    }
                }

                is DialerSubScreen.ContactForm -> {
                    NewContactScreen(
                        initialForm = sub.initialData,
                        onCancel = { viewModel.navigateBackToMain() },
                        onSave = { form ->
                            if (uiState.hasWriteContactsPermission) {
                                viewModel.saveContact(form)
                            } else {
                                pendingSaveContactForm = form
                                writeContactsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.WRITE_CONTACTS,
                                        Manifest.permission.READ_CONTACTS
                                    )
                                )
                            }
                        }
                    )
                }

                DialerSubScreen.Settings -> {
                    val hasAllPerms = uiState.hasContactsPermission &&
                        uiState.hasWriteContactsPermission &&
                        uiState.hasCallLogPermission &&
                        uiState.hasCallPhonePermission
                    SettingsScreen(
                        simAccounts = uiState.simAccounts,
                        themeMode = uiState.themeMode,
                        ringtones = uiState.ringtones,
                        selectedRingtoneTitle = uiState.selectedRingtoneTitle,
                        vibrationEnabled = uiState.vibrationEnabled,
                        blockedCount = uiState.blockedNumbers.size,
                        recycleBinCount = uiState.recycleBinContacts.size,
                        duplicateGroups = uiState.duplicateGroups,
                        lastBackupTimestamp = uiState.lastBackupTimestamp,
                        isDefaultDialer = uiState.isDefaultDialer,
                        hasAllCorePermissions = hasAllPerms,
                        onBack = { viewModel.navigateBackToMain() },
                        onSelectTheme = { mode -> viewModel.setThemeMode(mode) },
                        onSelectRingtone = { ringtone -> viewModel.selectRingtone(ringtone) },
                        onToggleVibration = { enabled -> viewModel.setVibrationEnabled(enabled) },
                        onOpenBlockedNumbers = { viewModel.openBlockedNumbersScreen() },
                        onOpenRecycleBin = { viewModel.openRecycleBinScreen() },
                        onImportContactsVcf = {
                            importVcfLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "*/*"))
                        },
                        onExportContactsVcf = {
                            exportVcfLauncher.launch("contacts_backup.vcf")
                        },
                        onMergeDuplicates = { viewModel.mergeAllDuplicateGroups() },
                        onManualSyncContacts = { viewModel.triggerManualContactsSync() },
                        onRequestPermissionsAndRole = { requestDefaultDialerRole() },
                        onOpenPersonalizationStudio = { viewModel.openPersonalizationStudio() }
                    )
                }

                is DialerSubScreen.PersonalizationStudio -> {
                    PersonalizationStudioHost(
                        initialCategory = sub.initialCategory,
                        onBackToApp = { viewModel.navigateBackToMain() },
                        onOpenSettings = { viewModel.openSettingsScreen() },
                        onAppliedEverywhere = {
                            val applied = PersonalizationManager.appliedConfig.value
                            viewModel.setThemeMode(applied.themeMode)
                        }
                    )
                }

                DialerSubScreen.BlockedNumbers -> {
                    BlockedNumbersScreen(
                        blockedNumbers = uiState.blockedNumbers,
                        onBack = { viewModel.openSettingsScreen() },
                        onBlockNumber = { number, label -> viewModel.blockNumber(number, label) },
                        onUnblockNumber = { number -> viewModel.unblockNumber(number) }
                    )
                }

                DialerSubScreen.RecycleBin -> {
                    RecycleBinScreen(
                        deletedContacts = uiState.recycleBinContacts,
                        onBack = { viewModel.navigateBackToMain() },
                        onRestoreContact = { recycleId -> viewModel.restoreFromRecycleBin(recycleId) },
                        onPermanentlyDeleteContact = { recycleId ->
                            viewModel.permanentlyDeleteFromRecycleBin(recycleId)
                        },
                        onEmptyRecycleBin = { viewModel.emptyRecycleBin() }
                    )
                }

                DialerSubScreen.None -> {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        bottomBar = {
                            ColorOsBottomNavigationBar(
                                selectedTab = uiState.selectedBottomTab,
                                onSelectTab = { tab -> viewModel.selectBottomTab(tab) }
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (uiState.selectedBottomTab == 0) {
                                CallsTabScreen(
                                    callLogs = uiState.callLogs,
                                    contacts = uiState.contacts,
                                    dialInput = uiState.dialInput,
                                    formattedDialInput = uiState.formattedDialInput,
                                    isDialPadExpanded = uiState.isDialPadExpanded,
                                    hasCallLogPermission = uiState.hasCallLogPermission,
                                    isDefaultDialer = uiState.isDefaultDialer,
                                    onDialDigit = { viewModel.onDialDigit(it) },
                                    onSetDialInput = { viewModel.onPasteDialInput(it) },
                                    onBackspace = { viewModel.onBackspace() },
                                    onClearDialInput = { viewModel.onClearDialInput() },
                                    onToggleDialPad = { viewModel.toggleDialPad(it) },
                                    onPlaceCall = { number -> initiateCall(number) },
                                    onOpenCallDetails = { log -> viewModel.openCallLogDetails(log) },
                                    onOpenContactDetails = { contactId ->
                                        viewModel.openContactDetailById(contactId)
                                    },
                                    onDeleteCallLogItem = { logId ->
                                        viewModel.deleteCallLogEntry(logId)
                                    },
                                    onClearAllCallHistory = {
                                        viewModel.clearAllCallHistory()
                                    },
                                    onCreateContactWithNumber = { num ->
                                        viewModel.openNewContactForm(prefilledNumber = num)
                                    },
                                    onOpenSettings = { viewModel.openSettingsScreen() },
                                    onOpenPersonalizationStudio = { viewModel.openPersonalizationStudio() },
                                    onOpenBlockedNumbers = { viewModel.openBlockedNumbersScreen() },
                                    onOpenManageContacts = { viewModel.selectBottomTab(1) },
                                    onRequestCallLogPermission = {
                                        callLogPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALL_LOG,
                                                Manifest.permission.WRITE_CALL_LOG,
                                                Manifest.permission.READ_PHONE_STATE,
                                                Manifest.permission.READ_CONTACTS
                                            )
                                        )
                                    },
                                    onRequestDefaultDialerRole = { requestDefaultDialerRole() }
                                )
                            } else {
                                ContactsTabScreen(
                                    contacts = uiState.contacts,
                                    recentlyViewedContacts = uiState.recentlyViewedContacts,
                                    duplicateGroups = uiState.duplicateGroups,
                                    recycleBinCount = uiState.recycleBinContacts.size,
                                    sortOrder = uiState.sortOrder,
                                    myProfile = uiState.myProfile,
                                    hasContactsPermission = uiState.hasContactsPermission,
                                    onOpenContact = { contactId ->
                                        viewModel.openContactDetailById(contactId)
                                    },
                                    onAddContactClick = { viewModel.openNewContactForm() },
                                    onChangeSortOrder = { order -> viewModel.changeSortOrder(order) },
                                    onSaveMyProfile = { form -> viewModel.saveMyProfile(form) },
                                    onMergeDuplicateGroup = { group -> viewModel.mergeDuplicateGroup(group) },
                                    onImportContactsVcf = {
                                        importVcfLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "*/*"))
                                    },
                                    onExportContactsVcf = {
                                        exportVcfLauncher.launch("contacts_backup.vcf")
                                    },
                                    onManualSyncContacts = { viewModel.triggerManualContactsSync() },
                                    onOpenRecycleBin = { viewModel.openRecycleBinScreen() },
                                    onOpenSettings = { viewModel.openSettingsScreen() },
                                    onOpenPersonalizationStudio = { viewModel.openPersonalizationStudio() },
                                    onRequestContactsPermission = {
                                        contactsPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CONTACTS,
                                                Manifest.permission.WRITE_CONTACTS
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Smart Notch / Smart Island is temporarily postponed per user request.
        if (false && smartIslandState.isIslandVisible && !smartIslandState.isSystemOverlayShowing) {
            if (smartIslandState.expansionMode == SmartIslandExpansionMode.EXPANDED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            CallSmartIslandManager.collapseIsland()
                        }
                        .testTag("smart_island_outside_tap_scrim")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CallSmartIslandHost(
                    islandState = smartIslandState,
                    onAnswerCall = { CallStateManager.answerRingingCall(context) },
                    onDeclineOrEndCall = { CallStateManager.rejectOrDisconnectCall(context) },
                    onToggleSpeaker = { CallStateManager.toggleSpeakerphone() },
                    onToggleMute = { CallStateManager.toggleMute() },
                    onExpandIsland = { CallSmartIslandManager.expandIsland() },
                    onCollapseIsland = { CallSmartIslandManager.collapseIsland() },
                    onDismissIncomingKeepRinging = { CallStateManager.dismissIncomingBannerKeepRinging() },
                    onOpenFullScreenCall = { CallSmartIslandManager.openFullScreenCallUi(context) },
                    onUpdateLongPressProgress = { progress, active ->
                        CallSmartIslandManager.updateLongPressProgress(progress, active)
                    },
                    onCancelLongPressProgress = {
                        CallSmartIslandManager.cancelLongPressProgress()
                    }
                )

                if (activeCall != null &&
                    !smartIslandState.hasOverlayPermission &&
                    !smartIslandState.overlayPermissionPromptDismissedInSession
                ) {
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        SmartIslandOverlayPermissionBanner(
                            onGrantClick = {
                                try {
                                    val overlayIntent = CallSmartIslandManager.createManageOverlayPermissionIntent(context)
                                    context.startActivity(overlayIntent)
                                } catch (e: Exception) {
                                    viewModel.showMessage("Unable to open Overlay settings: ${e.localizedMessage ?: "error"}")
                                }
                            },
                            onDismissClick = {
                                CallSmartIslandManager.setOverlayPromptDismissedInSession(true)
                            }
                        )
                    }
                }
            }
        }
    }

    // First-Launch Default Phone App Explanation & RoleManager Prompt
    if (uiState.showFirstLaunchRoleDialog && !uiState.isDefaultDialer) {
        val cardBg = MaterialTheme.colorScheme.background
        val textPrimary = MaterialTheme.colorScheme.onBackground
        val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

        AlertDialog(
            onDismissRequest = { viewModel.dismissFirstLaunchRoleDialog() },
            shape = RoundedCornerShape(22.dp),
            containerColor = cardBg,
            title = {
                Text(
                    text = stringResource(R.string.default_dialer_prompt_title),
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.default_dialer_prompt_body),
                        color = textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Current status: Not default phone app",
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { requestDefaultDialerRole() },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen),
                    modifier = Modifier.testTag("dialog_set_default_dialer_btn")
                ) {
                    Text(stringResource(R.string.set_as_default), color = ColorOsWhite)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissFirstLaunchRoleDialog()
                        val perms = mutableListOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS,
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.WRITE_CALL_LOG,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.ANSWER_PHONE_CALLS
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        corePermissionsLauncher.launch(perms.toTypedArray())
                    },
                    modifier = Modifier.testTag("dialog_continue_limited_btn")
                ) {
                    Text(
                        text = stringResource(R.string.continue_limited),
                        color = textSecondary
                    )
                }
            }
        )
    }
}

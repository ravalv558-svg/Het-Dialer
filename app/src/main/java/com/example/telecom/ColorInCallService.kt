package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.example.R
import com.example.model.ActiveCallUiState
import com.example.ui.screens.IncomingCallPresentationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ColorInCallService : InCallService() {

    companion object {
        const val CHANNEL_INCOMING_CALL_ID = "het_phone_incoming_call_high_v4"
        const val CHANNEL_ONGOING_CALL_ID = "het_phone_ongoing_call_v4"
        private const val NOTIFICATION_ID = 1001
        private const val REQ_OPEN_ACTIVE_CALL = 2001
        private const val REQ_ANSWER_CALL = 2002
        private const val REQ_DECLINE_CALL = 2003
        private const val REQ_OPEN_FULL_INCOMING = 2004
        private const val REQ_RESTORE_BANNER = 2005
        private const val REQ_TOGGLE_SPEAKER = 2006
        private const val REQ_TOGGLE_MUTE = 2007

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

                val ringtoneUri = try {
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                } catch (_: Throwable) {
                    null
                }
                val ringtoneAudioAttrs = try {
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                } catch (_: Throwable) {
                    null
                }

                // High-importance channel for incoming calls and full-screen call presentation.
                // Never marked silent on NotificationCompat.Builder so Android SystemUI always permits
                // fullScreenIntent execution and displays a visible incoming-call notification with
                // working Answer and Decline controls if full-screen presentation is restricted.
                val incomingCallChannel = NotificationChannel(
                    CHANNEL_INCOMING_CALL_ID,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Displays incoming calls, full-screen call alerts, and Answer/Decline controls"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                    if (ringtoneUri != null && ringtoneAudioAttrs != null) {
                        setSound(ringtoneUri, ringtoneAudioAttrs)
                    }
                    enableVibration(true)
                    setShowBadge(true)
                }

                // Ongoing active call status channel with working Speaker, Mute, and End Call actions.
                val ongoingCallChannel = NotificationChannel(
                    CHANNEL_ONGOING_CALL_ID,
                    "Ongoing Calls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows ongoing call status and call controls"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }

                nm.createNotificationChannel(incomingCallChannel)
                nm.createNotificationChannel(ongoingCallChannel)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateCollectorJob: Job? = null
    private var smartIslandTickerJob: Job? = null
    private var followUpLaunchJob: Job? = null
    private var hasInCallWindowBeenShownForCurrentCall = false
    private var isHandlingOnCallAdded = false
    private var isLaunchingInCallWindow = false
    private var isServiceInForeground = false
    private var lastPostedNotificationSignature: String? = null
    private var lastAutoPresentedCallId: String? = null
    private var smartIslandOverlayController: SmartIslandOverlayWindowController? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            CallStateManager.onExternalAudioDevicesChanged(applicationContext)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            CallStateManager.onExternalAudioDevicesChanged(applicationContext)
        }
    }

    override fun onCreate() {
        super.onCreate()
        smartIslandOverlayController = SmartIslandOverlayWindowController(applicationContext)
        CallSmartIslandManager.refreshOverlayPermissionState(applicationContext)
        ensureNotificationChannels(this)
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
        }
        startObservingCallState()
        startSmartIslandStateSyncLoop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        ensureNotificationChannels(this)
        CallStateManager.attachService(this)
        CallSmartIslandManager.refreshOverlayPermissionState(applicationContext)
        startObservingCallState()
        startSmartIslandStateSyncLoop()
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        followUpLaunchJob?.cancel()
        CallStateManager.detachService(this)
        syncSmartIslandImmediate()
        hasInCallWindowBeenShownForCurrentCall = false
        isHandlingOnCallAdded = false
        isLaunchingInCallWindow = false
        lastAutoPresentedCallId = null
        stopForegroundAndCancelNotification()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        followUpLaunchJob?.cancel()
        smartIslandTickerJob?.cancel()
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Throwable) {
        }
        smartIslandOverlayController?.detachOverlay()
        smartIslandOverlayController = null
        hasInCallWindowBeenShownForCurrentCall = false
        isHandlingOnCallAdded = false
        isLaunchingInCallWindow = false
        lastAutoPresentedCallId = null
        stopForegroundAndCancelNotification()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ensureNotificationChannels(this)
        hasInCallWindowBeenShownForCurrentCall = CallStateManager.isInCallActivityVisible.value
        CallStateManager.resetMinimizedStateForNewCall()

        isHandlingOnCallAdded = true
        try {
            CallStateManager.attachService(this)
            CallStateManager.onCallAdded(applicationContext, call)
        } finally {
            isHandlingOnCallAdded = false
        }

        val uiState = CallStateManager.activeCallUiState.value
        val isAlreadyVisible = CallStateManager.isInCallActivityVisible.value
        lastAutoPresentedCallId = uiState?.callId

        // 1. Immediately publish the valid incoming/outgoing call notification and promote
        //    ColorInCallService to FOREGROUND_SERVICE_TYPE_PHONE_CALL FIRST.
        if (uiState != null) {
            updateCallNotification(
                uiState = uiState,
                isBannerDismissedBySwipe = false,
                isInCallWindowVisible = isAlreadyVisible,
                isInitialAlert = true
            )
        }

        // 2. Launch the Full-Screen Call Activity (InCallActivity) only for outgoing/active calls
        //    (never launch the custom full-screen incoming call UI when a call is ringing).
        if (!isAlreadyVisible && uiState != null && !uiState.isRinging) {
            launchInCallWindow(
                compactIncomingOnly = false,
                initialIncomingMode = IncomingCallPresentationMode.FULL_SCREEN
            )
        }

        // 3. Synchronize Smart Island state (never suppressing or covering the incoming/outgoing call UI).
        syncSmartIslandImmediate()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallStateManager.onCallRemoved(call)
        syncSmartIslandImmediate()
        if (calls.isEmpty()) {
            followUpLaunchJob?.cancel()
            hasInCallWindowBeenShownForCurrentCall = false
            isLaunchingInCallWindow = false
            lastAutoPresentedCallId = null
            stopForegroundAndCancelNotification()
        }
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallStateManager.onAudioStateChanged(audioState)
        syncSmartIslandImmediate()
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            CallStateManager.onPlatformCallEndpointChanged(callEndpoint)
            syncSmartIslandImmediate()
        }
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            CallStateManager.onPlatformAvailableCallEndpointsChanged(availableEndpoints)
            syncSmartIslandImmediate()
        }
    }

    /**
     * Invoked when Telecom reports a state transition on an active or newly placed call.
     */
    fun onTelecomCallStateChanged(call: Call, state: Int) {
        syncSmartIslandImmediate()
        val uiState = CallStateManager.activeCallUiState.value ?: return
        val isVisible = CallStateManager.isInCallActivityVisible.value
        val userMinimized = CallStateManager.hasUserMinimizedCurrentCall.value

        val shouldEnsureFullScreenVisible =
            (state == Call.STATE_DIALING ||
                state == Call.STATE_CONNECTING ||
                state == Call.STATE_ACTIVE) &&
                !isVisible &&
                !userMinimized &&
                !hasInCallWindowBeenShownForCurrentCall &&
                !isLaunchingInCallWindow

        if (shouldEnsureFullScreenVisible) {
            updateCallNotification(
                uiState = uiState,
                isBannerDismissedBySwipe = CallStateManager.isBannerDismissedBySwipe.value,
                isInCallWindowVisible = false,
                isInitialAlert = true
            )
            launchInCallWindow(
                compactIncomingOnly = false,
                initialIncomingMode = IncomingCallPresentationMode.FULL_SCREEN
            )
        }
    }

    private fun computeElapsedCallSeconds(callState: ActiveCallUiState?): Long {
        if (callState == null) return 0L
        if (callState.state != Call.STATE_ACTIVE && callState.state != Call.STATE_HOLDING) return 0L
        val baseMillis = callState.connectTimeMillis
        if (baseMillis <= 0L) return 0L
        return ((System.currentTimeMillis() - baseMillis) / 1000L).coerceAtLeast(0L)
    }

    private fun syncSmartIslandImmediate() {
        val callState = CallStateManager.activeCallUiState.value
        val recState = CallRecordingManager.sessionState.value
        val aiState = AiCallAssistantManager.uiState.value
        val dismissed = CallStateManager.isBannerDismissedBySwipe.value
        val isInCallVisible = CallStateManager.isInCallActivityVisible.value
        val elapsedSec = computeElapsedCallSeconds(callState)

        CallSmartIslandManager.onExternalStatesUpdated(
            callState = callState,
            recordingState = recState,
            aiState = aiState,
            isBannerDismissedBySwipe = dismissed,
            elapsedCallSeconds = elapsedSec,
            isFullScreenCallUiVisible = isInCallVisible
        )

        // Smart Island / Smart Notch is temporarily postponed per user request:
        // keep the cross-app overlay detached so it never interferes with incoming or active call screens.
        smartIslandOverlayController?.syncOverlayVisibility(false)
    }

    private fun startSmartIslandStateSyncLoop() {
        if (smartIslandTickerJob?.isActive == true) return
        smartIslandTickerJob = serviceScope.launch {
            while (true) {
                syncSmartIslandImmediate()
                delay(500L)
            }
        }
    }

    /**
     * Synchronously hides the Smart Island overlay when [InCallActivity] becomes visible
     * and restores it when [InCallActivity] leaves the foreground.
     */
    fun onInCallActivityVisibilityChanged(visible: Boolean) {
        if (visible) {
            hasInCallWindowBeenShownForCurrentCall = true
            isLaunchingInCallWindow = false
            followUpLaunchJob?.cancel()
        }

        CallSmartIslandManager.setFullScreenCallUiVisible(visible)
        syncSmartIslandImmediate()

        val currentCall = CallStateManager.activeCallUiState.value
        if (currentCall != null) {
            updateCallNotification(
                uiState = currentCall,
                isBannerDismissedBySwipe = CallStateManager.isBannerDismissedBySwipe.value,
                isInCallWindowVisible = visible,
                isInitialAlert = false
            )
        }
    }

    /**
     * Called when the user swipes UP on the incoming-call banner/island to dismiss it
     * while keeping the incoming call ringing.
     */
    fun onBannerDismissedBySwipeUp() {
        followUpLaunchJob?.cancel()
        isLaunchingInCallWindow = false
        syncSmartIslandImmediate()
        val currentCall = CallStateManager.activeCallUiState.value
        if (currentCall != null) {
            updateCallNotification(
                uiState = currentCall,
                isBannerDismissedBySwipe = true,
                isInCallWindowVisible = false,
                isInitialAlert = false
            )
        }
    }

    /**
     * Requests presenting the incoming call UI in either compact banner or full-screen mode.
     * Custom incoming call screen is disabled; ringing calls use only the native notification.
     */
    fun requestPresentIncomingUi(compactIncomingOnly: Boolean) {
        val currentCall = CallStateManager.activeCallUiState.value ?: return
        if (!currentCall.isRinging) {
            requestPresentActiveCallUi()
            return
        }
        updateCallNotification(
            uiState = currentCall,
            isBannerDismissedBySwipe = CallStateManager.isBannerDismissedBySwipe.value,
            isInCallWindowVisible = false,
            isInitialAlert = false
        )
    }

    /**
     * Presents the Full-Screen Active Call Screen (`InCallActivity`) when an incoming call is answered,
     * an outgoing call is placed, or the user performs a 3-second long press on the Call Smart Island.
     */
    fun requestPresentActiveCallUi() {
        val currentCall = CallStateManager.activeCallUiState.value ?: return
        if (currentCall.isRinging) {
            return
        }
        followUpLaunchJob?.cancel()
        CallStateManager.resetMinimizedStateForNewCall()
        updateCallNotification(
            uiState = currentCall,
            isBannerDismissedBySwipe = CallStateManager.isBannerDismissedBySwipe.value,
            isInCallWindowVisible = CallStateManager.isInCallActivityVisible.value,
            isInitialAlert = !CallStateManager.isInCallActivityVisible.value
        )
        launchInCallWindow(
            compactIncomingOnly = false,
            initialIncomingMode = IncomingCallPresentationMode.FULL_SCREEN
        )
    }

    private fun startObservingCallState() {
        if (stateCollectorJob?.isActive == true) return
        stateCollectorJob = serviceScope.launch {
            var wasRingingPreviously = false
            combine(
                CallStateManager.activeCallUiState,
                CallStateManager.isBannerDismissedBySwipe,
                CallStateManager.isInCallActivityVisible
            ) { uiState, isDismissed, isVisible ->
                Triple(uiState, isDismissed, isVisible)
            }.collectLatest { (uiState, isDismissed, isVisible) ->
                syncSmartIslandImmediate()
                if (uiState == null) {
                    wasRingingPreviously = false
                    lastAutoPresentedCallId = null
                    hasInCallWindowBeenShownForCurrentCall = false
                    isLaunchingInCallWindow = false
                    followUpLaunchJob?.cancel()
                    stopForegroundAndCancelNotification()
                } else {
                    if (isHandlingOnCallAdded) {
                        wasRingingPreviously = uiState.isRinging
                        return@collectLatest
                    }

                    val isNewCallId = uiState.callId != lastAutoPresentedCallId
                    val transitionedFromRingingToActive = wasRingingPreviously && !uiState.isRinging
                    wasRingingPreviously = uiState.isRinging

                    if (isNewCallId) {
                        lastAutoPresentedCallId = uiState.callId
                        hasInCallWindowBeenShownForCurrentCall = isVisible
                    }

                    if (transitionedFromRingingToActive ||
                        (isNewCallId && !isVisible && !CallStateManager.hasUserMinimizedCurrentCall.value)
                    ) {
                        CallStateManager.resetMinimizedStateForNewCall()
                        updateCallNotification(
                            uiState = uiState,
                            isBannerDismissedBySwipe = isDismissed,
                            isInCallWindowVisible = isVisible,
                            isInitialAlert = !isVisible
                        )
                        if (!uiState.isRinging && !isVisible && !isLaunchingInCallWindow) {
                            launchInCallWindow(
                                compactIncomingOnly = false,
                                initialIncomingMode = IncomingCallPresentationMode.FULL_SCREEN
                            )
                        }
                    } else {
                        updateCallNotification(
                            uiState = uiState,
                            isBannerDismissedBySwipe = isDismissed,
                            isInCallWindowVisible = isVisible,
                            isInitialAlert = false
                        )
                    }
                }
            }
        }
    }

    private fun buildInCallActivityIntent(
        compactIncomingOnly: Boolean,
        initialIncomingMode: IncomingCallPresentationMode?
    ): Intent {
        val mode = initialIncomingMode ?: IncomingCallPresentationMode.FULL_SCREEN
        return Intent(this, InCallActivity::class.java).apply {
            putExtra(InCallActivity.EXTRA_COMPACT_INCOMING_ONLY, compactIncomingOnly)
            putExtra(InCallActivity.EXTRA_FORCE_FULL_ACTIVE_UI, !compactIncomingOnly)
            putExtra(InCallActivity.EXTRA_INITIAL_INCOMING_MODE, mode.name)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
    }

    private fun launchInCallWindow(
        compactIncomingOnly: Boolean,
        initialIncomingMode: IncomingCallPresentationMode? = IncomingCallPresentationMode.FULL_SCREEN
    ): Boolean {
        isLaunchingInCallWindow = true
        val intent = buildInCallActivityIntent(compactIncomingOnly, initialIncomingMode)
        var launchedImmediate = false
        try {
            startActivity(intent)
            launchedImmediate = true
        } catch (_: Throwable) {
            launchedImmediate = sendFullScreenCallPendingIntent(intent)
        }

        // Single clean follow-up check at 550ms: allows lock-screen wake-up and activity transition
        // to complete without overlapping launch interrupts. If InCallActivity has still not reached
        // STARTED/RESUMED, fire the full-screen PendingIntent once and clear isLaunchingInCallWindow.
        followUpLaunchJob?.cancel()
        followUpLaunchJob = serviceScope.launch {
            delay(550L)
            if (CallStateManager.activeCallUiState.value != null &&
                !CallStateManager.isInCallActivityVisible.value &&
                !CallStateManager.hasUserMinimizedCurrentCall.value
            ) {
                sendFullScreenCallPendingIntent(intent)
                delay(250L)
            }
            isLaunchingInCallWindow = false
            syncSmartIslandImmediate()
        }
        return launchedImmediate
    }

    private fun sendFullScreenCallPendingIntent(intent: Intent): Boolean {
        return try {
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQ_OPEN_ACTIVE_CALL,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = android.app.ActivityOptions.makeBasic().apply {
                    try {
                        setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    } catch (_: Throwable) {
                    }
                }
                pendingIntent.send(this, 0, null, null, null, null, options.toBundle())
            } else {
                pendingIntent.send()
            }
            true
        } catch (_: Throwable) {
            try {
                startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun resolveCallerSingleLabel(uiState: ActiveCallUiState): String {
        if (uiState.isWaitingCallRinging) {
            val waitingSavedName = uiState.waitingCallerName?.trim().orEmpty()
            if (waitingSavedName.isNotEmpty()) return waitingSavedName
            val waitingNum = uiState.waitingCallNumber?.trim().orEmpty()
            return waitingNum.ifEmpty { "Unknown" }
        }
        val savedName = uiState.displayName?.trim().orEmpty()
        if (savedName.isNotEmpty()) {
            return savedName
        }
        val number = uiState.number.trim()
        return number.ifEmpty { "Unknown" }
    }

    private fun updateCallNotification(
        uiState: ActiveCallUiState,
        isBannerDismissedBySwipe: Boolean,
        isInCallWindowVisible: Boolean,
        isInitialAlert: Boolean = false
    ) {
        try {
            ensureNotificationChannels(this)

            val isRingingOrWaiting = uiState.isRinging || uiState.isWaitingCallRinging
            val callerSingleTitle = resolveCallerSingleLabel(uiState)
            val signature = buildString {
                append(uiState.callId)
                append('|').append(uiState.state)
                append('|').append(isRingingOrWaiting)
                append('|').append(callerSingleTitle)
                append('|').append(uiState.number)
                append('|').append(uiState.locationLabel.orEmpty())
                append('|').append(uiState.isSpeakerOn)
                append('|').append(uiState.isBluetoothOn)
                append('|').append(uiState.activeAudioEndpoint.id)
                append('|').append(uiState.isMuted)
                append('|').append(uiState.isRecordingActive)
                append('|').append(isInCallWindowVisible)
            }

            if (!isInitialAlert && isServiceInForeground && signature == lastPostedNotificationSignature) {
                return
            }
            lastPostedNotificationSignature = signature

            val answerIntent = Intent(this, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_ANSWER_CALL
            }
            val answerPendingIntent = PendingIntent.getBroadcast(
                this,
                REQ_ANSWER_CALL,
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_DECLINE_CALL
            }
            val declinePendingIntent = PendingIntent.getBroadcast(
                this,
                REQ_DECLINE_CALL,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val fullScreenCallIntent = buildInCallActivityIntent(
                compactIncomingOnly = false,
                initialIncomingMode = IncomingCallPresentationMode.FULL_SCREEN
            )
            val openFullCallPendingIntent = PendingIntent.getActivity(
                this,
                REQ_OPEN_FULL_INCOMING,
                fullScreenCallIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val callerPerson = Person.Builder()
                .setName(callerSingleTitle)
                .setImportant(true)
                .build()

            val notification = if (isRingingOrWaiting) {
                // CRITICAL: Always use CHANNEL_INCOMING_CALL_ID (IMPORTANCE_HIGH, PRIORITY_MAX)
                // and NEVER call setSilent(true) while an incoming call is ringing!
                // This ensures:
                // 1) Android SystemUI permits fullScreenIntent to open InCallActivity immediately.
                // 2) If full-screen presentation is restricted by the OS, a visible incoming-call
                //    notification with working Answer and Decline buttons is ALWAYS shown.
                val secondaryInfo = if (!uiState.displayName.isNullOrBlank() && uiState.number.isNotBlank()) {
                    "Incoming call • ${uiState.number}"
                } else if (!uiState.locationLabel.isNullOrBlank()) {
                    "Incoming call • ${uiState.locationLabel}"
                } else {
                    "Incoming call"
                }

                val builder = NotificationCompat.Builder(this, CHANNEL_INCOMING_CALL_ID)
                    .setSmallIcon(R.drawable.ic_call_answer_white)
                    .setContentTitle(callerSingleTitle)
                    .setContentText(secondaryInfo)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(!isInitialAlert)

                try {
                    builder.setStyle(
                        NotificationCompat.CallStyle.forIncomingCall(
                            callerPerson,
                            declinePendingIntent,
                            answerPendingIntent
                        )
                    )
                } catch (_: Throwable) {
                    builder.addAction(
                        R.drawable.ic_call_end_white,
                        "Decline",
                        declinePendingIntent
                    )
                    builder.addAction(
                        R.drawable.ic_call_answer_white,
                        "Answer",
                        answerPendingIntent
                    )
                }

                builder.build()
            } else {
                val speakerIntent = Intent(this, CallActionReceiver::class.java).apply {
                    action = CallActionReceiver.ACTION_TOGGLE_SPEAKER_CALL
                }
                val speakerPendingIntent = PendingIntent.getBroadcast(
                    this,
                    REQ_TOGGLE_SPEAKER,
                    speakerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val muteIntent = Intent(this, CallActionReceiver::class.java).apply {
                    action = CallActionReceiver.ACTION_TOGGLE_MUTE_CALL
                }
                val mutePendingIntent = PendingIntent.getBroadcast(
                    this,
                    REQ_TOGGLE_MUTE,
                    muteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val statusLabel = when {
                    uiState.isRecordingActive -> "Recording active • Tap for Call Screen"
                    uiState.state == Call.STATE_HOLDING -> "Call on hold"
                    uiState.state == Call.STATE_DIALING || uiState.state == Call.STATE_CONNECTING -> "Calling..."
                    else -> "Ongoing call"
                }

                val useHighPriorityLaunch = isInitialAlert &&
                    !isInCallWindowVisible &&
                    !CallStateManager.hasUserMinimizedCurrentCall.value

                val channelToUse = if (useHighPriorityLaunch) {
                    CHANNEL_INCOMING_CALL_ID
                } else {
                    CHANNEL_ONGOING_CALL_ID
                }

                val builder = NotificationCompat.Builder(this, channelToUse)
                    .setSmallIcon(R.drawable.ic_call_answer_white)
                    .setContentTitle(callerSingleTitle)
                    .setContentText(statusLabel)
                    .setPriority(
                        if (useHighPriorityLaunch) {
                            NotificationCompat.PRIORITY_MAX
                        } else {
                            NotificationCompat.PRIORITY_DEFAULT
                        }
                    )
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setOnlyAlertOnce(!isInitialAlert)
                    .setAutoCancel(false)
                    .setContentIntent(openFullCallPendingIntent)

                try {
                    builder.setStyle(
                        NotificationCompat.CallStyle.forOngoingCall(
                            callerPerson,
                            declinePendingIntent
                        )
                    )
                } catch (_: Throwable) {
                    builder.addAction(R.drawable.ic_call_end_white, "End Call", declinePendingIntent)
                }

                builder.addAction(
                    R.drawable.ic_call_answer_white,
                    if (uiState.isSpeakerOn) "Speaker: ON" else "Speaker",
                    speakerPendingIntent
                )
                builder.addAction(
                    R.drawable.ic_call_answer_white,
                    if (uiState.isMuted) "Unmute" else "Mute",
                    mutePendingIntent
                )

                if (useHighPriorityLaunch) {
                    builder.setFullScreenIntent(openFullCallPendingIntent, true)
                }

                builder.build()
            }

            promoteForegroundOrNotify(notification)
        } catch (_: Exception) {
        }
    }

    private fun promoteForegroundOrNotify(notification: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        var promoted = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isServiceInForeground = true
            promoted = true
        } catch (_: Exception) {
        }
        if (!promoted) {
            try {
                nm?.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {
            }
        }
    }

    private fun stopForegroundAndCancelNotification() {
        isServiceInForeground = false
        lastPostedNotificationSignature = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }
}

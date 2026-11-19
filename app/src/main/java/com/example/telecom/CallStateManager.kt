package com.example.telecom

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.PhoneNumberUtils
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.data.DialerRepository
import com.example.model.ActiveCallUiState
import com.example.model.CallAudioEndpointItem
import com.example.model.CallAudioEndpointType
import com.example.ui.screens.IncomingCallPresentationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object CallStateManager {

    private data class CallerResolvedMeta(
        val displayName: String?,
        val location: String?,
        val photoUri: String?,
        val isBlocked: Boolean
    )

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var inCallService: InCallService? = null
    private val activeCalls = CopyOnWriteArrayList<Call>()
    private val registeredCallbackCalls = CopyOnWriteArrayList<Call>()
    private val callerCache = java.util.concurrent.ConcurrentHashMap<String, CallerResolvedMeta>()
    private val pendingCallerLookups = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var lastAnsweredCallHash: Int = 0
    private var lastAnsweredAtMs: Long = 0L
    private var lastRejectedCallHash: Int = 0
    private var lastRejectedAtMs: Long = 0L

    private var isMutedState: Boolean = false
    private var currentAudioRoute: Int = CallAudioState.ROUTE_EARPIECE
    private var supportedRouteMask: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
    private var transientStatusMessage: String? = null
    private var isNearEarState: Boolean = false
    private var isRecordingActiveState: Boolean = false

    val audioEndpointRouter = CallAudioEndpointRouter(
        onStateChanged = { snapshot ->
            currentAudioRoute = snapshot.currentLegacyRouteMask
            supportedRouteMask = snapshot.supportedLegacyRouteMask
            updateProximitySensorRegistration()
            syncUiState()
        },
        onRoutingError = { errorMsg ->
            showStatusMessage(errorMsg)
        }
    )

    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null

    private val _activeCallUiState = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCallUiState: StateFlow<ActiveCallUiState?> = _activeCallUiState.asStateFlow()

    private val _incomingPresentationMode = MutableStateFlow(IncomingCallPresentationMode.FULL_SCREEN)
    val incomingPresentationMode: StateFlow<IncomingCallPresentationMode> = _incomingPresentationMode.asStateFlow()

    private val _isBannerDismissedBySwipe = MutableStateFlow(false)
    val isBannerDismissedBySwipe: StateFlow<Boolean> = _isBannerDismissedBySwipe.asStateFlow()

    private val _isInCallActivityVisible = MutableStateFlow(false)
    val isInCallActivityVisible: StateFlow<Boolean> = _isInCallActivityVisible.asStateFlow()

    private val _hasUserMinimizedCurrentCall = MutableStateFlow(false)
    val hasUserMinimizedCurrentCall: StateFlow<Boolean> = _hasUserMinimizedCurrentCall.asStateFlow()

    private var toneGenerator: ToneGenerator? = null

    fun markCallUiMinimizedByUser() {
        _hasUserMinimizedCurrentCall.value = true
    }

    fun resetMinimizedStateForNewCall() {
        _hasUserMinimizedCurrentCall.value = false
    }

    fun setIncomingPresentationMode(mode: IncomingCallPresentationMode) {
        _incomingPresentationMode.value = mode
        if (mode == IncomingCallPresentationMode.FULL_SCREEN ||
            mode == IncomingCallPresentationMode.COMPACT_BANNER
        ) {
            _isBannerDismissedBySwipe.value = false
        }
        if (mode == IncomingCallPresentationMode.FULL_SCREEN) {
            _hasUserMinimizedCurrentCall.value = false
        }
    }

    fun expandIncomingCallToFullScreen() {
        _isBannerDismissedBySwipe.value = false
        _hasUserMinimizedCurrentCall.value = false
        _incomingPresentationMode.value = IncomingCallPresentationMode.FULL_SCREEN
        (inCallService as? ColorInCallService)?.requestPresentIncomingUi(
            compactIncomingOnly = false
        )
    }

    private var activeInCallActivityToken: Int = 0
    private var lastInCallLaunchElapsedMs: Long = 0L

    /**
     * Opens the original Full-Screen Active Call Screen (`InCallActivity`) for an ongoing
     * or newly answered/placed call.
     */
    fun openFullScreenActiveCallUi(context: Context? = null) {
        _isBannerDismissedBySwipe.value = false
        _hasUserMinimizedCurrentCall.value = false
        val currentCall = _activeCallUiState.value
        if (currentCall?.isRinging == true) {
            _incomingPresentationMode.value = IncomingCallPresentationMode.FULL_SCREEN
        }
        val service = inCallService as? ColorInCallService
        if (service != null && context !is android.app.Activity) {
            service.requestPresentActiveCallUi()
            return
        }
        val ctx = context ?: inCallService ?: return
        launchFullScreenInCallActivityIntent(ctx, isRinging = currentCall?.isRinging == true)
    }

    fun launchFullScreenInCallActivityIntent(context: Context, isRinging: Boolean = false): Boolean {
        _hasUserMinimizedCurrentCall.value = false
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        if (_isInCallActivityVisible.value && (nowElapsed - lastInCallLaunchElapsedMs) < 500L) {
            return true
        }
        lastInCallLaunchElapsedMs = nowElapsed

        val intent = Intent(context, InCallActivity::class.java).apply {
            putExtra(
                InCallActivity.EXTRA_INITIAL_INCOMING_MODE,
                IncomingCallPresentationMode.FULL_SCREEN.name
            )
            putExtra(InCallActivity.EXTRA_COMPACT_INCOMING_ONLY, false)
            putExtra(InCallActivity.EXTRA_FORCE_FULL_ACTIVE_UI, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }

        // If called directly from an Activity context (e.g. MainActivity when initiating a call),
        // direct startActivity(intent) has full foreground launch privilege.
        if (context is android.app.Activity) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Throwable) {
            }
        }

        var launched = false
        try {
            context.startActivity(intent)
            launched = true
        } catch (_: Throwable) {
        }

        if (!launched) {
            try {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    2099,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
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
                    pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
                } else {
                    pendingIntent.send()
                }
                launched = true
            } catch (_: Throwable) {
            }
        }
        return launched
    }

    fun collapseIncomingCallToBanner() {
        _isBannerDismissedBySwipe.value = false
        _hasUserMinimizedCurrentCall.value = true
        _incomingPresentationMode.value = IncomingCallPresentationMode.COMPACT_BANNER
        (inCallService as? ColorInCallService)?.requestPresentIncomingUi(
            compactIncomingOnly = true
        )
    }

    /**
     * Dismisses the visible floating banner when the user swipes UP on it,
     * WITHOUT rejecting or ending the incoming call. The call continues ringing.
     */
    fun dismissIncomingBannerKeepRinging() {
        _isBannerDismissedBySwipe.value = true
        _hasUserMinimizedCurrentCall.value = true
        _incomingPresentationMode.value = IncomingCallPresentationMode.COLLAPSED_INDICATOR
        (inCallService as? ColorInCallService)?.onBannerDismissedBySwipeUp()
    }

    fun setInCallActivityVisible(visible: Boolean, activityToken: Int = 0) {
        if (visible) {
            if (activityToken != 0) {
                activeInCallActivityToken = activityToken
            }
        } else {
            if (activityToken != 0 && activeInCallActivityToken != 0 && activityToken != activeInCallActivityToken) {
                // Ignore stale onStop/onDestroy from a previous InCallActivity instance
                return
            }
            if (activityToken != 0 && activityToken == activeInCallActivityToken) {
                activeInCallActivityToken = 0
            }
        }
        val changed = _isInCallActivityVisible.value != visible
        _isInCallActivityVisible.value = visible
        CallSmartIslandManager.setFullScreenCallUiVisible(visible)
        if (changed) {
            (inCallService as? ColorInCallService)?.onInCallActivityVisibilityChanged(visible)
        }
    }

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val sensor = event?.sensor ?: return
            if (sensor.type == Sensor.TYPE_PROXIMITY) {
                val distance = event.values.firstOrNull() ?: sensor.maximumRange
                val near = distance < sensor.maximumRange && distance < 5f
                if (isNearEarState != near) {
                    isNearEarState = near
                    syncUiState()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) {
                unregisterCallCallbackSafe(call)
                activeCalls.remove(call)
            }
            updateProximitySensorRegistration()
            syncUiState()
            (inCallService as? ColorInCallService)?.onTelecomCallStateChanged(call, state)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            syncUiState()
        }

        override fun onCallDestroyed(call: Call) {
            unregisterCallCallbackSafe(call)
            activeCalls.remove(call)
            updateProximitySensorRegistration()
            syncUiState()
        }
    }

    private fun registerCallCallbackOnce(call: Call) {
        if (!registeredCallbackCalls.contains(call)) {
            registeredCallbackCalls.add(call)
            try {
                call.registerCallback(callCallback)
            } catch (_: Throwable) {
                registeredCallbackCalls.remove(call)
            }
        }
    }

    private fun unregisterCallCallbackSafe(call: Call) {
        registeredCallbackCalls.remove(call)
        try {
            call.unregisterCallback(callCallback)
        } catch (_: Throwable) {
        }
    }

    private fun syncCallsFromInCallService() {
        val service = inCallService ?: return
        val liveServiceCalls = try {
            service.calls.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        for (c in liveServiceCalls) {
            val st = getCallStateCompat(c)
            if (st != Call.STATE_DISCONNECTED && st != Call.STATE_DISCONNECTING) {
                if (!activeCalls.contains(c)) {
                    activeCalls.add(c)
                }
                registerCallCallbackOnce(c)
            }
        }
        activeCalls.removeAll { tracked ->
            val st = getCallStateCompat(tracked)
            val shouldRemove = st == Call.STATE_DISCONNECTED ||
                st == Call.STATE_DISCONNECTING ||
                (liveServiceCalls.isNotEmpty() && !liveServiceCalls.contains(tracked))
            if (shouldRemove) {
                unregisterCallCallbackSafe(tracked)
            }
            shouldRemove
        }
    }

    @Suppress("DEPRECATION")
    fun attachService(service: InCallService) {
        inCallService = service
        audioEndpointRouter.refreshConnectedAudioDeviceNames(service.applicationContext)
        service.callAudioState?.let { audio ->
            isMutedState = audio.isMuted
            currentAudioRoute = audio.route
            supportedRouteMask = audio.supportedRouteMask
            audioEndpointRouter.onCallAudioStateChanged(
                context = service.applicationContext,
                audioState = audio,
                service = service
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                service.currentCallEndpoint?.let { ep ->
                    audioEndpointRouter.onPlatformCallEndpointChanged(ep)
                }
            } catch (_: Throwable) {
            }
        }
        syncCallsFromInCallService()
        initProximityResources(service.applicationContext)
        updateProximitySensorRegistration()
        if (activeCalls.isNotEmpty()) {
            syncUiState()
        }
    }

    fun detachService(service: InCallService) {
        if (inCallService === service) {
            inCallService = null
        }
        releaseProximityResources()
        activeCalls.forEach {
            unregisterCallCallbackSafe(it)
        }
        registeredCallbackCalls.clear()
        activeCalls.clear()
        audioEndpointRouter.reset()
        CallRecordingManager.stopRecording(reason = "Call ended — recording stopped.")
        AiCallAssistantManager.stopSession()
        isRecordingActiveState = false
        activeInCallActivityToken = 0
        _isBannerDismissedBySwipe.value = false
        _hasUserMinimizedCurrentCall.value = false
        _isInCallActivityVisible.value = false
        CallSmartIslandManager.setFullScreenCallUiVisible(false)
        _incomingPresentationMode.value = IncomingCallPresentationMode.FULL_SCREEN
        _activeCallUiState.value = null
    }

    private fun initProximityResources(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                if (proximityWakeLock == null) {
                    proximityWakeLock = pm.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        "Phone:InCallProximityWakeLock"
                    ).apply {
                        setReferenceCounted(false)
                    }
                }
            }
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        } catch (_: Exception) {
        }
    }

    private fun updateProximitySensorRegistration() {
        val hasActiveOrDialing = activeCalls.any {
            val st = getCallStateCompat(it)
            st == Call.STATE_ACTIVE || st == Call.STATE_DIALING || st == Call.STATE_CONNECTING
        }
        val usingEarpiece = currentAudioRoute == CallAudioState.ROUTE_EARPIECE

        try {
            if (hasActiveOrDialing && usingEarpiece) {
                if (proximityWakeLock?.isHeld == false) {
                    proximityWakeLock?.acquire(60 * 60 * 1000L)
                }
                proximitySensor?.let { sensor ->
                    sensorManager?.registerListener(
                        proximityListener,
                        sensor,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                }
            } else {
                if (proximityWakeLock?.isHeld == true) {
                    proximityWakeLock?.release()
                }
                sensorManager?.unregisterListener(proximityListener)
                isNearEarState = false
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseProximityResources() {
        try {
            sensorManager?.unregisterListener(proximityListener)
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
            }
            isNearEarState = false
        } catch (_: Exception) {
        }
    }

    fun onCallAdded(context: Context, call: Call) {
        val appContext = context.applicationContext
        val rawNumber = extractCallNumber(call)
        val repo = DialerRepository(appContext)

        // Check if incoming call is from a blocked number
        if (getCallStateCompat(call) == Call.STATE_RINGING && rawNumber.isNotBlank()) {
            val blocked = try {
                repo.isNumberBlockedSync(rawNumber)
            } catch (_: Throwable) {
                false
            }
            if (blocked) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        call.reject(Call.REJECT_REASON_UNWANTED)
                    } else {
                        @Suppress("DEPRECATION")
                        call.reject(false, null)
                    }
                    return
                } catch (_: Exception) {
                }
            }
        }

        if (!activeCalls.contains(call)) {
            activeCalls.add(call)
        }
        registerCallCallbackOnce(call)

        // Immediately publish initial call state so ColorInCallService can post the foreground
        // notification and launch InCallActivity in 0ms without waiting on ContentResolver I/O.
        _isBannerDismissedBySwipe.value = false
        _hasUserMinimizedCurrentCall.value = false
        _incomingPresentationMode.value = IncomingCallPresentationMode.FULL_SCREEN
        updateProximitySensorRegistration()
        syncUiState()

        triggerCallerLookupIfNeeded(appContext, rawNumber, repo)
    }

    private fun triggerCallerLookupIfNeeded(
        context: Context?,
        rawNumber: String,
        repository: DialerRepository? = null
    ) {
        val appContext = context?.applicationContext ?: return
        if (rawNumber.isBlank() || callerCache.containsKey(rawNumber)) return
        if (!pendingCallerLookups.add(rawNumber)) return

        scope.launch(Dispatchers.IO) {
            try {
                val repo = repository ?: DialerRepository(appContext)
                val info = lookupCallerInfo(appContext, rawNumber, repo)
                callerCache[rawNumber] = info
                mainHandler.post { syncUiState() }
            } catch (_: Throwable) {
            } finally {
                pendingCallerLookups.remove(rawNumber)
            }
        }
    }

    /**
     * Clears cached caller metadata (for all numbers if [rawNumber] is null, or for a specific number)
     * so updated contact photos/names are freshly resolved.
     */
    fun invalidateCallerCache(rawNumber: String? = null) {
        if (rawNumber.isNullOrBlank()) {
            callerCache.clear()
        } else {
            callerCache.remove(rawNumber)
        }
    }

    /**
     * Synchronously resolves and caches the highest-quality contact photo URI for [rawNumber],
     * falling back to [fallbackDetailsPhotoUri] when no local contact photo is found.
     */
    fun resolveContactPhotoUri(
        context: Context,
        rawNumber: String,
        fallbackDetailsPhotoUri: Uri? = null
    ): String? {
        val appContext = context.applicationContext
        val trimmed = rawNumber.trim()
        if (trimmed.isNotEmpty()) {
            val resolved = lookupCallerInfo(appContext, trimmed, DialerRepository(appContext))
            callerCache[trimmed] = resolved
            if (!resolved.photoUri.isNullOrBlank()) {
                return resolved.photoUri
            }
        }
        return fallbackDetailsPhotoUri?.toString()?.takeIf { it.isNotBlank() }
    }

    fun onCallRemoved(call: Call) {
        unregisterCallCallbackSafe(call)
        activeCalls.remove(call)
        updateProximitySensorRegistration()
        syncUiState()
    }

    @Suppress("DEPRECATION")
    fun onAudioStateChanged(audioState: CallAudioState?) {
        if (audioState != null) {
            isMutedState = audioState.isMuted
            audioEndpointRouter.onCallAudioStateChanged(
                context = inCallService?.applicationContext,
                audioState = audioState,
                service = inCallService
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onPlatformCallEndpointChanged(callEndpoint: CallEndpoint) {
        audioEndpointRouter.refreshConnectedAudioDeviceNames(inCallService?.applicationContext)
        audioEndpointRouter.onPlatformCallEndpointChanged(callEndpoint)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onPlatformAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        audioEndpointRouter.refreshConnectedAudioDeviceNames(inCallService?.applicationContext)
        audioEndpointRouter.onPlatformAvailableEndpointsChanged(availableEndpoints, inCallService)
    }

    @Suppress("DEPRECATION")
    fun onExternalAudioDevicesChanged(context: Context?) {
        audioEndpointRouter.refreshConnectedAudioDeviceNames(context)
        val service = inCallService
        if (service != null) {
            service.callAudioState?.let { audio ->
                audioEndpointRouter.onCallAudioStateChanged(
                    context = context ?: service.applicationContext,
                    audioState = audio,
                    service = service
                )
            }
        }
        syncUiState()
    }

    private fun selectPrimaryCall(): Call? {
        syncCallsFromInCallService()
        if (activeCalls.isEmpty()) return null
        // If there is already an active call and a second call is ringing (Call Waiting), keep active call primary and expose the ringing call as waitingCall
        val active = activeCalls.firstOrNull {
            getCallStateCompat(it) == Call.STATE_ACTIVE
        }
        if (active != null) return active
        return activeCalls.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING }
            ?: activeCalls.firstOrNull {
                getCallStateCompat(it) == Call.STATE_DIALING || getCallStateCompat(it) == Call.STATE_CONNECTING
            }
            ?: activeCalls.firstOrNull { getCallStateCompat(it) == Call.STATE_HOLDING }
            ?: activeCalls.firstOrNull()
    }

    @Suppress("DEPRECATION")
    fun getCallStateCompat(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details?.state ?: call.state
        } else {
            call.state
        }
    }

    private fun extractCallNumber(call: Call): String {
        val handleUri: Uri? = call.details?.handle
        val raw = handleUri?.schemeSpecificPart.orEmpty().trim()
        return raw.ifBlank {
            call.details?.callerDisplayName?.toString().orEmpty().trim()
        }
    }

    private fun formatNumber(raw: String): String {
        if (raw.isBlank()) return "Unknown number"
        val country = Locale.getDefault().country.ifBlank { "US" }
        return try {
            PhoneNumberUtils.formatNumber(raw, country) ?: raw
        } catch (_: Exception) {
            raw
        }
    }

    private fun syncUiState() {
        val primary = selectPrimaryCall()
        if (primary == null) {
            if (isRecordingActiveState) {
                CallRecordingManager.stopRecording(reason = "Call ended — recording stopped.")
            }
            isRecordingActiveState = false
            AiCallAssistantManager.stopSession()
            LiveIncomingVoiceWaveformController.stopMonitoring()
            _isBannerDismissedBySwipe.value = false
            _hasUserMinimizedCurrentCall.value = false
            _incomingPresentationMode.value = IncomingCallPresentationMode.FULL_SCREEN
            _activeCallUiState.value = null
            return
        }

        val state = getCallStateCompat(primary)
        if ((state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) && activeCalls.size <= 1) {
            if (isRecordingActiveState) {
                CallRecordingManager.stopRecording(reason = "Call ended — recording stopped.")
            }
            isRecordingActiveState = false
            AiCallAssistantManager.stopSession()
            LiveIncomingVoiceWaveformController.stopMonitoring()
            _isBannerDismissedBySwipe.value = false
            _hasUserMinimizedCurrentCall.value = false
            _incomingPresentationMode.value = IncomingCallPresentationMode.FULL_SCREEN
            _activeCallUiState.value = null
            return
        }

        if (state != Call.STATE_ACTIVE) {
            LiveIncomingVoiceWaveformController.stopMonitoring()
            if (isRecordingActiveState) {
                CallRecordingManager.stopRecording(reason = "Call state changed — recording stopped.")
                isRecordingActiveState = false
            }
        }

        val details = primary.details
        val rawNumber = extractCallNumber(primary)
        val formattedNumber = formatNumber(rawNumber)
        if (rawNumber.isNotBlank() && !callerCache.containsKey(rawNumber)) {
            triggerCallerLookupIfNeeded(inCallService?.applicationContext, rawNumber)
        }
        val cachedMeta = callerCache[rawNumber]
        val callerName = cachedMeta?.displayName
            ?: details?.contactDisplayName?.takeIf { it.isNotBlank() }
            ?: details?.callerDisplayName?.takeIf { it.isNotBlank() }
        val locationLabel = cachedMeta?.location
        val platformPhotoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            details?.contactPhotoUri?.toString()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val photoUri = cachedMeta?.photoUri?.takeIf { it.isNotBlank() } ?: platformPhotoUri

        val capabilities = details?.callCapabilities ?: 0
        val canHold = (capabilities and Call.Details.CAPABILITY_SUPPORT_HOLD) != 0 ||
            (capabilities and Call.Details.CAPABILITY_HOLD) != 0
        val canMute = (capabilities and Call.Details.CAPABILITY_MUTE) != 0 || true
        val supportsVideo =
            (capabilities and Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL) != 0 &&
                (capabilities and Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL) != 0

        val rawConnectTime = details?.connectTimeMillis ?: 0L
        val connectTime = if (rawConnectTime <= 0L && state == Call.STATE_ACTIVE) {
            System.currentTimeMillis()
        } else {
            rawConnectTime
        }
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details?.callDirection == Call.Details.DIRECTION_INCOMING || state == Call.STATE_RINGING
        } else {
            state == Call.STATE_RINGING
        }

        // Check for Call Waiting (a second call ringing while primary is active) or held secondary call
        val waitingCall = activeCalls.firstOrNull {
            it !== primary && getCallStateCompat(it) == Call.STATE_RINGING
        }
        val waitingRawNum = waitingCall?.let { extractCallNumber(it) }
        val waitingName = waitingRawNum?.let { callerCache[it]?.displayName }

        val secondaryHeldCall = activeCalls.firstOrNull {
            it !== primary && getCallStateCompat(it) != Call.STATE_RINGING
        }
        val secondaryName = secondaryHeldCall?.let { sec ->
            val secNum = extractCallNumber(sec)
            callerCache[secNum]?.displayName ?: formatNumber(secNum)
        }

        val audioSnap = audioEndpointRouter.getSnapshot()
        val isBtAvailable = audioSnap.isBluetoothAvailable ||
            (supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0
        val isBtActive = audioSnap.isBluetoothActive ||
            currentAudioRoute == CallAudioState.ROUTE_BLUETOOTH
        val isSpeakerActive = audioSnap.isSpeakerActive ||
            currentAudioRoute == CallAudioState.ROUTE_SPEAKER

        val isUnknown = rawNumber.isBlank() || callerName.isNullOrBlank()
        val isBlocked = cachedMeta?.isBlocked == true

        _activeCallUiState.value = ActiveCallUiState(
            callId = details?.handle?.toString() ?: primary.hashCode().toString(),
            number = formattedNumber,
            displayName = callerName,
            photoUri = photoUri,
            locationLabel = locationLabel,
            state = state,
            connectTimeMillis = connectTime,
            isIncoming = isIncoming,
            canHold = canHold,
            isOnHold = state == Call.STATE_HOLDING,
            canMute = canMute,
            isMuted = isMutedState,
            isSpeakerOn = isSpeakerActive,
            isBluetoothOn = isBtActive,
            isBluetoothAvailable = isBtAvailable,
            activeAudioEndpoint = audioSnap.activeEndpoint,
            availableAudioEndpoints = audioSnap.availableEndpoints,
            pendingAudioEndpointId = audioSnap.pendingEndpointId,
            supportsVideo = supportsVideo,
            isVideoActive = VideoProfile.isVideo(details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY),
            callCount = activeCalls.size,
            secondaryCallerName = secondaryName,
            waitingCallNumber = waitingRawNum?.let { formatNumber(it) },
            waitingCallerName = waitingName,
            isWaitingCallRinging = waitingCall != null,
            isUnknownNumber = isUnknown,
            isBlockedOrSpam = isBlocked,
            spamWarningLabel = when {
                isBlocked -> "Blocked number"
                isUnknown -> "Unknown number"
                else -> null
            },
            isNearEar = isNearEarState,
            isRecordingActive = isRecordingActiveState,
            statusMessage = transientStatusMessage
        )
    }

    fun setRecordingActiveState(active: Boolean) {
        if (isRecordingActiveState != active) {
            isRecordingActiveState = active
            syncUiState()
        }
    }

    fun showStatusMessage(message: String) {
        transientStatusMessage = message
        syncUiState()
        mainHandler.postDelayed({
            if (transientStatusMessage == message) {
                transientStatusMessage = null
                syncUiState()
            }
        }, 3400L)
    }

    @Suppress("DEPRECATION")
    fun answerRingingCall(context: Context? = null): Boolean {
        val ctx = context ?: inCallService?.applicationContext
        syncCallsFromInCallService()
        val ringing = activeCalls.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING }
            ?: selectPrimaryCall()?.takeIf { getCallStateCompat(it) == Call.STATE_RINGING }

        val nowMs = System.currentTimeMillis()
        val targetHash = ringing?.hashCode() ?: 0
        if (targetHash != 0 && targetHash == lastAnsweredCallHash && (nowMs - lastAnsweredAtMs) < 400L) {
            return true
        }
        lastAnsweredCallHash = targetHash
        lastAnsweredAtMs = nowMs

        var answeredViaCallObject = false
        if (ringing != null) {
            try {
                // If there is already an active call (Call Waiting), hold it if supported and answer the waiting call
                val currentActive = activeCalls.firstOrNull {
                    it !== ringing && getCallStateCompat(it) == Call.STATE_ACTIVE
                }
                if (currentActive != null) {
                    try {
                        currentActive.hold()
                    } catch (_: Exception) {
                    }
                }
                ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
                answeredViaCallObject = true
            } catch (_: Throwable) {
                try {
                    val videoState = ringing.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
                    ringing.answer(videoState)
                    answeredViaCallObject = true
                } catch (_: Throwable) {
                }
            }
        }

        var answeredViaTelecomManager = false
        if (!answeredViaCallObject && ctx != null) {
            answeredViaTelecomManager = tryAcceptRingingCallViaTelecomManager(ctx)
        }

        // Also schedule a 250ms verification pass: if the call is still ringing on OEM firmware
        // (e.g., Motorola G57 Power), invoke TelecomManager.acceptRingingCall() and retry Call.answer().
        if (ringing != null) {
            mainHandler.postDelayed({
                try {
                    syncCallsFromInCallService()
                    if (activeCalls.contains(ringing) && getCallStateCompat(ringing) == Call.STATE_RINGING) {
                        if (ctx != null) {
                            tryAcceptRingingCallViaTelecomManager(ctx)
                        }
                        try {
                            ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
                        } catch (_: Throwable) {
                        }
                    }
                    syncUiState()
                } catch (_: Throwable) {
                }
            }, 250L)

            mainHandler.postDelayed({
                try {
                    syncCallsFromInCallService()
                    if (activeCalls.contains(ringing) && getCallStateCompat(ringing) == Call.STATE_RINGING) {
                        showStatusMessage("Could not answer call yet — please swipe up or tap Answer again.")
                    }
                    syncUiState()
                } catch (_: Throwable) {
                }
            }, 800L)
        } else if (!answeredViaTelecomManager) {
            showStatusMessage("Unable to answer call: no active ringing call found.")
        }

        _isBannerDismissedBySwipe.value = false
        _hasUserMinimizedCurrentCall.value = false
        _incomingPresentationMode.value = IncomingCallPresentationMode.FULL_SCREEN
        syncUiState()
        if (!_isInCallActivityVisible.value) {
            openFullScreenActiveCallUi(ctx)
        }
        return answeredViaCallObject || answeredViaTelecomManager
    }

    @Suppress("DEPRECATION")
    private fun tryAcceptRingingCallViaTelecomManager(ctx: Context): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (tm != null) {
                    tm.acceptRingingCall()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun tryEndCallViaTelecomManager(ctx: Context): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) == PackageManager.PERMISSION_GRANTED &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ) {
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                tm?.endCall() == true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    fun declineWaitingCall() {
        syncCallsFromInCallService()
        val waiting = activeCalls.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING } ?: return
        try {
            waiting.reject(false, null)
        } catch (_: Throwable) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    waiting.reject(Call.REJECT_REASON_DECLINED)
                }
            } catch (_: Throwable) {
            }
            try {
                waiting.disconnect()
            } catch (_: Throwable) {
            }
        }
        syncUiState()
    }

    fun swapCalls() {
        syncCallsFromInCallService()
        val held = activeCalls.firstOrNull { getCallStateCompat(it) == Call.STATE_HOLDING }
        val active = activeCalls.firstOrNull { getCallStateCompat(it) == Call.STATE_ACTIVE }
        try {
            if (held != null) {
                held.unhold()
            } else if (active != null) {
                active.hold()
            }
        } catch (_: Exception) {
            showStatusMessage("Unable to switch calls on this carrier connection.")
        }
    }

    @Suppress("DEPRECATION")
    fun rejectOrDisconnectCall(context: Context? = null): Boolean {
        val ctx = context ?: inCallService?.applicationContext
        syncCallsFromInCallService()
        val ringingCall = activeCalls.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING }
        val call = ringingCall ?: selectPrimaryCall()

        val nowMs = System.currentTimeMillis()
        val targetHash = call?.hashCode() ?: 0
        if (targetHash != 0 && targetHash == lastRejectedCallHash && (nowMs - lastRejectedAtMs) < 400L) {
            return true
        }
        lastRejectedCallHash = targetHash
        lastRejectedAtMs = nowMs

        var handled = false
        if (call != null) {
            val state = getCallStateCompat(call)
            if (state == Call.STATE_RINGING) {
                // On Android / Motorola Telecom implementations, call.reject(false, null) is the universal
                // standard InCallService rejection path; we also try reject(REJECT_REASON_DECLINED) and
                // disconnect() if needed, and we DO NOT remove the call from activeCalls until Telecom
                // confirms STATE_DISCONNECTING / STATE_DISCONNECTED or onCallRemoved.
                try {
                    call.reject(false, null)
                    handled = true
                } catch (_: Throwable) {
                }
                if (!handled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        call.reject(Call.REJECT_REASON_DECLINED)
                        handled = true
                    } catch (_: Throwable) {
                    }
                }
                if (!handled) {
                    try {
                        call.disconnect()
                        handled = true
                    } catch (_: Throwable) {
                    }
                }

                // Verification pass at 250ms: if the incoming call is STILL ringing in Telecom,
                // invoke all fallback rejection paths (reject with reason, disconnect, TelecomManager.endCall).
                mainHandler.postDelayed({
                    try {
                        syncCallsFromInCallService()
                        if (activeCalls.contains(call) && getCallStateCompat(call) == Call.STATE_RINGING) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    call.reject(Call.REJECT_REASON_DECLINED)
                                } catch (_: Throwable) {
                                }
                            }
                            try {
                                call.disconnect()
                            } catch (_: Throwable) {
                            }
                            if (ctx != null) {
                                tryEndCallViaTelecomManager(ctx)
                            }
                        }
                        syncUiState()
                    } catch (_: Throwable) {
                    }
                }, 250L)

                mainHandler.postDelayed({
                    try {
                        syncCallsFromInCallService()
                        if (activeCalls.contains(call) && getCallStateCompat(call) == Call.STATE_RINGING) {
                            showStatusMessage("Could not decline call — please swipe up or tap Decline again.")
                        }
                        syncUiState()
                    } catch (_: Throwable) {
                    }
                }, 800L)
            } else {
                try {
                    call.disconnect()
                    handled = true
                } catch (_: Throwable) {
                }
            }
        }

        if (!handled && ctx != null) {
            handled = tryEndCallViaTelecomManager(ctx)
            if (!handled) {
                showStatusMessage("Unable to end call: please check default Phone app permissions.")
            }
        }

        syncUiState()
        return handled
    }

    @Suppress("DEPRECATION")
    fun rejectWithSmsReply(context: Context, smsBody: String) {
        syncCallsFromInCallService()
        val call = activeCalls.firstOrNull { getCallStateCompat(it) == Call.STATE_RINGING }
            ?: selectPrimaryCall()
        val rawNumber = call?.let { extractCallNumber(it) }.orEmpty()
        try {
            if (call != null && getCallStateCompat(call) == Call.STATE_RINGING) {
                call.reject(true, smsBody)
            } else {
                call?.disconnect()
            }
        } catch (_: Exception) {
            rejectOrDisconnectCall(context)
        }
        if (rawNumber.isNotBlank()) {
            openSmsComposer(context, rawNumber, smsBody)
        }
        syncUiState()
    }

    fun toggleMute() {
        val service = inCallService
        if (service == null) {
            showStatusMessage("Audio control unavailable outside active Telecom session.")
            return
        }
        val target = !isMutedState
        service.setMuted(target)
        isMutedState = target
        syncUiState()
    }

    fun selectAudioEndpoint(endpoint: CallAudioEndpointItem): Boolean {
        return audioEndpointRouter.requestEndpointChange(
            service = inCallService,
            targetEndpoint = endpoint
        )
    }

    fun selectAudioEndpointById(endpointId: String): Boolean {
        val snap = audioEndpointRouter.getSnapshot()
        val target = snap.availableEndpoints.firstOrNull { it.id == endpointId } ?: return false
        return selectAudioEndpoint(target)
    }

    fun selectAudioEndpointByType(type: CallAudioEndpointType): Boolean {
        val snap = audioEndpointRouter.getSnapshot()
        val target = snap.availableEndpoints.firstOrNull { it.type == type } ?: return false
        return selectAudioEndpoint(target)
    }

    fun toggleSpeakerphone() {
        audioEndpointRouter.toggleSpeakerSmart(service = inCallService)
    }

    /**
     * Enforces Speakerphone ON and unmutes the microphone so the Gujarati AI Call Assistant
     * can speak over the loudspeaker and attempt microphone capture.
     * Distinguishes between active Telecom calls (`MODE_IN_CALL` / `InCallService` active)
     * and standalone `MODE_NORMAL` voice pipeline testing.
     */
    @Suppress("DEPRECATION")
    fun ensureSpeakerphoneAndUnmutedForAi(context: Context? = null): Boolean {
        var routedViaTelecom = false
        val service = inCallService
        val hasActiveTelecomCall = service != null && activeCalls.isNotEmpty()

        if (service != null) {
            try {
                if (isMutedState) {
                    service.setMuted(false)
                    isMutedState = false
                }
                if (currentAudioRoute != CallAudioState.ROUTE_SPEAKER) {
                    service.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                    currentAudioRoute = CallAudioState.ROUTE_SPEAKER
                }
                routedViaTelecom = true
            } catch (_: Exception) {
            }
        }

        val ctx = context ?: service?.applicationContext
        if (ctx != null) {
            try {
                val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    audioManager.isMicrophoneMute = false
                    val isInCallMode = hasActiveTelecomCall ||
                        audioManager.mode == AudioManager.MODE_IN_CALL ||
                        audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

                    if (isInCallMode) {
                        audioManager.isSpeakerphoneOn = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                                    it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                                }
                                if (speakerDevice != null && audioManager.communicationDevice?.id != speakerDevice.id) {
                                    audioManager.setCommunicationDevice(speakerDevice)
                                }
                            } catch (_: Exception) {
                            }
                        }
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                        val targetMinVol = (maxVol * 0.75f).toInt().coerceAtLeast(1)
                        if (currentVol < targetMinVol) {
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_VOICE_CALL,
                                targetMinVol,
                                0
                            )
                        }
                    } else {
                        // In MODE_NORMAL (e.g., Settings diagnostic test), ensure media/TTS volume is audible
                        // without forcing telephony speakerphone mode that could disrupt normal AudioRecord capture.
                        val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val curMusicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val targetMusicVol = (maxMusicVol * 0.65f).toInt().coerceAtLeast(1)
                        if (curMusicVol < targetMusicVol) {
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                targetMusicVol,
                                0
                            )
                        }
                    }
                    routedViaTelecom = true
                }
            } catch (_: Exception) {
            }
        }

        updateProximitySensorRegistration()
        syncUiState()
        return routedViaTelecom
    }

    fun toggleBluetoothAudio() {
        audioEndpointRouter.toggleBluetoothSmart(service = inCallService)
    }

    fun toggleHold() {
        val call = selectPrimaryCall() ?: return
        val state = getCallStateCompat(call)
        val details = call.details
        val capabilities = details?.callCapabilities ?: 0
        val supportsHold = (capabilities and Call.Details.CAPABILITY_SUPPORT_HOLD) != 0 ||
            (capabilities and Call.Details.CAPABILITY_HOLD) != 0
        if (!supportsHold && state != Call.STATE_HOLDING) {
            showStatusMessage("Call hold is not supported by your mobile network for this call.")
            return
        }
        try {
            if (state == Call.STATE_HOLDING) {
                call.unhold()
            } else {
                call.hold()
            }
        } catch (e: Exception) {
            showStatusMessage("Could not change hold state: ${e.localizedMessage ?: "unsupported"}")
        }
    }

    fun requestVideoUpgrade() {
        val call = selectPrimaryCall() ?: return
        val capabilities = call.details?.callCapabilities ?: 0
        val supportsVideo =
            (capabilities and Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL) != 0 &&
                (capabilities and Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL) != 0
        if (!supportsVideo || call.videoCall == null) {
            showStatusMessage("Carrier video calling is not supported on this connection.")
            return
        }
        try {
            call.videoCall?.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_BIDIRECTIONAL))
            showStatusMessage("Requesting video call upgrade...")
        } catch (_: Exception) {
            showStatusMessage("Unable to switch to video call.")
        }
    }

    fun handleRecordButtonClick() {
        // Handled by ActiveCallScreen (opens CallRecordingConsentDialog or stops active CallRecordingManager session)
    }

    fun playDtmfTone(digit: Char) {
        val call = selectPrimaryCall()
        try {
            call?.playDtmfTone(digit)
            mainHandler.postDelayed({
                try {
                    call?.stopDtmfTone()
                } catch (_: Exception) {
                }
            }, 160L)
        } catch (_: Exception) {
        }
        playLocalKeyTone(digit)
    }

    fun playLocalKeyTone(digit: Char) {
        val toneType = when (digit) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> ToneGenerator.TONE_DTMF_0
        }
        scope.launch(Dispatchers.IO) {
            try {
                synchronized(this@CallStateManager) {
                    if (toneGenerator == null) {
                        toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 70)
                    }
                    toneGenerator?.startTone(toneType, 120)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun isDefaultDialer(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
                }
            }
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.defaultDialerPackage == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun createRoleRequestIntent(context: Context): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                }
            }
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Initiates a real phone call using SIM 1 via Android TelecomManager.
     * Never interferes with emergency calling behavior.
     */
    fun placeRealCall(
        context: Context,
        rawNumber: String,
        phoneAccountHandle: PhoneAccountHandle? = null,
        isVideo: Boolean = false,
        onNeedPermission: () -> Unit,
        onError: (String) -> Unit
    ) {
        val clean = rawNumber.trim()
        if (clean.isEmpty()) {
            onError("Enter a phone number to call.")
            return
        }

        val uri = Uri.fromParts("tel", clean, null)

        val isEmergency = try {
            PhoneNumberUtils.isEmergencyNumber(clean)
        } catch (_: Exception) {
            false
        }
        if (isEmergency) {
            try {
                val emergencyIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(emergencyIntent)
                return
            } catch (e: Exception) {
                onError("Unable to launch emergency dialer: ${e.localizedMessage ?: "error"}")
                return
            }
        }

        val hasCallPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCallPhone) {
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (telecomManager != null) {
                    val sim1Handle = phoneAccountHandle ?: try {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_PHONE_STATE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            telecomManager.callCapablePhoneAccounts.firstOrNull()
                        } else null
                    } catch (_: Exception) {
                        null
                    }

                    val extras = Bundle().apply {
                        if (sim1Handle != null) {
                            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim1Handle)
                        }
                        if (isVideo) {
                            putInt(
                                TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                                VideoProfile.STATE_BIDIRECTIONAL
                            )
                        }
                    }
                    telecomManager.placeCall(uri, extras)
                    presentOptimisticOutgoingCallUi(context, clean, isVideo)
                    return
                }
            } catch (se: SecurityException) {
                onNeedPermission()
                return
            } catch (e: Exception) {
                try {
                    val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(callIntent)
                    presentOptimisticOutgoingCallUi(context, clean, isVideo)
                    return
                } catch (_: Exception) {
                    onError("Unable to place call: ${e.localizedMessage ?: "Check SIM 1 status"}")
                    return
                }
            }
        } else {
            onNeedPermission()
        }
    }

    private fun presentOptimisticOutgoingCallUi(context: Context, cleanNumber: String, isVideo: Boolean) {
        try {
            val appCtx = context.applicationContext
            if (cleanNumber.isNotBlank() && !callerCache.containsKey(cleanNumber)) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val repo = DialerRepository(appCtx)
                        val info = lookupCallerInfo(appCtx, cleanNumber, repo)
                        callerCache[cleanNumber] = info
                        mainHandler.post {
                            if (activeCalls.isNotEmpty()) {
                                syncUiState()
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
            _isBannerDismissedBySwipe.value = false
            _hasUserMinimizedCurrentCall.value = false
            _incomingPresentationMode.value = IncomingCallPresentationMode.FULL_SCREEN
            launchFullScreenInCallActivityIntent(context, isRinging = false)
        } catch (_: Exception) {
        }
    }

    fun openSmsComposer(context: Context, rawNumber: String, body: String = "") {
        val clean = rawNumber.trim()
        if (clean.isEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(clean)}")).apply {
                if (body.isNotBlank()) {
                    putExtra("sms_body", body)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun launchVideoCall(
        context: Context,
        rawNumber: String,
        onNeedCallPermission: () -> Unit,
        onMessage: (String) -> Unit
    ) {
        val clean = rawNumber.trim()
        if (clean.isEmpty()) {
            onMessage("No phone number available for video call.")
            return
        }
        try {
            val duoIntent = Intent("com.google.android.apps.tachyon.action.CALL").apply {
                data = Uri.parse("tel:$clean")
                setPackage("com.google.android.apps.tachyon")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (duoIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(duoIntent)
                return
            }
        } catch (_: Exception) {
        }

        try {
            val hasPhoneState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPhoneState) {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val accounts = tm?.callCapablePhoneAccounts.orEmpty()
                val videoSupported = accounts.any { handle ->
                    val acc = tm?.getPhoneAccount(handle)
                    (acc?.capabilities ?: 0) and android.telecom.PhoneAccount.CAPABILITY_VIDEO_CALLING != 0
                }
                if (videoSupported) {
                    placeRealCall(
                        context = context,
                        rawNumber = clean,
                        isVideo = true,
                        onNeedPermission = onNeedCallPermission,
                        onError = onMessage
                    )
                    return
                }
            }
        } catch (_: Exception) {
        }

        onMessage("Video calling is unavailable: neither carrier ViLTE nor a supported video-calling app was found on this device.")
    }

    private fun lookupCallerInfo(
        context: Context,
        rawNumber: String,
        repository: DialerRepository
    ): CallerResolvedMeta {
        var displayName: String? = null
        var location: String? = null
        var photoUri: String? = null
        var thumbnailPhotoUri: String? = null
        var contactId: Long? = null
        val isBlocked = try {
            repository.isNumberBlockedSync(rawNumber)
        } catch (_: Throwable) {
            false
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(rawNumber)
                )
                context.contentResolver.query(
                    lookupUri,
                    arrayOf(
                        ContactsContract.PhoneLookup._ID,
                        ContactsContract.PhoneLookup.CONTACT_ID,
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_URI,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                        val contactIdIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                        val thumbIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)

                        val resolvedContactId = if (contactIdIdx >= 0 && !cursor.isNull(contactIdIdx)) {
                            cursor.getLong(contactIdIdx).takeIf { it > 0L }
                        } else {
                            null
                        } ?: if (idIdx >= 0 && !cursor.isNull(idIdx)) {
                            cursor.getLong(idIdx).takeIf { it > 0L }
                        } else {
                            null
                        }

                        contactId = resolvedContactId
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }
                        }
                        if (photoIdx >= 0) {
                            photoUri = cursor.getString(photoIdx)?.takeIf { it.isNotBlank() }
                        }
                        if (thumbIdx >= 0) {
                            thumbnailPhotoUri = cursor.getString(thumbIdx)?.takeIf { it.isNotBlank() }
                        }
                    }
                }

                // Fallback to normalized phone matching on CommonDataKinds.Phone if PhoneLookup filter
                // didn't resolve a contact or high-resolution photo URI.
                if (displayName == null || (photoUri == null && thumbnailPhotoUri == null)) {
                    val targetKey = repository.normalizePhoneKey(rawNumber)
                    if (targetKey.isNotEmpty()) {
                        context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            ),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            val cIdIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                            val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                            val thumbIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                            while (cursor.moveToNext()) {
                                val num = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                                if (repository.normalizePhoneKey(num) == targetKey) {
                                    if (contactId == null && cIdIdx >= 0 && !cursor.isNull(cIdIdx)) {
                                        contactId = cursor.getLong(cIdIdx).takeIf { it > 0L }
                                    }
                                    if (displayName == null && nameIdx >= 0) {
                                        displayName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }
                                    }
                                    if (photoUri == null && photoIdx >= 0) {
                                        photoUri = cursor.getString(photoIdx)?.takeIf { it.isNotBlank() }
                                    }
                                    if (thumbnailPhotoUri == null && thumbIdx >= 0) {
                                        thumbnailPhotoUri = cursor.getString(thumbIdx)?.takeIf { it.isNotBlank() }
                                    }
                                    if (displayName != null && (photoUri != null || thumbnailPhotoUri != null)) {
                                        break
                                    }
                                }
                            }
                        }
                    }
                }

                // If PHOTO_URI was not directly populated on the cursor, check Contacts table PHOTO_URI
                // or high-res Contacts.Photo.DISPLAY_PHOTO stream before falling back to thumbnail.
                if (photoUri == null && contactId != null) {
                    val cId = contactId!!
                    context.contentResolver.query(
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cId),
                        arrayOf(
                            ContactsContract.Contacts.PHOTO_URI,
                            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val pIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                            val tIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                            if (pIdx >= 0) {
                                photoUri = cursor.getString(pIdx)?.takeIf { it.isNotBlank() }
                            }
                            if (thumbnailPhotoUri == null && tIdx >= 0) {
                                thumbnailPhotoUri = cursor.getString(tIdx)?.takeIf { it.isNotBlank() }
                            }
                        }
                    }

                    if (photoUri == null) {
                        val displayPhotoUri = Uri.withAppendedPath(
                            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cId),
                            ContactsContract.Contacts.Photo.DISPLAY_PHOTO
                        )
                        val hasDisplayPhotoStream = try {
                            context.contentResolver.openAssetFileDescriptor(displayPhotoUri, "r")?.use {
                                true
                            } ?: false
                        } catch (_: Exception) {
                            false
                        }
                        if (hasDisplayPhotoStream) {
                            photoUri = displayPhotoUri.toString()
                        }
                    }
                }

                if (photoUri == null && thumbnailPhotoUri != null) {
                    photoUri = thumbnailPhotoUri
                }

                if (contactId != null) {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.StructuredPostal.REGION,
                            ContactsContract.CommonDataKinds.StructuredPostal.CITY,
                            ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS
                        ),
                        "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?",
                        arrayOf(contactId.toString()),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val region = cursor.getString(0).orEmpty().trim()
                            val city = cursor.getString(1).orEmpty().trim()
                            val formatted = cursor.getString(2).orEmpty().trim()
                            location = region.ifBlank { city }.ifBlank { formatted }.takeIf { it.isNotBlank() }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if ((location == null || displayName == null || photoUri == null) &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.GEOCODED_LOCATION,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.CACHED_PHOTO_URI
                    ),
                    "${CallLog.Calls.NUMBER} = ?",
                    arrayOf(rawNumber),
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        if (location == null) {
                            location = cursor.getString(0)?.takeIf { it.isNotBlank() }
                        }
                        if (displayName == null) {
                            displayName = cursor.getString(1)?.takeIf { it.isNotBlank() }
                        }
                        if (photoUri == null) {
                            photoUri = cursor.getString(2)?.takeIf { it.isNotBlank() }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return CallerResolvedMeta(
            displayName = displayName,
            location = location,
            photoUri = photoUri,
            isBlocked = isBlocked
        )
    }
}

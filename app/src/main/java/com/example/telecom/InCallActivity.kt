package com.example.telecom

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.data.DialerRepository
import com.example.data.PersonalizationManager
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.IncomingCallPresentationMode
import com.example.ui.screens.IncomingCallScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INITIAL_INCOMING_MODE = "extra_initial_incoming_mode"
        const val EXTRA_COMPACT_INCOMING_ONLY = "extra_compact_incoming_only"
        const val EXTRA_FORCE_FULL_ACTIVE_UI = "extra_force_full_active_ui"
    }

    private var compactIncomingOnly by mutableStateOf(false)
    private var forceFullActiveUi by mutableStateOf(true)
    private var lastResumedElapsedMs: Long = 0L
    private var userExplicitlyLeftActivity: Boolean = false
    private val activityInstanceToken: Int = System.identityHashCode(this)

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseAppCheckHelper.configureAppCheck(this, intent)
        PersonalizationManager.ensureInitialized(applicationContext)
        configureLockscreenPresentation()
        parseWindowModeFromIntent(intent)

        setContent {
            val repository = remember { DialerRepository(applicationContext) }
            val appliedPersonalization by PersonalizationManager.appliedConfig.collectAsStateWithLifecycle()

            MyApplicationTheme(
                themeMode = appliedPersonalization.themeMode,
                personalization = appliedPersonalization
            ) {
                val activeCall by CallStateManager.activeCallUiState.collectAsStateWithLifecycle()
                val incomingMode by CallStateManager.incomingPresentationMode.collectAsStateWithLifecycle()
                val isBannerDismissed by CallStateManager.isBannerDismissedBySwipe.collectAsStateWithLifecycle()
                val recordingState by CallRecordingManager.sessionState.collectAsStateWithLifecycle()
                val aiState by AiCallAssistantManager.uiState.collectAsStateWithLifecycle()
                var hasObservedActiveCall by remember { mutableStateOf(activeCall != null) }

                LaunchedEffect(activeCall) {
                    if (activeCall != null) {
                        hasObservedActiveCall = true
                    }
                }

                LaunchedEffect(activeCall, recordingState, aiState, isBannerDismissed) {
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
                            aiState = aiState,
                            isBannerDismissedBySwipe = isBannerDismissed,
                            elapsedCallSeconds = elapsedSec,
                            isFullScreenCallUiVisible = true
                        )
                        if (call == null) break
                        delay(500L)
                    }
                }

                LaunchedEffect(
                    activeCall,
                    incomingMode,
                    isBannerDismissed,
                    compactIncomingOnly,
                    forceFullActiveUi,
                    hasObservedActiveCall
                ) {
                    val call = activeCall
                    if (call == null) {
                        if (hasObservedActiveCall) {
                            dismissWithoutAnimation()
                        } else {
                            // Allow sufficient grace window when launched immediately upon placing an outgoing call
                            // before InCallService.onCallAdded delivers the real Telecom Call object.
                            delay(3500L)
                            if (CallStateManager.activeCallUiState.value == null) {
                                dismissWithoutAnimation()
                            }
                        }
                    } else if (call.isRinging) {
                        // Do not display HET Phone's custom full-screen incoming call interface;
                        // incoming calls use only Android's native heads-up notification.
                        dismissWithoutAnimation()
                    } else {
                        // Outgoing dialing, connecting, active, or holding call:
                        // ALWAYS display the Full-Screen Active Call Screen.
                        if (compactIncomingOnly || !forceFullActiveUi) {
                            compactIncomingOnly = false
                            forceFullActiveUi = true
                        }
                        configureFullScreenCallWindow()
                    }
                }

                val currentCall = activeCall
                if (currentCall != null && !currentCall.isRinging) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        run {
                            // Minimizing InCallActivity via system Back button closes the Full-Screen Call UI
                            // while keeping the call active, immediately restoring the Call Smart Island.
                            BackHandler(enabled = true) {
                                userExplicitlyLeftActivity = true
                                CallStateManager.markCallUiMinimizedByUser()
                                dismissWithoutAnimation()
                            }

                            val savedNote = remember(currentCall.number) {
                                repository.loadCallNote(currentCall.number)
                            }
                            ActiveCallScreen(
                                callState = currentCall,
                                initialNote = savedNote,
                                onSaveNote = { note ->
                                    repository.saveCallNote(currentCall.number, note)
                                    CallStateManager.showStatusMessage(
                                        "Note saved for ${currentCall.displayName ?: currentCall.number}"
                                    )
                                },
                                onToggleMute = { CallStateManager.toggleMute() },
                                onToggleSpeaker = { CallStateManager.toggleSpeakerphone() },
                                onToggleBluetooth = { CallStateManager.toggleBluetoothAudio() },
                                onSelectAudioEndpoint = { endpoint ->
                                    CallStateManager.selectAudioEndpoint(endpoint)
                                },
                                onToggleHold = { CallStateManager.toggleHold() },
                                onAddCall = {
                                    userExplicitlyLeftActivity = true
                                    CallStateManager.markCallUiMinimizedByUser()
                                    CallStateManager.setInCallActivityVisible(false, activityInstanceToken)
                                    val dialIntent = Intent(this@InCallActivity, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(dialIntent)
                                    finish()
                                },
                                onRequestVideo = { CallStateManager.requestVideoUpgrade() },
                                onRecordClick = { CallStateManager.handleRecordButtonClick() },
                                onPlayDtmf = { digit -> CallStateManager.playDtmfTone(digit) },
                                onEndCall = {
                                    CallStateManager.rejectOrDisconnectCall(this@InCallActivity)
                                    dismissWithoutAnimation()
                                },
                                onAcceptWaitingCall = {
                                    CallStateManager.answerRingingCall(this@InCallActivity)
                                },
                                onDeclineWaitingCall = {
                                    CallStateManager.declineWaitingCall()
                                },
                                onSwapCalls = {
                                    CallStateManager.swapCalls()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        userExplicitlyLeftActivity = false
        if (lastResumedElapsedMs <= 0L) {
            lastResumedElapsedMs = SystemClock.elapsedRealtime()
        }
        CallStateManager.setInCallActivityVisible(true, activityInstanceToken)
    }

    override fun onResume() {
        super.onResume()
        userExplicitlyLeftActivity = false
        lastResumedElapsedMs = SystemClock.elapsedRealtime()
        CallStateManager.setInCallActivityVisible(true, activityInstanceToken)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (lastResumedElapsedMs <= 0L) {
                lastResumedElapsedMs = SystemClock.elapsedRealtime()
            }
            CallStateManager.setInCallActivityVisible(true, activityInstanceToken)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userExplicitlyLeftActivity = true
        if (CallStateManager.activeCallUiState.value != null) {
            CallStateManager.markCallUiMinimizedByUser()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing && !isChangingConfigurations) {
            CallStateManager.setInCallActivityVisible(false, activityInstanceToken)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val visibleDurationMs = if (lastResumedElapsedMs > 0L) {
                SystemClock.elapsedRealtime() - lastResumedElapsedMs
            } else {
                0L
            }
            if ((userExplicitlyLeftActivity || visibleDurationMs >= 800L) &&
                CallStateManager.activeCallUiState.value != null
            ) {
                CallStateManager.markCallUiMinimizedByUser()
            }
            lastResumedElapsedMs = 0L
            CallStateManager.setInCallActivityVisible(false, activityInstanceToken)
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            lastResumedElapsedMs = 0L
            CallStateManager.setInCallActivityVisible(false, activityInstanceToken)
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        userExplicitlyLeftActivity = false
        configureLockscreenPresentation()
        parseWindowModeFromIntent(intent)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            CallStateManager.setInCallActivityVisible(true, activityInstanceToken)
        }
    }

    private fun parseWindowModeFromIntent(intent: Intent?) {
        val currentCall = CallStateManager.activeCallUiState.value
        val isCurrentlyRinging = currentCall?.isRinging == true
        val initialModeExtra = intent?.getStringExtra(EXTRA_INITIAL_INCOMING_MODE)
        val explicitCompactOnly = intent?.getBooleanExtra(EXTRA_COMPACT_INCOMING_ONLY, false) == true

        if (!isCurrentlyRinging && currentCall != null) {
            // Whenever a call is active, dialing, connecting, or holding, always present full-screen ActiveCallScreen
            forceFullActiveUi = true
            compactIncomingOnly = false
            configureFullScreenCallWindow()
            return
        }

        if (explicitCompactOnly && initialModeExtra == IncomingCallPresentationMode.COMPACT_BANNER.name && isCurrentlyRinging) {
            CallStateManager.setIncomingPresentationMode(IncomingCallPresentationMode.COMPACT_BANNER)
            forceFullActiveUi = false
            compactIncomingOnly = true
            configureCompactTopCardWindow()
            return
        }

        // Default for all incoming and outgoing calls: Full-Screen Call UI
        CallStateManager.setIncomingPresentationMode(IncomingCallPresentationMode.FULL_SCREEN)
        forceFullActiveUi = true
        compactIncomingOnly = false
        configureFullScreenCallWindow()
    }

    /**
     * Configures the window as a non-modal, non-focus-stealing, top-aligned compact card
     * when explicitly requested.
     */
    private fun configureCompactTopCardWindow() {
        try {
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setGravity(Gravity.TOP)
        } catch (_: Exception) {
        }
    }

    /**
     * Restores full-screen window attributes when displaying the full-screen incoming call UI
     * or the outgoing/active call screen.
     */
    private fun configureFullScreenCallWindow() {
        try {
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            window.setGravity(Gravity.FILL)
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun dismissWithoutAnimation() {
        lastResumedElapsedMs = 0L
        CallStateManager.setInCallActivityVisible(false, activityInstanceToken)
        finish()
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    private fun configureLockscreenPresentation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }
}

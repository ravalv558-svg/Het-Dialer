package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.telecom.Call
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.ActiveCallUiState
import com.example.model.CallAudioEndpointType
import com.example.model.WaveformAccentStyle
import com.example.ui.theme.LocalPersonalizationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Legitimate incoming remote-audio source status for cellular calls on Android.
 *
 * Architectural note:
 * Standard third-party Android dialer apps (`InCallService` with `ROLE_DIALER`) manage call signaling
 * and Telecom audio endpoint routing (`CallEndpoint`), while cellular voice streams are routed by
 * the baseband and Audio HAL (`AUDIO_MODE_IN_CALL`).
 * Accessing strictly the remote party's incoming cellular voice (`MediaRecorder.AudioSource.VOICE_DOWNLINK`)
 * requires the signature/privileged permission `android.permission.CAPTURE_AUDIO_OUTPUT` and HAL support.
 * When unavailable, this controller never falls back to the local microphone (`MIC` / `VOICE_COMMUNICATION`)
 * and never generates random/fake audio levels.
 */
enum class IncomingVoiceLevelSourceStatus(
    val isRemoteAudioCaptureAvailable: Boolean,
    val diagnosticMessage: String
) {
    INACTIVE_NO_ACTIVE_CALL(
        isRemoteAudioCaptureAvailable = false,
        diagnosticMessage = "Waveform idle: no connected active call."
    ),
    ACTIVE_VOICE_DOWNLINK_CAPTURE(
        isRemoteAudioCaptureAvailable = true,
        diagnosticMessage = "Connected to legitimate Android VOICE_DOWNLINK incoming call audio stream."
    ),
    READY_EXTERNAL_TELECOM_FEED(
        isRemoteAudioCaptureAvailable = true,
        diagnosticMessage = "Receiving real-time incoming remote-party voice amplitude from supported Telecom/IMS feed."
    ),
    UNAVAILABLE_PRIVILEGED_DOWNLINK_REQUIRED(
        isRemoteAudioCaptureAvailable = false,
        diagnosticMessage = "Android restricts remote cellular call audio (VOICE_DOWNLINK) to privileged system apps holding CAPTURE_AUDIO_OUTPUT. Local microphone and random simulation are disabled; UI remains ready for supported Telecom/downlink integration."
    ),
    UNAVAILABLE_HAL_DOWNLINK_BLOCKED(
        isRemoteAudioCaptureAvailable = false,
        diagnosticMessage = "Device Audio HAL blocked VOICE_DOWNLINK initialization during cellular call. Waveform remains in calm stationary state without fake levels."
    )
}

/**
 * Immutable UI state for the Live Incoming Voice Waveform on the ongoing call screen.
 */
data class LiveVoiceWaveformUiState(
    val isCallActive: Boolean = false,
    val callId: String = "",
    val isLocalMicMuted: Boolean = false,
    val activeAudioEndpointType: CallAudioEndpointType = CallAudioEndpointType.EARPIECE,
    val sourceStatus: IncomingVoiceLevelSourceStatus = IncomingVoiceLevelSourceStatus.INACTIVE_NO_ACTIVE_CALL,
    val rawIncomingAmplitude: Float = 0f,
    val smoothedIncomingAmplitude: Float = 0f,
    val barFractions: List<Float> = LiveIncomingVoiceWaveformController.calmStationaryBarProfile()
) {
    /**
     * True when the remote party's incoming speech is actively driving the waveform above silence threshold.
     */
    val isRemoteVoiceSpeaking: Boolean
        get() = isCallActive && smoothedIncomingAmplitude > LiveIncomingVoiceWaveformController.SILENCE_THRESHOLD

    /**
     * Peak bar height fraction (in `0f..1f`) across all bars in the current frame.
     */
    val peakBarFraction: Float
        get() = barFractions.maxOrNull() ?: LiveIncomingVoiceWaveformController.MIN_CALM_BAR_FRACTION

    /**
     * Average bar height fraction (in `0f..1f`) across all bars in the current frame.
     */
    val averageBarFraction: Float
        get() = if (barFractions.isEmpty()) {
            LiveIncomingVoiceWaveformController.MIN_CALM_BAR_FRACTION
        } else {
            barFractions.sum() / barFractions.size.toFloat()
        }
}

/**
 * Real-time controller for the ongoing call screen's **Live Voice Waveform**.
 *
 * Guarantees:
 * 1. Reacts **ONLY** to the remote party's incoming voice (`VOICE_DOWNLINK` when privileged access
 *    is legitimately granted, or a supported Telecom/IMS remote-party audio level feed).
 * 2. **Never** reacts to local microphone input or the user's own voice.
 * 3. **Never** uses random or simulated audio levels.
 * 4. **Never** records, buffers, saves, or transmits call audio (computes instantaneous RMS in-place).
 * 5. **Never** interferes with Bluetooth, Speaker, Earpiece, or Wired Headset audio routing.
 * 6. Stops immediately when the call disconnects or the ongoing call screen is disposed.
 */
object LiveIncomingVoiceWaveformController {

    const val BAR_COUNT: Int = 25
    const val MIN_CALM_BAR_FRACTION: Float = 0.075f
    const val MAX_CALM_BAR_FRACTION: Float = 0.110f
    const val SILENCE_THRESHOLD: Float = 0.015f

    private const val SAMPLE_RATE_HZ = 8000
    private const val ATTACK_SMOOTHING = 0.46f
    private const val DECAY_SMOOTHING = 0.24f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var downlinkCaptureJob: Job? = null
    private var activeAudioRecord: AudioRecord? = null

    // Ring buffer of the last N real incoming amplitude samples (strictly for smooth multi-bar envelope)
    private val amplitudeHistory = FloatArray(BAR_COUNT) { 0f }
    private var historyWriteIndex: Int = 0

    // Pre-computed bell-curve spatial weights so center bars respond tallest and outer bars taper smoothly
    private val spatialEnvelopeWeights: FloatArray = FloatArray(BAR_COUNT) { index ->
        val center = (BAR_COUNT - 1) / 2f
        val normalizedDist = abs(index - center) / center // 0f at center, 1f at edges
        val cosineBell = 0.5f * (1f + cos(normalizedDist * Math.PI)).toFloat()
        // Subtle harmonic ribbing across adjacent bars for a premium acoustic appearance
        val ribFactor = when (index % 3) {
            0 -> 1.00f
            1 -> 0.86f
            else -> 0.93f
        }
        (0.28f + 0.72f * cosineBell) * ribFactor
    }

    // Calm, nearly stationary resting profile when the remote party is silent or capture is unavailable
    private val calmProfile: List<Float> = List(BAR_COUNT) { index ->
        val center = (BAR_COUNT - 1) / 2f
        val normalizedDist = abs(index - center) / center
        val gentleArc = 1f - (normalizedDist * normalizedDist * 0.65f)
        (MIN_CALM_BAR_FRACTION + (MAX_CALM_BAR_FRACTION - MIN_CALM_BAR_FRACTION) * gentleArc)
            .coerceIn(MIN_CALM_BAR_FRACTION, MAX_CALM_BAR_FRACTION)
    }

    private val _uiState = MutableStateFlow(LiveVoiceWaveformUiState())
    val uiState: StateFlow<LiveVoiceWaveformUiState> = _uiState.asStateFlow()

    fun calmStationaryBarProfile(): List<Float> = calmProfile

    /**
     * Inspects whether the current Android runtime legitimately grants access to the remote party's
     * cellular call audio (`VOICE_DOWNLINK` requires both `RECORD_AUDIO` and privileged `CAPTURE_AUDIO_OUTPUT`).
     */
    fun canCapturePrivilegedVoiceDownlink(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val hasRecordAudio = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val hasCaptureAudioOutput = ContextCompat.checkSelfPermission(
                context,
                "android.permission.CAPTURE_AUDIO_OUTPUT"
            ) == PackageManager.PERMISSION_GRANTED
            hasRecordAudio && hasCaptureAudioOutput
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Synchronizes the waveform controller with the current [ActiveCallUiState].
     * Automatically starts legitimate downlink monitoring if `state == Call.STATE_ACTIVE` and
     * stops immediately when the call disconnects or leaves the active state.
     */
    @Synchronized
    fun onCallStateChanged(context: Context?, callState: ActiveCallUiState?) {
        if (callState == null || callState.state != Call.STATE_ACTIVE) {
            stopMonitoring()
            return
        }

        val current = _uiState.value
        val activeEndpointType = when {
            callState.isBluetoothOn ||
                callState.activeAudioEndpoint.type == CallAudioEndpointType.BLUETOOTH ->
                CallAudioEndpointType.BLUETOOTH
            callState.isSpeakerOn ||
                callState.activeAudioEndpoint.type == CallAudioEndpointType.SPEAKER ->
                CallAudioEndpointType.SPEAKER
            callState.activeAudioEndpoint.type == CallAudioEndpointType.WIRED_HEADSET ->
                CallAudioEndpointType.WIRED_HEADSET
            else -> CallAudioEndpointType.EARPIECE
        }

        val isSameActiveCall = current.isCallActive && current.callId == callState.callId
        if (isSameActiveCall) {
            // Preserve ongoing amplitude state across Mute toggles and Bluetooth/Speaker/Earpiece routing changes
            _uiState.update { state ->
                state.copy(
                    isLocalMicMuted = callState.isMuted,
                    activeAudioEndpointType = activeEndpointType
                )
            }
            return
        }

        // New active call connected -> reset history and check legitimate VOICE_DOWNLINK availability
        clearHistoryBuffer()
        val hasPrivilegedDownlink = canCapturePrivilegedVoiceDownlink(context)
        if (hasPrivilegedDownlink && context != null) {
            startPrivilegedVoiceDownlinkCapture(
                callId = callState.callId,
                isLocalMicMuted = callState.isMuted,
                activeEndpointType = activeEndpointType
            )
        } else {
            stopDownlinkAudioRecordInternal()
            _uiState.value = LiveVoiceWaveformUiState(
                isCallActive = true,
                callId = callState.callId,
                isLocalMicMuted = callState.isMuted,
                activeAudioEndpointType = activeEndpointType,
                sourceStatus = IncomingVoiceLevelSourceStatus.UNAVAILABLE_PRIVILEGED_DOWNLINK_REQUIRED,
                rawIncomingAmplitude = 0f,
                smoothedIncomingAmplitude = 0f,
                barFractions = calmProfile
            )
        }
    }

    /**
     * Feeds a real-time incoming remote-party voice amplitude sample (`0.0f` = silence, `1.0f` = loud speech)
     * from a supported Telecom/IMS remote audio level callback or verified `VOICE_DOWNLINK` stream.
     *
     * Note: Local microphone mute state (`isLocalMicMuted`) is tracked for metadata only and never suppresses
     * or alters incoming remote-party voice amplitude.
     */
    @Synchronized
    fun onIncomingRemoteAudioLevel(
        remoteVoiceLevel: Float,
        isCallActive: Boolean = _uiState.value.isCallActive,
        callId: String = _uiState.value.callId,
        isLocalMicMuted: Boolean = _uiState.value.isLocalMicMuted,
        activeEndpointType: CallAudioEndpointType = _uiState.value.activeAudioEndpointType,
        sourceStatus: IncomingVoiceLevelSourceStatus = IncomingVoiceLevelSourceStatus.READY_EXTERNAL_TELECOM_FEED
    ) {
        if (!isCallActive) {
            stopMonitoring()
            return
        }

        val clampedRaw = if (remoteVoiceLevel.isNaN()) 0f else remoteVoiceLevel.coerceIn(0f, 1f)
        val previousSmoothed = _uiState.value.smoothedIncomingAmplitude
        val smoothingFactor = if (clampedRaw >= previousSmoothed) ATTACK_SMOOTHING else DECAY_SMOOTHING
        val nextSmoothed = run {
            val blended = previousSmoothed + (clampedRaw - previousSmoothed) * smoothingFactor
            if (clampedRaw <= 0.005f && blended < SILENCE_THRESHOLD) 0f else blended.coerceIn(0f, 1f)
        }

        pushAmplitudeHistorySample(nextSmoothed)
        val updatedBars = computeBarFractions(
            currentSmoothed = nextSmoothed,
            previousBars = _uiState.value.barFractions
        )

        _uiState.value = LiveVoiceWaveformUiState(
            isCallActive = true,
            callId = callId,
            isLocalMicMuted = isLocalMicMuted,
            activeAudioEndpointType = activeEndpointType,
            sourceStatus = sourceStatus,
            rawIncomingAmplitude = clampedRaw,
            smoothedIncomingAmplitude = nextSmoothed,
            barFractions = updatedBars
        )
    }

    /**
     * Advances silence settling by [steps] frames when the remote party stops speaking.
     * Gradually settles the waveform into its calm, nearly stationary state.
     */
    @Synchronized
    fun advanceSilenceFrames(steps: Int = 1) {
        val safeSteps = steps.coerceAtLeast(1)
        repeat(safeSteps) {
            if (!_uiState.value.isCallActive) return
            onIncomingRemoteAudioLevel(
                remoteVoiceLevel = 0f,
                isCallActive = _uiState.value.isCallActive,
                callId = _uiState.value.callId,
                isLocalMicMuted = _uiState.value.isLocalMicMuted,
                activeEndpointType = _uiState.value.activeAudioEndpointType,
                sourceStatus = _uiState.value.sourceStatus
            )
        }
    }

    /**
     * Explicitly ignores local microphone / user's own voice levels so the waveform never reacts
     * to the local user speaking.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onLocalMicrophoneInputIgnored(localMicLevel: Float) {
        // Architectural guarantee: local microphone amplitude is strictly ignored.
    }

    /**
     * Immediately stops all waveform processing and resets the state to idle when the call disconnects
     * or the ongoing call screen is destroyed.
     */
    @Synchronized
    fun stopMonitoring() {
        stopDownlinkAudioRecordInternal()
        clearHistoryBuffer()
        _uiState.value = LiveVoiceWaveformUiState(
            isCallActive = false,
            callId = "",
            isLocalMicMuted = false,
            activeAudioEndpointType = CallAudioEndpointType.EARPIECE,
            sourceStatus = IncomingVoiceLevelSourceStatus.INACTIVE_NO_ACTIVE_CALL,
            rawIncomingAmplitude = 0f,
            smoothedIncomingAmplitude = 0f,
            barFractions = calmProfile
        )
    }

    private fun startPrivilegedVoiceDownlinkCapture(
        callId: String,
        isLocalMicMuted: Boolean,
        activeEndpointType: CallAudioEndpointType
    ) {
        stopDownlinkAudioRecordInternal()
        val minBufferBytes = try {
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        } catch (_: Throwable) {
            AudioRecord.ERROR_BAD_VALUE
        }

        if (minBufferBytes <= 0) {
            _uiState.value = LiveVoiceWaveformUiState(
                isCallActive = true,
                callId = callId,
                isLocalMicMuted = isLocalMicMuted,
                activeAudioEndpointType = activeEndpointType,
                sourceStatus = IncomingVoiceLevelSourceStatus.UNAVAILABLE_HAL_DOWNLINK_BLOCKED,
                rawIncomingAmplitude = 0f,
                smoothedIncomingAmplitude = 0f,
                barFractions = calmProfile
            )
            return
        }

        val record = try {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_DOWNLINK,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBufferBytes, 640)
            )
        } catch (_: Throwable) {
            null
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            try {
                record?.release()
            } catch (_: Throwable) {
            }
            _uiState.value = LiveVoiceWaveformUiState(
                isCallActive = true,
                callId = callId,
                isLocalMicMuted = isLocalMicMuted,
                activeAudioEndpointType = activeEndpointType,
                sourceStatus = IncomingVoiceLevelSourceStatus.UNAVAILABLE_HAL_DOWNLINK_BLOCKED,
                rawIncomingAmplitude = 0f,
                smoothedIncomingAmplitude = 0f,
                barFractions = calmProfile
            )
            return
        }

        activeAudioRecord = record
        _uiState.value = LiveVoiceWaveformUiState(
            isCallActive = true,
            callId = callId,
            isLocalMicMuted = isLocalMicMuted,
            activeAudioEndpointType = activeEndpointType,
            sourceStatus = IncomingVoiceLevelSourceStatus.ACTIVE_VOICE_DOWNLINK_CAPTURE,
            rawIncomingAmplitude = 0f,
            smoothedIncomingAmplitude = 0f,
            barFractions = calmProfile
        )

        downlinkCaptureJob = scope.launch {
            val pcmScratchBuffer = ShortArray(320) // 40ms @ 8kHz mono; overwritten in-place, never stored
            try {
                record.startRecording()
                while (isActive && _uiState.value.isCallActive) {
                    val readCount = record.read(pcmScratchBuffer, 0, pcmScratchBuffer.size)
                    if (readCount > 0) {
                        var sumSquares = 0.0
                        var peakSample = 0
                        for (i in 0 until readCount) {
                            val sample = pcmScratchBuffer[i].toInt()
                            val absSample = abs(sample)
                            if (absSample > peakSample) peakSample = absSample
                            sumSquares += sample.toDouble() * sample.toDouble()
                            // Zero out sample immediately so audio is never retained in memory
                            pcmScratchBuffer[i] = 0
                        }
                        val rms = sqrt(sumSquares / readCount.toDouble()).toFloat()
                        // Normalize conversational speech RMS/peak into 0f..1f
                        val normalizedRms = (rms / 6500f).coerceIn(0f, 1f)
                        val normalizedPeak = (peakSample / 18000f).coerceIn(0f, 1f)
                        val combinedLevel = (normalizedRms * 0.72f + normalizedPeak * 0.28f)
                            .let { level -> if (level < 0.018f) 0f else level.coerceIn(0f, 1f) }

                        onIncomingRemoteAudioLevel(
                            remoteVoiceLevel = combinedLevel,
                            isCallActive = _uiState.value.isCallActive,
                            callId = _uiState.value.callId,
                            isLocalMicMuted = _uiState.value.isLocalMicMuted,
                            activeEndpointType = _uiState.value.activeAudioEndpointType,
                            sourceStatus = IncomingVoiceLevelSourceStatus.ACTIVE_VOICE_DOWNLINK_CAPTURE
                        )
                    } else if (readCount < 0) {
                        break
                    }
                    delay(40L)
                }
            } catch (_: Throwable) {
                if (_uiState.value.isCallActive) {
                    _uiState.update { state ->
                        state.copy(
                            sourceStatus = IncomingVoiceLevelSourceStatus.UNAVAILABLE_HAL_DOWNLINK_BLOCKED,
                            rawIncomingAmplitude = 0f,
                            smoothedIncomingAmplitude = 0f,
                            barFractions = calmProfile
                        )
                    }
                }
            } finally {
                try {
                    record.stop()
                } catch (_: Throwable) {
                }
                try {
                    record.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun stopDownlinkAudioRecordInternal() {
        downlinkCaptureJob?.cancel()
        downlinkCaptureJob = null
        val record = activeAudioRecord
        activeAudioRecord = null
        if (record != null) {
            try {
                record.stop()
            } catch (_: Throwable) {
            }
            try {
                record.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun clearHistoryBuffer() {
        for (i in amplitudeHistory.indices) {
            amplitudeHistory[i] = 0f
        }
        historyWriteIndex = 0
    }

    private fun pushAmplitudeHistorySample(sample: Float) {
        amplitudeHistory[historyWriteIndex] = sample
        historyWriteIndex = (historyWriteIndex + 1) % amplitudeHistory.size
    }

    private fun computeBarFractions(
        currentSmoothed: Float,
        previousBars: List<Float>
    ): List<Float> {
        if (currentSmoothed <= SILENCE_THRESHOLD) {
            // Smoothly settle each bar toward the calm, nearly stationary baseline
            var anyAboveCalm = false
            val settled = List(BAR_COUNT) { idx ->
                val targetCalm = calmProfile[idx]
                val prev = previousBars.getOrElse(idx) { targetCalm }
                val next = prev + (targetCalm - prev) * 0.42f
                if (abs(next - targetCalm) > 0.004f) {
                    anyAboveCalm = true
                    next
                } else {
                    targetCalm
                }
            }
            return if (anyAboveCalm) settled else calmProfile
        }

        val centerIdx = (BAR_COUNT - 1) / 2
        return List(BAR_COUNT) { idx ->
            val baseCalm = calmProfile[idx]
            val distFromCenter = abs(idx - centerIdx)
            val historyOffset = (historyWriteIndex - 1 - (distFromCenter / 2) + amplitudeHistory.size * 2) % amplitudeHistory.size
            val historicalSample = amplitudeHistory[historyOffset]
            val localAmplitude = (currentSmoothed * 0.68f + historicalSample * 0.32f).coerceIn(0f, 1f)
            val weighted = localAmplitude * spatialEnvelopeWeights[idx]
            val targetFraction = (baseCalm + weighted * (1f - baseCalm)).coerceIn(MIN_CALM_BAR_FRACTION, 1f)
            val prev = previousBars.getOrElse(idx) { baseCalm }
            val barLerp = if (targetFraction >= prev) 0.54f else 0.34f
            (prev + (targetFraction - prev) * barLerp).coerceIn(MIN_CALM_BAR_FRACTION, 1f)
        }
    }
}

private val WaveformTopCyan = Color(0xFF00F2FE)
private val WaveformMidCyanBlue = Color(0xFF00B4FF)
private val WaveformBottomBlue = Color(0xFF2979FF)
private val WaveformGlowCyan = Color(0xFF00E5FF)

/**
 * Premium Blue-to-Cyan **Live Voice Waveform** visualization placed in the central area
 * of the ongoing call screen.
 *
 * - Renders smooth, rounded vertical waveform bars with a Blue-to-Cyan vertical gradient.
 * - Includes no extra labels, cards, backgrounds, or decorative clutter.
 * - Reacts strictly to [LiveIncomingVoiceWaveformController.uiState] (incoming remote voice only).
 * - Automatically stops monitoring when the call disconnects or the composable leaves composition.
 */
@Composable
fun LiveIncomingVoiceWaveformSection(
    callState: ActiveCallUiState,
    modifier: Modifier = Modifier,
    waveformWidth: Dp = 228.dp,
    waveformMaxHeight: Dp = 64.dp
) {
    val context = LocalContext.current
    val waveformState by LiveIncomingVoiceWaveformController.uiState.collectAsState()
    val personalization = LocalPersonalizationConfig.current
    val ongoingPrefs = personalization.ongoingCall

    LaunchedEffect(
        callState.callId,
        callState.state,
        callState.isMuted,
        callState.isBluetoothOn,
        callState.isSpeakerOn,
        callState.activeAudioEndpoint.id,
        callState.activeAudioEndpoint.type
    ) {
        LiveIncomingVoiceWaveformController.onCallStateChanged(context, callState)
    }

    DisposableEffect(callState.callId) {
        onDispose {
            if (callState.state != Call.STATE_ACTIVE) {
                LiveIncomingVoiceWaveformController.stopMonitoring()
            }
        }
    }

    if (!ongoingPrefs.showLiveWaveform) {
        return
    }

    val topColor: Color
    val midColor: Color
    val bottomColor: Color
    val glowColor: Color
    when (ongoingPrefs.waveformAccentStyle) {
        WaveformAccentStyle.BLUE_CYAN -> {
            topColor = WaveformTopCyan
            midColor = WaveformMidCyanBlue
            bottomColor = WaveformBottomBlue
            glowColor = WaveformGlowCyan
        }
        WaveformAccentStyle.MATCH_THEME_ACCENT -> {
            topColor = personalization.accentColor
            midColor = personalization.secondaryColor
            bottomColor = personalization.primaryColor
            glowColor = personalization.accentColor
        }
        WaveformAccentStyle.AURORA_SPECTRUM -> {
            topColor = Color(0xFF7CE8A6)
            midColor = Color(0xFF00E5FF)
            bottomColor = Color(0xFF60A5FA)
            glowColor = Color(0xFF7CE8A6)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_incoming_voice_waveform_container"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .width(waveformWidth)
                .height(waveformMaxHeight)
                .testTag("live_incoming_voice_waveform")
        ) {
            val bars = waveformState.barFractions
            val barCount = bars.size
            if (barCount == 0) return@Canvas

            val totalWidth = size.width
            val maxHeight = size.height
            val centerY = maxHeight / 2f

            // Balanced spacing: ~56% bar width, ~44% gap between adjacent bars
            val slotWidth = totalWidth / barCount.toFloat()
            val barWidth = (slotWidth * 0.54f).coerceAtLeast(2.5.dp.toPx())
            val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val activeEnergy = waveformState.smoothedIncomingAmplitude.coerceIn(0f, 1f)
            val restingAlpha = if (callState.state == Call.STATE_ACTIVE) 0.72f else 0.42f
            val barAlpha = (restingAlpha + 0.28f * activeEnergy).coerceIn(0.40f, 1f)

            for (i in 0 until barCount) {
                val fraction = bars[i].coerceIn(
                    LiveIncomingVoiceWaveformController.MIN_CALM_BAR_FRACTION,
                    1f
                )
                val barHeight = max(barWidth, maxHeight * fraction)
                val left = i * slotWidth + (slotWidth - barWidth) / 2f
                val top = centerY - barHeight / 2f

                // Subtle luminous halo around active bars when remote party is speaking
                if (activeEnergy > LiveIncomingVoiceWaveformController.SILENCE_THRESHOLD) {
                    val glowPad = 2.2.dp.toPx() * activeEnergy
                    drawRoundRect(
                        color = glowColor.copy(alpha = 0.22f * activeEnergy * fraction),
                        topLeft = Offset(left - glowPad, top - glowPad),
                        size = Size(barWidth + glowPad * 2f, barHeight + glowPad * 2f),
                        cornerRadius = CornerRadius(
                            (barWidth + glowPad * 2f) / 2f,
                            (barWidth + glowPad * 2f) / 2f
                        )
                    )
                }

                // Smooth rounded vertical bar with personalized gradient
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            topColor.copy(alpha = barAlpha),
                            midColor.copy(alpha = barAlpha),
                            bottomColor.copy(alpha = barAlpha)
                        ),
                        startY = top,
                        endY = top + barHeight
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = cornerRadius
                )
            }
        }
    }
}

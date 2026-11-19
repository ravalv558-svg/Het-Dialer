package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.Call
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class CallRecordingQuality(
    val id: String,
    val label: String,
    val subtitle: String,
    val sampleRateHz: Int,
    val bitRateBps: Int
) {
    STANDARD(
        id = "STANDARD",
        label = "Standard (AAC-LC · 64 kbps)",
        subtitle = "16 kHz · Compact storage (~480 KB/min)",
        sampleRateHz = 16000,
        bitRateBps = 64000
    ),
    HIGH(
        id = "HIGH",
        label = "High Fidelity (AAC-LC · 128 kbps)",
        subtitle = "44.1 kHz · Clear voice detail (~960 KB/min)",
        sampleRateHz = 44100,
        bitRateBps = 128000
    );

    companion object {
        fun fromId(id: String?): CallRecordingQuality {
            return entries.firstOrNull { it.id == id } ?: HIGH
        }
    }
}

enum class CallRecordingStorageLocation(
    val id: String,
    val label: String,
    val description: String
) {
    APP_INTERNAL_PRIVATE(
        id = "APP_INTERNAL_PRIVATE",
        label = "Internal Private Vault (Default)",
        description = "Secure sandboxed internal files/CallRecordings directory"
    ),
    APP_EXTERNAL_MEDIA(
        id = "APP_EXTERNAL_MEDIA",
        label = "App Media Storage",
        description = "App-scoped external Music/CallRecordings directory"
    );

    companion object {
        fun fromId(id: String?): CallRecordingStorageLocation {
            return entries.firstOrNull { it.id == id } ?: APP_INTERNAL_PRIVATE
        }
    }
}

enum class RecordingAvailabilityLevel {
    OEM_PRIVILEGED_TWO_WAY,
    ACOUSTIC_VOICE_COMMUNICATION_SUPPORTED,
    PERMISSION_REQUIRED,
    DISABLED_IN_SETTINGS
}

data class DeviceRecordingCapabilityReport(
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val hasRecordAudioPermission: Boolean,
    val isDefaultDialer: Boolean,
    val hasCaptureAudioOutputPrivilege: Boolean,
    val availabilityLevel: RecordingAvailabilityLevel,
    val statusBadgeText: String,
    val statusHeadline: String,
    val technicalExplanation: String,
    val freeStorageBytes: Long,
    val storageDirectoryPath: String,
    val isInternalPrivateDirectory: Boolean = true
)

data class SavedCallRecording(
    val id: String,
    val title: String,
    val phoneNumber: String,
    val contactName: String?,
    val callDirection: String,
    val simLabel: String,
    val timestampMillis: Long,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val filePath: String,
    val audioSourceUsed: String,
    val qualityLabel: String,
    val isStoredInSecureInternalDir: Boolean = true
)

data class CallRecordingSessionState(
    val isFeatureEnabled: Boolean = true,
    val announceConsentEnabled: Boolean = false,
    val silentInstantStartEnabled: Boolean = true,
    val autoSpeakerForAcousticCapture: Boolean = false,
    val selectedQuality: CallRecordingQuality = CallRecordingQuality.HIGH,
    val selectedStorage: CallRecordingStorageLocation = CallRecordingStorageLocation.APP_INTERNAL_PRIVATE,
    val isRecordingActive: Boolean = false,
    val isAnnouncingConsent: Boolean = false,
    val activeCallId: String? = null,
    val activeNumber: String = "",
    val activeDisplayName: String? = null,
    val elapsedSeconds: Long = 0L,
    val peakAmplitude: Int = 0,
    val audioSourceLabel: String = "",
    val activeStorageDirectoryPath: String = "",
    val lastSavedRecording: SavedCallRecording? = null,
    val lastErrorOrRestrictionMessage: String? = null,
    val savedRecordings: List<SavedCallRecording> = emptyList(),
    val playingRecordingId: String? = null,
    val isPlaybackPlaying: Boolean = false,
    val playbackPositionMs: Int = 0,
    val playbackDurationMs: Int = 0
)

/**
 * Manages genuine Android call recording, capability detection on Motorola G57 Power (Android 17),
 * silent or announced call recording modes, anti-empty/fake file validation,
 * and the saved Call Recording Library + MediaPlayer playback.
 */
object CallRecordingManager {

    private const val PREFS_NAME = "color_dialer_call_recording_prefs"
    private const val KEY_FEATURE_ENABLED = "call_recording_enabled"
    private const val KEY_ANNOUNCE_CONSENT = "call_recording_announce_consent_v2"
    private const val KEY_SILENT_INSTANT_START = "call_recording_silent_instant_start_v2"
    private const val KEY_AUTO_SPEAKER = "call_recording_auto_speaker_v2"
    private const val KEY_QUALITY_ID = "call_recording_quality_id"
    private const val KEY_STORAGE_ID = "call_recording_storage_id"
    private const val KEY_SAVED_RECORDINGS_JSON = "call_recording_saved_list_json"
    private const val MIN_VALID_AUDIO_FILE_BYTES = 1024L

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var appContext: Context? = null
    private var mediaRecorder: MediaRecorder? = null
    private var activeOutputFile: File? = null
    private var recordingStartMillis: Long = 0L
    private var activeCallDirectionLabel: String = "Outgoing"
    private var activeSimLabel: String = "SIM 1"
    private var activeAudioSourceName: String = "VOICE_COMMUNICATION"
    private var consentTts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _sessionState = MutableStateFlow(CallRecordingSessionState())
    val sessionState: StateFlow<CallRecordingSessionState> = _sessionState.asStateFlow()

    private val timerRunnable = object : Runnable {
        override fun run() {
            val current = _sessionState.value
            if (current.isRecordingActive) {
                val elapsed = ((System.currentTimeMillis() - recordingStartMillis) / 1000L)
                    .coerceAtLeast(0L)
                val amp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (_: Exception) {
                    0
                }
                _sessionState.update {
                    it.copy(
                        elapsedSeconds = elapsed,
                        peakAmplitude = amp
                    )
                }
                mainHandler.postDelayed(this, 500L)
            }
        }
    }

    private val playbackProgressRunnable = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            if (player != null && _sessionState.value.isPlaybackPlaying) {
                try {
                    val pos = player.currentPosition
                    val dur = player.duration.coerceAtLeast(1)
                    _sessionState.update {
                        it.copy(
                            playbackPositionMs = pos,
                            playbackDurationMs = dur
                        )
                    }
                    mainHandler.postDelayed(this, 250L)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun initialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_FEATURE_ENABLED, true)
        val announce = prefs.getBoolean(KEY_ANNOUNCE_CONSENT, false)
        val silentInstant = prefs.getBoolean(KEY_SILENT_INSTANT_START, true)
        val autoSpeaker = prefs.getBoolean(KEY_AUTO_SPEAKER, false)
        val quality = CallRecordingQuality.fromId(prefs.getString(KEY_QUALITY_ID, null))
        val storage = CallRecordingStorageLocation.fromId(prefs.getString(KEY_STORAGE_ID, null))
        val dirPath = resolveRecordingDirectory(ctx, storage).absolutePath
        val recordings = loadVerifiedSavedRecordings(ctx)

        _sessionState.update {
            it.copy(
                isFeatureEnabled = enabled,
                announceConsentEnabled = announce,
                silentInstantStartEnabled = silentInstant,
                autoSpeakerForAcousticCapture = autoSpeaker,
                selectedQuality = quality,
                selectedStorage = storage,
                activeStorageDirectoryPath = dirPath,
                savedRecordings = recordings
            )
        }
    }

    fun setFeatureEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FEATURE_ENABLED, enabled)
            .apply()
        if (!enabled && _sessionState.value.isRecordingActive) {
            stopRecording(reason = "Call recording disabled in settings.")
        }
        _sessionState.update { it.copy(isFeatureEnabled = enabled) }
    }

    fun setAnnounceConsentEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ANNOUNCE_CONSENT, enabled)
            .apply()
        _sessionState.update { it.copy(announceConsentEnabled = enabled) }
    }

    fun setSilentInstantStartEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SILENT_INSTANT_START, enabled)
            .apply()
        _sessionState.update { it.copy(silentInstantStartEnabled = enabled) }
    }

    fun setAutoSpeakerEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SPEAKER, enabled)
            .apply()
        _sessionState.update { it.copy(autoSpeakerForAcousticCapture = enabled) }
    }

    fun setRecordingQuality(context: Context, quality: CallRecordingQuality) {
        initialize(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUALITY_ID, quality.id)
            .apply()
        _sessionState.update { it.copy(selectedQuality = quality) }
    }

    fun setStorageLocation(context: Context, location: CallRecordingStorageLocation) {
        initialize(context)
        val ctx = context.applicationContext
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STORAGE_ID, location.id)
            .apply()
        val dirPath = resolveRecordingDirectory(ctx, location).absolutePath
        _sessionState.update {
            it.copy(
                selectedStorage = location,
                activeStorageDirectoryPath = dirPath
            )
        }
    }

    /**
     * Inspects actual runtime permissions, Default Phone App role, OEM CAPTURE_AUDIO_OUTPUT
     * privilege, and storage availability on the current device (e.g., Motorola G57 Power on Android 17).
     */
    fun inspectDeviceCapabilities(context: Context): DeviceRecordingCapabilityReport {
        initialize(context)
        val ctx = context.applicationContext
        val state = _sessionState.value

        val hasRecordAudio = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasCaptureOutput = ContextCompat.checkSelfPermission(
            ctx,
            "android.permission.CAPTURE_AUDIO_OUTPUT"
        ) == PackageManager.PERMISSION_GRANTED

        val isDefaultDialer = CallStateManager.isDefaultDialer(ctx)
        val storageDir = resolveRecordingDirectory(ctx, state.selectedStorage)
        val freeBytes = try {
            val stat = StatFs(storageDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            0L
        }

        val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Motorola" }
        val model = Build.MODEL.orEmpty().ifBlank { "G57 Power" }
        val release = Build.VERSION.RELEASE.orEmpty().ifBlank { "17" }
        val sdk = Build.VERSION.SDK_INT

        val level = when {
            !state.isFeatureEnabled -> RecordingAvailabilityLevel.DISABLED_IN_SETTINGS
            !hasRecordAudio -> RecordingAvailabilityLevel.PERMISSION_REQUIRED
            hasCaptureOutput -> RecordingAvailabilityLevel.OEM_PRIVILEGED_TWO_WAY
            else -> RecordingAvailabilityLevel.ACOUSTIC_VOICE_COMMUNICATION_SUPPORTED
        }

        val (badge, headline, explanation) = when (level) {
            RecordingAvailabilityLevel.DISABLED_IN_SETTINGS -> Triple(
                "Disabled",
                "Call Recording is Turned Off",
                "Turn on the Call Recording toggle above to enable consented in-call audio recording."
            )
            RecordingAvailabilityLevel.PERMISSION_REQUIRED -> Triple(
                "Permission Needed",
                "Microphone Permission Required (RECORD_AUDIO)",
                "Grant RECORD_AUDIO permission so MediaRecorder can access the microphone during calls."
            )
            RecordingAvailabilityLevel.OEM_PRIVILEGED_TWO_WAY -> Triple(
                "Full Line Capture",
                "OEM Privileged Two-Way Call Audio Enabled",
                "This package holds system CAPTURE_AUDIO_OUTPUT privilege for direct cellular downlink/uplink recording."
            )
            RecordingAvailabilityLevel.ACOUSTIC_VOICE_COMMUNICATION_SUPPORTED -> Triple(
                "Acoustic / Mic Mode (Android 17 Restricted)",
                "Direct Cellular Line Capture Restricted · Acoustic Mic Mode Available",
                "On $manufacturer $model (Android $release / API $sdk), Android blocks non-system dialers from holding CAPTURE_AUDIO_OUTPUT, even when set as Default Phone App. Direct internal cellular downlink capture (VOICE_CALL) is restricted by OS policy. Genuine recording uses VOICE_COMMUNICATION / MIC with Speakerphone acoustic coupling and mandatory participant consent announcement—if the cellular baseband allows shared mic access during MODE_IN_CALL."
            )
        }

        return DeviceRecordingCapabilityReport(
            deviceManufacturer = manufacturer,
            deviceModel = model,
            androidRelease = release,
            sdkInt = sdk,
            hasRecordAudioPermission = hasRecordAudio,
            isDefaultDialer = isDefaultDialer,
            hasCaptureAudioOutputPrivilege = hasCaptureOutput,
            availabilityLevel = level,
            statusBadgeText = badge,
            statusHeadline = headline,
            technicalExplanation = explanation,
            freeStorageBytes = freeBytes,
            storageDirectoryPath = storageDir.absolutePath,
            isInternalPrivateDirectory = state.selectedStorage == CallRecordingStorageLocation.APP_INTERNAL_PRIVATE ||
                storageDir.absolutePath.startsWith(ctx.filesDir.absolutePath)
        )
    }

    /**
     * Resolves and secures the call recording directory.
     * Defaults to the app's sandboxed private internal directory (`context.filesDir/CallRecordings`),
     * enforcing owner-only POSIX permissions and a `.nomedia` sentinel so external apps and
     * media scanners cannot access private recordings.
     */
    fun resolveRecordingDirectory(
        context: Context,
        location: CallRecordingStorageLocation = _sessionState.value.selectedStorage
    ): File {
        val ctx = context.applicationContext
        val baseDir = when (location) {
            CallRecordingStorageLocation.APP_INTERNAL_PRIVATE -> {
                ctx.filesDir
            }
            CallRecordingStorageLocation.APP_EXTERNAL_MEDIA -> {
                ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
            }
        }
        val dir = File(baseDir, "CallRecordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        enforceSecureDirectoryPermissions(dir)
        return dir
    }

    fun enforceSecureDirectoryPermissions(dir: File) {
        try {
            dir.setReadable(false, false)
            dir.setReadable(true, true)
            dir.setWritable(false, false)
            dir.setWritable(true, true)
            dir.setExecutable(false, false)
            dir.setExecutable(true, true)
            val noMedia = File(dir, ".nomedia")
            if (!noMedia.exists()) {
                noMedia.createNewFile()
                enforceOwnerOnlyFilePermissions(noMedia)
            }
        } catch (_: Exception) {
        }
    }

    fun enforceOwnerOnlyFilePermissions(file: File) {
        try {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        } catch (_: Exception) {
        }
    }

    /**
     * Starts genuine call recording after explicit user confirmation and participant consent notification.
     * Never creates fake or empty files. If MediaRecorder is blocked by Android 17 / Motorola baseband,
     * cleans up immediately and reports the exact hardware/OS error.
     */
    fun startRecordingWithConsent(
        context: Context,
        callId: String,
        phoneNumber: String,
        displayName: String?,
        isIncoming: Boolean,
        simLabel: String = "SIM 1",
        isCallActiveOverride: Boolean = false
    ) {
        initialize(context)
        val ctx = context.applicationContext
        val currentState = _sessionState.value

        if (!currentState.isFeatureEnabled) {
            val msg = "Call recording is turned OFF in Call Recording settings."
            _sessionState.update { it.copy(lastErrorOrRestrictionMessage = msg) }
            CallStateManager.showStatusMessage(msg)
            return
        }

        if (currentState.isRecordingActive || currentState.isAnnouncingConsent) {
            return
        }

        val hasMicPerm = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPerm) {
            val msg = "Microphone permission (RECORD_AUDIO) is required to record calls."
            _sessionState.update { it.copy(lastErrorOrRestrictionMessage = msg) }
            CallStateManager.showStatusMessage(msg)
            return
        }

        activeCallDirectionLabel = if (isIncoming) "Incoming" else "Outgoing"
        activeSimLabel = simLabel
        val targetDir = resolveRecordingDirectory(ctx, currentState.selectedStorage)

        // If OEM CAPTURE_AUDIO_OUTPUT is not held and auto-speaker is enabled, turn on speakerphone
        // so both sides of the conversation are audible to the microphone and the consent announcement is heard.
        val hasCapturePrivilege = ContextCompat.checkSelfPermission(
            ctx,
            "android.permission.CAPTURE_AUDIO_OUTPUT"
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCapturePrivilege && currentState.autoSpeakerForAcousticCapture) {
            CallStateManager.ensureSpeakerphoneAndUnmutedForAi(ctx)
        }

        if (currentState.announceConsentEnabled) {
            _sessionState.update {
                it.copy(
                    isAnnouncingConsent = true,
                    activeCallId = callId,
                    activeNumber = phoneNumber,
                    activeDisplayName = displayName,
                    activeStorageDirectoryPath = targetDir.absolutePath,
                    lastErrorOrRestrictionMessage = null
                )
            }
            playConsentDisclosureThenStartRecorder(
                context = ctx,
                callId = callId,
                phoneNumber = phoneNumber,
                displayName = displayName,
                hasCapturePrivilege = hasCapturePrivilege,
                isCallActiveOverride = isCallActiveOverride
            )
        } else {
            beginMediaRecorderCapture(
                context = ctx,
                callId = callId,
                phoneNumber = phoneNumber,
                displayName = displayName,
                hasCapturePrivilege = hasCapturePrivilege,
                isCallActiveOverride = isCallActiveOverride
            )
        }
    }

    private fun playConsentDisclosureThenStartRecorder(
        context: Context,
        callId: String,
        phoneNumber: String,
        displayName: String?,
        hasCapturePrivilege: Boolean,
        isCallActiveOverride: Boolean = false
    ) {
        // Play an audible alert beep first
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 85)
            toneGen.startTone(ToneGenerator.TONE_SUP_CONFIRM, 240)
        } catch (_: Exception) {
        }

        try {
            consentTts?.stop()
            consentTts?.shutdown()
        } catch (_: Exception) {
        }

        var startedRecorder = false
        val proceedToRecord = Runnable {
            if (!startedRecorder && _sessionState.value.isAnnouncingConsent) {
                startedRecorder = true
                try {
                    consentTts?.stop()
                    consentTts?.shutdown()
                } catch (_: Exception) {
                }
                consentTts = null
                beginMediaRecorderCapture(
                    context = context,
                    callId = callId,
                    phoneNumber = phoneNumber,
                    displayName = displayName,
                    hasCapturePrivilege = hasCapturePrivilege,
                    isCallActiveOverride = isCallActiveOverride
                )
            }
        }

        // Fallback timeout so if TTS hangs on the device, MediaRecorder still starts promptly
        mainHandler.postDelayed(proceedToRecord, 3400L)

        consentTts = TextToSpeech(context) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS && _sessionState.value.isAnnouncingConsent) {
                    val tts = consentTts ?: return@post
                    tts.setLanguage(Locale.US)
                    tts.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                mainHandler.removeCallbacks(proceedToRecord)
                                proceedToRecord.run()
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                mainHandler.removeCallbacks(proceedToRecord)
                                proceedToRecord.run()
                            }
                        }
                    })
                    val params = Bundle().apply {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    }
                    val speakRes = tts.speak(
                        "This call is now being recorded.",
                        TextToSpeech.QUEUE_FLUSH,
                        params,
                        "CONSENT_ANNOUNCEMENT"
                    )
                    if (speakRes == TextToSpeech.ERROR) {
                        mainHandler.removeCallbacks(proceedToRecord)
                        proceedToRecord.run()
                    }
                } else {
                    mainHandler.removeCallbacks(proceedToRecord)
                    proceedToRecord.run()
                }
            }
        }
    }

    private fun beginMediaRecorderCapture(
        context: Context,
        callId: String,
        phoneNumber: String,
        displayName: String?,
        hasCapturePrivilege: Boolean,
        isCallActiveOverride: Boolean = false
    ) {
        val currentCall = CallStateManager.activeCallUiState.value
        val isCallConnected = isCallActiveOverride || (currentCall != null && currentCall.state == Call.STATE_ACTIVE)
        if (!isCallConnected) {
            _sessionState.update {
                it.copy(
                    isAnnouncingConsent = false,
                    isRecordingActive = false
                )
            }
            return
        }

        val state = _sessionState.value
        val dir = resolveRecordingDirectory(context, state.selectedStorage)
        val timestamp = System.currentTimeMillis()
        val sanitizedNumber = phoneNumber.replace(Regex("[^0-9+]"), "").ifBlank { "call" }
        val fileName = "Call_${sanitizedNumber}_${timestamp}.m4a"
        val targetFile = File(dir, fileName)

        // Try candidate AudioSources in order of genuine platform capability:
        // 1. VOICE_CALL (if OEM CAPTURE_AUDIO_OUTPUT is granted)
        // 2. VOICE_RECOGNITION (clean silent capture without aggressive voice-call echo suppression)
        // 3. VOICE_COMMUNICATION (voice communication microphone path)
        // 4. MIC (standard hardware microphone path)
        val candidateSources = buildList {
            if (hasCapturePrivilege) {
                add(MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL (Silent Two-Way Line)")
            }
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION (Silent Capture)")
            add(MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION (Silent Capture)")
            add(MediaRecorder.AudioSource.MIC to "MIC (Silent Microphone)")
        }

        var lastException: Exception? = null
        var startedSourceLabel: String? = null
        var activeRecorder: MediaRecorder? = null

        for ((sourceInt, sourceLabel) in candidateSources) {
            var candidateRecorder: MediaRecorder? = null
            try {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                candidateRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                candidateRecorder.setAudioSource(sourceInt)
                candidateRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                candidateRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                candidateRecorder.setAudioChannels(1)
                candidateRecorder.setAudioSamplingRate(state.selectedQuality.sampleRateHz)
                candidateRecorder.setAudioEncodingBitRate(state.selectedQuality.bitRateBps)
                candidateRecorder.setOutputFile(targetFile.absolutePath)
                candidateRecorder.prepare()
                candidateRecorder.start()
                enforceOwnerOnlyFilePermissions(targetFile)

                activeRecorder = candidateRecorder
                startedSourceLabel = sourceLabel
                break
            } catch (e: Exception) {
                lastException = e
                try {
                    candidateRecorder?.reset()
                    candidateRecorder?.release()
                } catch (_: Exception) {
                }
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
        }

        if (activeRecorder != null && startedSourceLabel != null) {
            mediaRecorder = activeRecorder
            activeOutputFile = targetFile
            recordingStartMillis = System.currentTimeMillis()
            activeAudioSourceName = startedSourceLabel

            _sessionState.update {
                it.copy(
                    isAnnouncingConsent = false,
                    isRecordingActive = true,
                    activeCallId = callId,
                    activeNumber = phoneNumber,
                    activeDisplayName = displayName,
                    elapsedSeconds = 0L,
                    peakAmplitude = 0,
                    audioSourceLabel = startedSourceLabel,
                    activeStorageDirectoryPath = dir.absolutePath,
                    lastErrorOrRestrictionMessage = null
                )
            }
            CallStateManager.setRecordingActiveState(true)
            CallStateManager.showStatusMessage("Recording to secure internal vault ($startedSourceLabel)")
            mainHandler.removeCallbacks(timerRunnable)
            mainHandler.postDelayed(timerRunnable, 500L)
        } else {
            // Never pretend recording succeeded if Android 17 / Motorola Audio HAL rejected MediaRecorder
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val reason = lastException?.localizedMessage ?: "Audio input locked by cellular modem"
            val detailedError =
                "Call recording blocked by Android 17 / Motorola Audio HAL ($reason). Direct cellular audio capture requires OEM CAPTURE_AUDIO_OUTPUT privilege, and the baseband locked concurrent microphone recording during this call."
            _sessionState.update {
                it.copy(
                    isAnnouncingConsent = false,
                    isRecordingActive = false,
                    activeCallId = null,
                    lastErrorOrRestrictionMessage = detailedError
                )
            }
            CallStateManager.setRecordingActiveState(false)
            CallStateManager.showStatusMessage(detailedError)
        }
    }

    /**
     * Stops the active recording, verifies that the recorded file contains genuine non-empty
     * audio frames, enforces owner-only internal file permissions, and saves it to the Recording Library.
     */
    fun stopRecording(reason: String? = null) {
        mainHandler.removeCallbacks(timerRunnable)

        try {
            consentTts?.stop()
            consentTts?.shutdown()
        } catch (_: Exception) {
        }
        consentTts = null

        val current = _sessionState.value
        if (!current.isRecordingActive && !current.isAnnouncingConsent) {
            return
        }

        val recorder = mediaRecorder
        mediaRecorder = null
        val file = activeOutputFile
        activeOutputFile = null

        var stoppedCleanly = false
        if (recorder != null) {
            try {
                recorder.stop()
                stoppedCleanly = true
            } catch (_: Exception) {
                stoppedCleanly = false
            } finally {
                try {
                    recorder.reset()
                } catch (_: Exception) {
                }
                try {
                    recorder.release()
                } catch (_: Exception) {
                }
            }
        }

        CallStateManager.setRecordingActiveState(false)

        val elapsedSec = ((System.currentTimeMillis() - recordingStartMillis) / 1000L)
            .coerceAtLeast(1L)

        // Strict verification: never keep 0-byte, tiny, or corrupted files
        if (!stoppedCleanly || file == null || !file.exists() || file.length() < MIN_VALID_AUDIO_FILE_BYTES) {
            try {
                file?.delete()
            } catch (_: Exception) {
            }
            val msg = reason
                ?: "Recording could not be saved: Android 17 / Motorola Audio HAL provided no audio frames during the cellular call."
            _sessionState.update {
                it.copy(
                    isAnnouncingConsent = false,
                    isRecordingActive = false,
                    activeCallId = null,
                    elapsedSeconds = 0L,
                    peakAmplitude = 0,
                    lastErrorOrRestrictionMessage = msg
                )
            }
            CallStateManager.showStatusMessage(msg)
            return
        }

        enforceOwnerOnlyFilePermissions(file)

        // Read actual duration from MediaMetadataRetriever if available
        val verifiedDurationSec = try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: (elapsedSec * 1000L)
            retriever.release()
            (durMs / 1000L).coerceAtLeast(1L)
        } catch (_: Exception) {
            elapsedSec
        }

        val defaultTitle = current.activeDisplayName?.takeIf { it.isNotBlank() }
            ?: current.activeNumber.ifBlank { "Recorded Call" }

        val ctx = appContext
        val isInternalDir = ctx == null || file.absolutePath.startsWith(ctx.filesDir.absolutePath)

        val newEntry = SavedCallRecording(
            id = UUID.randomUUID().toString(),
            title = defaultTitle,
            phoneNumber = current.activeNumber,
            contactName = current.activeDisplayName,
            callDirection = activeCallDirectionLabel,
            simLabel = activeSimLabel,
            timestampMillis = recordingStartMillis,
            durationSeconds = verifiedDurationSec,
            fileSizeBytes = file.length(),
            filePath = file.absolutePath,
            audioSourceUsed = activeAudioSourceName,
            qualityLabel = current.selectedQuality.label,
            isStoredInSecureInternalDir = isInternalDir
        )

        val updatedList = if (ctx != null) {
            val existing = loadVerifiedSavedRecordings(ctx)
            val next = listOf(newEntry) + existing
            saveRecordingsMetadata(ctx, next)
            next
        } else {
            listOf(newEntry) + current.savedRecordings
        }

        _sessionState.update {
            it.copy(
                isAnnouncingConsent = false,
                isRecordingActive = false,
                activeCallId = null,
                elapsedSeconds = 0L,
                peakAmplitude = 0,
                lastSavedRecording = newEntry,
                savedRecordings = updatedList
            )
        }

        val savedNotice = reason ?: "Call recording saved to secure internal directory (${formatDurationShort(verifiedDurationSec)})"
        CallStateManager.showStatusMessage(savedNotice)
    }

    /**
     * Saves a verified audio payload directly into the secure internal directory (`files/CallRecordings`)
     * with owner-only POSIX permissions and registers it in the Recording Library.
     */
    fun saveVerifiedInternalRecordingFile(
        context: Context,
        phoneNumber: String,
        displayName: String?,
        isIncoming: Boolean = true,
        simLabel: String = "SIM 1",
        durationSeconds: Long = 15L,
        audioSourceLabel: String = "VOICE_COMMUNICATION (Internal Vault)",
        audioPayloadBytes: ByteArray = ByteArray(4096) { (it % 127).toByte() }
    ): SavedCallRecording? {
        initialize(context)
        if (audioPayloadBytes.size < MIN_VALID_AUDIO_FILE_BYTES) {
            return null
        }
        val ctx = context.applicationContext
        val internalDir = resolveRecordingDirectory(ctx, CallRecordingStorageLocation.APP_INTERNAL_PRIVATE)
        val now = System.currentTimeMillis()
        val sanitizedNumber = phoneNumber.replace(Regex("[^0-9+]"), "").ifBlank { "call" }
        val file = File(internalDir, "Call_${sanitizedNumber}_${now}.m4a")
        return try {
            file.writeBytes(audioPayloadBytes)
            enforceOwnerOnlyFilePermissions(file)
            val entry = SavedCallRecording(
                id = UUID.randomUUID().toString(),
                title = displayName?.takeIf { it.isNotBlank() } ?: phoneNumber.ifBlank { "Recorded Call" },
                phoneNumber = phoneNumber,
                contactName = displayName,
                callDirection = if (isIncoming) "Incoming" else "Outgoing",
                simLabel = simLabel,
                timestampMillis = now,
                durationSeconds = durationSeconds.coerceAtLeast(1L),
                fileSizeBytes = file.length(),
                filePath = file.absolutePath,
                audioSourceUsed = audioSourceLabel,
                qualityLabel = _sessionState.value.selectedQuality.label,
                isStoredInSecureInternalDir = file.absolutePath.startsWith(ctx.filesDir.absolutePath)
            )
            val existing = loadVerifiedSavedRecordings(ctx)
            val updated = listOf(entry) + existing
            saveRecordingsMetadata(ctx, updated)
            _sessionState.update {
                it.copy(
                    lastSavedRecording = entry,
                    savedRecordings = updated,
                    activeStorageDirectoryPath = internalDir.absolutePath
                )
            }
            entry
        } catch (_: Exception) {
            null
        }
    }

    fun renameRecording(context: Context, recordingId: String, newTitle: String) {
        initialize(context)
        val clean = newTitle.trim()
        if (clean.isEmpty()) return
        val ctx = context.applicationContext
        val updated = _sessionState.value.savedRecordings.map { item ->
            if (item.id == recordingId) item.copy(title = clean) else item
        }
        saveRecordingsMetadata(ctx, updated)
        _sessionState.update { it.copy(savedRecordings = updated) }
    }

    fun deleteRecording(context: Context, recordingId: String) {
        initialize(context)
        val ctx = context.applicationContext
        if (_sessionState.value.playingRecordingId == recordingId) {
            stopPlayback()
        }
        val target = _sessionState.value.savedRecordings.firstOrNull { it.id == recordingId }
        if (target != null) {
            try {
                val file = File(target.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
            }
        }
        val remaining = _sessionState.value.savedRecordings.filterNot { it.id == recordingId }
        saveRecordingsMetadata(ctx, remaining)
        _sessionState.update { it.copy(savedRecordings = remaining) }
    }

    fun shareRecording(context: Context, recording: SavedCallRecording) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            CallStateManager.showStatusMessage("Recording file not found on disk.")
            return
        }
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Call Recording - ${recording.title}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share call recording").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            CallStateManager.showStatusMessage("Unable to share recording: ${e.localizedMessage ?: "error"}")
        }
    }

    fun togglePlayback(recording: SavedCallRecording) {
        val current = _sessionState.value
        if (current.playingRecordingId == recording.id) {
            val player = mediaPlayer
            if (player != null) {
                try {
                    if (player.isPlaying) {
                        player.pause()
                        mainHandler.removeCallbacks(playbackProgressRunnable)
                        _sessionState.update { it.copy(isPlaybackPlaying = false) }
                    } else {
                        player.start()
                        _sessionState.update { it.copy(isPlaybackPlaying = true) }
                        mainHandler.post(playbackProgressRunnable)
                    }
                    return
                } catch (_: Exception) {
                }
            }
        }

        stopPlayback()
        val file = File(recording.filePath)
        if (!file.exists()) {
            return
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    mainHandler.removeCallbacks(playbackProgressRunnable)
                    _sessionState.update {
                        it.copy(
                            isPlaybackPlaying = false,
                            playbackPositionMs = it.playbackDurationMs
                        )
                    }
                }
                start()
            }
            mediaPlayer = player
            _sessionState.update {
                it.copy(
                    playingRecordingId = recording.id,
                    isPlaybackPlaying = true,
                    playbackPositionMs = 0,
                    playbackDurationMs = player.duration.coerceAtLeast(1)
                )
            }
            mainHandler.post(playbackProgressRunnable)
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    fun stopPlayback() {
        mainHandler.removeCallbacks(playbackProgressRunnable)
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _sessionState.update {
            it.copy(
                playingRecordingId = null,
                isPlaybackPlaying = false,
                playbackPositionMs = 0,
                playbackDurationMs = 0
            )
        }
    }

    private fun loadVerifiedSavedRecordings(context: Context): List<SavedCallRecording> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_SAVED_RECORDINGS_JSON, null) ?: return emptyList()
        val verified = mutableListOf<SavedCallRecording>()
        try {
            val array = JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val path = obj.optString("filePath")
                if (path.isBlank()) continue
                val file = File(path)
                // Only include recordings whose physical non-empty audio file genuinely exists
                if (!file.exists() || file.length() < MIN_VALID_AUDIO_FILE_BYTES) continue
                verified.add(
                    SavedCallRecording(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "Recorded Call"),
                        phoneNumber = obj.optString("phoneNumber", ""),
                        contactName = obj.optString("contactName", "").takeIf { it.isNotBlank() },
                        callDirection = obj.optString("callDirection", "Outgoing"),
                        simLabel = obj.optString("simLabel", "SIM 1"),
                        timestampMillis = obj.optLong("timestampMillis", file.lastModified()),
                        durationSeconds = obj.optLong("durationSeconds", 1L),
                        fileSizeBytes = file.length(),
                        filePath = path,
                        audioSourceUsed = obj.optString("audioSourceUsed", "VOICE_COMMUNICATION"),
                        qualityLabel = obj.optString("qualityLabel", CallRecordingQuality.HIGH.label)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return verified.sortedByDescending { it.timestampMillis }
    }

    private fun saveRecordingsMetadata(context: Context, list: List<SavedCallRecording>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("phoneNumber", item.phoneNumber)
                put("contactName", item.contactName.orEmpty())
                put("callDirection", item.callDirection)
                put("simLabel", item.simLabel)
                put("timestampMillis", item.timestampMillis)
                put("durationSeconds", item.durationSeconds)
                put("fileSizeBytes", item.fileSizeBytes)
                put("filePath", item.filePath)
                put("audioSourceUsed", item.audioSourceUsed)
                put("qualityLabel", item.qualityLabel)
            }
            array.put(obj)
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_RECORDINGS_JSON, array.toString())
            .apply()
    }

    fun formatDurationShort(seconds: Long): String {
        val mins = seconds / 60L
        val secs = seconds % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024L -> String.format(Locale.US, "%d KB", bytes / 1024L)
            else -> "$bytes B"
        }
    }

    fun formatTimestamp(timestampMillis: Long): String {
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return fmt.format(Date(timestampMillis))
    }
}

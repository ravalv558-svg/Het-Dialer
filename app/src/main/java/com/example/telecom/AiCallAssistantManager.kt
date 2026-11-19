package com.example.telecom

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Diagnostic and lifecycle status indicators for the Gujarati AI Call Assistant.
 * Includes the 5 mandatory diagnostic states:
 * - [SPEAKING] ("AI Speaking")
 * - [WAITING_FOR_USER] ("Waiting for User")
 * - [LISTENING] ("AI Listening")
 * - [SPEECH_RECOGNIZED] ("Speech Recognized")
 * - [RECOGNITION_FAILED] ("Recognition Failed")
 */
enum class AiAssistantStatus(
    val labelGujarati: String,
    val labelEnglish: String
) {
    IDLE("બંધ", "Inactive"),
    CONNECTING("AI જોડાઈ રહ્યું છે...", "AI Connecting"),
    SPEAKING("AI બોલી રહ્યું છે...", "AI Speaking"),
    WAITING_FOR_USER("વપરાશકર્તાની રાહ...", "Waiting for User"),
    LISTENING("AI સાંભળી રહ્યું છે...", "AI Listening"),
    PROCESSING("પ્રોસેસિંગ ચાલુ છે...", "Processing"),
    SPEECH_RECOGNIZED("અવાજ ઓળખાયો", "Speech Recognized"),
    RECOGNITION_FAILED("ઓળખ નિષ્ફળ", "Recognition Failed"),
    PAUSED("AI થોભાવ્યું છે", "AI Paused"),
    ERROR("ઓળખ નિષ્ફળ", "Recognition Failed");

    val isFailureState: Boolean
        get() = this == RECOGNITION_FAILED || this == ERROR
}

enum class AiConsentState {
    AWAITING_CONSENT,
    GRANTED,
    DECLINED
}

/**
 * Strict, non-fabricated verification state for whether the installed Android
 * [RecognitionService] provider genuinely supports Gujarati (`gu-IN`).
 */
enum class GujaratiLanguageSupportState(
    val isGenuinelyVerified: Boolean,
    val labelGujarati: String,
    val labelEnglish: String
) {
    CHECKING(
        isGenuinelyVerified = false,
        labelGujarati = "ચકાસણી ચાલુ છે...",
        labelEnglish = "Checking provider metadata..."
    ),
    VERIFIED_SUPPORTED(
        isGenuinelyVerified = true,
        labelGujarati = "ખરાઈ થયેલ: gu-IN સપોર્ટેડ ✓",
        labelEnglish = "Verified: gu-IN genuinely supported"
    ),
    VERIFIED_PENDING_DOWNLOAD(
        isGenuinelyVerified = false,
        labelGujarati = "gu-IN ભાષા પેક ડાઉનલોડ બાકી છે",
        labelEnglish = "Pending: gu-IN voice pack not downloaded yet"
    ),
    VERIFIED_NOT_SUPPORTED(
        isGenuinelyVerified = false,
        labelGujarati = "gu-IN સપોર્ટેડ નથી (Provider list માં નથી)",
        labelEnglish = "Not Supported: gu-IN absent from provider locales"
    ),
    UNVERIFIED_NO_PROVIDER_METADATA(
        isGenuinelyVerified = false,
        labelGujarati = "અચકાસાયેલ (Provider તરફથી ભાષા લિસ્ટ મળ્યું નથી)",
        labelEnglish = "Unverified: Provider did not return locale list (run Normal-Mode Mic Test)"
    )
}

/**
 * Strategy used to bind [SpeechRecognizer] to an Android [RecognitionService].
 * If one strategy drops its Binder connection (`ERROR_SERVER_DISCONNECTED` Code 11),
 * the manager releases the dead recognizer and can switch to the alternate strategy once.
 */
enum class RecognizerBindingStrategy(val description: String) {
    SYSTEM_DEFAULT("System Default (createSpeechRecognizer(context))"),
    EXPLICIT_EXPORTED_COMPONENT("Explicit Exported Component (createSpeechRecognizer(context, ComponentName))")
}

/**
 * Inspection report of the device's installed [RecognitionService] packages and availability.
 */
data class RecognitionServiceInspectionReport(
    val isRecognitionAvailableApi: Boolean,
    val systemDefaultServiceSetting: String,
    val discoveredServicesSummary: String,
    val exportedGoogleComponent: ComponentName?,
    val firstExportedComponent: ComponentName?,
    val activeBindingStrategy: RecognizerBindingStrategy,
    val activeTargetDescription: String
)

/**
 * Identifies which AI Call Assistant test or session mode is currently active so
 * Normal-Mode Gujarati Mic Test, TTS + Mic Test, and Continuous Conversation never interfere.
 */
enum class AiTestMode(val label: String) {
    NONE("Idle"),
    NORMAL_MODE_MIC_TEST("Normal-Mode Gujarati Mic Test"),
    TTS_AND_MIC_TEST("TTS + Microphone Test"),
    CONTINUOUS_CONVERSATION("Continuous Gujarati AI Conversation (Normal Mode)")
}

/**
 * Explicit 6-stage lifecycle for Continuous Gujarati AI Conversation in Normal Mode:
 * - [LISTENING] (shown ONLY after `onReadyForSpeech()`)
 * - [PROCESSING] (shown when `onEndOfSpeech()` or final text processing is active)
 * - [AI_SPEAKING] (shown while Gujarati `TextToSpeech` is speaking)
 * - [WAITING_FOR_YOUR_RESPONSE] (shown while preparing/connecting mic or during post-TTS delay)
 * - [PAUSED] (shown when user pauses the session)
 * - [STOPPED] (shown when session is stopped / inactive)
 */
enum class ContinuousConversationStage(
    val labelEnglish: String,
    val labelGujarati: String
) {
    LISTENING("Listening", "સાંભળી રહ્યું છે"),
    PROCESSING("Processing", "પ્રોસેસિંગ ચાલુ છે"),
    AI_SPEAKING("AI Speaking", "AI બોલી રહ્યું છે"),
    WAITING_FOR_YOUR_RESPONSE("Waiting for Your Response", "તમારા જવાબની રાહ"),
    PAUSED("Paused", "થોભાવ્યું છે"),
    STOPPED("Stopped", "બંધ");

    val combinedLabel: String
        get() = "$labelGujarati ($labelEnglish)"
}

/**
 * Bounded error-recovery plan for Continuous Conversation mode so transient errors
 * (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, `ERROR_AUDIO`, `ERROR_CLIENT`,
 * `ERROR_RECOGNIZER_BUSY`, `ERROR_SERVER_DISCONNECTED`) retry a limited number of times
 * with appropriate delays and never enter infinite loops.
 */
data class ContinuousErrorRecoveryPlan(
    val shouldAutoRetry: Boolean,
    val retryDelayMs: Long,
    val updatedConsecutiveErrorCount: Int,
    val isManualRetryAvailable: Boolean,
    val switchBindingStrategy: Boolean,
    val gujaratiStatusExplanation: String,
    val englishStatusExplanation: String
) {
    val gujaratiStatusMessage: String
        get() = gujaratiStatusExplanation
}

/**
 * Dedicated state for Continuous Gujarati AI Conversation in Normal Mode (`MODE_NORMAL`).
 */
data class ContinuousConversationState(
    val sessionId: Long = 0L,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val stage: ContinuousConversationStage = ContinuousConversationStage.STOPPED,
    val completedTurnCount: Int = 0,
    val lastRecognizedGujaratiText: String = "",
    val partialGujaratiText: String = "",
    val lastAiGujaratiResponse: String = "",
    val conversationHistory: List<AiConversationTurn> = emptyList(),
    val recentTopics: List<String> = emptyList(),
    val isTtsSpeaking: Boolean = false,
    val readyForSpeechFired: Boolean = false,
    val isReceivingMicAudio: Boolean = false,
    val audioFramesInCurrentTurn: Int = 0,
    val currentMicRmsDb: Float = 0f,
    val peakMicRmsDb: Float = 0f,
    val micActivityLabel: String = "નિષ્ક્રિય (Idle)",
    val postTtsRestartDelayMs: Long = 500L,
    val consecutiveSilenceOrNoMatchCount: Int = 0,
    val consecutiveTransientErrorCount: Int = 0,
    val consecutiveDisconnectCount: Int = 0,
    val maxAutoRetriesPerTurn: Int = 2,
    val lastErrorCode: Int? = null,
    val lastErrorSymbol: String? = null,
    val lastErrorSymbolAndCode: String? = null,
    val lastErrorGujaratiExplanation: String = "",
    val isManualRetryAvailable: Boolean = false,
    val activeScreenName: String = "Settings",
    val wasPausedByBackground: Boolean = false,
    val statusDetailGujarati: String = "સતત ગુજરાતી વાતચીત શરૂ કરવા માટે 'સતત વાતચીત શરૂ કરો' બટન દબાવો.",
    val statusDetailEnglish: String = "Tap 'Start Continuous Conversation' to speak naturally in Gujarati across multiple turns."
) {
    val isActive: Boolean
        get() = isRunning

    val turnCount: Int
        get() = completedTurnCount

    val recognizedGujaratiText: String
        get() = lastRecognizedGujaratiText

    val aiGujaratiResponse: String
        get() = lastAiGujaratiResponse
}

/**
 * Status of the standalone Normal-Mode (`MODE_NORMAL`) Gujarati Microphone Recognition Test.
 */
enum class NormalModeMicTestStage(val label: String) {
    NOT_RUN("Not Run Yet (હજુ ટેસ્ટ કર્યો નથી)"),
    CONNECTING("Connecting — SpeechRecognizer(gu-IN) જોડાઈ રહ્યું છે..."),
    STARTING("Connecting — SpeechRecognizer(gu-IN) જોડાઈ રહ્યું છે..."),
    LISTENING_SPEAK_NOW("Listening in MODE_NORMAL (onReadyForSpeech=YES) — હવે ગુજરાતીમાં બોલો..."),
    PROCESSING("Processing Speech (onEndOfSpeech) — ગુજરાતી શબ્દો ચકાસાઈ રહ્યા છે..."),
    SUCCEEDED("CONFIRMED ✓ — Genuine Gujarati Speech Recognized"),
    FAILED("NOT CONFIRMED — SpeechRecognizer Failed or No Match"),
    STOPPED("Stopped by User (વપરાશકર્તા દ્વારા ટેસ્ટ રોકવામાં આવ્યો)")
}

data class NormalModeGujaratiMicTestState(
    val sessionId: Long = 0L,
    val stage: NormalModeMicTestStage = NormalModeMicTestStage.NOT_RUN,
    val isRunning: Boolean = false,
    val audioModeAtTestStart: String = "MODE_NORMAL",
    val serviceUsed: String = "",
    val readyForSpeechFired: Boolean = false,
    val rmsFramesReceived: Int = 0,
    val peakRmsDb: Float = 0f,
    val partialTranscript: String = "",
    val recognizedGujaratiText: String = "",
    val errorSymbolAndCode: String? = null,
    val gujaratiExplanation: String = "",
    val diagnosticSummary: String = "Tap 'Normal-Mode Gujarati Mic Test' to verify whether your device's SpeechRecognizer genuinely transcribes Gujarati speech."
)

/**
 * Dedicated independent state for the TTS + Microphone Test so its progress and final
 * result remain visible after TTS finishes and never collide with Normal-Mode Mic Test.
 */
data class TtsAndMicTestState(
    val sessionId: Long = 0L,
    val isRunning: Boolean = false,
    val ttsInitStatus: String = "Not started",
    val ttsSpeakingStatus: String = "Not started",
    val micRecognitionStatus: String = "Not started",
    val readyForSpeechFired: Boolean = false,
    val partialTranscript: String = "",
    val finalRecognizedTranscript: String = "",
    val errorSymbolAndCode: String? = null,
    val gujaratiExplanation: String = "",
    val finalOutcomeSummary: String = "Tap 'TTS + Microphone Test' to test Gujarati TTS greeting followed by Gujarati microphone recognition."
)

data class AiConversationTurn(
    val id: String = UUID.randomUUID().toString(),
    val speaker: String, // "AI" or "CALLER" / "USER"
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class ConversationTurnDecision(
    val aiReplyGujarati: String,
    val nextConsentState: AiConsentState,
    val shouldStopImmediately: Boolean,
    val detectedTopic: String = "GENERAL"
)

/**
 * Result of a lightweight pre-flight local [AudioRecord] probe performed before starting
 * [SpeechRecognizer] (and immediately released before `startListening()` so they never contend).
 * NOTE: A passing local [AudioRecord] probe ONLY proves raw hardware microphone read access inside
 * this app's process; it does NOT prove that the external [SpeechRecognizer] service works.
 */
data class LocalAudioRecordProbeResult(
    val hasRecordPermission: Boolean,
    val hasMicrophoneHardware: Boolean,
    val isInCellularCallMode: Boolean,
    val isCallRecorderHoldingMic: Boolean,
    val activeCompetingRecordersCount: Int,
    val initializedSuccessfully: Boolean,
    val startedRecordingSuccessfully: Boolean,
    val audioSourceTested: String,
    val bytesOrSamplesRead: Int,
    val peakPcmAmplitude: Int,
    val isReceivingNonZeroPcm: Boolean,
    val micPermissionStatusText: String,
    val micAvailabilityStatusText: String,
    val audioRecordInitStatusText: String,
    val localMicAccessSummary: String,
    val cellularCallAudioAccessSummary: String,
    val unavailableAudioSourceExplanation: String
)

/**
 * Structured diagnostic breakdown for an Android [SpeechRecognizer] error code.
 */
data class SpeechRecognizerErrorInfo(
    val errorCode: Int,
    val errorSymbol: String,
    val summaryGujarati: String,
    val technicalDetail: String,
    val shouldRecreateRecognizer: Boolean,
    val isTransientSilenceOrTimeout: Boolean
) {
    val formattedCodeAndSymbol: String
        get() = "$errorSymbol (Code $errorCode)"

    val userFacingDiagnostic: String
        get() = "[$formattedCodeAndSymbol] $summaryGujarati — $technicalDetail"
}

data class AiAssistantUiState(
    val activeSessionId: Long = 0L,
    val sessionStartTimeMillis: Long = 0L,
    val activeTestMode: AiTestMode = AiTestMode.NONE,
    val isTestPanelVisible: Boolean = true,
    val status: AiAssistantStatus = AiAssistantStatus.IDLE,
    val consentState: AiConsentState = AiConsentState.AWAITING_CONSENT,
    val userInstruction: String = GujaratiConversationEngine.DEFAULT_USER_INSTRUCTION_GUJARATI,
    val aiEngineModeStatus: String = "Gemini 3.8 Flash (Cloud AI) + On-Device Gujarati Engine",
    val connectionModeSummary: String = "Acoustic Speakerphone + Device Mic (Permitted Android Path)",
    val lastHeardCallerText: String = "",
    val partialCallerText: String = "",
    val lastAiSpokenText: String = "",
    val turns: List<AiConversationTurn> = emptyList(),
    val diagnosticNote: String? = null,
    val isSpeakerphoneEnforced: Boolean = false,
    // Independent TTS Pipeline Diagnostics (AI Speaking)
    val isTtsInitialized: Boolean = false,
    val isTtsSpeaking: Boolean = false,
    val ttsLocaleStatus: String = "માંગણી બાકી (Pending)",
    // Section A: SpeechRecognizer & Gujarati (gu-IN) Service Diagnostics
    val isRecognizerAvailable: Boolean = false,
    val isRecognizerInitialized: Boolean = false,
    val recognizerServiceComponent: String = "",
    val systemDefaultRecognitionService: String = "Checking...",
    val discoveredRecognitionServices: String = "Checking...",
    val activeBindingStrategyLabel: String = RecognizerBindingStrategy.SYSTEM_DEFAULT.description,
    val configuredRecognizerLocale: String = "gu-IN",
    val gujaratiSupportState: GujaratiLanguageSupportState = GujaratiLanguageSupportState.CHECKING,
    val gujaratiLanguageSupported: Boolean? = null,
    val gujaratiLanguageSupportStatus: String = GujaratiLanguageSupportState.CHECKING.labelEnglish,
    val isStartListeningCalled: Boolean = false,
    val isRecognizerReadyForSpeech: Boolean = false,
    val isReceivingMicAudio: Boolean = false,
    val audioFrameCount: Int = 0,
    val lastMicRmsDb: Float = 0f,
    val peakMicRmsDb: Float = 0f,
    val lastErrorCode: Int? = null,
    val lastErrorSymbol: String? = null,
    val lastErrorDetail: String? = null,
    val lastErrorTimestampMillis: Long? = null,
    val lastErrorTimestampFormatted: String = "",
    val lastErrorWithTimestamp: String = "None (કોઈ ભૂલ નથી)",
    val binderDisconnectCount: Int = 0,
    val audioContentionDetail: String = "",
    // 8-Part Detailed Audio Diagnostics Panel Fields
    val micPermissionStatus: String = "Checking...",
    val micAvailabilityStatus: String = "Checking...",
    val audioRecordingInitStatus: String = "Not tested yet",
    val audioRoutingInfo: String = "Checking...",
    val speechRecognizerInitStatus: String = "Idle (Created on demand when listening starts)",
    val recognitionSessionStatus: String = "IDLE (Inactive)",
    val realAudioReachingEngineStatus: String = "NO — Waiting for session",
    // Section B: Local Hardware Microphone Diagnostics (Strictly Separate from SpeechRecognizer & SIM Call)
    val localMicrophoneAccessStatus: String = "Checking local microphone...",
    val audioRecordScopeDisclaimer: String =
        "Note: Local AudioRecord probe only tests raw mic read in this app's process; it does NOT prove SpeechRecognizer IPC or gu-IN transcription works.",
    // Section C: Cellular SIM Call Audio Diagnostics (Strictly Separate from Local Mic)
    val cellularCallAudioAccessStatus: String =
        "SIM Call Downlink (VOICE_DOWNLINK / CAPTURE_AUDIO_OUTPUT): UNAVAILABLE to 3rd-party apps",
    val isSimCallAudioRestrictionDetected: Boolean = false,
    val unavailableAudioSourceExplanation: String = "",
    val simCallRestrictionReportGujarati: String? = null,
    val controlledRetryCount: Int = 0,
    // Section D: Standalone Normal-Mode Gujarati (gu-IN) Microphone Recognition Test
    val normalModeMicTest: NormalModeGujaratiMicTestState = NormalModeGujaratiMicTestState(),
    // Section E: Independent TTS + Microphone Test State
    val ttsAndMicTest: TtsAndMicTestState = TtsAndMicTestState(),
    // Continuous Gujarati AI Conversation State (Normal Mode)
    val continuousConversation: ContinuousConversationState = ContinuousConversationState()
) {
    val isActiveSession: Boolean
        get() = status != AiAssistantStatus.IDLE ||
            normalModeMicTest.isRunning ||
            ttsAndMicTest.isRunning ||
            continuousConversation.isActive
}

/**
 * Pure, deterministic, context-aware Gujarati conversation & consent engine.
 * Handles:
 * 1. Explicit AI identity disclosure and consent request in Gujarati for active calls.
 * 2. Immediate polite termination if the other participant declines consent or asks to stop.
 * 3. Multi-turn context-aware Gujarati conversation in Normal Mode (`evaluateContinuousTurn`):
 *    - Remembers recent user messages and AI responses across 10+ consecutive turns.
 *    - Understands follow-up questions using previous turns and topic memory.
 *    - Avoids repeating the same response unnecessarily.
 *    - Handles empty or unclear speech honestly without inventing transcripts.
 */
object GujaratiConversationEngine {

    const val GUJARATI_LOCALE_TAG = "gu-IN"

    const val DEFAULT_USER_INSTRUCTION_GUJARATI =
        "હું અત્યારે વ્યસ્ત છું અને થોડી વારમાં સામેથી ફોન કરીશ. કૃપા કરીને તમારો સંદેશ જણાવો."

    const val INITIAL_DISCLOSURE_AND_CONSENT_GUJARATI =
        "નમસ્તે, હું AI કૉલ આસિસ્ટન્ટ વાત કરી રહ્યો છું. શું હું આ કૉલમાં આગળ વાત કરી શકું?"

    const val DECLINE_FAREWELL_GUJARATI =
        "ઠીક છે, તમારી સંમતિ નથી એટલે હું AI આસિસ્ટન્ટ બંધ કરું છું. આભાર, નમસ્તે."

    const val UNCLEAR_SPEECH_PROMPT_GUJARATI =
        "તમારો અવાજ થોડો અસ્પષ્ટ સંભળાયો. કૃપા કરીને ફરીથી સ્પષ્ટ ગુજરાતીમાં બોલશો?"

    private val declineKeywords = listOf(
        "ના", "નથી", "નહીં", "નહિ", "વાત નથી કરવી", "બંધ કરો", "મૂકો", "કટ કરો", "બસ રેવા દો",
        "no", "nope", "dont", "don't", "stop", "cancel", "decline", "not interested", "hang up"
    )

    private val affirmativeKeywords = listOf(
        "હા", "હાજી", "ચોક્કસ", "ઓકે", "બોલો", "કહો", "વાંધો નહીં", "ચાલશે", "સારું", "જી",
        "yes", "yeah", "ok", "okay", "sure", "go ahead", "bolo", "ha", "haan", "saru"
    )

    fun isExplicitDecline(rawSpeech: String): Boolean {
        val normalized = rawSpeech.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        val tokens = normalized.split(Regex("[\\s,!.?]+")).filter { it.isNotBlank() }

        if (tokens.firstOrNull() in listOf("ના", "no", "nope", "નહીં", "નથી", "stop")) {
            return true
        }
        return declineKeywords.any { kw ->
            if (kw.length <= 3) {
                tokens.contains(kw)
            } else {
                normalized.contains(kw)
            }
        }
    }

    fun isExplicitAffirmativeOrQuestion(rawSpeech: String): Boolean {
        val normalized = rawSpeech.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        if (affirmativeKeywords.any { normalized.contains(it) }) return true
        return normalized.length >= 4 && !isExplicitDecline(normalized)
    }

    /**
     * Evaluates a user's spoken Gujarati turn during Continuous Conversation in Normal Mode,
     * maintaining full conversation history (`conversationHistory`), understanding follow-up
     * questions from earlier turns, and avoiding repeating responses across 10+ turns.
     */
    fun evaluateContinuousTurn(
        userSpeech: String,
        conversationHistory: List<AiConversationTurn>,
        recentTopics: List<String> = emptyList(),
        userInstruction: String = DEFAULT_USER_INSTRUCTION_GUJARATI
    ): ConversationTurnDecision {
        val cleanInput = userSpeech.trim()
        val effectiveInstruction = userInstruction.trim().ifBlank { DEFAULT_USER_INSTRUCTION_GUJARATI }
        val previousAiReplies = conversationHistory.filter { it.speaker == "AI" }.map { it.text }
        val previousUserMessages = conversationHistory
            .filter { it.speaker != "AI" }
            .map { it.text }
            .let { list ->
                if (list.lastOrNull() == cleanInput && list.size > 1) {
                    list.dropLast(1)
                } else {
                    list
                }
            }

        if (isExplicitDecline(cleanInput)) {
            return ConversationTurnDecision(
                aiReplyGujarati = DECLINE_FAREWELL_GUJARATI,
                nextConsentState = AiConsentState.DECLINED,
                shouldStopImmediately = true,
                detectedTopic = "DECLINE_STOP"
            )
        }

        // Handle unclear or 1-character noise without inventing transcripts
        val meaningfulChars = cleanInput.filter { it.isLetterOrDigit() }
        if (meaningfulChars.length < 2) {
            val unclearCandidates = listOf(
                UNCLEAR_SPEECH_PROMPT_GUJARATI,
                "ક્ષમા કરશો, તમારી વાત સ્પષ્ટ સમજાઈ નહીં. કૃપા કરીને ફરી એક વાર ગુજરાતીમાં કહેશો?",
                "અવાજ થોડો ધીમો આવ્યો છે. આપ શું કહેવા માંગો છો તે સ્પષ્ટ જણાવશો?"
            )
            return ConversationTurnDecision(
                aiReplyGujarati = pickNonRepeating(unclearCandidates, previousAiReplies),
                nextConsentState = AiConsentState.GRANTED,
                shouldStopImmediately = false,
                detectedTopic = "UNCLEAR"
            )
        }

        if (GujaratiAiCloudEngine.isSensitiveCommitmentOrPaymentRequest(cleanInput)) {
            return ConversationTurnDecision(
                aiReplyGujarati = GujaratiAiCloudEngine.buildSafeNoCommitmentGujaratiReply(cleanInput),
                nextConsentState = AiConsentState.GRANTED,
                shouldStopImmediately = false,
                detectedTopic = "SAFETY_NO_COMMITMENT"
            )
        }

        val currentTopic = detectTopic(cleanInput)
        val previousTopic = recentTopics.lastOrNull { it != "GENERAL" && it != "UNCLEAR" }
            ?: previousUserMessages.lastOrNull()?.let { detectTopic(it) }
            ?: "GENERAL"
        val isFollowUp = isFollowUpQuestionOrDetail(cleanInput)
        val resolvedTopic = if (currentTopic == "GENERAL" && isFollowUp && previousTopic != "GENERAL") {
            previousTopic
        } else {
            currentTopic
        }

        val candidates = buildContextAwareCandidates(
            callerSpeech = cleanInput,
            userInstruction = effectiveInstruction,
            includeConsentThanks = false,
            previousUserMessages = previousUserMessages,
            resolvedTopic = resolvedTopic,
            isFollowUp = isFollowUp
        )

        return ConversationTurnDecision(
            aiReplyGujarati = pickNonRepeating(candidates, previousAiReplies),
            nextConsentState = AiConsentState.GRANTED,
            shouldStopImmediately = false,
            detectedTopic = resolvedTopic
        )
    }

    fun evaluateCallerInput(
        callerSpeech: String,
        currentConsent: AiConsentState,
        userInstruction: String,
        previousAiReplies: List<String>,
        previousUserMessages: List<String> = emptyList()
    ): ConversationTurnDecision {
        val cleanInput = callerSpeech.trim()
        val effectiveInstruction = userInstruction.trim().ifBlank { DEFAULT_USER_INSTRUCTION_GUJARATI }

        if (isExplicitDecline(cleanInput)) {
            return ConversationTurnDecision(
                aiReplyGujarati = DECLINE_FAREWELL_GUJARATI,
                nextConsentState = AiConsentState.DECLINED,
                shouldStopImmediately = true,
                detectedTopic = "DECLINE_STOP"
            )
        }

        if (currentConsent == AiConsentState.AWAITING_CONSENT) {
            if (isExplicitAffirmativeOrQuestion(cleanInput)) {
                val onlyShortYes = cleanInput.length <= 12 &&
                    affirmativeKeywords.any { cleanInput.lowercase(Locale.ROOT).contains(it) }

                val candidates = if (onlyShortYes) {
                    listOf(
                        "આભાર. $effectiveInstruction આપનું શું કામ હતું તે જણાવી શકો છો?",
                        "ધન્યવાદ. $effectiveInstruction આપનો કોઈ મહત્વનો સંદેશ હોય તો કહો, હું નોંધી લઉં છું.",
                        "જી આભાર. $effectiveInstruction આપ શું સંદેશ આપવા માંગો છો?"
                    )
                } else {
                    buildContextAwareCandidates(
                        callerSpeech = cleanInput,
                        userInstruction = effectiveInstruction,
                        includeConsentThanks = true,
                        previousUserMessages = previousUserMessages,
                        resolvedTopic = detectTopic(cleanInput),
                        isFollowUp = false
                    )
                }

                return ConversationTurnDecision(
                    aiReplyGujarati = pickNonRepeating(candidates, previousAiReplies),
                    nextConsentState = AiConsentState.GRANTED,
                    shouldStopImmediately = false,
                    detectedTopic = detectTopic(cleanInput)
                )
            } else {
                val promptAgain = pickNonRepeating(
                    listOf(
                        "કૃપા કરીને જણાવશો, શું હું AI આસિસ્ટન્ટ તરીકે આપની સાથે વાત ચાલુ રાખી શકું? હા અથવા ના કહો.",
                        "હું AI કૉલ આસિસ્ટન્ટ છું. આગળ વાત કરવા માટે કૃપા કરીને હા કહો, અથવા બંધ કરવા ના કહો.",
                        "આપની સંમતિ હોય તો જ હું આગળ વાત કરી શકું. કૃપા કરીને હા અથવા ના જણાવો."
                    ),
                    previousAiReplies
                )
                return ConversationTurnDecision(
                    aiReplyGujarati = promptAgain,
                    nextConsentState = AiConsentState.AWAITING_CONSENT,
                    shouldStopImmediately = false,
                    detectedTopic = "AWAITING_CONSENT"
                )
            }
        }

        if (GujaratiAiCloudEngine.isSensitiveCommitmentOrPaymentRequest(cleanInput)) {
            return ConversationTurnDecision(
                aiReplyGujarati = GujaratiAiCloudEngine.buildSafeNoCommitmentGujaratiReply(cleanInput),
                nextConsentState = AiConsentState.GRANTED,
                shouldStopImmediately = false,
                detectedTopic = "SAFETY_NO_COMMITMENT"
            )
        }

        val currentTopic = detectTopic(cleanInput)
        val previousTopic = previousUserMessages.lastOrNull()?.let { detectTopic(it) } ?: "GENERAL"
        val isFollowUp = isFollowUpQuestionOrDetail(cleanInput)
        val resolvedTopic = if (currentTopic == "GENERAL" && isFollowUp && previousTopic != "GENERAL") {
            previousTopic
        } else {
            currentTopic
        }

        val candidates = buildContextAwareCandidates(
            callerSpeech = cleanInput,
            userInstruction = effectiveInstruction,
            includeConsentThanks = false,
            previousUserMessages = previousUserMessages,
            resolvedTopic = resolvedTopic,
            isFollowUp = isFollowUp
        )
        return ConversationTurnDecision(
            aiReplyGujarati = pickNonRepeating(candidates, previousAiReplies),
            nextConsentState = AiConsentState.GRANTED,
            shouldStopImmediately = false,
            detectedTopic = resolvedTopic
        )
    }

    internal fun detectTopic(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            listOf("મેં શું કહ્યું", "હમણાં શું કહ્યું", "શું યાદ છે", "મારો સંદેશ શું", "અગાઉ શું", "શું વાત થઈ")
                .any { lower.contains(it) } -> "MEMORY_RECALL"
            listOf("તમે કોણ", "તમારું નામ", "કોણ બોલે", "શું કામ કરો", "ગુજરાતી આવડે", "who are you")
                .any { lower.contains(it) } -> "AI_IDENTITY"
            listOf("અર્જન્ટ", "જરૂરી", "તાત્કાલિક", "જલ્દી", "ઇમરજન્સી", "urgent", "important", "emergency")
                .any { lower.contains(it) } -> "URGENT"
            listOf("પાર્સલ", "ડિલિવરી", "કુરિયર", "ઓર્ડર", "ગેટ", "નીચે", "સિક્યુરિટી", "delivery", "parcel", "courier", "order")
                .any { lower.contains(it) } -> "DELIVERY"
            listOf("ક્યાં છે", "ક્યાં છો", "વાત કેમ", "ફોન કેમ", "ક્યારે મળશે", "ક્યારે વાત", "ક્યારે ફ્રી", "where", "busy", "free")
                .any { lower.contains(it) } -> "AVAILABILITY"
            listOf("ઓફિસ", "મીટિંગ", "કામ", "પ્રોજેક્ટ", "પેમેન્ટ", "બિલ", "હિસાબ", "ક્લાયન્ટ", "office", "meeting", "work", "payment")
                .any { lower.contains(it) } -> "OFFICE_WORK"
            listOf("કેમ છો", "નમસ્તે", "હેલો", "જય શ્રી કૃષ્ણ", "જય જિનેન્દ્ર", "રામ રામ", "જય માતાજી", "સુપ્રભાત", "hello", "kem cho", "namaste")
                .any { lower.contains(it) } -> "GREETING"
            listOf("કહી દેજો", "ફોન કરવાનું", "સંદેશ", "આભાર", "થેન્ક યુ", "સારું ચાલો", "મૂકું છું", "thank", "bye")
                .any { lower.contains(it) } -> "CLOSING_OR_MESSAGE"
            else -> "GENERAL"
        }
    }

    internal fun isFollowUpQuestionOrDetail(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        val hasDigits = lower.any { it.isDigit() }
        val followUpMarkers = listOf(
            "ક્યાં મૂકું", "કોને આપું", "ક્યારે", "કેટલા વાગ્યે", "વાગ્યે", "કાલે", "આજે", "સવારે", "સાંજે",
            "નંબર", "હા", "જી", "ઓકે", "પછી", "બીજું", "તો શું", "કેવી રીતે", "ત્યાં", "એમને", "તેમને"
        )
        return hasDigits || followUpMarkers.any { lower.contains(it) }
    }

    private fun buildContextAwareCandidates(
        callerSpeech: String,
        userInstruction: String,
        includeConsentThanks: Boolean,
        previousUserMessages: List<String>,
        resolvedTopic: String,
        isFollowUp: Boolean
    ): List<String> {
        val prefix = if (includeConsentThanks) "આભાર. " else ""
        val lastUserMsg = previousUserMessages.lastOrNull()
        val contextReference = if (!lastUserMsg.isNullOrBlank() && isFollowUp) {
            "અગાઉની વાત (\"$lastUserMsg\") સાથે મેં તમારી આ વિગત (\"$callerSpeech\") પણ જોડી દીધી છે. "
        } else {
            ""
        }

        return when (resolvedTopic) {
            "MEMORY_RECALL" -> {
                if (previousUserMessages.isNotEmpty()) {
                    val recentSummary = previousUserMessages.takeLast(3).joinToString("; ") { "\"$it\"" }
                    listOf(
                        "${prefix}હા, મને આપણી અગાઉની વાતચીતમાં થયેલી વાત યાદ છે. તમે જણાવ્યું હતું કે: $recentSummary. મેં આ બધી વિગત સાચવી રાખી છે.",
                        "${prefix}ચોક્કસ, અગાઉની વાતચીતમાં આપે કહ્યું હતું ($recentSummary) તે મારી પાસે નોંધાયેલું છે. શું તેમાં કંઈ સુધારો કરવો છે?",
                        "${prefix}મેં અગાઉની વાતચીતમાં આપના સંદેશા ($recentSummary) ક્રમસર નોંધી લીધા છે અને તેમને ચોક્કસ પહોંચાડીશ."
                    )
                } else {
                    listOf(
                        "${prefix}આપણે હમણાં જ વાતચીત શરૂ કરી છે. કૃપા કરીને આપનો સંદેશ જણાવો, હું બધું યાદ રાખીશ.",
                        "${prefix}હજુ સુધી કોઈ સંદેશ નોંધાયો નથી. આપ શું કહેવા માંગો છો તે જણાવશો?"
                    )
                }
            }

            "AI_IDENTITY" -> listOf(
                "${prefix}હું ગુજરાતી AI કૉલ આસિસ્ટન્ટ છું. હું તમારી વાત ગુજરાતીમાં સમજી શકું છું અને તમારો સંદેશ નોંધી શકું છું. $userInstruction",
                "${prefix}નમસ્તે! હું આ ફોનનો ગુજરાતી AI સહાયક છું. આપ ગુજરાતીમાં સહજ રીતે વાત કરી શકો છો.",
                "${prefix}હું AI આસિસ્ટન્ટ તરીકે આપની મદદ માટે છું. આપનો જે પણ સંદેશ હોય તે ગુજરાતીમાં જણાવો, હું તરત નોંધી લઈશ.",
                "${prefix}જી, હું ગુજરાતી ભાષામાં સતત વાતચીત કરી શકું છું. આપ શું જણાવવા માંગો છો?"
            )

            "URGENT" -> listOf(
                "${prefix}${contextReference}હું સમજી શકું છું કે આ ખૂબ જરૂરી કામ છે. મેં તમારો અર્જન્ટ સંદેશ (\"$callerSpeech\") નોંધી લીધો છે અને તેમને તરત જ જાણ કરીશ.",
                "${prefix}${contextReference}તમારી વાત મહત્વની છે. હું અત્યારે જ પ્રાયોરિટી નોંધ મૂકું છું જેથી તેઓ ફ્રી થતાં જ સૌથી પહેલા તમને કૉલ કરે.",
                "${prefix}${contextReference}ચોક્કસ, આ તાત્કાલિક બાબતની નોંધ લેવાઈ ગઈ છે. શું તમારે બીજી કોઈ વિગત કે સમય ઉમેરવો છે?",
                "${prefix}${contextReference}મેં આ અર્જન્ટ કામ અંગે હાઇ-એલર્ટ નોંધ મૂકી દીધી છે. તેઓ ખૂબ જલ્દી તમારો સંપર્ક કરશે.",
                "${prefix}${contextReference}આપની તાત્કાલિક જરૂરિયાત મેં નોંધી લીધી છે. શું કોઈ વૈકલ્પિક ફોન નંબર આપવો છે?"
            )

            "DELIVERY" -> listOf(
                "${prefix}${contextReference}ડિલિવરી અંગે જાણ કરવા બદલ આભાર. $userInstruction કૃપા કરીને પાર્સલ સિક્યુરિટી ગેટ પર અથવા દરવાજા પાસે મૂકી શકો છો.",
                "${prefix}${contextReference}મેં નોંધી લીધું છે કે આપ ડિલિવરી (\"$callerSpeech\") માટે વાત કરી રહ્યા છો. તેઓને તરત જ જાણ કરવામાં આવી રહી છે.",
                "${prefix}${contextReference}ઓર્ડર અને ડિલિવરીનો સંદેશ નોંધાઈ ગયો છે. શું પાર્સલ માટે કોઈ ઓટીપી કે બીજી સૂચના છે?",
                "${prefix}${contextReference}સારું, પાર્સલ અંગેની તમારી વિગત મેં સાચવી લીધી છે. તમે સિક્યુરિટી પાસે પાર્સલ જમા કરાવી શકો છો.",
                "${prefix}${contextReference}ડિલિવરીની માહિતી મળી ગઈ છે. આભાર, હું તેમને હમણાં જ મેસેજ મોકલી દઉં છું."
            )

            "AVAILABILITY" -> listOf(
                "${prefix}${contextReference}$userInstruction આપનો સંદેશ જણાવશો તો હું તેમને ચોક્કસ પહોંચાડી દઈશ.",
                "${prefix}${contextReference}અત્યારે તેઓ ફોન પર સીધી વાત કરી શકે તેમ નથી કારણ કે: $userInstruction આપ શું કહેવા માંગો છો?",
                "${prefix}${contextReference}તેઓ થોડા સમયમાં ફ્રી થઈને તમને સામેથી કૉલ કરશે. ત્યાં સુધી આપનો સંદેશ અહીં જણાવી શકો છો.",
                "${prefix}${contextReference}હાલમાં તેઓ વ્યસ્ત છે, પણ મેં તમારી પૂછપરછ (\"$callerSpeech\") નોંધી લીધી છે. કેટલા વાગ્યે ફોન કરવાનું કહું?",
                "${prefix}${contextReference}જી, તેઓ કામ પૂરું થતાં જ આપને વળતો કૉલ કરશે. કોઈ ખાસ વાત હોય તો જણાવો."
            )

            "OFFICE_WORK" -> listOf(
                "${prefix}${contextReference}તમારા કામ અને ઓફિસ અંગેની વાત (\"$callerSpeech\") મેં નોંધી લીધી છે. $userInstruction",
                "${prefix}${contextReference}આભાર, કામ અંગેનો તમારો સંદેશ સુરક્ષિત રીતે નોંધાઈ ગયો છે. તેઓ ફ્રી થતાં જ સંપર્ક કરશે.",
                "${prefix}${contextReference}મેં આ ઓફિસ વિગત કૉલ નોટ્સમાં લખી લીધી છે. શું આ બાબતે કોઈ સમય કે નંબર નોંધવાનો છે?",
                "${prefix}${contextReference}મીટિંગ અને કામ બાબતની તમારી સૂચના સાચવી લેવામાં આવી છે. બીજું કંઈ ઉમેરવું છે?",
                "${prefix}${contextReference}ચોક્કસ, ઓફિસના આ કામની વિગત હું તેમને પ્રાથમિકતાથી જણાવી દઈશ."
            )

            "GREETING" -> listOf(
                "${prefix}નમસ્તે, જય શ્રી કૃષ્ણ! $userInstruction આપનું શું કામ હતું તે જણાવશો?",
                "${prefix}નમસ્તે! હું AI કૉલ આસિસ્ટન્ટ છું. $userInstruction આપનો સંદેશ જણાવો, હું નોંધી લઉં છું.",
                "${prefix}હેલો, કેમ છો! આપની વાત હું સાંભળી રહ્યો છું. કૃપા કરીને આપનો સંદેશ કહો.",
                "${prefix}જય માતાજી, નમસ્તે! આપ ગુજરાતીમાં વાત ચાલુ રાખી શકો છો, હું આપની મદદ માટે તૈયાર છું.",
                "${prefix}સુપ્રભાત અને નમસ્તે! આપ શું કહેવા માંગો છો તે જણાવશો?"
            )

            "CLOSING_OR_MESSAGE" -> listOf(
                "${prefix}${contextReference}ચોક્કસ, હું તમારો પૂરો સંદેશ તેમને જણાવી દઈશ અને સામેથી કૉલ કરવાનું કહીશ. આભાર!",
                "${prefix}${contextReference}જી ચોક્કસ, તમારી વાત (\"$callerSpeech\") નોંધાઈ ગઈ છે. વાત કરવા બદલ આપનો ખૂબ ખૂબ આભાર, નમસ્તે.",
                "${prefix}${contextReference}સારું, મેં બધી વિગત નોંધી લીધી છે. તેઓ ટૂંક સમયમાં આપને કૉલ કરશે. ધન્યવાદ!",
                "${prefix}${contextReference}આપનો સંદેશ સુરક્ષિત રીતે સેવ થઈ ગયો છે. બીજી કોઈ વાત હોય તો નિઃસંકોચ કહો.",
                "${prefix}${contextReference}ખૂબ સરસ, હું તેમને યાદ અપાવી દઈશ કે આપે ફોન કર્યો હતો. આભાર!"
            )

            else -> listOf(
                "${prefix}${contextReference}જી, મેં તમારી વાત નોંધી લીધી છે: \"$callerSpeech\". $userInstruction બીજું કંઈ કહેવું છે?",
                "${prefix}${contextReference}આભાર, તમારો સંદેશ (\"$callerSpeech\") નોંધાઈ ગયો છે. તેઓ ફ્રી થતાં જ તમને સામેથી ફોન કરશે.",
                "${prefix}${contextReference}હું સમજી ગયો. મેં તમારો આ સંદેશ (\"$callerSpeech\") કૉલ નોટ્સમાં સાચવી લીધો છે. શું બીજી કોઈ વિગત ઉમેરવી છે?",
                "${prefix}${contextReference}ચોક્કસ, આપે જણાવ્યું કે \"$callerSpeech\" — મેં તેની નોંધ કરી લીધી છે. આગળ કંઈ જણાવવું છે?",
                "${prefix}${contextReference}તમારી વાત (\"$callerSpeech\") સ્પષ્ટ રીતે નોંધાઈ ગઈ છે. હું તેમને આ માહિતી જરૂર પહોંચાડીશ.",
                "${prefix}${contextReference}જી બરાબર, \"$callerSpeech\" અંગેની વિગત મેં ઉમેરી દીધી છે. શું કોઈ સમય કે નંબર પણ આપવો છે?"
            )
        }
    }

    private fun pickNonRepeating(candidates: List<String>, previousReplies: List<String>): String {
        val lastReply = previousReplies.lastOrNull()
        // First prefer a candidate that has NEVER been used in this session
        val neverUsed = candidates.firstOrNull { !previousReplies.contains(it) }
        if (neverUsed != null) return neverUsed
        // Otherwise pick a candidate different from the immediately preceding reply
        val differentFromLast = candidates.firstOrNull { it != lastReply }
        if (differentFromLast != null) {
            val turnNum = previousReplies.size + 1
            return "$differentFromLast (નોંધ #$turnNum)"
        }
        return candidates.first()
    }
}

/**
 * Pure helper mapping every Android [SpeechRecognizer] error constant to its exact symbol,
 * code, Gujarati explanation, and diagnostic root cause.
 * Includes detailed root-cause diagnosis for `ERROR_SERVER_DISCONNECTED (Code 11)` as well as
 * `ERROR_NO_MATCH (Code 7)`, `ERROR_SPEECH_TIMEOUT (Code 6)`, `ERROR_AUDIO (Code 3)`,
 * `ERROR_LANGUAGE_NOT_SUPPORTED (Code 12)`, and `ERROR_LANGUAGE_UNAVAILABLE (Code 13)`.
 */
internal fun describeSpeechRecognizerError(
    errorCode: Int,
    hasReceivedMicAudio: Boolean = false,
    isTtsSpeaking: Boolean = false,
    audioContentionInfo: String = "",
    wasReadyForSpeech: Boolean = false,
    peakRmsDb: Float = 0f,
    activeServiceComponent: String = ""
): SpeechRecognizerErrorInfo {
    val serviceNote = if (activeServiceComponent.isNotBlank()) " [Service: $activeServiceComponent]" else ""
    val contentionSuffix = if (audioContentionInfo.isNotBlank()) " [$audioContentionInfo]$serviceNote" else serviceNote
    val ttsConflictNote = if (isTtsSpeaking) " (TTS output was still active when recognizer ran!)" else ""
    val isInCall = audioContentionInfo.contains("MODE_IN_CALL")
    val micSignalNote = if (hasReceivedMicAudio) {
        val dbNote = if (peakRmsDb > 0f) String.format(Locale.US, " (peak %.1f dB)", peakRmsDb) else ""
        "Mic audio frames were received before error$dbNote."
    } else {
        "Zero speech audio frames reached SpeechRecognizer."
    }

    return when (errorCode) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_NETWORK_TIMEOUT",
            summaryGujarati = "નેટવર્ક ટાઇમઆઉટ: ગુજરાતી સ્પીચ સર્વિસનો સંપર્ક સમયસર થયો નહીં",
            technicalDetail = "Speech service network timed out waiting for Gujarati (gu-IN) recognition.$contentionSuffix",
            shouldRecreateRecognizer = false,
            isTransientSilenceOrTimeout = false
        )
        SpeechRecognizer.ERROR_NETWORK -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_NETWORK",
            summaryGujarati = "નેટવર્ક કનેક્શન ભૂલ: ઓનલાઇન ગુજરાતી ઓળખ માટે ઇન્ટરનેટ અથવા ઓફલાઇન gu-IN પેક જરૂરી છે",
            technicalDetail = "SpeechRecognizer requires network or offline gu-IN language pack.$contentionSuffix",
            shouldRecreateRecognizer = false,
            isTransientSilenceOrTimeout = false
        )
        SpeechRecognizer.ERROR_AUDIO -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_AUDIO",
            summaryGujarati = "માઇક્રોફોન / ઑડિયો સ્ટ્રીમ લૉક (ERROR_AUDIO): સિમ કૉલ બેઝબેન્ડ અથવા કૉલ રેકોર્ડર માઇક્રોફોન વાપરી રહ્યું છે",
            technicalDetail = "AudioRecord inside RecognitionService failed to read microphone stream (cellular baseband or active Call Recorder holding mic lock). $micSignalNote$ttsConflictNote$contentionSuffix",
            shouldRecreateRecognizer = true,
            isTransientSilenceOrTimeout = false
        )
        SpeechRecognizer.ERROR_SERVER -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_SERVER",
            summaryGujarati = "સ્પીચ સર્વર ભૂલ: ગુજરાતી (gu-IN) ઓળખ સર્વર તરફથી ભૂલ આવી",
            technicalDetail = "RecognitionService backend returned an error for locale gu-IN.$contentionSuffix",
            shouldRecreateRecognizer = false,
            isTransientSilenceOrTimeout = false
        )
        SpeechRecognizer.ERROR_CLIENT -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_CLIENT",
            summaryGujarati = "ક્લાયન્ટ / સેશન સ્ટેટ ભૂલ: SpeechRecognizer ફરીથી શરૂ કરવામાં આવી રહ્યું છે",
            technicalDetail = "SpeechRecognizer client state error or overlapping cancel/startListening request. Recreating clean recognizer instance.$ttsConflictNote$contentionSuffix",
            shouldRecreateRecognizer = true,
            isTransientSilenceOrTimeout = false
        )
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_SPEECH_TIMEOUT",
            summaryGujarati = "નિર્ધારિત સમયમાં કોઈ ગુજરાતી અવાજ સંભળાયો નહીં (Speech Timeout)",
            technicalDetail = "No speech input detected within listening window. $micSignalNote$contentionSuffix",
            shouldRecreateRecognizer = !wasReadyForSpeech && !hasReceivedMicAudio,
            isTransientSilenceOrTimeout = true
        )
        SpeechRecognizer.ERROR_NO_MATCH -> {
            if (!hasReceivedMicAudio && !wasReadyForSpeech) {
                val modeInCallExplanation = if (isInCall) {
                    "SpeechRecognizer aborted with ERROR_NO_MATCH before onReadyForSpeech fired because MODE_IN_CALL cellular audio policy blocked/silenced external RecognitionService capture (and hardware AEC cancels speakerphone downlink). "
                } else {
                    "SpeechRecognizer returned ERROR_NO_MATCH before onReadyForSpeech fired. "
                }
                SpeechRecognizerErrorInfo(
                    errorCode = errorCode,
                    errorSymbol = "ERROR_NO_MATCH",
                    summaryGujarati = if (isInCall) {
                        "સિમ કૉલ (MODE_IN_CALL) દરમિયાન SpeechRecognizer સુધી કોઈ અવાજ પહોંચ્યો નહીં (0 Audio Frames)"
                    } else {
                        "SpeechRecognizer સુધી કોઈ અવાજ પહોંચ્યો નહીં (ગુજરાતી શબ્દો મળ્યા નહીં)"
                    },
                    technicalDetail = "${modeInCallExplanation}$micSignalNote$ttsConflictNote$contentionSuffix",
                    shouldRecreateRecognizer = true,
                    isTransientSilenceOrTimeout = true
                )
            } else if (!hasReceivedMicAudio) {
                SpeechRecognizerErrorInfo(
                    errorCode = errorCode,
                    errorSymbol = "ERROR_NO_MATCH",
                    summaryGujarati = "માઇક્રોફોન ખુલ્યો પણ કોઈ સ્પષ્ટ ગુજરાતી અવાજ પહોંચ્યો નહીં (No Match)",
                    technicalDetail = "Recognizer session opened, but zero speech audio frames above threshold reached SpeechRecognizer. $micSignalNote$contentionSuffix",
                    shouldRecreateRecognizer = false,
                    isTransientSilenceOrTimeout = true
                )
            } else {
                SpeechRecognizerErrorInfo(
                    errorCode = errorCode,
                    errorSymbol = "ERROR_NO_MATCH",
                    summaryGujarati = "ગુજરાતી શબ્દો સ્પષ્ટ ઓળખાયા નહીં (No Match) — કૃપા કરીને ફરીથી સ્પષ્ટ ગુજરાતીમાં બોલો",
                    technicalDetail = "Audio was captured but could not be matched to Gujarati (gu-IN) words. $micSignalNote$contentionSuffix",
                    shouldRecreateRecognizer = false,
                    isTransientSilenceOrTimeout = true
                )
            }
        }
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_RECOGNIZER_BUSY",
            summaryGujarati = "SpeechRecognizer પહેલેથી વ્યસ્ત છે (Busy Session)",
            technicalDetail = "RecognitionService is busy with an existing session. Recreating recognizer instance.$ttsConflictNote$contentionSuffix",
            shouldRecreateRecognizer = true,
            isTransientSilenceOrTimeout = false
        )
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_INSUFFICIENT_PERMISSIONS",
            summaryGujarati = "માઇક્રોફોન પરવાનગી અથવા સિસ્ટમ કૉલ ઑડિયો ઍક્સેસ નકારવામાં આવી (Insufficient Permissions)",
            technicalDetail = "App has RECORD_AUDIO, but Android blocked the external RecognitionService from capturing mic/call audio during an active call or background state.$contentionSuffix",
            shouldRecreateRecognizer = true,
            isTransientSilenceOrTimeout = false
        )
        10 -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_TOO_MANY_REQUESTS",
            summaryGujarati = "વધુ પડતી વિનંતીઓ (Too Many Requests)",
            technicalDetail = "SpeechRecognizer rate limit reached; delaying before next listen attempt.$contentionSuffix",
            shouldRecreateRecognizer = true,
            isTransientSilenceOrTimeout = false
        )
        11 -> {
            val disconnectRootCause = if (isInCall) {
                "Android RecognitionService Binder connection dropped (onServiceDisconnected / dead Binder) while in MODE_IN_CALL. On Android 17 / Motorola, Google RecognitionService unbinds when AudioPolicy denies in-call voice capture or when an idle/incompatible service component is bound."
            } else {
                "Android RecognitionService Binder connection dropped (onServiceDisconnected). Target RecognitionService unbound or rejected the session; disconnected recognizer has been released."
            }
            SpeechRecognizerErrorInfo(
                errorCode = errorCode,
                errorSymbol = "ERROR_SERVER_DISCONNECTED",
                summaryGujarati = "Google / Android સ્પીચ સર્વિસ સાથેનું બાઇન્ડર કનેક્શન તૂટી ગયું (ERROR_SERVER_DISCONNECTED - Code 11). ડિસ્કનેક્ટ થયેલ SpeechRecognizer સુરક્ષિત રીતે બંધ કરીને સાફ કરવામાં આવ્યું છે",
                technicalDetail = "$disconnectRootCause $micSignalNote$ttsConflictNote$contentionSuffix",
                shouldRecreateRecognizer = true,
                isTransientSilenceOrTimeout = false
            )
        }
        12 -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_LANGUAGE_NOT_SUPPORTED",
            summaryGujarati = "આ સ્પીચ સર્વિસમાં ગુજરાતી (gu-IN) ભાષા સપોર્ટેડ નથી (Code 12)",
            technicalDetail = "The active system RecognitionService explicitly reported that locale gu-IN is NOT supported.$contentionSuffix",
            shouldRecreateRecognizer = false,
            isTransientSilenceOrTimeout = false
        )
        13 -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_LANGUAGE_UNAVAILABLE",
            summaryGujarati = "ગુજરાતી (gu-IN) ભાષા પેક ફોનમાં ઉપલબ્ધ નથી — કૃપા કરીને Google Voice Settings માં ગુજરાતી ડાઉનલોડ કરો (Code 13)",
            technicalDetail = "Locale gu-IN is recognized by the provider, but its voice model is not downloaded/available on this device.$contentionSuffix",
            shouldRecreateRecognizer = false,
            isTransientSilenceOrTimeout = false
        )
        else -> SpeechRecognizerErrorInfo(
            errorCode = errorCode,
            errorSymbol = "ERROR_UNKNOWN_$errorCode",
            summaryGujarati = "સ્પીચ ઓળખમાં અણધારી ભૂલ (Code $errorCode)",
            technicalDetail = "Unhandled SpeechRecognizer error code $errorCode. $micSignalNote$contentionSuffix",
            shouldRecreateRecognizer = true,
            isTransientSilenceOrTimeout = false
        )
    }
}

/**
 * Pure helper evaluating raw language lists from `RecognitionSupport` or `ACTION_GET_LANGUAGE_DETAILS`
 * without ever falsely reporting `gu-IN` support when metadata is empty or missing.
 */
internal fun evaluateGenuineGujaratiLanguageSupport(
    installedOrSupportedLocales: List<String>,
    pendingDownloadLocales: List<String> = emptyList(),
    metadataWasReturnedByProvider: Boolean
): Pair<GujaratiLanguageSupportState, String> {
    if (!metadataWasReturnedByProvider || (installedOrSupportedLocales.isEmpty() && pendingDownloadLocales.isEmpty())) {
        return GujaratiLanguageSupportState.UNVERIFIED_NO_PROVIDER_METADATA to
            "Unverified — RecognitionService returned no language metadata (run Normal-Mode gu-IN Mic Test to confirm)"
    }

    val matchesActive = installedOrSupportedLocales.any { localeStr ->
        val norm = localeStr.trim().replace('_', '-').lowercase(Locale.ROOT)
        norm == "gu-in" || norm == "gu" || norm.startsWith("gu-")
    }
    if (matchesActive) {
        val matchedTags = installedOrSupportedLocales.filter {
            it.trim().replace('_', '-').lowercase(Locale.ROOT).startsWith("gu")
        }.joinToString()
        return GujaratiLanguageSupportState.VERIFIED_SUPPORTED to
            "Verified Supported ✓ (Provider reported: $matchedTags among ${installedOrSupportedLocales.size} locales)"
    }

    val matchesPending = pendingDownloadLocales.any { localeStr ->
        val norm = localeStr.trim().replace('_', '-').lowercase(Locale.ROOT)
        norm == "gu-in" || norm == "gu" || norm.startsWith("gu-")
    }
    if (matchesPending) {
        return GujaratiLanguageSupportState.VERIFIED_PENDING_DOWNLOAD to
            "Not Ready — gu-IN is listed as pending download in provider"
    }

    val sampleLocales = installedOrSupportedLocales.take(4).joinToString()
    return GujaratiLanguageSupportState.VERIFIED_NOT_SUPPORTED to
        "Not Supported — gu-IN absent from ${installedOrSupportedLocales.size} provider locales (e.g., $sampleLocales)"
}

/**
 * Builds the [RecognizerIntent.ACTION_RECOGNIZE_SPEECH] intent explicitly configured
 * with Gujarati (`gu-IN`) as the primary recognition language.
 */
internal fun buildGujaratiRecognizerIntent(callingPackage: String): Intent {
    val guLocaleTag = Locale.forLanguageTag(GujaratiConversationEngine.GUJARATI_LOCALE_TAG).toLanguageTag()
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, guLocaleTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, guLocaleTag)
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, callingPackage)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
    }
}

/**
 * Pure helper verifying whether an asynchronous callback belongs to the currently active session.
 * Prevents late callbacks from a stopped or superseded session from modifying a newer session.
 */
internal fun isSessionCallbackValid(
    callbackSessionId: Long,
    currentActiveSessionId: Long
): Boolean {
    return callbackSessionId > 0L && callbackSessionId == currentActiveSessionId
}

/**
 * Pure state-transition helpers for the Normal-Mode Gujarati Mic Test so the exact
 * lifecycle (`CONNECTING` -> `LISTENING_SPEAK_NOW` only after `onReadyForSpeech` ->
 * `SUCCEEDED` only on non-empty Gujarati transcript, or `FAILED` with exact error code)
 * can be deterministically verified in unit tests.
 */
internal fun transitionNormalModeMicTestToConnecting(
    sessionId: Long,
    audioModeLabel: String,
    serviceUsed: String
): NormalModeGujaratiMicTestState {
    val isInCall = audioModeLabel.contains("MODE_IN_CALL")
    return NormalModeGujaratiMicTestState(
        sessionId = sessionId,
        stage = NormalModeMicTestStage.CONNECTING,
        isRunning = true,
        audioModeAtTestStart = audioModeLabel,
        serviceUsed = serviceUsed,
        readyForSpeechFired = false,
        rmsFramesReceived = 0,
        peakRmsDb = 0f,
        partialTranscript = "",
        recognizedGujaratiText = "",
        errorSymbolAndCode = null,
        gujaratiExplanation = if (isInCall) {
            "ચેતવણી: હાલમાં સિમ કૉલ ચાલુ છે (MODE_IN_CALL). સાચા નોર્મલ-મોડ ટેસ્ટ માટે કૉલ પૂરો થયા પછી ટેસ્ટ કરો."
        } else {
            "SpeechRecognizer(gu-IN) જોડાઈ રહ્યું છે (Connecting)... માઇક્રોફોન ખુલે (onReadyForSpeech) તેની રાહ જુઓ."
        },
        diagnosticSummary = if (isInCall) {
            "Connecting in MODE_IN_CALL (Warning: active SIM call may block external SpeechRecognizer)."
        } else {
            "Connecting — Binding fresh SpeechRecognizer(gu-IN) in MODE_NORMAL; waiting for onReadyForSpeech()..."
        }
    )
}

internal fun transitionNormalModeMicTestOnReadyForSpeech(
    current: NormalModeGujaratiMicTestState,
    callbackSessionId: Long,
    activeSessionId: Long
): NormalModeGujaratiMicTestState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) || current.sessionId != callbackSessionId) {
        return current
    }
    return current.copy(
        stage = NormalModeMicTestStage.LISTENING_SPEAK_NOW,
        isRunning = true,
        readyForSpeechFired = true,
        gujaratiExplanation = "માઇક્રોફોન તૈયાર છે (onReadyForSpeech=YES) — હવે ગુજરાતીમાં સ્પષ્ટ બોલો...",
        diagnosticSummary = "Listening (onReadyForSpeech=YES) — Speak Gujarati now (હવે ગુજરાતીમાં બોલો)..."
    )
}

internal fun transitionNormalModeMicTestOnPartialResults(
    current: NormalModeGujaratiMicTestState,
    callbackSessionId: Long,
    activeSessionId: Long,
    partialText: String
): NormalModeGujaratiMicTestState {
    val clean = partialText.trim()
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) ||
        current.sessionId != callbackSessionId ||
        clean.isEmpty()
    ) {
        return current
    }
    return current.copy(
        partialTranscript = clean,
        gujaratiExplanation = "સાંભળ્યું (Partial gu-IN): \"$clean\"",
        diagnosticSummary = "Partial Gujarati transcript received: \"$clean\""
    )
}

internal fun transitionNormalModeMicTestOnFinalResults(
    current: NormalModeGujaratiMicTestState,
    callbackSessionId: Long,
    activeSessionId: Long,
    finalRecognizedText: String
): NormalModeGujaratiMicTestState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) || current.sessionId != callbackSessionId) {
        return current
    }
    val clean = finalRecognizedText.trim()
    if (clean.isEmpty()) {
        val noMatchInfo = describeSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_NO_MATCH,
            hasReceivedMicAudio = current.rmsFramesReceived > 0,
            wasReadyForSpeech = current.readyForSpeechFired,
            peakRmsDb = current.peakRmsDb,
            activeServiceComponent = current.serviceUsed
        )
        return current.copy(
            stage = NormalModeMicTestStage.FAILED,
            isRunning = false,
            errorSymbolAndCode = noMatchInfo.formattedCodeAndSymbol,
            gujaratiExplanation = noMatchInfo.summaryGujarati,
            diagnosticSummary = "NOT CONFIRMED — ${noMatchInfo.formattedCodeAndSymbol}: ${noMatchInfo.summaryGujarati}"
        )
    }
    return current.copy(
        stage = NormalModeMicTestStage.SUCCEEDED,
        isRunning = false,
        recognizedGujaratiText = clean,
        errorSymbolAndCode = null,
        gujaratiExplanation = "સફળતાપૂર્વક ગુજરાતી અવાજ ઓળખાયો (CONFIRMED ✓): \"$clean\"",
        diagnosticSummary = "CONFIRMED ✓ — Genuine Gujarati (gu-IN) speech recognized: \"$clean\""
    )
}

internal fun transitionNormalModeMicTestOnError(
    current: NormalModeGujaratiMicTestState,
    callbackSessionId: Long,
    activeSessionId: Long,
    errorInfo: SpeechRecognizerErrorInfo,
    audioRecordProbeSucceeded: Boolean = false
): NormalModeGujaratiMicTestState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) || current.sessionId != callbackSessionId) {
        return current
    }
    val probeNote = if (audioRecordProbeSucceeded) {
        " (Note: Local AudioRecord probe succeeded, but SpeechRecognizer failed with ${errorInfo.formattedCodeAndSymbol} — AudioRecord probe alone does NOT prove SpeechRecognizer works.)"
    } else {
        ""
    }
    return current.copy(
        stage = NormalModeMicTestStage.FAILED,
        isRunning = false,
        errorSymbolAndCode = errorInfo.formattedCodeAndSymbol,
        gujaratiExplanation = "[${errorInfo.formattedCodeAndSymbol}] ${errorInfo.summaryGujarati}",
        diagnosticSummary = "NOT CONFIRMED — ${errorInfo.formattedCodeAndSymbol}: ${errorInfo.summaryGujarati}$probeNote"
    )
}

internal fun startContinuousConversationState(
    sessionId: Long,
    postTtsRestartDelayMs: Long = 500L,
    activeScreenName: String = "Settings"
): ContinuousConversationState {
    val clampedDelay = postTtsRestartDelayMs.coerceIn(200L, 2500L)
    return ContinuousConversationState(
        sessionId = sessionId,
        isRunning = true,
        isPaused = false,
        stage = ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE,
        completedTurnCount = 0,
        lastRecognizedGujaratiText = "",
        partialGujaratiText = "",
        lastAiGujaratiResponse = "",
        conversationHistory = emptyList(),
        recentTopics = emptyList(),
        isTtsSpeaking = false,
        readyForSpeechFired = false,
        isReceivingMicAudio = false,
        audioFramesInCurrentTurn = 0,
        currentMicRmsDb = 0f,
        peakMicRmsDb = 0f,
        micActivityLabel = "માઇક્રોફોન તૈયાર થઈ રહ્યું છે (Awaiting onReadyForSpeech)...",
        postTtsRestartDelayMs = clampedDelay,
        consecutiveSilenceOrNoMatchCount = 0,
        consecutiveTransientErrorCount = 0,
        consecutiveDisconnectCount = 0,
        maxAutoRetriesPerTurn = 2,
        lastErrorCode = null,
        lastErrorSymbol = null,
        lastErrorSymbolAndCode = null,
        lastErrorGujaratiExplanation = "",
        isManualRetryAvailable = false,
        activeScreenName = activeScreenName,
        wasPausedByBackground = false,
        statusDetailGujarati = "SpeechRecognizer(gu-IN) શરૂ થઈ રહ્યું છે — માઇક્રોફોન તૈયાર થાય (onReadyForSpeech) તેની રાહ જુઓ...",
        statusDetailEnglish = "Starting SpeechRecognizer(gu-IN) — waiting for onReadyForSpeech() before displaying Listening..."
    )
}

internal fun transitionContinuousOnReadyForSpeech(
    current: ContinuousConversationState,
    callbackSessionId: Long,
    activeSessionId: Long
): ContinuousConversationState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) ||
        current.sessionId != callbackSessionId ||
        !current.isRunning ||
        current.isPaused
    ) {
        return current
    }
    return current.copy(
        stage = ContinuousConversationStage.LISTENING,
        isTtsSpeaking = false,
        readyForSpeechFired = true,
        isReceivingMicAudio = false,
        audioFramesInCurrentTurn = 0,
        currentMicRmsDb = 0f,
        partialGujaratiText = "",
        isManualRetryAvailable = false,
        micActivityLabel = "માઇક્રોફોન ખુલ્લું છે (onReadyForSpeech=YES) — હવે ગુજરાતીમાં બોલો...",
        statusDetailGujarati = "સાંભળી રહ્યું છે (onReadyForSpeech=YES) — હવે ગુજરાતીમાં સહજ રીતે બોલો...",
        statusDetailEnglish = "Listening (onReadyForSpeech=YES) — Speak in Gujarati now..."
    )
}

internal fun transitionContinuousOnRmsChanged(
    current: ContinuousConversationState,
    callbackSessionId: Long,
    activeSessionId: Long,
    rmsdB: Float
): ContinuousConversationState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) ||
        current.sessionId != callbackSessionId ||
        !current.isRunning ||
        current.isPaused ||
        current.stage != ContinuousConversationStage.LISTENING
    ) {
        return current
    }
    val isVoiceFrame = rmsdB > 1.2f
    val hasVoiceActivity = current.isReceivingMicAudio || isVoiceFrame
    val nextFrames = if (isVoiceFrame) current.audioFramesInCurrentTurn + 1 else current.audioFramesInCurrentTurn
    val nextPeak = maxOf(current.peakMicRmsDb, rmsdB)
    val label = if (hasVoiceActivity) {
        String.format(
            Locale.US,
            "અવાજ સંભળાઈ રહ્યો છે (RMS: %.1f dB, Peak: %.1f dB, Frames: %d)",
            rmsdB,
            nextPeak,
            nextFrames
        )
    } else {
        String.format(Locale.US, "માઇક્રોફોન તૈયાર — અવાજની રાહ (%.1f dB)", rmsdB)
    }
    return current.copy(
        currentMicRmsDb = rmsdB,
        peakMicRmsDb = nextPeak,
        audioFramesInCurrentTurn = nextFrames,
        isReceivingMicAudio = hasVoiceActivity,
        micActivityLabel = label
    )
}

internal fun transitionContinuousOnPartialResults(
    current: ContinuousConversationState,
    callbackSessionId: Long,
    activeSessionId: Long,
    partialText: String
): ContinuousConversationState {
    val clean = partialText.trim()
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) ||
        current.sessionId != callbackSessionId ||
        !current.isRunning ||
        current.isPaused ||
        clean.isEmpty()
    ) {
        return current
    }
    return current.copy(
        stage = ContinuousConversationStage.LISTENING,
        readyForSpeechFired = true,
        isReceivingMicAudio = true,
        audioFramesInCurrentTurn = current.audioFramesInCurrentTurn + 1,
        partialGujaratiText = clean,
        micActivityLabel = "ગુજરાતી શબ્દો ઓળખાઈ રહ્યા છે: \"$clean\"",
        statusDetailGujarati = "સાંભળી રહ્યું છે: \"$clean\"",
        statusDetailEnglish = "Listening (Partial gu-IN): \"$clean\""
    )
}

internal fun transitionContinuousOnEndOfSpeech(
    current: ContinuousConversationState,
    callbackSessionId: Long,
    activeSessionId: Long
): ContinuousConversationState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) ||
        current.sessionId != callbackSessionId ||
        !current.isRunning ||
        current.isPaused
    ) {
        return current
    }
    return current.copy(
        stage = ContinuousConversationStage.PROCESSING,
        readyForSpeechFired = false,
        isReceivingMicAudio = false,
        currentMicRmsDb = 0f,
        micActivityLabel = "અવાજ પૂર્ણ થયો — ગુજરાતી વાક્ય પ્રોસેસ થઈ રહ્યું છે...",
        statusDetailGujarati = "પ્રોસેસિંગ ચાલુ છે — ગુજરાતી વાક્ય સમજીને જવાબ તૈયાર થઈ રહ્યો છે...",
        statusDetailEnglish = "Processing spoken Gujarati and generating contextual response..."
    )
}

internal fun transitionContinuousOnFinalSpeechRecognized(
    current: ContinuousConversationState,
    callbackSessionId: Long,
    activeSessionId: Long,
    recognizedText: String
): ContinuousConversationState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) ||
        current.sessionId != callbackSessionId ||
        !current.isRunning ||
        current.isPaused
    ) {
        return current
    }
    val clean = recognizedText.trim()
    if (clean.isEmpty()) {
        // Never fabricate a transcript on empty speech
        return current
    }

    val userTurn = AiConversationTurn(
        speaker = "USER",
        text = clean
    )
    val historyWithUser = (current.conversationHistory + userTurn).takeLast(60)

    return current.copy(
        stage = ContinuousConversationStage.PROCESSING,
        lastRecognizedGujaratiText = clean,
        partialGujaratiText = "",
        conversationHistory = historyWithUser,
        isReceivingMicAudio = false,
        readyForSpeechFired = false,
        currentMicRmsDb = 0f,
        consecutiveSilenceOrNoMatchCount = 0,
        consecutiveTransientErrorCount = 0,
        consecutiveDisconnectCount = 0,
        lastErrorCode = null,
        lastErrorSymbol = null,
        lastErrorSymbolAndCode = null,
        lastErrorGujaratiExplanation = "",
        isManualRetryAvailable = false,
        micActivityLabel = "ગુજરાતી વાક્ય ઓળખાયું — AI જવાબ તૈયાર થઈ રહ્યો છે...",
        statusDetailGujarati = "તમારી વાત સમજાઈ (\"$clean\") — AI જવાબ બોલવાની તૈયારી...",
        statusDetailEnglish = "Recognized Gujarati speech — preparing contextual AI response..."
    )
}

internal fun transitionContinuousOnSpeakingStarted(
    current: ContinuousConversationState,
    callbackSessionId: Long,
    activeSessionId: Long,
    aiReplyGujarati: String,
    detectedTopic: String = "GENERAL"
): ContinuousConversationState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) ||
        current.sessionId != callbackSessionId ||
        !current.isRunning ||
        current.isPaused
    ) {
        return current
    }
    val cleanReply = aiReplyGujarati.trim().ifBlank { current.lastAiGujaratiResponse }
    val updatedHistory = if (cleanReply.isNotEmpty() &&
        (current.conversationHistory.lastOrNull()?.speaker != "AI" ||
            current.conversationHistory.lastOrNull()?.text != cleanReply)
    ) {
        (current.conversationHistory + AiConversationTurn(speaker = "AI", text = cleanReply)).takeLast(60)
    } else {
        current.conversationHistory
    }
    val updatedTopics = (current.recentTopics + detectedTopic).takeLast(16)
    val nextTurnCount = current.completedTurnCount + 1

    return current.copy(
        stage = ContinuousConversationStage.AI_SPEAKING,
        completedTurnCount = nextTurnCount,
        lastAiGujaratiResponse = cleanReply,
        conversationHistory = updatedHistory,
        recentTopics = updatedTopics,
        isTtsSpeaking = true,
        readyForSpeechFired = false,
        isReceivingMicAudio = false,
        currentMicRmsDb = 0f,
        micActivityLabel = "AI ગુજરાતીમાં બોલી રહ્યું છે (માઇક બંધ — TTS પૂર્ણ થયા પછી આપમેળે શરૂ થશે)",
        statusDetailGujarati = "AI ગુજરાતીમાં જવાબ બોલી રહ્યું છે (TTS પૂર્ણ થયા પછી માઇક આપમેળે શરૂ થશે)...",
        statusDetailEnglish = "AI Speaking in Gujarati (Mic is off; will auto-restart after TTS completes)..."
    )
}

internal fun transitionContinuousOnTtsDone(
    current: ContinuousConversationState,
    callbackSessionId: Long,
    activeSessionId: Long,
    shouldStopSession: Boolean = false
): ContinuousConversationState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) ||
        current.sessionId != callbackSessionId ||
        !current.isRunning ||
        current.isPaused
    ) {
        return current
    }
    if (shouldStopSession) {
        return current.copy(
            isRunning = false,
            isPaused = false,
            stage = ContinuousConversationStage.STOPPED,
            isTtsSpeaking = false,
            readyForSpeechFired = false,
            isReceivingMicAudio = false,
            currentMicRmsDb = 0f,
            micActivityLabel = "વાતચીત પૂર્ણ થઈ (Stopped)",
            statusDetailGujarati = "વાતચીત પૂર્ણ થઈ અને બંધ કરવામાં આવી.",
            statusDetailEnglish = "Conversation completed and stopped."
        )
    }
    return current.copy(
        stage = ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE,
        isTtsSpeaking = false,
        readyForSpeechFired = false,
        isReceivingMicAudio = false,
        currentMicRmsDb = 0f,
        micActivityLabel = "AI બોલવાનું પૂર્ણ — ${current.postTtsRestartDelayMs}ms પછી માઇક આપમેળે શરૂ થશે...",
        statusDetailGujarati = "AI બોલવાનું પૂર્ણ થયું — તમારા જવાબની રાહ (${current.postTtsRestartDelayMs}ms પછી માઇક આપમેળે શરૂ થશે)...",
        statusDetailEnglish = "Waiting for Your Response (Restarting gu-IN mic after ${current.postTtsRestartDelayMs}ms echo guard)..."
    )
}

internal fun computeContinuousErrorRecoveryPlan(
    errorCode: Int,
    current: ContinuousConversationState,
    hasAlternativeBindingStrategy: Boolean = false
): ContinuousErrorRecoveryPlan {
    val errorInfo = describeSpeechRecognizerError(errorCode = errorCode)
    val isSilenceOrNoMatch = errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
        errorCode == SpeechRecognizer.ERROR_NO_MATCH
    val isDisconnect = errorCode == 11

    val currentCategoryCount = when {
        isSilenceOrNoMatch -> current.consecutiveSilenceOrNoMatchCount
        isDisconnect -> current.consecutiveDisconnectCount
        else -> current.consecutiveTransientErrorCount
    }
    val nextCount = currentCategoryCount + 1

    // Fatal / non-recoverable errors (Missing permission or language unsupported/unavailable)
    if (errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
        errorCode == 12 ||
        errorCode == 13 ||
        errorCode == -1
    ) {
        return ContinuousErrorRecoveryPlan(
            shouldAutoRetry = false,
            retryDelayMs = 0L,
            updatedConsecutiveErrorCount = nextCount,
            isManualRetryAvailable = true,
            switchBindingStrategy = false,
            gujaratiStatusExplanation = "[${errorInfo.formattedCodeAndSymbol}] ${errorInfo.summaryGujarati} — કૃપા કરીને 'ફરી સાંભળો' (Retry) દબાવો.",
            englishStatusExplanation = "${errorInfo.formattedCodeAndSymbol} — Auto-retry stopped; manual retry available."
        )
    }

    val effectiveMaxRetries = when {
        isSilenceOrNoMatch -> current.maxAutoRetriesPerTurn
        errorCode == SpeechRecognizer.ERROR_AUDIO -> minOf(1, current.maxAutoRetriesPerTurn)
        isDisconnect -> minOf(1, current.maxAutoRetriesPerTurn)
        else -> current.maxAutoRetriesPerTurn
    }

    if (currentCategoryCount < effectiveMaxRetries) {
        val delayMs = when (errorCode) {
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH -> 900L
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1100L
            11 -> 1200L
            SpeechRecognizer.ERROR_AUDIO -> 1400L
            else -> 1200L
        }
        val switchStrategy = (isDisconnect && hasAlternativeBindingStrategy)
        val retryBadge = "(Auto-Retry $nextCount/$effectiveMaxRetries)"
        val gujaratiMsg = when (errorCode) {
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "કોઈ અવાજ સંભળાયો નહીં (ERROR_SPEECH_TIMEOUT Code 6) — ફરી સાંભળવાની તૈયારી $retryBadge..."
            SpeechRecognizer.ERROR_NO_MATCH ->
                "ગુજરાતી શબ્દો સ્પષ્ટ ઓળખાયા નહીં (ERROR_NO_MATCH Code 7) — કૃપા કરીને ફરી બોલો $retryBadge..."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "SpeechRecognizer વ્યસ્ત હતું (ERROR_RECOGNIZER_BUSY Code 8) — સાફ કરીને ફરી શરૂ થઈ રહ્યું છે $retryBadge..."
            SpeechRecognizer.ERROR_CLIENT ->
                "ક્લાયન્ટ સેશન રીસેટ (ERROR_CLIENT Code 5) — ફરી સાંભળવાની તૈયારી $retryBadge..."
            SpeechRecognizer.ERROR_AUDIO ->
                "માઇક્રોફોન સ્ટ્રીમ ભૂલ (ERROR_AUDIO Code 3) — ફરી પ્રયાસ $retryBadge..."
            11 ->
                "સ્પીચ સર્વિસ ડિસ્કનેક્ટ થઈ (ERROR_SERVER_DISCONNECTED Code 11) — નવું બાઇન્ડિંગ શરૂ થઈ રહ્યું છે $retryBadge..."
            else ->
                "[${errorInfo.formattedCodeAndSymbol}] ${errorInfo.summaryGujarati} $retryBadge..."
        }
        return ContinuousErrorRecoveryPlan(
            shouldAutoRetry = true,
            retryDelayMs = delayMs,
            updatedConsecutiveErrorCount = nextCount,
            isManualRetryAvailable = true,
            switchBindingStrategy = switchStrategy,
            gujaratiStatusExplanation = gujaratiMsg,
            englishStatusExplanation = "${errorInfo.formattedCodeAndSymbol} — Scheduling controlled retry $nextCount/$effectiveMaxRetries after ${delayMs}ms."
        )
    }

    return ContinuousErrorRecoveryPlan(
        shouldAutoRetry = false,
        retryDelayMs = 0L,
        updatedConsecutiveErrorCount = nextCount,
        isManualRetryAvailable = true,
        switchBindingStrategy = false,
        gujaratiStatusExplanation = "[${errorInfo.formattedCodeAndSymbol}] ${errorInfo.summaryGujarati} — ઓટો-રીટ્રાય મર્યાદા પૂર્ણ થઈ. ફરી વાત કરવા 'ફરી સાંભળો (Manual Retry)' દબાવો.",
        englishStatusExplanation = "${errorInfo.formattedCodeAndSymbol} — Max auto-retries ($effectiveMaxRetries) reached; tap Manual Retry to continue."
    )
}

internal fun transitionContinuousOnError(
    current: ContinuousConversationState,
    callbackSessionId: Long,
    activeSessionId: Long,
    errorInfo: SpeechRecognizerErrorInfo,
    recoveryPlan: ContinuousErrorRecoveryPlan
): ContinuousConversationState {
    if (!isSessionCallbackValid(callbackSessionId, activeSessionId) ||
        current.sessionId != callbackSessionId ||
        !current.isRunning ||
        current.isPaused
    ) {
        return current
    }
    val errorCode = errorInfo.errorCode
    val isSilenceOrNoMatch = errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
        errorCode == SpeechRecognizer.ERROR_NO_MATCH
    val isDisconnect = errorCode == 11

    val nextSilenceCount = if (isSilenceOrNoMatch) {
        current.consecutiveSilenceOrNoMatchCount + 1
    } else {
        current.consecutiveSilenceOrNoMatchCount
    }
    val nextDisconnectCount = if (isDisconnect) {
        current.consecutiveDisconnectCount + 1
    } else {
        current.consecutiveDisconnectCount
    }
    val nextTransientCount = if (!isSilenceOrNoMatch && !isDisconnect) {
        current.consecutiveTransientErrorCount + 1
    } else {
        current.consecutiveTransientErrorCount
    }

    val nextStage = if (recoveryPlan.shouldAutoRetry) {
        ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE
    } else {
        ContinuousConversationStage.STOPPED
    }

    return current.copy(
        isRunning = recoveryPlan.shouldAutoRetry,
        isPaused = false,
        stage = nextStage,
        isTtsSpeaking = false,
        readyForSpeechFired = false,
        isReceivingMicAudio = false,
        currentMicRmsDb = 0f,
        partialGujaratiText = "",
        consecutiveSilenceOrNoMatchCount = nextSilenceCount,
        consecutiveDisconnectCount = nextDisconnectCount,
        consecutiveTransientErrorCount = nextTransientCount,
        lastErrorCode = errorCode,
        lastErrorSymbol = errorInfo.errorSymbol,
        lastErrorSymbolAndCode = errorInfo.formattedCodeAndSymbol,
        lastErrorGujaratiExplanation = recoveryPlan.gujaratiStatusExplanation,
        isManualRetryAvailable = recoveryPlan.isManualRetryAvailable,
        micActivityLabel = if (recoveryPlan.shouldAutoRetry) {
            "ફરી સાંભળવાની તૈયારી (${recoveryPlan.retryDelayMs}ms બાદ માઇક શરૂ થશે)..."
        } else {
            "માઇક્રોફોન બંધ (${errorInfo.formattedCodeAndSymbol} — Manual Retry ઉપલબ્ધ)"
        },
        statusDetailGujarati = recoveryPlan.gujaratiStatusExplanation,
        statusDetailEnglish = recoveryPlan.englishStatusExplanation
    )
}

internal fun transitionContinuousOnPause(
    current: ContinuousConversationState,
    pausedByBackground: Boolean = false,
    newSessionId: Long = current.sessionId
): ContinuousConversationState {
    if (!current.isRunning && current.stage == ContinuousConversationStage.STOPPED) return current
    return current.copy(
        sessionId = newSessionId,
        isRunning = true,
        isPaused = true,
        stage = ContinuousConversationStage.PAUSED,
        isTtsSpeaking = false,
        readyForSpeechFired = false,
        isReceivingMicAudio = false,
        currentMicRmsDb = 0f,
        partialGujaratiText = "",
        wasPausedByBackground = pausedByBackground,
        micActivityLabel = "થોભાવ્યું છે (Paused — માઇક્રોફોન મુક્ત)",
        statusDetailGujarati = if (pausedByBackground) {
            "એપ બેકગ્રાઉન્ડમાં જતાં સતત વાતચીત સુરક્ષિત રીતે થોભાવવામાં આવી છે (Paused)."
        } else {
            "સતત ગુજરાતી વાતચીત થોભાવવામાં આવી છે (Paused) — આગળ વધવા 'ચાલુ રાખો (Resume)' દબાવો."
        },
        statusDetailEnglish = if (pausedByBackground) {
            "Paused safely while app was in background — tap Resume to continue."
        } else {
            "Paused by user — tap Resume to continue conversation."
        }
    )
}

internal fun transitionContinuousOnResume(
    current: ContinuousConversationState,
    newSessionId: Long
): ContinuousConversationState {
    return current.copy(
        sessionId = newSessionId,
        isRunning = true,
        isPaused = false,
        stage = ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE,
        isTtsSpeaking = false,
        readyForSpeechFired = false,
        isReceivingMicAudio = false,
        currentMicRmsDb = 0f,
        partialGujaratiText = "",
        consecutiveSilenceOrNoMatchCount = 0,
        consecutiveTransientErrorCount = 0,
        consecutiveDisconnectCount = 0,
        lastErrorCode = null,
        lastErrorSymbol = null,
        lastErrorSymbolAndCode = null,
        lastErrorGujaratiExplanation = "",
        isManualRetryAvailable = false,
        wasPausedByBackground = false,
        micActivityLabel = "માઇક્રોફોન ફરી જોડાઈ રહ્યું છે (Awaiting onReadyForSpeech)...",
        statusDetailGujarati = "વાતચીત ફરી શરૂ થઈ — માઇક્રોફોન તૈયાર થાય (onReadyForSpeech) તેની રાહ જુઓ...",
        statusDetailEnglish = "Resuming conversation — waiting for onReadyForSpeech()..."
    )
}

internal fun transitionContinuousOnStop(
    current: ContinuousConversationState,
    newSessionId: Long = current.sessionId
): ContinuousConversationState {
    return current.copy(
        sessionId = newSessionId,
        isRunning = false,
        isPaused = false,
        stage = ContinuousConversationStage.STOPPED,
        isTtsSpeaking = false,
        readyForSpeechFired = false,
        isReceivingMicAudio = false,
        currentMicRmsDb = 0f,
        partialGujaratiText = "",
        isManualRetryAvailable = false,
        wasPausedByBackground = false,
        micActivityLabel = "બંધ (Stopped — માઇક્રોફોન અને TTS મુક્ત)",
        statusDetailGujarati = "સતત ગુજરાતી વાતચીત બંધ કરવામાં આવી છે (Stopped). અગાઉની વાતચીત નીચે સુરક્ષિત છે.",
        statusDetailEnglish = "Continuous Gujarati conversation stopped. Conversation history preserved."
    )
}

internal fun transitionContinuousOnScreenSwitched(
    current: ContinuousConversationState,
    newScreenName: String
): ContinuousConversationState {
    if (newScreenName.isBlank()) return current
    return current.copy(activeScreenName = newScreenName)
}

private data class PendingContinuousUtterance(
    val text: String,
    val utteranceId: String,
    val shouldStopImmediately: Boolean,
    val boundSessionId: Long
)

/**
 * Manages live Gujarati AI Call Assistant sessions, Continuous Gujarati AI Conversation in Normal Mode,
 * and standalone Normal-Mode Gujarati microphone tests:
 * 1. Continuous Gujarati AI Conversation (`startContinuousConversation`):
 *    - Implements the 10-step continuous cycle: Start -> SpeechRecognizer(gu-IN) -> Wait for `onReadyForSpeech()`
 *      before showing `Listening` -> Recognize speech -> Process text -> Generate contextual Gujarati reply ->
 *      Speak via Gujarati TTS -> Wait for `UtteranceProgressListener.onDone()` -> Wait configurable post-TTS delay ->
 *      Automatically restart `SpeechRecognizer(gu-IN)` -> Repeat until Pause or Stop.
 * 2. Prevents test panel / dialog auto-closing:
 *    - Keeps `isTestPanelVisible = true` across all state transitions.
 *    - Never clears completed test or conversation results when a session stops.
 * 3. Enforces strict `sessionId` isolation (`activeSessionId`):
 *    - Every asynchronous callback verifies `boundSessionId == activeSessionId` before mutating state.
 */
object AiCallAssistantManager {

    private const val PREFS_NAME = "coloros_ai_call_assistant_prefs"
    private const val KEY_USER_INSTRUCTION = "gujarati_user_instruction"
    private const val ECHO_GUARD_DELAY_MS = 450L
    private const val DEFAULT_CONTINUOUS_POST_TTS_DELAY_MS = 500L
    private const val READY_FOR_SPEECH_WATCHDOG_MS = 6000L
    private const val SPEECH_RECOGNIZED_DISPLAY_MS = 480L
    private const val AUTO_RETRY_DELAY_MS = 2500L
    private const val DISCONNECT_SWITCH_RETRY_DELAY_MS = 1100L
    private const val MAX_CONTROLLED_RETRIES_IN_CALL_ZERO_FRAMES = 1
    private const val MAX_DISCONNECT_FALLBACK_RETRIES = 1

    private val mainHandler: Handler? by lazy {
        try {
            Looper.getMainLooper()?.let { Handler(it) }
        } catch (_: Throwable) {
            null
        }
    }
    private val probeExecutor by lazy { Executors.newSingleThreadExecutor() }
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    @Volatile
    private var lastTtsFinishedTimestampMillis: Long = 0L

    // Monotonically increasing session ID to isolate callbacks across sessions
    @Volatile
    private var activeSessionId: Long = 0L

    @Synchronized
    private fun allocateNextSessionId(): Long {
        activeSessionId += 1L
        return activeSessionId
    }

    fun getCurrentSessionId(): Long = activeSessionId

    // Pipeline 1: TextToSpeech (AI Speaking)
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady: Boolean = false
    private var isTtsCurrentlySpeaking: Boolean = false
    private var ttsAudioFocusRequest: AudioFocusRequest? = null
    private var pendingContinuousUtterance: PendingContinuousUtterance? = null
    private var configuredPostTtsDelayMs: Long = DEFAULT_CONTINUOUS_POST_TTS_DELAY_MS

    // Pipeline 2: SpeechRecognizer (AI Listening — created on demand right before startListening)
    private var speechRecognizer: SpeechRecognizer? = null
    private var supportProbeRecognizer: SpeechRecognizer? = null
    private var currentBindingStrategy: RecognizerBindingStrategy = RecognizerBindingStrategy.SYSTEM_DEFAULT
    private var latestServiceInspection: RecognitionServiceInspectionReport? = null
    private var isRecognitionSessionInFlight: Boolean = false
    private var isListeningActive: Boolean = false
    private var isStandaloneNormalMicTestMode: Boolean = false
    private var controlledRetryCount: Int = 0
    private var consecutiveZeroFrameFailures: Int = 0
    private var consecutiveDisconnectFailures: Int = 0
    private var lastProbeResult: LocalAudioRecordProbeResult? = null
    private var activeWatchdogRunnable: Runnable? = null
    private var activePendingRestartRunnable: Runnable? = null
    private var lastProcessedUtteranceSignature: String = ""

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private fun cancelPendingRestartRunnable() {
        activePendingRestartRunnable?.let { mainHandler?.removeCallbacks(it) }
        activePendingRestartRunnable = null
    }

    private fun scheduleReadyForSpeechWatchdog(boundSessionId: Long, boundTestMode: AiTestMode) {
        activeWatchdogRunnable?.let { mainHandler?.removeCallbacks(it) }
        val runnable = Runnable {
            if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@Runnable
            val current = _uiState.value
            if (current.isStartListeningCalled && !current.isRecognizerReadyForSpeech) {
                releaseDisconnectedOrStuckRecognizer()
                val contention = appContext?.let { inspectAudioAndMicContention(it) }.orEmpty()
                val isInCall = contention.contains("MODE_IN_CALL")
                val timeStr = formatCurrentTime()
                val errorSymbol = "WATCHDOG_START_TIMEOUT"
                val errorCode = -2
                val detail = if (isInCall) {
                    "SpeechRecognizer.startListening() was called during MODE_IN_CALL, but onReadyForSpeech() never fired within 6.0s because Android 17 / Motorola AudioPolicy blocked external RecognitionService mic access. [$contention]"
                } else {
                    "SpeechRecognizer.startListening() was called, but onReadyForSpeech() did not fire within 6.0s. [$contention]"
                }
                val gujSummary = if (isInCall) {
                    "ચાલુ સિમ કૉલ (MODE_IN_CALL) દરમિયાન Android 17 સિસ્ટમે SpeechRecognizer ને માઇક્રોફોન સ્ટ્રીમ ખોલવા દીધો નહીં (onReadyForSpeech Timeout)."
                } else {
                    "SpeechRecognizer શરૂ થયું પણ 6 સેકન્ડમાં માઇક્રોફોન સ્ટ્રીમ ખુલ્યો નહીં (onReadyForSpeech Timeout)."
                }

                if (boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION) {
                    handleSpeechRecognizerError(
                        errorCode = SpeechRecognizer.ERROR_AUDIO,
                        boundSessionId = boundSessionId,
                        boundTestMode = AiTestMode.CONTINUOUS_CONVERSATION
                    )
                } else if (boundTestMode == AiTestMode.NORMAL_MODE_MIC_TEST || isStandaloneNormalMicTestMode) {
                    isStandaloneNormalMicTestMode = false
                    _uiState.update { state ->
                        state.copy(
                            isTestPanelVisible = true,
                            status = AiAssistantStatus.RECOGNITION_FAILED,
                            isStartListeningCalled = false,
                            isRecognizerReadyForSpeech = false,
                            recognitionSessionStatus = "NORMAL_MIC_TEST_TIMEOUT ($errorSymbol Code $errorCode)",
                            lastErrorCode = errorCode,
                            lastErrorSymbol = errorSymbol,
                            lastErrorDetail = detail,
                            lastErrorTimestampMillis = System.currentTimeMillis(),
                            lastErrorTimestampFormatted = timeStr,
                            lastErrorWithTimestamp = "$errorSymbol (Code $errorCode) at $timeStr",
                            diagnosticNote = "[$errorSymbol (Code $errorCode)] $gujSummary",
                            normalModeMicTest = state.normalModeMicTest.copy(
                                stage = NormalModeMicTestStage.FAILED,
                                isRunning = false,
                                errorSymbolAndCode = "$errorSymbol (Code $errorCode)",
                                gujaratiExplanation = "[$errorSymbol (Code $errorCode)] $gujSummary",
                                diagnosticSummary = "NOT CONFIRMED — SpeechRecognizer did not fire onReadyForSpeech within 6.0s ($detail)"
                            )
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isTestPanelVisible = true,
                            status = AiAssistantStatus.RECOGNITION_FAILED,
                            isStartListeningCalled = false,
                            isRecognizerReadyForSpeech = false,
                            recognitionSessionStatus = "FAILED (onReadyForSpeech Timeout in ${if (isInCall) "MODE_IN_CALL" else "MODE_NORMAL"})",
                            realAudioReachingEngineStatus = "NO — Zero audio frames reached SpeechRecognizer (onReadyForSpeech=NO)",
                            lastErrorCode = errorCode,
                            lastErrorSymbol = errorSymbol,
                            lastErrorDetail = detail,
                            lastErrorTimestampMillis = System.currentTimeMillis(),
                            lastErrorTimestampFormatted = timeStr,
                            lastErrorWithTimestamp = "$errorSymbol (Code $errorCode) at $timeStr",
                            audioContentionDetail = contention,
                            audioRoutingInfo = contention,
                            isSimCallAudioRestrictionDetected = isInCall || state.isSimCallAudioRestrictionDetected,
                            unavailableAudioSourceExplanation = if (isInCall) {
                                buildSimCallUnavailableSourcesExplanation()
                            } else {
                                state.unavailableAudioSourceExplanation
                            },
                            simCallRestrictionReportGujarati = if (isInCall) {
                                buildSimCallRestrictionReportGujarati()
                            } else {
                                state.simCallRestrictionReportGujarati
                            },
                            diagnosticNote = "[$errorSymbol (Code $errorCode)] $gujSummary",
                            ttsAndMicTest = state.ttsAndMicTest.copy(
                                isRunning = false,
                                micRecognitionStatus = "FAILED — $errorSymbol (Code $errorCode)",
                                errorSymbolAndCode = "$errorSymbol (Code $errorCode)",
                                gujaratiExplanation = gujSummary,
                                finalOutcomeSummary = "TTS succeeded, but Microphone Recognition failed: [$errorSymbol (Code $errorCode)] $gujSummary"
                            )
                        )
                    }
                }
            }
        }
        activeWatchdogRunnable = runnable
        mainHandler?.postDelayed(runnable, READY_FOR_SPEECH_WATCHDOG_MS)
    }

    private fun cancelReadyForSpeechWatchdog() {
        activeWatchdogRunnable?.let { mainHandler?.removeCallbacks(it) }
        activeWatchdogRunnable = null
    }

    private fun formatCurrentTime(millis: Long = System.currentTimeMillis()): String {
        return try {
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(millis))
        } catch (_: Exception) {
            ""
        }
    }

    internal fun buildSimCallUnavailableSourcesExplanation(): String {
        return "1) VOICE_DOWNLINK / VOICE_CALL (Remote SIM caller audio): UNAVAILABLE — requires privileged system permission CAPTURE_AUDIO_OUTPUT not granted to 3rd-party apps on Android 17.\n" +
            "2) VOICE_RECOGNITION in MODE_IN_CALL (External RecognitionService): RESTRICTED — Android 17 / Motorola AudioPolicy silences, blocks, or disconnects (Code 11 / Code 7) external SpeechRecognizer processes while the cellular modem owns MODE_IN_CALL, and hardware DSP Acoustic Echo Cancellation (AEC) subtracts speakerphone output from the microphone."
    }

    internal fun buildSimCallRestrictionReportGujarati(): String {
        return "Android 17 / Motorola સિમ કૉલ ઑડિયો મર્યાદા: થર્ડ-પાર્ટી ડાઈલર એપને સિમ કૉલનો સામેનો અવાજ (VOICE_DOWNLINK / CAPTURE_AUDIO_OUTPUT) સીધો વાંચવાની પરવાનગી હોતી નથી, અને MODE_IN_CALL દરમિયાન હાર્ડવેર Echo Cancellation (AEC) તથા AudioPolicy બાહ્ય SpeechRecognizer ને કૉલ ઑડિયો આપતા નથી. AI આસિસ્ટન્ટ ગુજરાતીમાં બોલવા (TTS), સંમતિ લેવા અને નીચેના વાક્ય બટનો દ્વારા જવાબ આપવા માટે ચાલુ છે."
    }

    fun loadSavedInstruction(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_USER_INSTRUCTION, null)?.trim().orEmpty()
        return saved.ifBlank { GujaratiConversationEngine.DEFAULT_USER_INSTRUCTION_GUJARATI }
    }

    fun updateUserInstruction(context: Context, newInstruction: String) {
        val clean = newInstruction.trim().ifBlank {
            GujaratiConversationEngine.DEFAULT_USER_INSTRUCTION_GUJARATI
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_INSTRUCTION, clean)
            .apply()
        _uiState.update { it.copy(userInstruction = clean) }
    }

    /**
     * Inspects all installed [RecognitionService] providers, `SpeechRecognizer.isRecognitionAvailable()`,
     * and the system default `voice_recognition_service` setting so we know the exact package being used.
     */
    @Suppress("DEPRECATION")
    fun inspectInstalledRecognitionServices(
        context: Context,
        preferredStrategy: RecognizerBindingStrategy = currentBindingStrategy
    ): RecognitionServiceInspectionReport {
        val isAvailableApi = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }

        val secureDefault = try {
            Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
                ?.trim()
                .orEmpty()
                .ifBlank { "Not set in Settings.Secure" }
        } catch (_: Exception) {
            "Unreadable"
        }

        val resolvedInfos = try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            context.packageManager.queryIntentServices(
                Intent(RecognitionService.SERVICE_INTERFACE),
                flags
            )
        } catch (_: Exception) {
            emptyList()
        }

        var exportedGoogle: ComponentName? = null
        var firstExported: ComponentName? = null
        val serviceDescriptions = mutableListOf<String>()

        for (resolveInfo in resolvedInfos) {
            val sInfo = resolveInfo.serviceInfo ?: continue
            val comp = ComponentName(sInfo.packageName, sInfo.name)
            val isExported = sInfo.exported
            serviceDescriptions.add("${comp.flattenToShortString()} (exported=$isExported)")
            if (isExported) {
                if (firstExported == null) {
                    firstExported = comp
                }
                if (exportedGoogle == null &&
                    (sInfo.packageName.contains("com.google.android.tts") ||
                        sInfo.packageName.contains("googlequicksearchbox") ||
                        sInfo.packageName.contains("google"))
                ) {
                    exportedGoogle = comp
                }
            }
        }

        val discoveredSummary = if (serviceDescriptions.isEmpty()) {
            "None found via queryIntentServices"
        } else {
            serviceDescriptions.joinToString("; ")
        }

        val explicitCandidate = exportedGoogle ?: firstExported
        val effectiveStrategy = when {
            preferredStrategy == RecognizerBindingStrategy.EXPLICIT_EXPORTED_COMPONENT && explicitCandidate != null ->
                RecognizerBindingStrategy.EXPLICIT_EXPORTED_COMPONENT
            isAvailableApi ->
                RecognizerBindingStrategy.SYSTEM_DEFAULT
            explicitCandidate != null ->
                RecognizerBindingStrategy.EXPLICIT_EXPORTED_COMPONENT
            else ->
                RecognizerBindingStrategy.SYSTEM_DEFAULT
        }

        val targetDesc = when (effectiveStrategy) {
            RecognizerBindingStrategy.SYSTEM_DEFAULT ->
                "System Default -> $secureDefault"
            RecognizerBindingStrategy.EXPLICIT_EXPORTED_COMPONENT ->
                "Explicit Component -> ${explicitCandidate?.flattenToShortString() ?: "None"}"
        }

        return RecognitionServiceInspectionReport(
            isRecognitionAvailableApi = isAvailableApi,
            systemDefaultServiceSetting = secureDefault,
            discoveredServicesSummary = discoveredSummary,
            exportedGoogleComponent = exportedGoogle,
            firstExportedComponent = firstExported,
            activeBindingStrategy = effectiveStrategy,
            activeTargetDescription = targetDesc
        )
    }

    /**
     * Inspects AudioManager mode, speakerphone state, microphone mute state, call stream volume,
     * and whether [CallRecordingManager] is holding the microphone.
     */
    fun inspectAudioAndMicContention(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val modeLabel = when (audioManager?.mode) {
            AudioManager.MODE_NORMAL -> "MODE_NORMAL"
            AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
            AudioManager.MODE_IN_CALL -> "MODE_IN_CALL (Cellular Call)"
            AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
            AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING"
            else -> "MODE_${audioManager?.mode ?: -1}"
        }
        val speakerOn = audioManager?.isSpeakerphoneOn == true
        val micMuted = audioManager?.isMicrophoneMute == true
        val callVolPct = try {
            if (audioManager != null) {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL).coerceAtLeast(1)
                val cur = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                "${(cur * 100) / max}%"
            } else {
                "N/A"
            }
        } catch (_: Exception) {
            "N/A"
        }
        val callRecorderActive = try {
            CallRecordingManager.sessionState.value.isRecordingActive
        } catch (_: Exception) {
            false
        }
        return buildString {
            append("Audio: $modeLabel")
            append(" | Speaker: ${if (speakerOn) "ON" else "OFF"}")
            append(" | MicMute: ${if (micMuted) "MUTED" else "UNMUTED"}")
            append(" | Vol: $callVolPct")
            if (callRecorderActive) {
                append(" | CONTENTION: CallRecorder ACTIVE (holding mic)")
            }
        }
    }

    /**
     * Runs a fast (~45ms) local [AudioRecord] probe and immediately releases the
     * [AudioRecord] instance in a `finally` block before returning.
     * IMPORTANT: A passing [AudioRecord] probe only verifies raw hardware microphone access
     * inside this app's process; it is never used to claim that [SpeechRecognizer] works.
     */
    @Suppress("MissingPermission")
    fun runPreFlightAudioRecordProbe(context: Context): LocalAudioRecordProbeResult {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val pm = context.packageManager
        val hasMicFeature = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        val isInCallMode = mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            CallStateManager.activeCallUiState.value != null

        val callRecorderActive = try {
            CallRecordingManager.sessionState.value.isRecordingActive
        } catch (_: Exception) {
            false
        }

        val activeConfigsCount = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && audioManager != null) {
                audioManager.activeRecordingConfigurations.size
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }

        val hardwareMicCount = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && audioManager != null) {
                audioManager.microphones.size
            } else if (hasMicFeature) {
                1
            } else {
                0
            }
        } catch (_: Exception) {
            if (hasMicFeature) 1 else 0
        }

        val hasHardware = hasMicFeature || hardwareMicCount > 0

        if (!hasPerm) {
            return LocalAudioRecordProbeResult(
                hasRecordPermission = false,
                hasMicrophoneHardware = hasHardware,
                isInCellularCallMode = isInCallMode,
                isCallRecorderHoldingMic = callRecorderActive,
                activeCompetingRecordersCount = activeConfigsCount,
                initializedSuccessfully = false,
                startedRecordingSuccessfully = false,
                audioSourceTested = "VOICE_RECOGNITION",
                bytesOrSamplesRead = 0,
                peakPcmAmplitude = 0,
                isReceivingNonZeroPcm = false,
                micPermissionStatusText = "DENIED (RECORD_AUDIO not granted)",
                micAvailabilityStatusText = "Unavailable (Permission denied)",
                audioRecordInitStatusText = "Skipped — RECORD_AUDIO permission missing",
                localMicAccessSummary = "Local Hardware Mic: Blocked (RECORD_AUDIO permission missing)",
                cellularCallAudioAccessSummary = "SIM Call Audio (VOICE_DOWNLINK): UNAVAILABLE (Blocked by Android OS)",
                unavailableAudioSourceExplanation = buildSimCallUnavailableSourcesExplanation()
            )
        }

        var initialized = false
        var started = false
        var sourceLabel = "VOICE_RECOGNITION"
        var samplesReadTotal = 0
        var peakAmp = 0
        var initErrorDetail = ""

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        var recorder: AudioRecord? = null
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                sourceLabel = "MIC (Fallback)"
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
            }

            if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                initialized = true
                recorder.startRecording()
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    started = true
                    val shortBuf = ShortArray(320)
                    for (attempt in 0 until 2) {
                        val readCount = recorder.read(shortBuf, 0, shortBuf.size)
                        if (readCount > 0) {
                            samplesReadTotal += readCount
                            for (i in 0 until readCount) {
                                val amp = abs(shortBuf[i].toInt())
                                if (amp > peakAmp) {
                                    peakAmp = amp
                                }
                            }
                        }
                    }
                } else {
                    initErrorDetail = "startRecording() state=${recorder.recordingState}"
                }
            } else {
                initErrorDetail = "AudioRecord.state=${recorder.state} (UNINITIALIZED)"
            }
        } catch (e: Exception) {
            initErrorDetail = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "error"}"
        } finally {
            try {
                if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {
            }
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }

        val hasNonZeroPcm = peakAmp > 0
        val permText = "GRANTED (RECORD_AUDIO)"
        val availabilityText = when {
            !hasHardware -> "Unavailable (No microphone hardware reported)"
            callRecorderActive -> "Contended — CallRecorder is actively holding MediaRecorder mic lock"
            isInCallMode -> "In-Call Mode (Cellular baseband owns primary uplink; $activeConfigsCount active recorder(s))"
            activeConfigsCount > 0 -> "Shared / Busy ($activeConfigsCount active AudioRecordingConfiguration(s))"
            else -> "Available (Hardware Mic Ready, 0 competing recorders)"
        }

        val initStatusText = when {
            !initialized -> "FAILED ($sourceLabel could not initialize: $initErrorDetail)"
            !started -> "INITIALIZED, but startRecording() blocked ($initErrorDetail)"
            samplesReadTotal <= 0 -> "STARTED ($sourceLabel), but 0 PCM samples returned"
            !hasNonZeroPcm && isInCallMode ->
                "INITIALIZED ($sourceLabel read $samplesReadTotal samples, peak=0 — silenced by MODE_IN_CALL AudioPolicy)"
            else ->
                "Local AudioRecord Probe OK ($sourceLabel read $samplesReadTotal PCM samples, peak=$peakAmp — does NOT prove SpeechRecognizer IPC works)"
        }

        val localMicSummary = when {
            !initialized || !started ->
                "Local Hardware Mic ($sourceLabel): UNAVAILABLE — locked by cellular call or competing recorder"
            isInCallMode && !hasNonZeroPcm ->
                "Local Hardware Mic ($sourceLabel): Silenced in MODE_IN_CALL (Android AudioPolicy blocks background/service capture during SIM call)"
            else ->
                "Local Hardware Mic ($sourceLabel): Accessible to this app's process ($samplesReadTotal samples read; SpeechRecognizer IPC tested separately)"
        }

        val cellularSummary = if (isInCallMode) {
            "Cellular Call Audio (VOICE_DOWNLINK / CAPTURE_AUDIO_OUTPUT): UNAVAILABLE to 3rd-party apps; Speakerphone AEC & AudioPolicy restrict in-call SpeechRecognizer"
        } else {
            "Cellular Call Audio: No SIM call active (MODE_NORMAL — testing local microphone only)"
        }

        return LocalAudioRecordProbeResult(
            hasRecordPermission = true,
            hasMicrophoneHardware = hasHardware,
            isInCellularCallMode = isInCallMode,
            isCallRecorderHoldingMic = callRecorderActive,
            activeCompetingRecordersCount = activeConfigsCount,
            initializedSuccessfully = initialized,
            startedRecordingSuccessfully = started,
            audioSourceTested = sourceLabel,
            bytesOrSamplesRead = samplesReadTotal,
            peakPcmAmplitude = peakAmp,
            isReceivingNonZeroPcm = hasNonZeroPcm,
            micPermissionStatusText = permText,
            micAvailabilityStatusText = availabilityText,
            audioRecordInitStatusText = initStatusText,
            localMicAccessSummary = localMicSummary,
            cellularCallAudioAccessSummary = cellularSummary,
            unavailableAudioSourceExplanation = buildSimCallUnavailableSourcesExplanation()
        )
    }

    /**
     * Activates the Gujarati AI Call Assistant session (or the Settings "TTS + Microphone Test").
     * - Allocates a new `sessionId` so any older recognition/TTS callbacks are ignored.
     * - Preserves any previous `normalModeMicTest` result so the two tests never overwrite each other.
     * - Only alters speakerphone routing if an actual phone call is active (`CallStateManager.activeCallUiState.value != null`),
     *   avoiding unnecessary `AudioManager` mode/routing changes during Settings testing.
     */
    fun startSession(
        context: Context,
        customInstruction: String? = null
    ) {
        val ctx = context.applicationContext
        appContext = ctx

        val sessionId = allocateNextSessionId()
        mainHandler?.removeCallbacksAndMessages(null)
        cancelReadyForSpeechWatchdog()
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
        isTtsCurrentlySpeaking = false
        abandonTtsAudioFocus(ctx)
        destroySpeechRecognizerInternal()

        isStandaloneNormalMicTestMode = false
        controlledRetryCount = 0
        consecutiveZeroFrameFailures = 0
        consecutiveDisconnectFailures = 0
        currentBindingStrategy = RecognizerBindingStrategy.SYSTEM_DEFAULT

        val instruction = customInstruction?.trim()?.takeIf { it.isNotBlank() }
            ?: loadSavedInstruction(ctx)
        updateUserInstruction(ctx, instruction)

        val hasMicPermission = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            val timeStr = formatCurrentTime()
            val gujErr = "[ERROR_INSUFFICIENT_PERMISSIONS (Code 9)] માઇક્રોફોન પરવાનગી (RECORD_AUDIO) જરૂરી છે."
            _uiState.update { prev ->
                prev.copy(
                    activeSessionId = sessionId,
                    activeTestMode = AiTestMode.TTS_AND_MIC_TEST,
                    isTestPanelVisible = true,
                    status = AiAssistantStatus.RECOGNITION_FAILED,
                    micPermissionStatus = "DENIED (RECORD_AUDIO missing)",
                    micAvailabilityStatus = "Unavailable (Permission required)",
                    audioRecordingInitStatus = "Blocked (Missing RECORD_AUDIO)",
                    recognitionSessionStatus = "FAILED (ERROR_INSUFFICIENT_PERMISSIONS Code 9)",
                    realAudioReachingEngineStatus = "NO — Permission denied",
                    lastErrorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    lastErrorSymbol = "ERROR_INSUFFICIENT_PERMISSIONS",
                    lastErrorDetail = "App does not have android.permission.RECORD_AUDIO granted.",
                    lastErrorTimestampMillis = System.currentTimeMillis(),
                    lastErrorTimestampFormatted = timeStr,
                    lastErrorWithTimestamp = "ERROR_INSUFFICIENT_PERMISSIONS (Code 9) at $timeStr",
                    diagnosticNote = gujErr,
                    normalModeMicTest = prev.normalModeMicTest.copy(isRunning = false),
                    ttsAndMicTest = TtsAndMicTestState(
                        sessionId = sessionId,
                        isRunning = false,
                        ttsInitStatus = "Skipped (Missing RECORD_AUDIO)",
                        ttsSpeakingStatus = "Not started",
                        micRecognitionStatus = "FAILED — ERROR_INSUFFICIENT_PERMISSIONS (Code 9)",
                        errorSymbolAndCode = "ERROR_INSUFFICIENT_PERMISSIONS (Code 9)",
                        gujaratiExplanation = gujErr,
                        finalOutcomeSummary = "FAILED — RECORD_AUDIO permission is not granted."
                    )
                )
            }
            return
        }

        // Only mutate speakerphone routing if an actual SIM call is active!
        val hasActiveCall = CallStateManager.activeCallUiState.value != null
        val speakerConfigured = if (hasActiveCall) {
            CallStateManager.ensureSpeakerphoneAndUnmutedForAi(ctx)
        } else {
            false
        }
        val initialContention = inspectAudioAndMicContention(ctx)
        val serviceReport = inspectInstalledRecognitionServices(ctx, currentBindingStrategy)
        latestServiceInspection = serviceReport

        val preservedNormalMicTest = _uiState.value.normalModeMicTest.copy(isRunning = false)

        _uiState.value = AiAssistantUiState(
            activeSessionId = sessionId,
            sessionStartTimeMillis = System.currentTimeMillis(),
            activeTestMode = AiTestMode.TTS_AND_MIC_TEST,
            isTestPanelVisible = true,
            status = AiAssistantStatus.CONNECTING,
            consentState = AiConsentState.AWAITING_CONSENT,
            userInstruction = instruction,
            connectionModeSummary = if (hasActiveCall) {
                "Active Call — Acoustic Speakerphone + Device Mic (SIM VOICE_DOWNLINK restricted by Android)"
            } else {
                "Normal Mode — Device Speaker + Microphone (gu-IN)"
            },
            turns = emptyList(),
            diagnosticNote = "ગુજરાતી TTS અને RecognitionService ચકાસણી શરૂ થઈ રહી છે (Session #$sessionId)...",
            isSpeakerphoneEnforced = speakerConfigured,
            isRecognizerAvailable = serviceReport.isRecognitionAvailableApi || serviceReport.firstExportedComponent != null,
            recognizerServiceComponent = serviceReport.activeTargetDescription,
            systemDefaultRecognitionService = serviceReport.systemDefaultServiceSetting,
            discoveredRecognitionServices = serviceReport.discoveredServicesSummary,
            activeBindingStrategyLabel = serviceReport.activeBindingStrategy.description,
            configuredRecognizerLocale = GujaratiConversationEngine.GUJARATI_LOCALE_TAG,
            audioContentionDetail = initialContention,
            micPermissionStatus = "GRANTED (RECORD_AUDIO)",
            audioRoutingInfo = initialContention,
            speechRecognizerInitStatus = "Ready to bind on demand (${serviceReport.activeTargetDescription})",
            recognitionSessionStatus = "CONNECTING (Initializing Gujarati TTS first, then Mic)",
            normalModeMicTest = preservedNormalMicTest,
            ttsAndMicTest = TtsAndMicTestState(
                sessionId = sessionId,
                isRunning = true,
                ttsInitStatus = "Initializing TextToSpeech (gu-IN)...",
                ttsSpeakingStatus = "Waiting for TTS engine...",
                micRecognitionStatus = "Queued — Will start after TTS greeting finishes",
                finalOutcomeSummary = "Step 1/2: Initializing Gujarati TextToSpeech..."
            )
        )

        probeExecutor.execute {
            val probe = runPreFlightAudioRecordProbe(ctx)
            lastProbeResult = probe
            mainHandler?.post {
                if (!isSessionCallbackValid(sessionId, activeSessionId)) return@post

                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        micPermissionStatus = probe.micPermissionStatusText,
                        micAvailabilityStatus = probe.micAvailabilityStatusText,
                        audioRecordingInitStatus = probe.audioRecordInitStatusText,
                        localMicrophoneAccessStatus = probe.localMicAccessSummary,
                        cellularCallAudioAccessStatus = probe.cellularCallAudioAccessSummary,
                        unavailableAudioSourceExplanation = probe.unavailableAudioSourceExplanation
                    )
                }

                destroySpeechRecognizerInternal()
                verifyGujaratiLanguageSupportHonest(ctx, sessionId)
                initTtsAndSpeakGreeting(ctx, sessionId)
            }
        }
    }

    /**
     * Standalone Normal-Mode (`MODE_NORMAL`) Gujarati Microphone Recognition Test.
     * - Allocates a new `sessionId` so any older callbacks are ignored.
     * - Preserves any previous `ttsAndMicTest` result so the two tests never overwrite each other.
     * - Does NOT touch `TextToSpeech` or `AudioManager` speakerphone routing.
     * - Shows `CONNECTING` first, transitions to `LISTENING_SPEAK_NOW` only when `onReadyForSpeech()` fires,
     *   shows partial results, and transitions to `SUCCEEDED` (`CONFIRMED ✓`) only when `onResults()`
     *   returns non-empty Gujarati text.
     */
    fun startStandaloneNormalModeGujaratiMicTest(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx

        val sessionId = allocateNextSessionId()
        mainHandler?.removeCallbacksAndMessages(null)
        cancelReadyForSpeechWatchdog()
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
        isTtsCurrentlySpeaking = false
        abandonTtsAudioFocus(ctx)
        destroySpeechRecognizerInternal()

        val hasMicPerm = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPerm) {
            val gujErr = "[ERROR_INSUFFICIENT_PERMISSIONS (Code 9)] માઇક્રોફોન પરવાનગી (RECORD_AUDIO) આપવામાં આવી નથી."
            _uiState.update { prev ->
                prev.copy(
                    activeSessionId = sessionId,
                    activeTestMode = AiTestMode.NORMAL_MODE_MIC_TEST,
                    isTestPanelVisible = true,
                    status = AiAssistantStatus.RECOGNITION_FAILED,
                    ttsAndMicTest = prev.ttsAndMicTest.copy(isRunning = false),
                    normalModeMicTest = NormalModeGujaratiMicTestState(
                        sessionId = sessionId,
                        stage = NormalModeMicTestStage.FAILED,
                        isRunning = false,
                        errorSymbolAndCode = "ERROR_INSUFFICIENT_PERMISSIONS (Code 9)",
                        gujaratiExplanation = gujErr,
                        diagnosticSummary = "NOT CONFIRMED — RECORD_AUDIO permission is not granted."
                    )
                )
            }
            return
        }

        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        val isInCall = mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            CallStateManager.activeCallUiState.value != null

        currentBindingStrategy = RecognizerBindingStrategy.SYSTEM_DEFAULT
        val serviceReport = inspectInstalledRecognitionServices(ctx, currentBindingStrategy)
        latestServiceInspection = serviceReport

        isStandaloneNormalMicTestMode = true
        controlledRetryCount = 0
        consecutiveDisconnectFailures = 0

        val audioModeLabel = if (isInCall) "MODE_IN_CALL (Warning: SIM call active!)" else "MODE_NORMAL"
        val initialNormalTestState = transitionNormalModeMicTestToConnecting(
            sessionId = sessionId,
            audioModeLabel = audioModeLabel,
            serviceUsed = serviceReport.activeTargetDescription
        )

        _uiState.update { prev ->
            prev.copy(
                activeSessionId = sessionId,
                activeTestMode = AiTestMode.NORMAL_MODE_MIC_TEST,
                isTestPanelVisible = true,
                status = AiAssistantStatus.CONNECTING,
                isTtsSpeaking = false,
                isStartListeningCalled = false,
                isRecognizerReadyForSpeech = false,
                lastErrorCode = null,
                lastErrorSymbol = null,
                lastErrorDetail = null,
                isRecognizerAvailable = serviceReport.isRecognitionAvailableApi || serviceReport.firstExportedComponent != null,
                recognizerServiceComponent = serviceReport.activeTargetDescription,
                systemDefaultRecognitionService = serviceReport.systemDefaultServiceSetting,
                discoveredRecognitionServices = serviceReport.discoveredServicesSummary,
                activeBindingStrategyLabel = serviceReport.activeBindingStrategy.description,
                recognitionSessionStatus = "NORMAL_MIC_TEST_CONNECTING (Session #$sessionId)",
                diagnosticNote = initialNormalTestState.gujaratiExplanation,
                ttsAndMicTest = prev.ttsAndMicTest.copy(isRunning = false),
                normalModeMicTest = initialNormalTestState
            )
        }

        probeExecutor.execute {
            val probe = runPreFlightAudioRecordProbe(ctx)
            lastProbeResult = probe
            mainHandler?.post {
                if (!isSessionCallbackValid(sessionId, activeSessionId)) return@post

                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        micPermissionStatus = probe.micPermissionStatusText,
                        micAvailabilityStatus = probe.micAvailabilityStatusText,
                        audioRecordingInitStatus = probe.audioRecordInitStatusText,
                        localMicrophoneAccessStatus = probe.localMicAccessSummary,
                        cellularCallAudioAccessStatus = probe.cellularCallAudioAccessSummary
                    )
                }
                verifyGujaratiLanguageSupportHonest(ctx, sessionId)
                startListeningNow(boundSessionId = sessionId, boundTestMode = AiTestMode.NORMAL_MODE_MIC_TEST)
            }
        }
    }

    /**
     * Stops the standalone Normal-Mode Gujarati Mic Test cleanly without hiding the test panel
     * or erasing a previously recognized transcript.
     */
    fun stopStandaloneNormalModeGujaratiMicTest() {
        allocateNextSessionId()
        mainHandler?.removeCallbacksAndMessages(null)
        cancelReadyForSpeechWatchdog()
        cancelPendingRestartRunnable()
        isStandaloneNormalMicTestMode = false
        releaseDisconnectedOrStuckRecognizer()
        _uiState.update { state ->
            val hasRecognizedText = state.normalModeMicTest.recognizedGujaratiText.isNotBlank()
            val preservedStage = when {
                hasRecognizedText -> NormalModeMicTestStage.SUCCEEDED
                state.normalModeMicTest.stage == NormalModeMicTestStage.FAILED -> NormalModeMicTestStage.FAILED
                else -> NormalModeMicTestStage.STOPPED
            }
            state.copy(
                activeTestMode = AiTestMode.NONE,
                isTestPanelVisible = true,
                status = if (hasRecognizedText) AiAssistantStatus.SPEECH_RECOGNIZED else AiAssistantStatus.IDLE,
                isStartListeningCalled = false,
                isRecognizerReadyForSpeech = false,
                recognitionSessionStatus = "STOPPED_BY_USER",
                normalModeMicTest = state.normalModeMicTest.copy(
                    isRunning = false,
                    stage = preservedStage,
                    gujaratiExplanation = if (hasRecognizedText) {
                        state.normalModeMicTest.gujaratiExplanation
                    } else {
                        "વપરાશકર્તા દ્વારા નોર્મલ-મોડ ગુજરાતી માઇક ટેસ્ટ રોકવામાં આવ્યો."
                    },
                    diagnosticSummary = if (hasRecognizedText) {
                        state.normalModeMicTest.diagnosticSummary
                    } else {
                        "Normal-Mode Gujarati mic test stopped by user."
                    }
                )
            )
        }
    }

    /**
     * Updates the configurable post-TTS delay (`250ms..2500ms`) used before automatically
     * restarting [SpeechRecognizer] after Gujarati [TextToSpeech] finishes speaking.
     */
    fun setPostTtsRestartDelayMs(delayMs: Long) {
        val clamped = delayMs.coerceIn(250L, 2500L)
        configuredPostTtsDelayMs = clamped
        _uiState.update { state ->
            state.copy(
                continuousConversation = state.continuousConversation.copy(
                    postTtsRestartDelayMs = clamped
                )
            )
        }
    }

    /**
     * Activates Continuous Gujarati AI Conversation in NORMAL MODE (`MODE_NORMAL`):
     * 1. User activates Continuous Conversation.
     * 2. SpeechRecognizer starts using `gu-IN`.
     * 3. Waits until `onReadyForSpeech()` before displaying `Listening`.
     * 4. Recognizes the user's actual Gujarati speech.
     * 5. Processes the recognized text (`Processing`).
     * 6. Generates an appropriate contextual Gujarati response using [GujaratiConversationEngine].
     * 7. Speaks the response using the Gujarati [TextToSpeech] engine (`AI Speaking`).
     * 8. Waits until TTS finishes completely (`UtteranceProgressListener.onDone()`).
     * 9. Automatically restarts [SpeechRecognizer] after the configurable post-TTS delay.
     * 10. Repeats until the user presses **Stop** or **Pause**.
     *
     * Repeated activation safely cancels any prior session, allocates a new `sessionId`,
     * and never creates duplicate listeners or overlapping TTS/SpeechRecognizer sessions.
     */
    fun startContinuousConversation(
        context: Context,
        customInstruction: String? = null,
        postTtsRestartDelayMs: Long = configuredPostTtsDelayMs
    ) {
        val ctx = context.applicationContext
        appContext = ctx

        val sessionId = allocateNextSessionId()
        mainHandler?.removeCallbacksAndMessages(null)
        cancelReadyForSpeechWatchdog()
        cancelPendingRestartRunnable()
        pendingContinuousUtterance = null
        lastProcessedUtteranceSignature = ""
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
        isTtsCurrentlySpeaking = false
        abandonTtsAudioFocus(ctx)
        destroySpeechRecognizerInternal()

        isStandaloneNormalMicTestMode = false
        controlledRetryCount = 0
        consecutiveZeroFrameFailures = 0
        consecutiveDisconnectFailures = 0
        currentBindingStrategy = RecognizerBindingStrategy.SYSTEM_DEFAULT
        configuredPostTtsDelayMs = postTtsRestartDelayMs.coerceIn(250L, 2500L)

        val instruction = customInstruction?.trim()?.takeIf { it.isNotBlank() }
            ?: loadSavedInstruction(ctx)
        updateUserInstruction(ctx, instruction)

        val hasMicPerm = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val currentScreen = _uiState.value.continuousConversation.activeScreenName.ifBlank { "Dialpad" }

        if (!hasMicPerm) {
            val gujErr = "[ERROR_INSUFFICIENT_PERMISSIONS (Code 9)] સતત ગુજરાતી વાતચીત માટે માઇક્રોફોન પરવાનગી (RECORD_AUDIO) જરૂરી છે."
            _uiState.update { prev ->
                prev.copy(
                    activeSessionId = sessionId,
                    activeTestMode = AiTestMode.CONTINUOUS_CONVERSATION,
                    isTestPanelVisible = true,
                    status = AiAssistantStatus.RECOGNITION_FAILED,
                    micPermissionStatus = "DENIED (RECORD_AUDIO missing)",
                    diagnosticNote = gujErr,
                    normalModeMicTest = prev.normalModeMicTest.copy(isRunning = false),
                    ttsAndMicTest = prev.ttsAndMicTest.copy(isRunning = false),
                    continuousConversation = ContinuousConversationState(
                        sessionId = sessionId,
                        isRunning = false,
                        stage = ContinuousConversationStage.STOPPED,
                        postTtsRestartDelayMs = configuredPostTtsDelayMs,
                        lastErrorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                        lastErrorSymbol = "ERROR_INSUFFICIENT_PERMISSIONS",
                        lastErrorSymbolAndCode = "ERROR_INSUFFICIENT_PERMISSIONS (Code 9)",
                        isManualRetryAvailable = true,
                        activeScreenName = currentScreen,
                        statusDetailGujarati = gujErr,
                        statusDetailEnglish = "FAILED — RECORD_AUDIO permission is required for Continuous Gujarati Conversation."
                    )
                )
            }
            return
        }

        val initialContention = inspectAudioAndMicContention(ctx)
        val serviceReport = inspectInstalledRecognitionServices(ctx, currentBindingStrategy)
        latestServiceInspection = serviceReport

        val initialContinuous = startContinuousConversationState(
            sessionId = sessionId,
            postTtsRestartDelayMs = configuredPostTtsDelayMs,
            activeScreenName = currentScreen
        )

        _uiState.update { prev ->
            prev.copy(
                activeSessionId = sessionId,
                sessionStartTimeMillis = System.currentTimeMillis(),
                activeTestMode = AiTestMode.CONTINUOUS_CONVERSATION,
                isTestPanelVisible = true,
                status = AiAssistantStatus.WAITING_FOR_USER,
                consentState = AiConsentState.GRANTED,
                userInstruction = instruction,
                turns = emptyList(),
                lastHeardCallerText = "",
                lastAiSpokenText = "",
                partialCallerText = "",
                isTtsSpeaking = false,
                isStartListeningCalled = false,
                isRecognizerReadyForSpeech = false,
                lastErrorCode = null,
                lastErrorSymbol = null,
                lastErrorDetail = null,
                isRecognizerAvailable = serviceReport.isRecognitionAvailableApi || serviceReport.firstExportedComponent != null,
                recognizerServiceComponent = serviceReport.activeTargetDescription,
                systemDefaultRecognitionService = serviceReport.systemDefaultServiceSetting,
                discoveredRecognitionServices = serviceReport.discoveredServicesSummary,
                activeBindingStrategyLabel = serviceReport.activeBindingStrategy.description,
                configuredRecognizerLocale = GujaratiConversationEngine.GUJARATI_LOCALE_TAG,
                audioContentionDetail = initialContention,
                audioRoutingInfo = initialContention,
                micPermissionStatus = "GRANTED (RECORD_AUDIO)",
                recognitionSessionStatus = "CONTINUOUS_STARTING (Session #$sessionId — Starting SpeechRecognizer gu-IN)",
                diagnosticNote = initialContinuous.statusDetailGujarati,
                normalModeMicTest = prev.normalModeMicTest.copy(isRunning = false),
                ttsAndMicTest = prev.ttsAndMicTest.copy(isRunning = false),
                continuousConversation = initialContinuous
            )
        }

        // Pre-initialize Gujarati TextToSpeech silently so it is warm when the first recognition turn completes
        ensureTtsInitializedForContinuousConversation(ctx, sessionId)

        probeExecutor.execute {
            val probe = runPreFlightAudioRecordProbe(ctx)
            lastProbeResult = probe
            mainHandler?.post {
                if (!isSessionCallbackValid(sessionId, activeSessionId)) return@post
                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        micPermissionStatus = probe.micPermissionStatusText,
                        micAvailabilityStatus = probe.micAvailabilityStatusText,
                        audioRecordingInitStatus = probe.audioRecordInitStatusText,
                        localMicrophoneAccessStatus = probe.localMicAccessSummary,
                        cellularCallAudioAccessStatus = probe.cellularCallAudioAccessSummary
                    )
                }
                verifyGujaratiLanguageSupportHonest(ctx, sessionId)
                startListeningNow(
                    boundSessionId = sessionId,
                    boundTestMode = AiTestMode.CONTINUOUS_CONVERSATION
                )
            }
        }
    }

    /**
     * Initializes [TextToSpeech] for Gujarati (`gu-IN`) in the background for Continuous Conversation
     * WITHOUT speaking an unsolicited greeting while [SpeechRecognizer] is starting.
     * If a response utterance was queued while TTS was still initializing, speaks it immediately on ready.
     */
    private fun ensureTtsInitializedForContinuousConversation(
        context: Context,
        boundSessionId: Long
    ) {
        if (textToSpeech != null && isTtsReady) {
            attachUtteranceListenerToCurrentTts(context, boundSessionId)
            return
        }

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
        }
        isTtsReady = false
        isTtsCurrentlySpeaking = false

        textToSpeech = TextToSpeech(context) { status ->
            mainHandler?.post {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@post
                if (status == TextToSpeech.SUCCESS) {
                    val tts = textToSpeech ?: return@post
                    val localeStatusLabel = configureGujaratiTtsVoice(tts)
                    attachUtteranceListenerToCurrentTts(context, boundSessionId)
                    isTtsReady = true
                    _uiState.update { state ->
                        state.copy(
                            isTestPanelVisible = true,
                            isTtsInitialized = true,
                            ttsLocaleStatus = localeStatusLabel
                        )
                    }
                    val pending = pendingContinuousUtterance
                    if (pending != null && isSessionCallbackValid(pending.boundSessionId, activeSessionId)) {
                        pendingContinuousUtterance = null
                        speakGujaratiResponse(
                            text = pending.text,
                            utteranceId = pending.utteranceId,
                            stopIfTtsUnavailable = pending.shouldStopImmediately,
                            boundSessionId = pending.boundSessionId
                        )
                    }
                } else {
                    isTtsReady = false
                    _uiState.update { state ->
                        state.copy(
                            isTestPanelVisible = true,
                            isTtsInitialized = false,
                            ttsLocaleStatus = "Init Failed (status=$status)"
                        )
                    }
                    val pending = pendingContinuousUtterance
                    if (pending != null && isSessionCallbackValid(pending.boundSessionId, activeSessionId)) {
                        pendingContinuousUtterance = null
                        onContinuousOrStandardTtsFinished(
                            context = context,
                            utteranceId = pending.utteranceId,
                            boundSessionId = pending.boundSessionId
                        )
                    }
                }
            }
        }
    }

    private fun configureGujaratiTtsVoice(tts: TextToSpeech): String {
        val guLocale = Locale.forLanguageTag(GujaratiConversationEngine.GUJARATI_LOCALE_TAG)
        val langResult = tts.setLanguage(guLocale)
        val localeStatusLabel = if (langResult == TextToSpeech.LANG_MISSING_DATA ||
            langResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            val hiLocale = Locale.forLanguageTag("hi-IN")
            val hiResult = tts.setLanguage(hiLocale)
            if (hiResult == TextToSpeech.LANG_MISSING_DATA ||
                hiResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts.setLanguage(Locale.getDefault())
                "${Locale.getDefault().toLanguageTag()} (Fallback — gu-IN missing)"
            } else {
                "hi-IN (Fallback — gu-IN voice pack missing)"
            }
        } else {
            "gu-IN (Active)"
        }

        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setPitch(1.0f)
        tts.setSpeechRate(0.96f)
        return localeStatusLabel
    }

    private fun attachUtteranceListenerToCurrentTts(
        context: Context,
        boundSessionId: Long
    ) {
        val tts = textToSpeech ?: return
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler?.post {
                    if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@post
                    isTtsCurrentlySpeaking = true
                    _uiState.update { state ->
                        if (state.status == AiAssistantStatus.IDLE) {
                            state
                        } else {
                            val isCont = state.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION
                            val updatedCont = if (isCont) {
                                state.continuousConversation.copy(
                                    stage = ContinuousConversationStage.AI_SPEAKING,
                                    isTtsSpeaking = true,
                                    readyForSpeechFired = false,
                                    isReceivingMicAudio = false,
                                    micActivityLabel = "AI ગુજરાતીમાં બોલી રહ્યું છે (માઇક બંધ)",
                                    statusDetailGujarati = "AI ગુજરાતીમાં જવાબ બોલી રહ્યું છે (AI Speaking)...",
                                    statusDetailEnglish = "AI Speaking in Gujarati via TextToSpeech (SpeechRecognizer stopped)..."
                                )
                            } else {
                                state.continuousConversation
                            }
                            state.copy(
                                isTestPanelVisible = true,
                                status = AiAssistantStatus.SPEAKING,
                                isTtsSpeaking = true,
                                isRecognizerReadyForSpeech = false,
                                recognitionSessionStatus = "PAUSED_FOR_TTS (AI Speaking in Gujarati)",
                                diagnosticNote = "AI ગુજરાતીમાં બોલી રહ્યું છે (TTS Active)...",
                                ttsAndMicTest = if (!isCont) {
                                    state.ttsAndMicTest.copy(
                                        isRunning = true,
                                        ttsSpeakingStatus = "Speaking Gujarati greeting (TTS Active)...",
                                        micRecognitionStatus = "Waiting for TTS to finish speaking...",
                                        finalOutcomeSummary = "Step 1/2: AI is speaking Gujarati greeting via TextToSpeech..."
                                    )
                                } else {
                                    state.ttsAndMicTest
                                },
                                continuousConversation = updatedCont
                            )
                        }
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler?.post {
                    if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@post
                    onContinuousOrStandardTtsFinished(
                        context = context,
                        utteranceId = utteranceId,
                        boundSessionId = boundSessionId
                    )
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler?.post {
                    if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@post
                    onContinuousOrStandardTtsFinished(
                        context = context,
                        utteranceId = utteranceId,
                        boundSessionId = boundSessionId,
                        hadTtsError = true
                    )
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler?.post {
                    if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@post
                    onContinuousOrStandardTtsFinished(
                        context = context,
                        utteranceId = utteranceId,
                        boundSessionId = boundSessionId,
                        hadTtsError = true,
                        ttsErrorCode = errorCode
                    )
                }
            }
        })
    }

    private fun onContinuousOrStandardTtsFinished(
        context: Context,
        utteranceId: String?,
        boundSessionId: Long,
        hadTtsError: Boolean = false,
        ttsErrorCode: Int? = null
    ) {
        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
        isTtsCurrentlySpeaking = false
        lastTtsFinishedTimestampMillis = System.currentTimeMillis()
        abandonTtsAudioFocus(context)

        val currentMode = _uiState.value.activeTestMode
        val isContinuous = currentMode == AiTestMode.CONTINUOUS_CONVERSATION

        _uiState.update { state ->
            val updatedCont = if (isContinuous) {
                transitionContinuousOnTtsDone(
                    current = state.continuousConversation,
                    callbackSessionId = boundSessionId,
                    activeSessionId = activeSessionId
                )
            } else {
                state.continuousConversation
            }
            val updatedTtsMic = if (!isContinuous) {
                state.ttsAndMicTest.copy(
                    ttsSpeakingStatus = if (hadTtsError) {
                        "TTS Error${ttsErrorCode?.let { " (Code $it)" }.orEmpty()} — Proceeding to Mic"
                    } else {
                        "Finished speaking ✓"
                    },
                    micRecognitionStatus = "Starting Microphone Recognition (gu-IN)...",
                    finalOutcomeSummary = "Step 1/2 Complete (TTS Finished) -> Step 2/2: Starting Gujarati Microphone Recognition..."
                )
            } else {
                state.ttsAndMicTest
            }
            state.copy(
                isTestPanelVisible = true,
                isTtsSpeaking = false,
                ttsAndMicTest = updatedTtsMic,
                continuousConversation = updatedCont
            )
        }

        if (utteranceId == "UTTERANCE_DECLINE_STOP" && !isContinuous) {
            stopSession(reasonNote = "સામેની વ્યક્તિએ સંમતિ ન આપતા AI આસિસ્ટન્ટ બંધ થયું.")
        } else {
            transitionToWaitingAndStartListening(
                preserveLastErrorBanner = false,
                boundSessionId = boundSessionId,
                boundTestMode = if (isContinuous) AiTestMode.CONTINUOUS_CONVERSATION else AiTestMode.TTS_AND_MIC_TEST
            )
        }
    }

    /**
     * Checks whether Gujarati (`gu-IN`) is genuinely supported by the installed recognition provider
     * WITHOUT using the active recognition session's `SpeechRecognizer` instance and WITHOUT falsely
     * reporting support if the provider returns empty/null metadata.
     */
    private fun verifyGujaratiLanguageSupportHonest(context: Context, boundSessionId: Long) {
        try {
            val detailsIntent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
            context.sendOrderedBroadcast(
                detailsIntent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
                        val extras = getResultExtras(true)
                        val supportedList = extras?.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                        if (supportedList != null && supportedList.isNotEmpty()) {
                            val (stateEnum, statusDetail) = evaluateGenuineGujaratiLanguageSupport(
                                installedOrSupportedLocales = supportedList,
                                pendingDownloadLocales = emptyList(),
                                metadataWasReturnedByProvider = true
                            )
                            applyGujaratiSupportResult(stateEnum, statusDetail)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            runIsolatedAndroid13SupportCheck(context, boundSessionId)
                        } else {
                            val (stateEnum, statusDetail) = evaluateGenuineGujaratiLanguageSupport(
                                installedOrSupportedLocales = emptyList(),
                                pendingDownloadLocales = emptyList(),
                                metadataWasReturnedByProvider = false
                            )
                            applyGujaratiSupportResult(stateEnum, statusDetail)
                        }
                    }
                },
                mainHandler,
                Activity.RESULT_OK,
                null,
                null
            )
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runIsolatedAndroid13SupportCheck(context, boundSessionId)
            } else {
                val (stateEnum, statusDetail) = evaluateGenuineGujaratiLanguageSupport(
                    installedOrSupportedLocales = emptyList(),
                    pendingDownloadLocales = emptyList(),
                    metadataWasReturnedByProvider = false
                )
                applyGujaratiSupportResult(stateEnum, statusDetail)
            }
        }
    }

    private fun runIsolatedAndroid13SupportCheck(context: Context, boundSessionId: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        destroySupportProbeRecognizer()
        try {
            val probe = SpeechRecognizer.createSpeechRecognizer(context)
            supportProbeRecognizer = probe
            val checkIntent = buildGujaratiRecognizerIntent(context.packageName)
            probe.checkRecognitionSupport(
                checkIntent,
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) {
                            mainHandler?.post { destroySupportProbeRecognizer() }
                            return
                        }
                        val activeLocales = (
                            recognitionSupport.installedOnDeviceLanguages +
                                recognitionSupport.supportedOnDeviceLanguages +
                                recognitionSupport.onlineLanguages
                            ).distinct()
                        val pendingLocales = recognitionSupport.pendingOnDeviceLanguages
                        val hasAnyMetadata = activeLocales.isNotEmpty() || pendingLocales.isNotEmpty()

                        val (stateEnum, statusDetail) = evaluateGenuineGujaratiLanguageSupport(
                            installedOrSupportedLocales = activeLocales,
                            pendingDownloadLocales = pendingLocales,
                            metadataWasReturnedByProvider = hasAnyMetadata
                        )
                        applyGujaratiSupportResult(stateEnum, statusDetail)
                        mainHandler?.post { destroySupportProbeRecognizer() }
                    }

                    override fun onError(error: Int) {
                        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) {
                            mainHandler?.post { destroySupportProbeRecognizer() }
                            return
                        }
                        val stateEnum = when (error) {
                            12 -> GujaratiLanguageSupportState.VERIFIED_NOT_SUPPORTED
                            13 -> GujaratiLanguageSupportState.VERIFIED_PENDING_DOWNLOAD
                            else -> GujaratiLanguageSupportState.UNVERIFIED_NO_PROVIDER_METADATA
                        }
                        val statusDetail = when (error) {
                            12 -> "Not Supported — checkRecognitionSupport returned ERROR_LANGUAGE_NOT_SUPPORTED (Code 12)"
                            13 -> "Not Ready — checkRecognitionSupport returned ERROR_LANGUAGE_UNAVAILABLE (Code 13)"
                            else -> "Unverified — checkRecognitionSupport returned Code $error (run Normal-Mode gu-IN Mic Test to confirm)"
                        }
                        applyGujaratiSupportResult(stateEnum, statusDetail)
                        mainHandler?.post { destroySupportProbeRecognizer() }
                    }
                }
            )
        } catch (_: Exception) {
            destroySupportProbeRecognizer()
            val (stateEnum, statusDetail) = evaluateGenuineGujaratiLanguageSupport(
                installedOrSupportedLocales = emptyList(),
                pendingDownloadLocales = emptyList(),
                metadataWasReturnedByProvider = false
            )
            applyGujaratiSupportResult(stateEnum, statusDetail)
        }
    }

    private fun applyGujaratiSupportResult(
        supportState: GujaratiLanguageSupportState,
        detailText: String
    ) {
        _uiState.update { current ->
            if (current.gujaratiSupportState == GujaratiLanguageSupportState.VERIFIED_SUPPORTED &&
                supportState == GujaratiLanguageSupportState.UNVERIFIED_NO_PROVIDER_METADATA
            ) {
                current
            } else {
                val boolSupported: Boolean? = when (supportState) {
                    GujaratiLanguageSupportState.VERIFIED_SUPPORTED -> true
                    GujaratiLanguageSupportState.VERIFIED_NOT_SUPPORTED,
                    GujaratiLanguageSupportState.VERIFIED_PENDING_DOWNLOAD -> false
                    GujaratiLanguageSupportState.CHECKING,
                    GujaratiLanguageSupportState.UNVERIFIED_NO_PROVIDER_METADATA -> null
                }
                current.copy(
                    gujaratiSupportState = supportState,
                    gujaratiLanguageSupported = boolSupported,
                    gujaratiLanguageSupportStatus = detailText,
                    speechRecognizerInitStatus = "${if (current.isRecognizerInitialized) "Bound" else "Ready on demand"} (${current.recognizerServiceComponent}) | gu-IN: $detailText"
                )
            }
        }
    }

    private fun destroySupportProbeRecognizer() {
        try {
            supportProbeRecognizer?.destroy()
        } catch (_: Exception) {
        }
        supportProbeRecognizer = null
    }

    /**
     * Creates a fresh [SpeechRecognizer] on the main thread right when `startListening()` is about to run.
     * Always releases any previous instance first so dead/disconnected Binders are never reused.
     */
    private fun createFreshSpeechRecognizerForSession(
        context: Context,
        boundSessionId: Long,
        boundTestMode: AiTestMode
    ): Boolean {
        destroySpeechRecognizerInternal()

        val report = inspectInstalledRecognitionServices(context, currentBindingStrategy)
        latestServiceInspection = report
        currentBindingStrategy = report.activeBindingStrategy

        val explicitComponent = report.exportedGoogleComponent ?: report.firstExportedComponent
        if (!report.isRecognitionAvailableApi && explicitComponent == null) {
            val timeStr = formatCurrentTime()
            _uiState.update {
                it.copy(
                    isTestPanelVisible = true,
                    isRecognizerAvailable = false,
                    isRecognizerInitialized = false,
                    recognizerServiceComponent = "None found",
                    systemDefaultRecognitionService = report.systemDefaultServiceSetting,
                    discoveredRecognitionServices = report.discoveredServicesSummary,
                    speechRecognizerInitStatus = "UNAVAILABLE — No RecognitionService installed on device",
                    recognitionSessionStatus = "FAILED (RECOGNITION_SERVICE_UNAVAILABLE)",
                    lastErrorCode = -1,
                    lastErrorSymbol = "RECOGNITION_SERVICE_UNAVAILABLE",
                    lastErrorDetail = "Neither SpeechRecognizer.isRecognitionAvailable() nor RecognitionService intent query found a speech engine on this device.",
                    lastErrorTimestampMillis = System.currentTimeMillis(),
                    lastErrorTimestampFormatted = timeStr,
                    lastErrorWithTimestamp = "RECOGNITION_SERVICE_UNAVAILABLE (Code -1) at $timeStr"
                )
            }
            return false
        }

        return try {
            val recognizer = when (report.activeBindingStrategy) {
                RecognizerBindingStrategy.EXPLICIT_EXPORTED_COMPONENT -> {
                    if (explicitComponent != null) {
                        SpeechRecognizer.createSpeechRecognizer(context, explicitComponent)
                    } else {
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                }
                RecognizerBindingStrategy.SYSTEM_DEFAULT -> {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            }
            recognizer.setRecognitionListener(
                createRecognitionListener(
                    boundSessionId = boundSessionId,
                    boundTestMode = boundTestMode
                )
            )
            speechRecognizer = recognizer
            isRecognitionSessionInFlight = false
            isListeningActive = false

            _uiState.update {
                it.copy(
                    isTestPanelVisible = true,
                    isRecognizerAvailable = true,
                    isRecognizerInitialized = true,
                    recognizerServiceComponent = report.activeTargetDescription,
                    systemDefaultRecognitionService = report.systemDefaultServiceSetting,
                    discoveredRecognitionServices = report.discoveredServicesSummary,
                    activeBindingStrategyLabel = report.activeBindingStrategy.description,
                    configuredRecognizerLocale = GujaratiConversationEngine.GUJARATI_LOCALE_TAG,
                    speechRecognizerInitStatus = "Bound (${report.activeTargetDescription}) | gu-IN: ${it.gujaratiLanguageSupportStatus}"
                )
            }
            true
        } catch (e: Exception) {
            val timeStr = formatCurrentTime()
            _uiState.update {
                it.copy(
                    isTestPanelVisible = true,
                    isRecognizerAvailable = report.isRecognitionAvailableApi,
                    isRecognizerInitialized = false,
                    speechRecognizerInitStatus = "INIT FAILED: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    recognitionSessionStatus = "FAILED (RECOGNIZER_INIT_EXCEPTION)",
                    lastErrorCode = -3,
                    lastErrorSymbol = "RECOGNIZER_INIT_EXCEPTION",
                    lastErrorDetail = "Failed to create SpeechRecognizer: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    lastErrorTimestampMillis = System.currentTimeMillis(),
                    lastErrorTimestampFormatted = timeStr,
                    lastErrorWithTimestamp = "RECOGNIZER_INIT_EXCEPTION (Code -3) at $timeStr",
                    diagnosticNote = "[RECOGNIZER_INIT_EXCEPTION] SpeechRecognizer શરૂ કરવામાં ભૂલ: ${e.localizedMessage ?: "error"}"
                )
            }
            false
        }
    }

    private fun createRecognitionListener(
        boundSessionId: Long,
        boundTestMode: AiTestMode
    ): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
                cancelReadyForSpeechWatchdog()
                isListeningActive = true
                consecutiveDisconnectFailures = 0
                val contention = appContext?.let { inspectAudioAndMicContention(it) }.orEmpty()
                _uiState.update { state ->
                    val isNormalTest = boundTestMode == AiTestMode.NORMAL_MODE_MIC_TEST
                    val isContinuous = boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION
                    val updatedNormalTest = if (isNormalTest) {
                        transitionNormalModeMicTestOnReadyForSpeech(
                            current = state.normalModeMicTest,
                            callbackSessionId = boundSessionId,
                            activeSessionId = activeSessionId
                        )
                    } else {
                        state.normalModeMicTest
                    }

                    val updatedTtsMicTest = if (boundTestMode == AiTestMode.TTS_AND_MIC_TEST) {
                        state.ttsAndMicTest.copy(
                            isRunning = true,
                            readyForSpeechFired = true,
                            micRecognitionStatus = "Listening (onReadyForSpeech=YES) — Speak Gujarati now...",
                            finalOutcomeSummary = "Step 2/2: Listening for Gujarati speech (onReadyForSpeech=YES)..."
                        )
                    } else {
                        state.ttsAndMicTest
                    }

                    val updatedContinuous = if (isContinuous) {
                        transitionContinuousOnReadyForSpeech(
                            current = state.continuousConversation,
                            callbackSessionId = boundSessionId,
                            activeSessionId = activeSessionId
                        )
                    } else {
                        state.continuousConversation
                    }

                    state.copy(
                        isTestPanelVisible = true,
                        status = AiAssistantStatus.LISTENING,
                        isRecognizerReadyForSpeech = true,
                        isReceivingMicAudio = false,
                        audioFrameCount = 0,
                        lastMicRmsDb = 0f,
                        partialCallerText = "",
                        audioContentionDetail = contention,
                        audioRoutingInfo = contention,
                        recognitionSessionStatus = "ACTIVE_LISTENING (onReadyForSpeech=YES, Mic Open for gu-IN)",
                        realAudioReachingEngineStatus = "Waiting for voice frames (onReadyForSpeech=YES, 0.0 dB)...",
                        diagnosticNote = "AI સાંભળી રહ્યું છે (gu-IN સક્રિય, ReadyForSpeech=YES) — કૃપા કરીને ગુજરાતીમાં બોલો...",
                        normalModeMicTest = updatedNormalTest,
                        ttsAndMicTest = updatedTtsMicTest,
                        continuousConversation = updatedContinuous
                    )
                }
            }

            override fun onBeginningOfSpeech() {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
                _uiState.update { state ->
                    val nextFrames = state.audioFrameCount + 1
                    val isNormalTest = boundTestMode == AiTestMode.NORMAL_MODE_MIC_TEST
                    val isContinuous = boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION
                    val updatedNormalTest = if (isNormalTest) {
                        state.normalModeMicTest.copy(
                            readyForSpeechFired = true,
                            rmsFramesReceived = nextFrames,
                            gujaratiExplanation = "માઇક્રોફોનમાં અવાજ સંભળાઈ રહ્યો છે — ગુજરાતી શબ્દો ઓળખાઈ રહ્યા છે...",
                            diagnosticSummary = "Speech detected in microphone — listening for Gujarati words..."
                        )
                    } else {
                        state.normalModeMicTest
                    }

                    val updatedTtsMicTest = if (boundTestMode == AiTestMode.TTS_AND_MIC_TEST) {
                        state.ttsAndMicTest.copy(
                            readyForSpeechFired = true,
                            micRecognitionStatus = "Receiving Speech (onBeginningOfSpeech)..."
                        )
                    } else {
                        state.ttsAndMicTest
                    }

                    val updatedContinuous = if (isContinuous && state.continuousConversation.isRunning) {
                        state.continuousConversation.copy(
                            stage = ContinuousConversationStage.LISTENING,
                            readyForSpeechFired = true,
                            isReceivingMicAudio = true,
                            audioFramesInCurrentTurn = nextFrames,
                            micActivityLabel = "માઇક્રોફોન સક્રિય — ગુજરાતી અવાજ સંભળાઈ રહ્યો છે (Receiving Speech)",
                            statusDetailGujarati = "તમારો ગુજરાતી અવાજ સંભળાઈ રહ્યો છે...",
                            statusDetailEnglish = "Receiving Gujarati speech in microphone (onBeginningOfSpeech)..."
                        )
                    } else {
                        state.continuousConversation
                    }

                    state.copy(
                        isTestPanelVisible = true,
                        status = AiAssistantStatus.LISTENING,
                        isRecognizerReadyForSpeech = true,
                        isReceivingMicAudio = true,
                        audioFrameCount = nextFrames,
                        recognitionSessionStatus = "RECEIVING_SPEECH (onBeginningOfSpeech triggered)",
                        realAudioReachingEngineStatus = String.format(
                            Locale.US,
                            "YES — Speech audio reaching SpeechRecognizer (RMS: %.1f dB, Peak: %.1f dB, Frames: %d)",
                            state.lastMicRmsDb,
                            state.peakMicRmsDb,
                            nextFrames
                        ),
                        diagnosticNote = "માઇક્રોફોનમાં ગુજરાતી અવાજ સંભળાઈ રહ્યો છે (Speech in progress)...",
                        normalModeMicTest = updatedNormalTest,
                        ttsAndMicTest = updatedTtsMicTest,
                        continuousConversation = updatedContinuous
                    )
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
                _uiState.update { state ->
                    val isRealSignal = rmsdB > 1.2f
                    val detectedAudio = state.isReceivingMicAudio || isRealSignal
                    val newPeak = maxOf(state.peakMicRmsDb, rmsdB)
                    val newFrames = if (isRealSignal) state.audioFrameCount + 1 else state.audioFrameCount
                    val realAudioStatus = if (detectedAudio) {
                        String.format(
                            Locale.US,
                            "YES — Real audio frames reaching SpeechRecognizer (RMS: %.1f dB, Peak: %.1f dB, Frames: %d)",
                            rmsdB,
                            newPeak,
                            newFrames
                        )
                    } else {
                        String.format(
                            Locale.US,
                            "NO — Only silence/noise floor (RMS: %.1f dB, 0 speech frames)",
                            rmsdB
                        )
                    }
                    val updatedNormalTest = if (boundTestMode == AiTestMode.NORMAL_MODE_MIC_TEST) {
                        state.normalModeMicTest.copy(
                            rmsFramesReceived = newFrames,
                            peakRmsDb = maxOf(state.normalModeMicTest.peakRmsDb, rmsdB)
                        )
                    } else {
                        state.normalModeMicTest
                    }

                    val updatedContinuous = if (boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION) {
                        transitionContinuousOnRmsChanged(
                            current = state.continuousConversation,
                            callbackSessionId = boundSessionId,
                            activeSessionId = activeSessionId,
                            rmsdB = rmsdB
                        )
                    } else {
                        state.continuousConversation
                    }

                    state.copy(
                        isTestPanelVisible = true,
                        lastMicRmsDb = rmsdB,
                        peakMicRmsDb = newPeak,
                        audioFrameCount = newFrames,
                        isReceivingMicAudio = detectedAudio,
                        realAudioReachingEngineStatus = realAudioStatus,
                        normalModeMicTest = updatedNormalTest,
                        continuousConversation = updatedContinuous
                    )
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
                if (buffer != null && buffer.isNotEmpty()) {
                    val hasNonZero = buffer.any { it.toInt() != 0 }
                    if (hasNonZero) {
                        _uiState.update { state ->
                            val nextFrames = state.audioFrameCount + 1
                            state.copy(
                                isTestPanelVisible = true,
                                isReceivingMicAudio = true,
                                audioFrameCount = nextFrames,
                                realAudioReachingEngineStatus = String.format(
                                    Locale.US,
                                    "YES — PCM audio buffer received by SpeechRecognizer (%d bytes, Frames: %d)",
                                    buffer.size,
                                    nextFrames
                                )
                            )
                        }
                    }
                }
            }

            override fun onEndOfSpeech() {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
                isListeningActive = false
                _uiState.update { state ->
                    val isContinuous = boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION
                    val updatedNormalTest = if (boundTestMode == AiTestMode.NORMAL_MODE_MIC_TEST &&
                        state.normalModeMicTest.isRunning
                    ) {
                        state.normalModeMicTest.copy(
                            stage = NormalModeMicTestStage.PROCESSING,
                            gujaratiExplanation = "અવાજ પૂર્ણ થયો — ગુજરાતી શબ્દો ચકાસાઈ રહ્યા છે...",
                            diagnosticSummary = "onEndOfSpeech fired — decoding Gujarati (gu-IN) transcript..."
                        )
                    } else {
                        state.normalModeMicTest
                    }

                    val updatedTtsMicTest = if (boundTestMode == AiTestMode.TTS_AND_MIC_TEST &&
                        state.ttsAndMicTest.isRunning
                    ) {
                        state.ttsAndMicTest.copy(
                            micRecognitionStatus = "Processing Speech (onEndOfSpeech — decoding gu-IN)..."
                        )
                    } else {
                        state.ttsAndMicTest
                    }

                    val updatedContinuous = if (isContinuous) {
                        transitionContinuousOnEndOfSpeech(
                            current = state.continuousConversation,
                            callbackSessionId = boundSessionId,
                            activeSessionId = activeSessionId
                        )
                    } else {
                        state.continuousConversation
                    }

                    state.copy(
                        isTestPanelVisible = true,
                        status = if (isContinuous) AiAssistantStatus.PROCESSING else state.status,
                        isRecognizerReadyForSpeech = false,
                        recognitionSessionStatus = "PROCESSING_SPEECH (onEndOfSpeech — decoding gu-IN)",
                        diagnosticNote = "અવાજ પૂર્ણ થયો, ગુજરાતી શબ્દો ચકાસાઈ રહ્યા છે...",
                        normalModeMicTest = updatedNormalTest,
                        ttsAndMicTest = updatedTtsMicTest,
                        continuousConversation = updatedContinuous
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
                val primaryPartial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
                val unstablePartial = partialResults
                    ?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()

                val combinedPartial = when {
                    primaryPartial.isNotEmpty() && unstablePartial.isNotEmpty() -> "$primaryPartial $unstablePartial".trim()
                    primaryPartial.isNotEmpty() -> primaryPartial
                    else -> unstablePartial
                }

                if (combinedPartial.isNotEmpty()) {
                    _uiState.update { state ->
                        val nextFrames = state.audioFrameCount + 1
                        val updatedNormalTest = if (boundTestMode == AiTestMode.NORMAL_MODE_MIC_TEST) {
                            transitionNormalModeMicTestOnPartialResults(
                                current = state.normalModeMicTest,
                                callbackSessionId = boundSessionId,
                                activeSessionId = activeSessionId,
                                partialText = combinedPartial
                            )
                        } else {
                            state.normalModeMicTest
                        }

                        val updatedTtsMicTest = if (boundTestMode == AiTestMode.TTS_AND_MIC_TEST) {
                            state.ttsAndMicTest.copy(
                                partialTranscript = combinedPartial,
                                micRecognitionStatus = "Partial gu-IN: \"$combinedPartial\""
                            )
                        } else {
                            state.ttsAndMicTest
                        }

                        val updatedContinuous = if (boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION) {
                            transitionContinuousOnPartialResults(
                                current = state.continuousConversation,
                                callbackSessionId = boundSessionId,
                                activeSessionId = activeSessionId,
                                partialText = combinedPartial
                            )
                        } else {
                            state.continuousConversation
                        }

                        state.copy(
                            isTestPanelVisible = true,
                            status = if (state.status == AiAssistantStatus.WAITING_FOR_USER ||
                                state.status == AiAssistantStatus.CONNECTING
                            ) {
                                AiAssistantStatus.LISTENING
                            } else {
                                state.status
                            },
                            gujaratiSupportState = GujaratiLanguageSupportState.VERIFIED_SUPPORTED,
                            gujaratiLanguageSupported = true,
                            gujaratiLanguageSupportStatus = "Verified Supported ✓ (Confirmed by live gu-IN recognition)",
                            isRecognizerReadyForSpeech = true,
                            isReceivingMicAudio = true,
                            audioFrameCount = nextFrames,
                            partialCallerText = combinedPartial,
                            recognitionSessionStatus = "PARTIAL_MATCH_RECEIVED (\"$combinedPartial\")",
                            realAudioReachingEngineStatus = "YES — Gujarati partial transcript decoded (Frames: $nextFrames)",
                            diagnosticNote = "સાંભળ્યું (Partial gu-IN): \"$combinedPartial\"",
                            normalModeMicTest = updatedNormalTest,
                            ttsAndMicTest = updatedTtsMicTest,
                            continuousConversation = updatedContinuous
                        )
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
                cancelReadyForSpeechWatchdog()
                isRecognitionSessionInFlight = false
                isListeningActive = false

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestFromResults = matches?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                val fallbackPartial = when (boundTestMode) {
                    AiTestMode.NORMAL_MODE_MIC_TEST -> {
                        _uiState.value.normalModeMicTest.partialTranscript.trim()
                            .ifBlank { _uiState.value.partialCallerText.trim() }
                    }
                    AiTestMode.CONTINUOUS_CONVERSATION -> {
                        _uiState.value.continuousConversation.partialGujaratiText.trim()
                            .ifBlank { _uiState.value.partialCallerText.trim() }
                    }
                    else -> {
                        _uiState.value.partialCallerText.trim()
                    }
                }
                val recognizedText = bestFromResults.ifBlank { fallbackPartial }

                // Release recognizer after session completes so Binder does not sit idle and disconnect later
                destroySpeechRecognizerInternal()

                if (recognizedText.isNotEmpty()) {
                    controlledRetryCount = 0
                    consecutiveZeroFrameFailures = 0
                    consecutiveDisconnectFailures = 0

                    _uiState.update { state ->
                        state.copy(
                            isTestPanelVisible = true,
                            gujaratiSupportState = GujaratiLanguageSupportState.VERIFIED_SUPPORTED,
                            gujaratiLanguageSupported = true,
                            gujaratiLanguageSupportStatus = "Verified Supported ✓ (Confirmed by live gu-IN recognition: \"$recognizedText\")"
                        )
                    }

                    if (boundTestMode == AiTestMode.NORMAL_MODE_MIC_TEST) {
                        isStandaloneNormalMicTestMode = false
                        _uiState.update { state ->
                            val finalNormalTest = transitionNormalModeMicTestOnFinalResults(
                                current = state.normalModeMicTest,
                                callbackSessionId = boundSessionId,
                                activeSessionId = activeSessionId,
                                finalRecognizedText = recognizedText
                            )
                            state.copy(
                                isTestPanelVisible = true,
                                status = AiAssistantStatus.SPEECH_RECOGNIZED,
                                isStartListeningCalled = false,
                                isRecognizerReadyForSpeech = false,
                                lastHeardCallerText = recognizedText,
                                partialCallerText = "",
                                recognitionSessionStatus = "NORMAL_MIC_TEST_CONFIRMED (\"$recognizedText\")",
                                diagnosticNote = finalNormalTest.gujaratiExplanation,
                                normalModeMicTest = finalNormalTest
                            )
                        }
                    } else if (boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION) {
                        val elapsedSinceTts = System.currentTimeMillis() - lastTtsFinishedTimestampMillis
                        if (GujaratiAiCloudEngine.isLikelyTtsSelfEcho(
                                recognizedText = recognizedText,
                                lastAiSpokenText = _uiState.value.lastAiSpokenText,
                                timeSinceTtsEndedMs = elapsedSinceTts
                            )
                        ) {
                            _uiState.update { state ->
                                state.copy(
                                    diagnosticNote = "AI ના પોતાના અવાજનો પડઘો (Self-Echo) અવગણવામાં આવ્યો — ફરી સાંભળવાનું શરૂ થાય છે..."
                                )
                            }
                            transitionToWaitingAndStartListening(
                                preserveLastErrorBanner = false,
                                boundSessionId = boundSessionId,
                                boundTestMode = AiTestMode.CONTINUOUS_CONVERSATION
                            )
                        } else {
                            onContinuousSpeechSuccessfullyRecognized(
                                recognizedText = recognizedText,
                                boundSessionId = boundSessionId
                            )
                        }
                    } else {
                        val elapsedSinceTts = System.currentTimeMillis() - lastTtsFinishedTimestampMillis
                        if (GujaratiAiCloudEngine.isLikelyTtsSelfEcho(
                                recognizedText = recognizedText,
                                lastAiSpokenText = _uiState.value.lastAiSpokenText,
                                timeSinceTtsEndedMs = elapsedSinceTts
                            )
                        ) {
                            _uiState.update { state ->
                                state.copy(
                                    diagnosticNote = "AI ના પોતાના અવાજનો પડઘો (Self-Echo) અવગણવામાં આવ્યો — ફરી સાંભળવાનું શરૂ થાય છે..."
                                )
                            }
                            transitionToWaitingAndStartListening(
                                preserveLastErrorBanner = false,
                                boundSessionId = boundSessionId,
                                boundTestMode = AiTestMode.TTS_AND_MIC_TEST
                            )
                        } else {
                            onCallerSpeechSuccessfullyRecognized(
                                recognizedText = recognizedText,
                                sourceLabel = "SpeechRecognizer (gu-IN)",
                                boundSessionId = boundSessionId
                            )
                        }
                    }
                } else {
                    handleSpeechRecognizerError(
                        errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                        boundSessionId = boundSessionId,
                        boundTestMode = boundTestMode
                    )
                }
            }

            override fun onError(error: Int) {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
                cancelReadyForSpeechWatchdog()
                isRecognitionSessionInFlight = false
                isListeningActive = false
                handleSpeechRecognizerError(
                    errorCode = error,
                    boundSessionId = boundSessionId,
                    boundTestMode = boundTestMode
                )
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    /**
     * Handles steps 4 -> 5 -> 6 -> 7 of Continuous Gujarati Conversation:
     * - Prevents duplicate processing of the same callback turn.
     * - Transitions to `Processing` (`ContinuousConversationStage.PROCESSING`).
     * - Evaluates a context-aware Gujarati response via [GujaratiConversationEngine.evaluateContinuousTurn].
     * - Transitions to `AI Speaking` (`ContinuousConversationStage.AI_SPEAKING`) and speaks via [TextToSpeech].
     */
    private fun onContinuousSpeechSuccessfullyRecognized(
        recognizedText: String,
        boundSessionId: Long
    ) {
        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
        val clean = recognizedText.trim()
        if (clean.isEmpty()) return

        val stateBefore = _uiState.value
        val contBefore = stateBefore.continuousConversation
        if (!contBefore.isRunning ||
            contBefore.stage == ContinuousConversationStage.PAUSED ||
            contBefore.stage == ContinuousConversationStage.STOPPED
        ) {
            return
        }

        val signature = "${boundSessionId}_${contBefore.completedTurnCount + 1}_$clean"
        if (signature == lastProcessedUtteranceSignature) {
            return
        }
        lastProcessedUtteranceSignature = signature

        releaseDisconnectedOrStuckRecognizer()

        val processingContState = transitionContinuousOnFinalSpeechRecognized(
            current = contBefore,
            callbackSessionId = boundSessionId,
            activeSessionId = activeSessionId,
            recognizedText = clean
        )
        val userTurn = processingContState.conversationHistory.lastOrNull()

        _uiState.update { current ->
            current.copy(
                isTestPanelVisible = true,
                status = AiAssistantStatus.PROCESSING,
                isStartListeningCalled = false,
                isRecognizerReadyForSpeech = false,
                recognitionSessionStatus = "CONTINUOUS_PROCESSING (\"$clean\")",
                lastHeardCallerText = clean,
                partialCallerText = "",
                lastErrorCode = null,
                lastErrorSymbol = null,
                lastErrorDetail = null,
                turns = if (userTurn != null) (current.turns + userTurn).takeLast(24) else current.turns,
                diagnosticNote = processingContState.statusDetailGujarati,
                continuousConversation = processingContState
            )
        }

        val ctx = appContext
        if (GujaratiAiCloudEngine.isNetworkConnected(ctx)) {
            aiScope.launch {
                val cloudResult = GujaratiAiCloudEngine.generateInCallGujaratiReply(
                    context = ctx,
                    callerSpeech = clean,
                    userStatusInstruction = stateBefore.userInstruction,
                    consentState = AiConsentState.GRANTED,
                    previousTurns = processingContState.conversationHistory,
                    recentTopics = processingContState.recentTopics,
                    isContinuousNormalMode = true
                )
                val applyBlock = Runnable {
                    if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@Runnable
                    val currentCont = _uiState.value.continuousConversation
                    if (!currentCont.isRunning ||
                        currentCont.stage == ContinuousConversationStage.PAUSED ||
                        currentCont.stage == ContinuousConversationStage.STOPPED
                    ) {
                        return@Runnable
                    }
                    val speakingContState = transitionContinuousOnSpeakingStarted(
                        current = currentCont,
                        callbackSessionId = boundSessionId,
                        activeSessionId = activeSessionId,
                        aiReplyGujarati = cloudResult.gujaratiReply,
                        detectedTopic = cloudResult.detectedTopic
                    )
                    _uiState.update { current ->
                        current.copy(
                            isTestPanelVisible = true,
                            aiEngineModeStatus = cloudResult.modelSourceLabel,
                            continuousConversation = speakingContState
                        )
                    }
                    val utteranceId = "CONTINUOUS_TURN_${boundSessionId}_${speakingContState.completedTurnCount}"
                    speakGujaratiResponse(
                        text = cloudResult.gujaratiReply,
                        utteranceId = utteranceId,
                        stopIfTtsUnavailable = cloudResult.shouldStopImmediately,
                        boundSessionId = boundSessionId
                    )
                }
                val handler = mainHandler
                if (handler != null) {
                    handler.post(applyBlock)
                } else {
                    applyBlock.run()
                }
            }
            return
        }

        // Evaluate contextual response synchronously when offline or in local unit test mode
        val decision = GujaratiConversationEngine.evaluateContinuousTurn(
            userSpeech = clean,
            conversationHistory = processingContState.conversationHistory,
            recentTopics = processingContState.recentTopics,
            userInstruction = stateBefore.userInstruction
        )

        val speakingContState = transitionContinuousOnSpeakingStarted(
            current = _uiState.value.continuousConversation,
            callbackSessionId = boundSessionId,
            activeSessionId = activeSessionId,
            aiReplyGujarati = decision.aiReplyGujarati,
            detectedTopic = decision.detectedTopic
        )

        _uiState.update { current ->
            current.copy(
                isTestPanelVisible = true,
                aiEngineModeStatus = "On-Device Gujarati Engine (Offline / Fast Mode)",
                continuousConversation = speakingContState
            )
        }

        val utteranceId = "CONTINUOUS_TURN_${boundSessionId}_${speakingContState.completedTurnCount}"
        speakGujaratiResponse(
            text = decision.aiReplyGujarati,
            utteranceId = utteranceId,
            stopIfTtsUnavailable = false,
            boundSessionId = boundSessionId
        )
    }

    /**
     * Handles any [SpeechRecognizer] error safely for the given `boundSessionId` and `boundTestMode`:
     * - Ignores stale errors from older sessions.
     * - Releases the disconnected or failed recognizer instance immediately.
     * - In `CONTINUOUS_CONVERSATION` mode, applies bounded error recovery for `ERROR_NO_MATCH` (7),
     *   `ERROR_SPEECH_TIMEOUT` (6), `ERROR_AUDIO` (3), `ERROR_CLIENT` (5), `ERROR_RECOGNIZER_BUSY` (8),
     *   and `ERROR_SERVER_DISCONNECTED` (11) without infinite restart loops.
     */
    private fun handleSpeechRecognizerError(
        errorCode: Int,
        boundSessionId: Long,
        boundTestMode: AiTestMode
    ) {
        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
        val current = _uiState.value
        val wasNormalMicTest = boundTestMode == AiTestMode.NORMAL_MODE_MIC_TEST
        val wasContinuous = boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION

        if (!wasNormalMicTest && !wasContinuous && (
            current.status == AiAssistantStatus.IDLE ||
                current.status == AiAssistantStatus.PAUSED ||
                current.status == AiAssistantStatus.SPEAKING
            )
        ) {
            releaseDisconnectedOrStuckRecognizer()
            return
        }

        if (wasContinuous && (
            !current.continuousConversation.isRunning ||
                current.continuousConversation.stage == ContinuousConversationStage.PAUSED ||
                current.continuousConversation.stage == ContinuousConversationStage.STOPPED ||
                current.continuousConversation.stage == ContinuousConversationStage.AI_SPEAKING
            )
        ) {
            releaseDisconnectedOrStuckRecognizer()
            return
        }

        val contention = appContext?.let { inspectAudioAndMicContention(it) }.orEmpty()
        val isInCall = contention.contains("MODE_IN_CALL")
        val hadAudioFrames = current.isReceivingMicAudio || current.audioFrameCount > 0
        val wasReady = current.isRecognizerReadyForSpeech

        if (!hadAudioFrames) {
            consecutiveZeroFrameFailures += 1
        } else {
            consecutiveZeroFrameFailures = 0
        }

        val errorInfo = describeSpeechRecognizerError(
            errorCode = errorCode,
            hasReceivedMicAudio = hadAudioFrames,
            isTtsSpeaking = isTtsCurrentlySpeaking || (textToSpeech?.isSpeaking == true),
            audioContentionInfo = contention,
            wasReadyForSpeech = wasReady,
            peakRmsDb = current.peakMicRmsDb,
            activeServiceComponent = current.recognizerServiceComponent
        )

        releaseDisconnectedOrStuckRecognizer()

        if (errorCode == 12) {
            applyGujaratiSupportResult(
                GujaratiLanguageSupportState.VERIFIED_NOT_SUPPORTED,
                "Not Supported — RecognitionService returned ERROR_LANGUAGE_NOT_SUPPORTED (Code 12) for gu-IN"
            )
        } else if (errorCode == 13) {
            applyGujaratiSupportResult(
                GujaratiLanguageSupportState.VERIFIED_PENDING_DOWNLOAD,
                "Language Pack Missing — RecognitionService returned ERROR_LANGUAGE_UNAVAILABLE (Code 13) for gu-IN"
            )
        }

        val timeStr = formatCurrentTime()
        val isServerDisconnected = errorCode == 11
        val nextDisconnectCount = if (isServerDisconnected) {
            current.binderDisconnectCount + 1
        } else {
            current.binderDisconnectCount
        }

        val report = latestServiceInspection
        val hasExplicitAlternative = (report?.exportedGoogleComponent ?: report?.firstExportedComponent) != null

        // Continuous Gujarati Conversation Mode Error Handling (Section 6)
        if (wasContinuous) {
            val recoveryPlan = computeContinuousErrorRecoveryPlan(
                errorCode = errorCode,
                current = current.continuousConversation,
                hasAlternativeBindingStrategy = hasExplicitAlternative
            )
            if (recoveryPlan.switchBindingStrategy && hasExplicitAlternative) {
                currentBindingStrategy = if (currentBindingStrategy == RecognizerBindingStrategy.SYSTEM_DEFAULT) {
                    RecognizerBindingStrategy.EXPLICIT_EXPORTED_COMPONENT
                } else {
                    RecognizerBindingStrategy.SYSTEM_DEFAULT
                }
            }

            val updatedContinuous = transitionContinuousOnError(
                current = current.continuousConversation,
                callbackSessionId = boundSessionId,
                activeSessionId = activeSessionId,
                errorInfo = errorInfo,
                recoveryPlan = recoveryPlan
            )

            _uiState.update { state ->
                state.copy(
                    isTestPanelVisible = true,
                    status = if (recoveryPlan.shouldAutoRetry) {
                        AiAssistantStatus.WAITING_FOR_USER
                    } else {
                        AiAssistantStatus.RECOGNITION_FAILED
                    },
                    isRecognizerInitialized = false,
                    isStartListeningCalled = false,
                    isRecognizerReadyForSpeech = false,
                    binderDisconnectCount = nextDisconnectCount,
                    activeBindingStrategyLabel = currentBindingStrategy.description,
                    recognitionSessionStatus = if (recoveryPlan.shouldAutoRetry) {
                        "CONTINUOUS_AUTO_RETRY (${errorInfo.formattedCodeAndSymbol} — Retry in ${recoveryPlan.retryDelayMs}ms)"
                    } else {
                        "CONTINUOUS_STOPPED_ON_ERROR (${errorInfo.formattedCodeAndSymbol} — Manual retry available)"
                    },
                    lastErrorCode = errorInfo.errorCode,
                    lastErrorSymbol = errorInfo.errorSymbol,
                    lastErrorDetail = errorInfo.technicalDetail,
                    lastErrorTimestampMillis = System.currentTimeMillis(),
                    lastErrorTimestampFormatted = timeStr,
                    lastErrorWithTimestamp = "${errorInfo.formattedCodeAndSymbol} at $timeStr",
                    audioContentionDetail = contention,
                    audioRoutingInfo = contention,
                    diagnosticNote = recoveryPlan.gujaratiStatusMessage,
                    continuousConversation = updatedContinuous
                )
            }

            if (recoveryPlan.shouldAutoRetry) {
                cancelPendingRestartRunnable()
                val retryRunnable = Runnable {
                    if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@Runnable
                    val latest = _uiState.value
                    if (latest.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION &&
                        latest.continuousConversation.isRunning &&
                        latest.continuousConversation.stage == ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE &&
                        !isRecognitionSessionInFlight
                    ) {
                        startListeningNow(
                            boundSessionId = boundSessionId,
                            boundTestMode = AiTestMode.CONTINUOUS_CONVERSATION
                        )
                    }
                }
                activePendingRestartRunnable = retryRunnable
                mainHandler?.postDelayed(retryRunnable, recoveryPlan.retryDelayMs)
            }
            return
        }

        var shouldRetryDisconnectWithAlternateStrategy = false
        if (isServerDisconnected) {
            consecutiveDisconnectFailures += 1
            if (consecutiveDisconnectFailures <= MAX_DISCONNECT_FALLBACK_RETRIES && hasExplicitAlternative) {
                currentBindingStrategy = if (currentBindingStrategy == RecognizerBindingStrategy.SYSTEM_DEFAULT) {
                    RecognizerBindingStrategy.EXPLICIT_EXPORTED_COMPONENT
                } else {
                    RecognizerBindingStrategy.SYSTEM_DEFAULT
                }
                shouldRetryDisconnectWithAlternateStrategy = true
            }
        } else {
            consecutiveDisconnectFailures = 0
        }

        if (wasNormalMicTest) {
            if (shouldRetryDisconnectWithAlternateStrategy) {
                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        status = AiAssistantStatus.CONNECTING,
                        isStartListeningCalled = false,
                        isRecognizerReadyForSpeech = false,
                        binderDisconnectCount = nextDisconnectCount,
                        lastErrorCode = errorInfo.errorCode,
                        lastErrorSymbol = errorInfo.errorSymbol,
                        lastErrorDetail = errorInfo.technicalDetail,
                        lastErrorTimestampMillis = System.currentTimeMillis(),
                        lastErrorTimestampFormatted = timeStr,
                        lastErrorWithTimestamp = "${errorInfo.formattedCodeAndSymbol} at $timeStr",
                        normalModeMicTest = it.normalModeMicTest.copy(
                            stage = NormalModeMicTestStage.CONNECTING,
                            isRunning = true,
                            errorSymbolAndCode = errorInfo.formattedCodeAndSymbol,
                            gujaratiExplanation = "ERROR_SERVER_DISCONNECTED (Code 11) — ડિસ્કનેક્ટ થયેલ સર્વિસ બંધ કરીને વૈકલ્પિક સ્પીચ સર્વિસ સાથે ૧ વાર ફરી પ્રયાસ...",
                            diagnosticSummary = "ERROR_SERVER_DISCONNECTED (Code 11) on previous binding — released dead recognizer and retrying once with ${currentBindingStrategy.description}..."
                        )
                    )
                }
                mainHandler?.postDelayed({
                    if (isSessionCallbackValid(boundSessionId, activeSessionId) &&
                        isStandaloneNormalMicTestMode &&
                        !isRecognitionSessionInFlight
                    ) {
                        startListeningNow(
                            boundSessionId = boundSessionId,
                            boundTestMode = AiTestMode.NORMAL_MODE_MIC_TEST
                        )
                    }
                }, DISCONNECT_SWITCH_RETRY_DELAY_MS)
                return
            }

            isStandaloneNormalMicTestMode = false
            _uiState.update { state ->
                val failedNormalState = transitionNormalModeMicTestOnError(
                    current = state.normalModeMicTest,
                    callbackSessionId = boundSessionId,
                    activeSessionId = activeSessionId,
                    errorInfo = errorInfo,
                    audioRecordProbeSucceeded = lastProbeResult?.initializedSuccessfully == true
                )
                state.copy(
                    isTestPanelVisible = true,
                    status = AiAssistantStatus.RECOGNITION_FAILED,
                    isStartListeningCalled = false,
                    isRecognizerReadyForSpeech = false,
                    binderDisconnectCount = nextDisconnectCount,
                    recognitionSessionStatus = "NORMAL_MIC_TEST_FAILED (${errorInfo.formattedCodeAndSymbol})",
                    lastErrorCode = errorInfo.errorCode,
                    lastErrorSymbol = errorInfo.errorSymbol,
                    lastErrorDetail = errorInfo.technicalDetail,
                    lastErrorTimestampMillis = System.currentTimeMillis(),
                    lastErrorTimestampFormatted = timeStr,
                    lastErrorWithTimestamp = "${errorInfo.formattedCodeAndSymbol} at $timeStr",
                    diagnosticNote = errorInfo.userFacingDiagnostic,
                    normalModeMicTest = failedNormalState
                )
            }
            return
        }

        // Standard AI Call Assistant / TTS + Mic Test error handling
        val simRestrictionDetected = isInCall && (
            !hadAudioFrames ||
                errorCode == SpeechRecognizer.ERROR_AUDIO ||
                errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                errorCode == 11 ||
                lastProbeResult?.isReceivingNonZeroPcm == false
            )

        val realAudioStatus = if (hadAudioFrames) {
            String.format(
                Locale.US,
                "YES — %d audio frames received (Peak: %.1f dB) before %s",
                current.audioFrameCount,
                current.peakMicRmsDb,
                errorInfo.errorSymbol
            )
        } else {
            "NO — Zero speech audio frames reached SpeechRecognizer (ReadyForSpeech=${if (wasReady) "YES" else "NO"}, 0.0 dB)"
        }

        val shouldScheduleControlledRetry = when {
            shouldRetryDisconnectWithAlternateStrategy && controlledRetryCount < 1 -> true
            isInCall && errorInfo.isTransientSilenceOrTimeout && controlledRetryCount < MAX_CONTROLLED_RETRIES_IN_CALL_ZERO_FRAMES -> true
            else -> false
        }

        val nextRetryCount = if (shouldScheduleControlledRetry) {
            controlledRetryCount + 1
        } else {
            controlledRetryCount
        }
        controlledRetryCount = nextRetryCount

        val sessionStatusText = when {
            shouldRetryDisconnectWithAlternateStrategy && shouldScheduleControlledRetry ->
                "DISCONNECTED (Code 11) — Released recognizer; switching to ${currentBindingStrategy.description} (Retry 1/1)"
            isServerDisconnected ->
                "DISCONNECTED (ERROR_SERVER_DISCONNECTED Code 11 — Recognizer released; auto-retry stopped)"
            shouldScheduleControlledRetry ->
                "FAILED (${errorInfo.formattedCodeAndSymbol}) — Controlled Retry $nextRetryCount/1 scheduled"
            simRestrictionDetected ->
                "BLOCKED_BY_SIM_CALL_AUDIO_POLICY (${errorInfo.formattedCodeAndSymbol} — Auto-retry stopped; use manual triggers or Retry)"
            else ->
                "FAILED (${errorInfo.formattedCodeAndSymbol} — Tap 'ફરી સાંભળો' to retry)"
        }

        _uiState.update { state ->
            state.copy(
                isTestPanelVisible = true,
                status = AiAssistantStatus.RECOGNITION_FAILED,
                isRecognizerInitialized = false,
                isStartListeningCalled = false,
                isRecognizerReadyForSpeech = false,
                binderDisconnectCount = nextDisconnectCount,
                activeBindingStrategyLabel = currentBindingStrategy.description,
                speechRecognizerInitStatus = "Released after ${errorInfo.errorSymbol} (Will recreate on next listen)",
                recognitionSessionStatus = sessionStatusText,
                realAudioReachingEngineStatus = realAudioStatus,
                lastErrorCode = errorInfo.errorCode,
                lastErrorSymbol = errorInfo.errorSymbol,
                lastErrorDetail = errorInfo.technicalDetail,
                lastErrorTimestampMillis = System.currentTimeMillis(),
                lastErrorTimestampFormatted = timeStr,
                lastErrorWithTimestamp = "${errorInfo.formattedCodeAndSymbol} at $timeStr",
                audioContentionDetail = contention,
                audioRoutingInfo = contention,
                isSimCallAudioRestrictionDetected = simRestrictionDetected || state.isSimCallAudioRestrictionDetected,
                unavailableAudioSourceExplanation = if (simRestrictionDetected) {
                    buildSimCallUnavailableSourcesExplanation()
                } else {
                    state.unavailableAudioSourceExplanation
                },
                simCallRestrictionReportGujarati = if (simRestrictionDetected) {
                    buildSimCallRestrictionReportGujarati()
                } else {
                    state.simCallRestrictionReportGujarati
                },
                controlledRetryCount = nextRetryCount,
                diagnosticNote = if (simRestrictionDetected && !shouldScheduleControlledRetry) {
                    "${errorInfo.userFacingDiagnostic}\n• ${buildSimCallRestrictionReportGujarati()}"
                } else {
                    errorInfo.userFacingDiagnostic
                },
                ttsAndMicTest = state.ttsAndMicTest.copy(
                    isRunning = shouldScheduleControlledRetry,
                    micRecognitionStatus = if (shouldScheduleControlledRetry) {
                        "Retrying once after ${errorInfo.formattedCodeAndSymbol}..."
                    } else {
                        "FAILED — ${errorInfo.formattedCodeAndSymbol}"
                    },
                    errorSymbolAndCode = errorInfo.formattedCodeAndSymbol,
                    gujaratiExplanation = errorInfo.summaryGujarati,
                    finalOutcomeSummary = "TTS Greeting: ${state.ttsAndMicTest.ttsSpeakingStatus} | Mic Result: FAILED (${errorInfo.formattedCodeAndSymbol} — ${errorInfo.summaryGujarati})"
                )
            )
        }

        if (shouldScheduleControlledRetry) {
            val retryDelay = if (isServerDisconnected) DISCONNECT_SWITCH_RETRY_DELAY_MS else AUTO_RETRY_DELAY_MS
            mainHandler?.postDelayed({
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@postDelayed
                val latest = _uiState.value
                if (latest.status == AiAssistantStatus.RECOGNITION_FAILED &&
                    latest.lastErrorCode == errorCode &&
                    !isRecognitionSessionInFlight
                ) {
                    transitionToWaitingAndStartListening(
                        preserveLastErrorBanner = true,
                        boundSessionId = boundSessionId,
                        boundTestMode = AiTestMode.TTS_AND_MIC_TEST
                    )
                }
            }, retryDelay)
        }
    }

    /**
     * Initializes [TextToSpeech] for Gujarati (`gu-IN`) and speaks the initial disclosure & consent prompt.
     */
    private fun initTtsAndSpeakGreeting(context: Context, boundSessionId: Long) {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
        }
        isTtsReady = false
        isTtsCurrentlySpeaking = false

        textToSpeech = TextToSpeech(context) { status ->
            mainHandler?.post {
                if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@post
                if (status == TextToSpeech.SUCCESS) {
                    val tts = textToSpeech ?: return@post
                    val localeStatusLabel = configureGujaratiTtsVoice(tts)
                    val fallbackNote = if (localeStatusLabel.contains("Fallback")) {
                        "નોંધ: ફોનમાં ગુજરાતી TTS વૉઇસ પેક નથી, તેથી $localeStatusLabel વપરાય છે."
                    } else {
                        null
                    }
                    attachUtteranceListenerToCurrentTts(context, boundSessionId)
                    isTtsReady = true
                    _uiState.update { state ->
                        state.copy(
                            isTestPanelVisible = true,
                            isTtsInitialized = true,
                            ttsLocaleStatus = localeStatusLabel,
                            diagnosticNote = fallbackNote ?: "ગુજરાતી TTS (gu-IN) સક્રિય — પ્રારંભિક સંમતિ સંદેશ બોલાઈ રહ્યો છે...",
                            ttsAndMicTest = state.ttsAndMicTest.copy(
                                ttsInitStatus = "Initialized ✓ ($localeStatusLabel)",
                                ttsSpeakingStatus = "Starting Gujarati greeting..."
                            )
                        )
                    }

                    speakGujaratiResponse(
                        text = GujaratiConversationEngine.INITIAL_DISCLOSURE_AND_CONSENT_GUJARATI,
                        utteranceId = "UTTERANCE_INITIAL_CONSENT",
                        boundSessionId = boundSessionId
                    )
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isTestPanelVisible = true,
                            isTtsInitialized = false,
                            ttsLocaleStatus = "Init Failed (status=$status)",
                            diagnosticNote = "TextToSpeech શરૂ થઈ શક્યું નહીં (Code $status). સીધું SpeechRecognizer શરૂ થઈ રહ્યું છે...",
                            ttsAndMicTest = state.ttsAndMicTest.copy(
                                ttsInitStatus = "Init Failed (Code $status)",
                                ttsSpeakingStatus = "Skipped (TTS unavailable)",
                                micRecognitionStatus = "Starting Microphone Recognition (gu-IN)..."
                            )
                        )
                    }
                    transitionToWaitingAndStartListening(
                        preserveLastErrorBanner = false,
                        boundSessionId = boundSessionId,
                        boundTestMode = AiTestMode.TTS_AND_MIC_TEST
                    )
                }
            }
        }
    }

    /**
     * Step 1 after TTS finishes: Transition to [AiAssistantStatus.WAITING_FOR_USER]
     * (and `ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE` when in Continuous Conversation mode),
     * ensure TTS playback, audio focus, and any previous recognition session are completely released,
     * wait for the configurable acoustic echo guard (`postTtsRestartDelayMs` in Continuous mode,
     * `ECHO_GUARD_DELAY_MS` in standard mode), and then invoke [startListeningNow].
     */
    private fun transitionToWaitingAndStartListening(
        preserveLastErrorBanner: Boolean,
        boundSessionId: Long = activeSessionId,
        boundTestMode: AiTestMode = _uiState.value.activeTestMode
    ) {
        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
        cancelReadyForSpeechWatchdog()
        cancelPendingRestartRunnable()
        val current = _uiState.value
        val isContinuous = boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION
        if (current.status == AiAssistantStatus.IDLE || current.status == AiAssistantStatus.PAUSED) {
            return
        }
        if (isContinuous && (
            !current.continuousConversation.isRunning ||
                current.continuousConversation.stage == ContinuousConversationStage.PAUSED ||
                current.continuousConversation.stage == ContinuousConversationStage.STOPPED
            )
        ) {
            return
        }

        releaseDisconnectedOrStuckRecognizer()

        val ttsStillActive = try {
            textToSpeech?.isSpeaking == true
        } catch (_: Exception) {
            false
        }
        if (ttsStillActive) {
            try {
                textToSpeech?.stop()
            } catch (_: Exception) {
            }
        }
        isTtsCurrentlySpeaking = false
        appContext?.let { abandonTtsAudioFocus(it) }

        val effectiveDelayMs = if (isContinuous) {
            current.continuousConversation.postTtsRestartDelayMs.coerceIn(250L, 2500L)
        } else {
            ECHO_GUARD_DELAY_MS
        }

        _uiState.update { state ->
            if (state.status == AiAssistantStatus.IDLE || state.status == AiAssistantStatus.PAUSED) {
                state
            } else {
                val updatedCont = if (isContinuous) {
                    state.continuousConversation.copy(
                        stage = ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE,
                        isTtsSpeaking = false,
                        readyForSpeechFired = false,
                        isReceivingMicAudio = false,
                        currentMicRmsDb = 0f,
                        partialGujaratiText = "",
                        micActivityLabel = "તમારા જવાબની રાહ જોવાઈ રહી છે (${effectiveDelayMs}ms બાદ માઇક શરૂ થશે)...",
                        statusDetailGujarati = "AI બોલવાનું પૂર્ણ થયું — તમારા જવાબની રાહ જોવાઈ રહી છે (Waiting for Your Response)...",
                        statusDetailEnglish = "TTS finished — waiting ${effectiveDelayMs}ms before auto-restarting SpeechRecognizer(gu-IN)..."
                    )
                } else {
                    state.continuousConversation
                }

                state.copy(
                    isTestPanelVisible = true,
                    status = AiAssistantStatus.WAITING_FOR_USER,
                    isTtsSpeaking = false,
                    isStartListeningCalled = false,
                    isRecognizerReadyForSpeech = false,
                    partialCallerText = "",
                    recognitionSessionStatus = "WAITING_ECHO_GUARD (Preparing fresh gu-IN recognizer in ${effectiveDelayMs}ms)",
                    lastErrorCode = if (preserveLastErrorBanner) state.lastErrorCode else null,
                    lastErrorSymbol = if (preserveLastErrorBanner) state.lastErrorSymbol else null,
                    lastErrorDetail = if (preserveLastErrorBanner) state.lastErrorDetail else null,
                    diagnosticNote = if (preserveLastErrorBanner && state.lastErrorSymbol != null) {
                        "ફરી સાંભળવાનો નિયંત્રિત પ્રયાસ (${state.controlledRetryCount}) — છેલ્લી ભૂલ: ${state.lastErrorSymbol} (Code ${state.lastErrorCode})..."
                    } else {
                        "AI બોલવાનું પૂર્ણ થયું — વપરાશકર્તાના અવાજની રાહ જોવાઈ રહી છે (Waiting for Your Response)..."
                    },
                    ttsAndMicTest = if (!isContinuous) {
                        state.ttsAndMicTest.copy(
                            isRunning = true,
                            micRecognitionStatus = "Connecting SpeechRecognizer(gu-IN) after TTS echo guard..."
                        )
                    } else {
                        state.ttsAndMicTest
                    },
                    continuousConversation = updatedCont
                )
            }
        }

        val restartRunnable = Runnable {
            if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@Runnable
            val latest = _uiState.value
            if (latest.status == AiAssistantStatus.WAITING_FOR_USER) {
                startListeningNow(
                    boundSessionId = boundSessionId,
                    boundTestMode = if (isContinuous) AiTestMode.CONTINUOUS_CONVERSATION else AiTestMode.TTS_AND_MIC_TEST
                )
            }
        }
        activePendingRestartRunnable = restartRunnable
        mainHandler?.postDelayed(restartRunnable, effectiveDelayMs)
    }

    /**
     * Step 2: Creates a fresh [SpeechRecognizer] on the main thread and immediately invokes
     * `recognizer.startListening(intent)` so the Binder connection never sits idle beforehand.
     */
    private fun startListeningNow(
        boundSessionId: Long = activeSessionId,
        boundTestMode: AiTestMode = when {
            isStandaloneNormalMicTestMode -> AiTestMode.NORMAL_MODE_MIC_TEST
            _uiState.value.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION -> AiTestMode.CONTINUOUS_CONVERSATION
            else -> AiTestMode.TTS_AND_MIC_TEST
        }
    ) {
        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
        val ctx = appContext ?: return
        val current = _uiState.value
        val isNormalTest = boundTestMode == AiTestMode.NORMAL_MODE_MIC_TEST
        val isContinuous = boundTestMode == AiTestMode.CONTINUOUS_CONVERSATION
        if (!isNormalTest &&
            (current.status == AiAssistantStatus.IDLE || current.status == AiAssistantStatus.PAUSED)
        ) {
            return
        }
        if (isContinuous && (
            !current.continuousConversation.isRunning ||
                current.continuousConversation.stage == ContinuousConversationStage.PAUSED ||
                current.continuousConversation.stage == ContinuousConversationStage.STOPPED
            )
        ) {
            return
        }

        // Prevent overlapping recognition sessions
        if (isRecognitionSessionInFlight || isListeningActive) {
            releaseDisconnectedOrStuckRecognizer()
            mainHandler?.postDelayed({
                if (isSessionCallbackValid(boundSessionId, activeSessionId)) {
                    startListeningNow(boundSessionId, boundTestMode)
                }
            }, 250L)
            return
        }

        // Ensure TextToSpeech output is NOT active when SpeechRecognizer starts
        val ttsActive = try {
            isTtsCurrentlySpeaking || (textToSpeech?.isSpeaking == true)
        } catch (_: Exception) {
            false
        }
        if (ttsActive) {
            try {
                textToSpeech?.stop()
            } catch (_: Exception) {
            }
            isTtsCurrentlySpeaking = false
            abandonTtsAudioFocus(ctx)
            mainHandler?.postDelayed({
                if (isSessionCallbackValid(boundSessionId, activeSessionId)) {
                    startListeningNow(boundSessionId, boundTestMode)
                }
            }, 240L)
            return
        }

        // Only alter speakerphone routing during an active phone call!
        if (!isNormalTest && !isContinuous && CallStateManager.activeCallUiState.value != null) {
            CallStateManager.ensureSpeakerphoneAndUnmutedForAi(ctx)
        }
        val contention = inspectAudioAndMicContention(ctx)

        val created = createFreshSpeechRecognizerForSession(
            context = ctx,
            boundSessionId = boundSessionId,
            boundTestMode = boundTestMode
        )
        val recognizer = speechRecognizer
        if (!created || recognizer == null) {
            if (isNormalTest) {
                isStandaloneNormalMicTestMode = false
                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        status = AiAssistantStatus.RECOGNITION_FAILED,
                        normalModeMicTest = it.normalModeMicTest.copy(
                            stage = NormalModeMicTestStage.FAILED,
                            isRunning = false,
                            errorSymbolAndCode = "RECOGNIZER_UNAVAILABLE (Code -1)",
                            gujaratiExplanation = "આ ફોનમાં કોઈ Speech RecognitionService ઉપલબ્ધ નથી.",
                            diagnosticSummary = "NOT CONFIRMED — No RecognitionService available on this device."
                        )
                    )
                }
            } else if (isContinuous) {
                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        status = AiAssistantStatus.RECOGNITION_FAILED,
                        continuousConversation = it.continuousConversation.copy(
                            isRunning = false,
                            stage = ContinuousConversationStage.STOPPED,
                            lastErrorCode = -1,
                            lastErrorSymbol = "RECOGNIZER_UNAVAILABLE",
                            lastErrorSymbolAndCode = "RECOGNIZER_UNAVAILABLE (Code -1)",
                            isManualRetryAvailable = true,
                            statusDetailGujarati = "આ ફોનમાં કોઈ Speech RecognitionService ઉપલબ્ધ નથી.",
                            statusDetailEnglish = "FAILED — No RecognitionService available on this device."
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        status = AiAssistantStatus.RECOGNITION_FAILED,
                        ttsAndMicTest = it.ttsAndMicTest.copy(
                            isRunning = false,
                            micRecognitionStatus = "FAILED — RECOGNIZER_UNAVAILABLE (Code -1)",
                            errorSymbolAndCode = "RECOGNIZER_UNAVAILABLE (Code -1)",
                            gujaratiExplanation = "આ ફોનમાં કોઈ Speech RecognitionService ઉપલબ્ધ નથી.",
                            finalOutcomeSummary = "FAILED — No RecognitionService available on this device."
                        )
                    )
                }
            }
            return
        }

        try {
            val intent = buildGujaratiRecognizerIntent(ctx.packageName)
            isRecognitionSessionInFlight = true
            isListeningActive = false

            _uiState.update { state ->
                val updatedNormalTest = if (isNormalTest) {
                    state.normalModeMicTest.copy(
                        stage = NormalModeMicTestStage.CONNECTING,
                        isRunning = true,
                        readyForSpeechFired = false,
                        gujaratiExplanation = "SpeechRecognizer.startListening(gu-IN) મોકલાયું — માઇક્રોફોન ખુલે (onReadyForSpeech) તેની રાહ જુઓ...",
                        diagnosticSummary = "Connecting — startListening(gu-IN) called via ${currentBindingStrategy.name}; waiting for onReadyForSpeech()..."
                    )
                } else {
                    state.normalModeMicTest
                }

                val updatedTtsMicTest = if (!isNormalTest && !isContinuous) {
                    state.ttsAndMicTest.copy(
                        isRunning = true,
                        micRecognitionStatus = "Connecting — startListening(gu-IN) called, awaiting onReadyForSpeech()..."
                    )
                } else {
                    state.ttsAndMicTest
                }

                val updatedContinuous = if (isContinuous) {
                    state.continuousConversation.copy(
                        stage = ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE,
                        readyForSpeechFired = false,
                        isReceivingMicAudio = false,
                        currentMicRmsDb = 0f,
                        partialGujaratiText = "",
                        micActivityLabel = "માઇક્રોફોન કનેક્ટ થઈ રહ્યું છે (Awaiting onReadyForSpeech)...",
                        statusDetailGujarati = "SpeechRecognizer(gu-IN) શરૂ થયું — માઇક્રોફોન તૈયાર થાય (onReadyForSpeech) તેની રાહ જુઓ...",
                        statusDetailEnglish = "startListening(gu-IN) called; waiting for onReadyForSpeech() before displaying Listening..."
                    )
                } else {
                    state.continuousConversation
                }

                state.copy(
                    isTestPanelVisible = true,
                    status = if (isNormalTest) AiAssistantStatus.CONNECTING else AiAssistantStatus.WAITING_FOR_USER,
                    isStartListeningCalled = true,
                    isRecognizerReadyForSpeech = false,
                    isReceivingMicAudio = false,
                    audioFrameCount = 0,
                    lastMicRmsDb = 0f,
                    audioContentionDetail = contention,
                    audioRoutingInfo = contention,
                    recognitionSessionStatus = "STARTING (startListening(gu-IN) sent via ${currentBindingStrategy.name}, awaiting onReadyForSpeech)",
                    realAudioReachingEngineStatus = "Awaiting onReadyForSpeech callback (0 frames so far)...",
                    diagnosticNote = "SpeechRecognizer.startListening(gu-IN) શરૂ થયું — માઇક્રોફોન તૈયાર થવાની રાહ (Waiting for onReadyForSpeech)...",
                    normalModeMicTest = updatedNormalTest,
                    ttsAndMicTest = updatedTtsMicTest,
                    continuousConversation = updatedContinuous
                )
            }

            scheduleReadyForSpeechWatchdog(boundSessionId, boundTestMode)
            recognizer.startListening(intent)
        } catch (e: Exception) {
            cancelReadyForSpeechWatchdog()
            releaseDisconnectedOrStuckRecognizer()
            val timeStr = formatCurrentTime()
            val errMsg = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "error"}"
            if (isNormalTest) {
                isStandaloneNormalMicTestMode = false
                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        status = AiAssistantStatus.RECOGNITION_FAILED,
                        isStartListeningCalled = false,
                        isRecognizerReadyForSpeech = false,
                        normalModeMicTest = it.normalModeMicTest.copy(
                            stage = NormalModeMicTestStage.FAILED,
                            isRunning = false,
                            errorSymbolAndCode = "START_LISTENING_EXCEPTION (Code -4)",
                            gujaratiExplanation = "[START_LISTENING_EXCEPTION] startListening() નિષ્ફળ: $errMsg",
                            diagnosticSummary = "NOT CONFIRMED — startListening() threw $errMsg"
                        )
                    )
                }
            } else if (isContinuous) {
                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        status = AiAssistantStatus.RECOGNITION_FAILED,
                        isStartListeningCalled = false,
                        isRecognizerReadyForSpeech = false,
                        continuousConversation = it.continuousConversation.copy(
                            isRunning = false,
                            stage = ContinuousConversationStage.STOPPED,
                            lastErrorCode = -4,
                            lastErrorSymbol = "START_LISTENING_EXCEPTION",
                            lastErrorSymbolAndCode = "START_LISTENING_EXCEPTION (Code -4)",
                            isManualRetryAvailable = true,
                            statusDetailGujarati = "[START_LISTENING_EXCEPTION] startListening() નિષ્ફળ: $errMsg",
                            statusDetailEnglish = "FAILED — startListening() threw $errMsg"
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        status = AiAssistantStatus.RECOGNITION_FAILED,
                        isStartListeningCalled = false,
                        isRecognizerReadyForSpeech = false,
                        recognitionSessionStatus = "FAILED (START_LISTENING_EXCEPTION)",
                        lastErrorCode = -4,
                        lastErrorSymbol = "START_LISTENING_EXCEPTION",
                        lastErrorDetail = errMsg,
                        lastErrorTimestampMillis = System.currentTimeMillis(),
                        lastErrorTimestampFormatted = timeStr,
                        lastErrorWithTimestamp = "START_LISTENING_EXCEPTION (Code -4) at $timeStr",
                        audioContentionDetail = contention,
                        audioRoutingInfo = contention,
                        diagnosticNote = "[START_LISTENING_EXCEPTION (Code -4)] startListening() નિષ્ફળ: $errMsg",
                        ttsAndMicTest = it.ttsAndMicTest.copy(
                            isRunning = false,
                            micRecognitionStatus = "FAILED — START_LISTENING_EXCEPTION (Code -4)",
                            errorSymbolAndCode = "START_LISTENING_EXCEPTION (Code -4)",
                            gujaratiExplanation = "startListening() નિષ્ફળ: $errMsg",
                            finalOutcomeSummary = "FAILED — startListening() threw $errMsg"
                        )
                    )
                }
            }
        }
    }

    /**
     * Step 3: Transitions to [AiAssistantStatus.SPEECH_RECOGNIZED] ("Speech Recognized"),
     * displays the recognized Gujarati text in `ttsAndMicTest` and `lastHeardCallerText`,
     * and then triggers the Gujarati response turn.
     */
    private fun onCallerSpeechSuccessfullyRecognized(
        recognizedText: String,
        sourceLabel: String,
        boundSessionId: Long = activeSessionId
    ) {
        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
        val clean = recognizedText.trim()
        if (clean.isEmpty()) return

        val stateBefore = _uiState.value
        if (stateBefore.status == AiAssistantStatus.IDLE) return

        releaseDisconnectedOrStuckRecognizer()

        _uiState.update { current ->
            current.copy(
                isTestPanelVisible = true,
                status = AiAssistantStatus.SPEECH_RECOGNIZED,
                isStartListeningCalled = false,
                isRecognizerReadyForSpeech = false,
                recognitionSessionStatus = "SPEECH_RECOGNIZED ($sourceLabel: \"$clean\")",
                lastHeardCallerText = clean,
                partialCallerText = "",
                lastErrorCode = null,
                lastErrorSymbol = null,
                lastErrorDetail = null,
                diagnosticNote = "અવાજ ઓળખાયો ($sourceLabel): \"$clean\"",
                ttsAndMicTest = current.ttsAndMicTest.copy(
                    isRunning = false,
                    micRecognitionStatus = "CONFIRMED ✓ — Speech Recognized ($sourceLabel)",
                    finalRecognizedTranscript = clean,
                    errorSymbolAndCode = null,
                    gujaratiExplanation = "સફળતાપૂર્વક ગુજરાતી અવાજ ઓળખાયો: \"$clean\"",
                    finalOutcomeSummary = "CONFIRMED ✓ — TTS + Mic Test Succeeded! Recognized Gujarati: \"$clean\""
                )
            )
        }

        mainHandler?.postDelayed({
            if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@postDelayed
            val latest = _uiState.value
            if (latest.status != AiAssistantStatus.IDLE && latest.status != AiAssistantStatus.PAUSED) {
                evaluateAndSpeakReplyForRecognizedText(clean, boundSessionId)
            }
        }, SPEECH_RECOGNIZED_DISPLAY_MS)
    }

    /**
     * Processes caller speech (either from live [SpeechRecognizer] or from the user's manual
     * phrase trigger) through the active session mode (`CONTINUOUS_CONVERSATION` or `TTS_AND_MIC_TEST`).
     */
    fun handleRecognizedCallerSpeech(callerText: String) {
        val clean = callerText.trim()
        if (clean.isEmpty()) return
        isStandaloneNormalMicTestMode = false
        val currentState = _uiState.value
        if (currentState.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION &&
            currentState.continuousConversation.isRunning
        ) {
            onContinuousSpeechSuccessfullyRecognized(
                recognizedText = clean,
                boundSessionId = activeSessionId
            )
            return
        }
        val sessionId = if (activeSessionId == 0L) allocateNextSessionId() else activeSessionId
        if (currentState.status == AiAssistantStatus.IDLE) {
            _uiState.update {
                it.copy(
                    activeSessionId = sessionId,
                    activeTestMode = AiTestMode.TTS_AND_MIC_TEST,
                    isTestPanelVisible = true,
                    status = AiAssistantStatus.WAITING_FOR_USER
                )
            }
        }
        onCallerSpeechSuccessfullyRecognized(
            recognizedText = clean,
            sourceLabel = "Manual / Assisted Trigger",
            boundSessionId = sessionId
        )
    }

    private fun evaluateAndSpeakReplyForRecognizedText(clean: String, boundSessionId: Long) {
        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
        val stateBefore = _uiState.value
        if (stateBefore.status == AiAssistantStatus.IDLE) return

        val callerTurn = AiConversationTurn(
            speaker = "CALLER",
            text = clean
        )
        val updatedTurnsWithCaller = (stateBefore.turns + callerTurn).takeLast(24)

        _uiState.update { current ->
            current.copy(
                isTestPanelVisible = true,
                status = AiAssistantStatus.PROCESSING,
                recognitionSessionStatus = "AI_THINKING (Generating Gujarati reply for: \"$clean\")",
                lastHeardCallerText = clean,
                partialCallerText = "",
                turns = updatedTurnsWithCaller,
                diagnosticNote = "AI વિચારી રહ્યું છે... (Generating Gujarati response)"
            )
        }

        val ctx = appContext
        if (GujaratiAiCloudEngine.isNetworkConnected(ctx)) {
            aiScope.launch {
                val cloudResult = GujaratiAiCloudEngine.generateInCallGujaratiReply(
                    context = ctx,
                    callerSpeech = clean,
                    userStatusInstruction = stateBefore.userInstruction,
                    consentState = stateBefore.consentState,
                    previousTurns = updatedTurnsWithCaller,
                    isContinuousNormalMode = false
                )
                val applyBlock = Runnable {
                    if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return@Runnable
                    if (_uiState.value.status == AiAssistantStatus.IDLE) return@Runnable
                    _uiState.update { current ->
                        current.copy(
                            isTestPanelVisible = true,
                            consentState = cloudResult.nextConsentState,
                            aiEngineModeStatus = cloudResult.modelSourceLabel
                        )
                    }
                    val utteranceId = if (cloudResult.shouldStopImmediately) {
                        "UTTERANCE_DECLINE_STOP"
                    } else {
                        "UTTERANCE_TURN_${System.currentTimeMillis()}"
                    }
                    speakGujaratiResponse(
                        text = cloudResult.gujaratiReply,
                        utteranceId = utteranceId,
                        stopIfTtsUnavailable = cloudResult.shouldStopImmediately,
                        boundSessionId = boundSessionId
                    )
                }
                val handler = mainHandler
                if (handler != null) {
                    handler.post(applyBlock)
                } else {
                    applyBlock.run()
                }
            }
            return
        }

        val previousAiReplies = stateBefore.turns
            .filter { it.speaker == "AI" }
            .map { it.text }
        val previousUserMessages = stateBefore.turns
            .filter { it.speaker != "AI" }
            .map { it.text }

        val decision = GujaratiConversationEngine.evaluateCallerInput(
            callerSpeech = clean,
            currentConsent = stateBefore.consentState,
            userInstruction = stateBefore.userInstruction,
            previousAiReplies = previousAiReplies,
            previousUserMessages = previousUserMessages
        )

        _uiState.update { current ->
            current.copy(
                isTestPanelVisible = true,
                consentState = decision.nextConsentState,
                aiEngineModeStatus = "On-Device Gujarati Engine (Offline / Fast Mode)"
            )
        }

        val utteranceId = if (decision.shouldStopImmediately) {
            "UTTERANCE_DECLINE_STOP"
        } else {
            "UTTERANCE_TURN_${System.currentTimeMillis()}"
        }

        speakGujaratiResponse(
            text = decision.aiReplyGujarati,
            utteranceId = utteranceId,
            stopIfTtsUnavailable = decision.shouldStopImmediately,
            boundSessionId = boundSessionId
        )
    }

    private fun speakGujaratiResponse(
        text: String,
        utteranceId: String,
        stopIfTtsUnavailable: Boolean = false,
        boundSessionId: Long = activeSessionId
    ) {
        if (!isSessionCallbackValid(boundSessionId, activeSessionId)) return
        releaseDisconnectedOrStuckRecognizer()
        cancelPendingRestartRunnable()

        val aiTurn = AiConversationTurn(
            speaker = "AI",
            text = text
        )
        isTtsCurrentlySpeaking = true
        val isContinuous = _uiState.value.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION

        _uiState.update { current ->
            current.copy(
                isTestPanelVisible = true,
                status = AiAssistantStatus.SPEAKING,
                isTtsSpeaking = true,
                isStartListeningCalled = false,
                isRecognizerReadyForSpeech = false,
                recognitionSessionStatus = "PAUSED_FOR_TTS (Speaking Gujarati reply)",
                lastAiSpokenText = text,
                partialCallerText = "",
                turns = (current.turns + aiTurn).takeLast(24),
                diagnosticNote = "AI બોલી રહ્યું છે (TextToSpeech Active)..."
            )
        }

        val tts = textToSpeech
        val ctx = appContext
        if (tts != null && isTtsReady) {
            if (ctx != null) {
                requestTtsAudioFocus(ctx)
                attachUtteranceListenerToCurrentTts(ctx, boundSessionId)
            }
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                isTtsCurrentlySpeaking = false
                if (ctx != null) abandonTtsAudioFocus(ctx)
                _uiState.update { it.copy(isTestPanelVisible = true, isTtsSpeaking = false) }
                if (stopIfTtsUnavailable && !isContinuous) {
                    stopSession(reasonNote = "સામેની વ્યક્તિએ સંમતિ ન આપતા AI આસિસ્ટન્ટ બંધ થયું.")
                } else if (ctx != null) {
                    onContinuousOrStandardTtsFinished(
                        context = ctx,
                        utteranceId = utteranceId,
                        boundSessionId = boundSessionId,
                        hadTtsError = true
                    )
                }
            }
        } else if (isContinuous && ctx != null) {
            // If TTS is still warming up in Continuous mode, queue the utterance and initialize TTS
            pendingContinuousUtterance = PendingContinuousUtterance(
                text = text,
                utteranceId = utteranceId,
                shouldStopImmediately = stopIfTtsUnavailable,
                boundSessionId = boundSessionId
            )
            ensureTtsInitializedForContinuousConversation(ctx, boundSessionId)
        } else {
            isTtsCurrentlySpeaking = false
            _uiState.update { it.copy(isTestPanelVisible = true, isTtsSpeaking = false) }
            if (stopIfTtsUnavailable && !isContinuous) {
                stopSession(reasonNote = "સામેની વ્યક્તિએ સંમતિ ન આપતા AI આસિસ્ટન્ટ બંધ થયું.")
            } else {
                transitionToWaitingAndStartListening(
                    preserveLastErrorBanner = false,
                    boundSessionId = boundSessionId,
                    boundTestMode = if (isContinuous) AiTestMode.CONTINUOUS_CONVERSATION else AiTestMode.TTS_AND_MIC_TEST
                )
            }
        }
    }

    private fun requestTtsAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                ttsAudioFocusRequest = req
                audioManager.requestAudioFocus(req)
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonTtsAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ttsAudioFocusRequest?.let { req ->
                    audioManager.abandonAudioFocusRequest(req)
                }
                ttsAudioFocusRequest = null
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Releases (`destroy()`) any active, stuck, or disconnected [SpeechRecognizer] instance
     * so dead Binder connections (`ERROR_SERVER_DISCONNECTED` Code 11) are never reused.
     */
    private fun releaseDisconnectedOrStuckRecognizer() {
        cancelReadyForSpeechWatchdog()
        destroySpeechRecognizerInternal()
    }

    private fun destroySpeechRecognizerInternal() {
        cancelReadyForSpeechWatchdog()
        val hadActive = isRecognitionSessionInFlight || isListeningActive || _uiState.value.isStartListeningCalled
        isRecognitionSessionInFlight = false
        isListeningActive = false
        val instance = speechRecognizer
        speechRecognizer = null
        if (instance != null) {
            if (hadActive) {
                try {
                    instance.cancel()
                } catch (_: Exception) {
                }
            }
            try {
                instance.destroy()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Pauses the active AI Assistant or Continuous Gujarati Conversation session cleanly:
     * - Releases any active [SpeechRecognizer]
     * - Stops any active [TextToSpeech] playback
     * - Preserves the full conversation history so the user can resume seamlessly.
     */
    fun pauseSession(pausedByBackground: Boolean = false) {
        allocateNextSessionId()
        mainHandler?.removeCallbacksAndMessages(null)
        cancelReadyForSpeechWatchdog()
        cancelPendingRestartRunnable()
        pendingContinuousUtterance = null
        isStandaloneNormalMicTestMode = false
        releaseDisconnectedOrStuckRecognizer()
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
        isTtsCurrentlySpeaking = false
        appContext?.let { abandonTtsAudioFocus(it) }
        _uiState.update { current ->
            if (current.status == AiAssistantStatus.IDLE && !current.continuousConversation.isRunning) {
                current
            } else {
                val updatedCont = if (current.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION ||
                    current.continuousConversation.isRunning
                ) {
                    transitionContinuousOnPause(
                        current = current.continuousConversation,
                        pausedByBackground = pausedByBackground
                    )
                } else {
                    current.continuousConversation
                }
                current.copy(
                    isTestPanelVisible = true,
                    status = AiAssistantStatus.PAUSED,
                    isTtsSpeaking = false,
                    isStartListeningCalled = false,
                    isRecognizerReadyForSpeech = false,
                    recognitionSessionStatus = "PAUSED (By ${if (pausedByBackground) "App Background" else "User"} — Recognizer released)",
                    partialCallerText = "",
                    diagnosticNote = if (pausedByBackground) {
                        "એપ બેકગ્રાઉન્ડમાં જતાં સતત વાતચીત થોભાવવામાં આવી છે (Paused)."
                    } else {
                        "AI આસિસ્ટન્ટ થોભાવવામાં આવ્યું છે (Paused)."
                    },
                    normalModeMicTest = current.normalModeMicTest.copy(isRunning = false),
                    ttsAndMicTest = current.ttsAndMicTest.copy(isRunning = false),
                    continuousConversation = updatedCont
                )
            }
        }
    }

    /**
     * Resumes a paused Continuous Gujarati Conversation (or standard AI Assistant session)
     * with a fresh `sessionId` while preserving all prior conversation history and context.
     */
    fun resumeSession() {
        val current = _uiState.value
        val isContinuous = current.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION ||
            current.continuousConversation.stage == ContinuousConversationStage.PAUSED
        if (current.status == AiAssistantStatus.IDLE && !isContinuous) return

        val sessionId = allocateNextSessionId()
        isStandaloneNormalMicTestMode = false
        controlledRetryCount = 0
        consecutiveDisconnectFailures = 0

        if (!isContinuous && CallStateManager.activeCallUiState.value != null) {
            appContext?.let { CallStateManager.ensureSpeakerphoneAndUnmutedForAi(it) }
        }

        val targetMode = if (isContinuous) AiTestMode.CONTINUOUS_CONVERSATION else AiTestMode.TTS_AND_MIC_TEST
        _uiState.update { state ->
            val updatedCont = if (isContinuous) {
                transitionContinuousOnResume(
                    current = state.continuousConversation,
                    newSessionId = sessionId
                )
            } else {
                state.continuousConversation
            }
            state.copy(
                activeSessionId = sessionId,
                activeTestMode = targetMode,
                isTestPanelVisible = true,
                status = AiAssistantStatus.WAITING_FOR_USER,
                continuousConversation = updatedCont
            )
        }

        if (isContinuous) {
            appContext?.let { ensureTtsInitializedForContinuousConversation(it, sessionId) }
            startListeningNow(
                boundSessionId = sessionId,
                boundTestMode = AiTestMode.CONTINUOUS_CONVERSATION
            )
        } else {
            transitionToWaitingAndStartListening(
                preserveLastErrorBanner = false,
                boundSessionId = sessionId,
                boundTestMode = AiTestMode.TTS_AND_MIC_TEST
            )
        }
    }

    /**
     * Manually releases any disconnected recognizer, re-runs the local [AudioRecord] probe,
     * resets retry counters, and starts a fresh recognition session (preserving Continuous Conversation
     * history if in Continuous mode).
     */
    fun retryListening() {
        val ctx = appContext ?: return
        val sessionId = allocateNextSessionId()
        isStandaloneNormalMicTestMode = false
        controlledRetryCount = 0
        consecutiveDisconnectFailures = 0

        val current = _uiState.value
        val isContinuous = current.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION ||
            current.continuousConversation.isManualRetryAvailable ||
            current.continuousConversation.isRunning

        if (!isContinuous && CallStateManager.activeCallUiState.value != null) {
            CallStateManager.ensureSpeakerphoneAndUnmutedForAi(ctx)
        }
        releaseDisconnectedOrStuckRecognizer()

        val targetMode = if (isContinuous) AiTestMode.CONTINUOUS_CONVERSATION else AiTestMode.TTS_AND_MIC_TEST

        _uiState.update { state ->
            val updatedCont = if (isContinuous) {
                transitionContinuousOnResume(
                    current = state.continuousConversation,
                    newSessionId = sessionId
                )
            } else {
                state.continuousConversation
            }
            state.copy(
                activeSessionId = sessionId,
                activeTestMode = targetMode,
                isTestPanelVisible = true,
                status = AiAssistantStatus.WAITING_FOR_USER,
                continuousConversation = updatedCont
            )
        }

        if (isContinuous) {
            ensureTtsInitializedForContinuousConversation(ctx, sessionId)
        }

        probeExecutor.execute {
            val probe = runPreFlightAudioRecordProbe(ctx)
            lastProbeResult = probe
            mainHandler?.post {
                if (!isSessionCallbackValid(sessionId, activeSessionId)) return@post
                _uiState.update {
                    it.copy(
                        isTestPanelVisible = true,
                        micPermissionStatus = probe.micPermissionStatusText,
                        micAvailabilityStatus = probe.micAvailabilityStatusText,
                        audioRecordingInitStatus = probe.audioRecordInitStatusText,
                        localMicrophoneAccessStatus = probe.localMicAccessSummary,
                        cellularCallAudioAccessStatus = probe.cellularCallAudioAccessSummary,
                        unavailableAudioSourceExplanation = probe.unavailableAudioSourceExplanation,
                        controlledRetryCount = 0
                    )
                }
                if (isContinuous) {
                    startListeningNow(
                        boundSessionId = sessionId,
                        boundTestMode = AiTestMode.CONTINUOUS_CONVERSATION
                    )
                } else {
                    transitionToWaitingAndStartListening(
                        preserveLastErrorBanner = false,
                        boundSessionId = sessionId,
                        boundTestMode = AiTestMode.TTS_AND_MIC_TEST
                    )
                }
            }
        }
    }

    /**
     * Records navigation across app screens (Dialpad, Contacts, Keypad, Voicemail, Settings)
     * during an active Continuous Conversation session without interrupting recognition or TTS.
     */
    fun onScreenSwitched(screenName: String) {
        if (screenName.isBlank()) return
        _uiState.update { state ->
            state.copy(
                continuousConversation = transitionContinuousOnScreenSwitched(
                    current = state.continuousConversation,
                    newScreenName = screenName
                )
            )
        }
    }

    /**
     * Safely pauses an active Continuous Conversation when the app moves to the background
     * so background microphone restrictions on Android 17 do not cause infinite error loops.
     */
    fun onAppMovedToBackground() {
        val current = _uiState.value
        if (current.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION &&
            current.continuousConversation.isRunning &&
            current.continuousConversation.stage != ContinuousConversationStage.PAUSED &&
            current.continuousConversation.stage != ContinuousConversationStage.STOPPED
        ) {
            pauseSession(pausedByBackground = true)
        }
    }

    /**
     * Updates the status banner when the app returns to the foreground after a background pause.
     */
    fun onAppReturnedToForeground() {
        val current = _uiState.value
        if (current.continuousConversation.stage == ContinuousConversationStage.PAUSED &&
            current.continuousConversation.wasPausedByBackground
        ) {
            _uiState.update { state ->
                state.copy(
                    diagnosticNote = "એપ પાછી ખુલી છે — સતત ગુજરાતી વાતચીત આગળ વધારવા 'Resume (ફરી શરૂ કરો)' દબાવો.",
                    continuousConversation = state.continuousConversation.copy(
                        statusDetailGujarati = "એપ પાછી ખુલી છે — વાતચીત આગળ વધારવા 'Resume' દબાવો.",
                        statusDetailEnglish = "App returned to foreground — tap Resume to continue your Gujarati conversation."
                    )
                )
            }
        }
    }

    fun testTtsGreetingOnly(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        startSession(ctx)
    }

    /**
     * Stops any active TTS, SpeechRecognizer, or Continuous Conversation session while PRESERVING
     * the last test results, transcripts, conversation history, and diagnostic explanations on screen
     * so the test panel never vanishes or clears the user's final result when a session ends or when
     * the user taps Stop.
     */
    fun stopSession(reasonNote: String? = null) {
        allocateNextSessionId()
        mainHandler?.removeCallbacksAndMessages(null)
        cancelReadyForSpeechWatchdog()
        cancelPendingRestartRunnable()
        pendingContinuousUtterance = null
        lastProcessedUtteranceSignature = ""
        isStandaloneNormalMicTestMode = false
        controlledRetryCount = 0
        consecutiveZeroFrameFailures = 0
        consecutiveDisconnectFailures = 0
        destroySpeechRecognizerInternal()
        destroySupportProbeRecognizer()

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
        }
        textToSpeech = null
        isTtsReady = false
        isTtsCurrentlySpeaking = false
        appContext?.let { abandonTtsAudioFocus(it) }

        if (!reasonNote.isNullOrBlank() && appContext != null) {
            try {
                CallStateManager.showStatusMessage(reasonNote)
            } catch (_: Throwable) {
            }
        }

        _uiState.update { prev ->
            val updatedCont = transitionContinuousOnStop(prev.continuousConversation)
            prev.copy(
                activeTestMode = AiTestMode.NONE,
                isTestPanelVisible = true,
                status = AiAssistantStatus.IDLE,
                isTtsSpeaking = false,
                isStartListeningCalled = false,
                isRecognizerReadyForSpeech = false,
                recognitionSessionStatus = "STOPPED (Session ended; previous test & conversation results preserved)",
                diagnosticNote = reasonNote ?: prev.diagnosticNote ?: "AI સેશન બંધ કરવામાં આવ્યું છે.",
                normalModeMicTest = prev.normalModeMicTest.copy(isRunning = false),
                ttsAndMicTest = prev.ttsAndMicTest.copy(isRunning = false),
                continuousConversation = updatedCont
            )
        }
    }

    fun describeSpeechRecognizerError(
        errorCode: Int,
        hasReceivedMicAudio: Boolean = false,
        isTtsSpeaking: Boolean = false,
        audioContentionInfo: String = "",
        wasReadyForSpeech: Boolean = false,
        peakRmsDb: Float = 0f,
        activeServiceComponent: String = ""
    ): SpeechRecognizerErrorInfo = com.example.telecom.describeSpeechRecognizerError(
        errorCode = errorCode,
        hasReceivedMicAudio = hasReceivedMicAudio,
        isTtsSpeaking = isTtsSpeaking,
        audioContentionInfo = audioContentionInfo,
        wasReadyForSpeech = wasReadyForSpeech,
        peakRmsDb = peakRmsDb,
        activeServiceComponent = activeServiceComponent
    )
}


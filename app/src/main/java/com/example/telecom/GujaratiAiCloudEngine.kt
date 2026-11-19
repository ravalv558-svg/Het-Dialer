package com.example.telecom

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.withTimeout
import java.util.Locale

/**
 * Result of generating a Gujarati response from either Cloud Gemini (`gemini-3.8-flash` via
 * Firebase AI Logic) or the deterministic on-device [GujaratiConversationEngine] fallback.
 */
data class GujaratiAiGenerationResult(
    val gujaratiReply: String,
    val nextConsentState: AiConsentState,
    val shouldStopImmediately: Boolean,
    val detectedTopic: String,
    val usedCloudGemini: Boolean,
    val modelSourceLabel: String,
    val fallbackReason: String? = null
)

/**
 * Hybrid Cloud + On-Device Gujarati Conversation Intelligence Engine for HET Phone.
 *
 * Enforces all safety, identity, consent, and conversational requirements:
 * 1. Always identifies itself honestly as an AI Call Assistant ("હું એક AI કૉલ સહાયક છું...").
 * 2. Never pretends to be the human phone owner.
 * 3. Never makes financial commitments, payments, promises, OTP/password disclosures, or binding decisions.
 * 4. Understands Gujarati script (`ગુજરાતી`), Romanized Gujarati (`Kem chho`, `Kya chho`), and mixed Gujarati-English speech.
 * 5. Maintains multi-turn context across the ongoing call or conversation session.
 * 6. Suppresses self-echo (ignores transcripts that match the AI's own recent TTS output).
 * 7. Gracefully degrades to the context-aware on-device [GujaratiConversationEngine] if offline or if the cloud API fails.
 */
object GujaratiAiCloudEngine {
    private const val TAG = "GujaratiAiCloudEngine"
    private const val CLOUD_MODEL_NAME = "gemini-3.8-flash"
    private const val CLOUD_TIMEOUT_MS = 9_000L

    private val commitmentOrPaymentKeywords = listOf(
        "પૈસા", "રૂપિયા", "પેમેન્ટ", "ટ્રાન્સફર", "ગૂગલ પે", "ફોન પે", "પેટીએમ",
        "ઓટીપી", "પાસવર્ડ", "બેંક", "એકાઉન્ટ", "લોન", "વચન", "પ્રોમિસ", "ગેરંટી", "સહી",
        "paisa", "rupiya", "payment", "transfer", "gpay", "google pay", "phonepe", "paytm",
        "upi", "otp", "password", "pin", "bank", "account", "loan", "promise", "guarantee",
        "commit", "commitment", "deal confirm", "order confirm"
    )

    /**
     * Checks whether the caller's speech asks for payments, OTP/passwords, financial transfers,
     * or binding commitments/promises on behalf of the phone owner.
     */
    fun isSensitiveCommitmentOrPaymentRequest(callerSpeech: String): Boolean {
        val normalized = callerSpeech.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return false
        return commitmentOrPaymentKeywords.any { keyword -> normalized.contains(keyword) }
    }

    /**
     * Builds a safe, polite Gujarati refusal when the caller asks for payments, OTPs, or commitments.
     */
    fun buildSafeNoCommitmentGujaratiReply(callerSpeech: String): String {
        val normalized = callerSpeech.trim().lowercase(Locale.ROOT)
        return if (normalized.contains("ઓટીપી") || normalized.contains("otp") ||
            normalized.contains("પાસવર્ડ") || normalized.contains("password") ||
            normalized.contains("pin")
        ) {
            "માફ કરશો, હું એક AI કૉલ સહાયક છું. સુરક્ષાના કારણોસર હું કોઈ OTP, પાસવર્ડ કે બેંકની માહિતી આપી શકતો નથી."
        } else {
            "હું એક AI કૉલ સહાયક છું, તેથી હું કોઈ નાણાકીય વ્યવહાર, પેમેન્ટ કે પાકું વચન આપી શકતો નથી. મેં તમારી વાત નોંધી લીધી છે અને તેઓ પોતે તમને ફોન કરીને નિર્ણય જણાવશે."
        }
    }

    /**
     * Detects whether a recognized speech transcript is actually acoustic echo from the AI's own
     * recent Gujarati TTS output so the assistant never talks to itself.
     */
    fun isLikelyTtsSelfEcho(
        recognizedText: String,
        lastAiSpokenText: String,
        timeSinceTtsEndedMs: Long = Long.MAX_VALUE
    ): Boolean {
        val cleanRecognized = normalizeForEchoComparison(recognizedText)
        val cleanAi = normalizeForEchoComparison(lastAiSpokenText)
        if (cleanRecognized.isBlank() || cleanAi.isBlank()) return false

        if (cleanRecognized.length < 4) {
            return timeSinceTtsEndedMs < 1_500L && cleanRecognized == cleanAi
        }

        if (cleanRecognized == cleanAi) return true
        if (cleanRecognized.length >= 6 && cleanAi.contains(cleanRecognized)) return true
        if (cleanAi.length >= 8 && cleanRecognized.contains(cleanAi)) return true

        val recWords = cleanRecognized.split(" ").filter { it.length >= 2 }
        val aiWords = cleanAi.split(" ").filter { it.length >= 2 }.toSet()
        if (recWords.size >= 3 && aiWords.isNotEmpty()) {
            val matched = recWords.count { it in aiWords }
            val ratio = matched.toDouble() / recWords.size.toDouble()
            if (ratio >= 0.72) return true
        }
        return false
    }

    private fun normalizeForEchoComparison(input: String): String {
        return input
            .lowercase(Locale.ROOT)
            .replace(Regex("[.,!?;:\"'()\\[\\]\\-–—।]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Formats the elapsed AI Call Assistant session duration as `MM:SS` (or `HH:MM:SS` if >= 1 hour).
     */
    fun formatSessionDuration(startTimeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (startTimeMillis <= 0L || nowMillis < startTimeMillis) return "00:00"
        val totalSeconds = ((nowMillis - startTimeMillis) / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun isNetworkConnected(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Generates a contextual, natural Gujarati response for an active call or continuous conversation:
     * 1. Evaluates the deterministic baseline & consent/decline/safety decision via [GujaratiConversationEngine].
     * 2. If consent was declined, or if it's a sensitive payment/OTP/commitment request, returns immediately.
     * 3. When online, queries Cloud Gemini (`gemini-3.8-flash`) via Firebase AI Logic with strict Gujarati
     *    in-call assistant system instructions and multi-turn conversation history.
     * 4. Falls back seamlessly to the deterministic context-aware [GujaratiConversationEngine] if offline or timed out.
     */
    suspend fun generateInCallGujaratiReply(
        context: Context?,
        callerSpeech: String,
        userStatusInstruction: String,
        consentState: AiConsentState,
        previousTurns: List<AiConversationTurn>,
        recentTopics: List<String> = emptyList(),
        isContinuousNormalMode: Boolean = false
    ): GujaratiAiGenerationResult {
        val trimmedSpeech = callerSpeech.trim()
        val previousAiReplies = previousTurns.filter { it.speaker == "AI" }.map { it.text }
        val previousUserMessages = previousTurns.filter { it.speaker != "AI" }.map { it.text }

        // 1. Evaluate deterministic baseline decision (handles consent, explicit decline, topic detection, safety)
        val baselineDecision = if (isContinuousNormalMode) {
            GujaratiConversationEngine.evaluateContinuousTurn(
                userSpeech = trimmedSpeech,
                conversationHistory = previousTurns,
                recentTopics = recentTopics,
                userInstruction = userStatusInstruction
            )
        } else {
            GujaratiConversationEngine.evaluateCallerInput(
                callerSpeech = trimmedSpeech,
                currentConsent = consentState,
                userInstruction = userStatusInstruction,
                previousAiReplies = previousAiReplies,
                previousUserMessages = previousUserMessages
            )
        }

        // 2. If the caller declined consent or asked to stop, return the immediate polite closing
        if (baselineDecision.shouldStopImmediately || baselineDecision.nextConsentState == AiConsentState.DECLINED) {
            return GujaratiAiGenerationResult(
                gujaratiReply = baselineDecision.aiReplyGujarati,
                nextConsentState = baselineDecision.nextConsentState,
                shouldStopImmediately = true,
                detectedTopic = baselineDecision.detectedTopic,
                usedCloudGemini = false,
                modelSourceLabel = "Consent Policy Guard (Immediate Stop)"
            )
        }

        // 3. If the caller is still awaiting consent (has not yet said Yes/question), keep the consent prompt
        if (!isContinuousNormalMode && baselineDecision.nextConsentState == AiConsentState.AWAITING_CONSENT) {
            return GujaratiAiGenerationResult(
                gujaratiReply = baselineDecision.aiReplyGujarati,
                nextConsentState = AiConsentState.AWAITING_CONSENT,
                shouldStopImmediately = false,
                detectedTopic = baselineDecision.detectedTopic,
                usedCloudGemini = false,
                modelSourceLabel = "Consent Verification Prompt (gu-IN)"
            )
        }

        // 4. Immediate safety check: never make commitments, payments, or share OTPs
        if (isSensitiveCommitmentOrPaymentRequest(trimmedSpeech)) {
            return GujaratiAiGenerationResult(
                gujaratiReply = buildSafeNoCommitmentGujaratiReply(trimmedSpeech),
                nextConsentState = baselineDecision.nextConsentState,
                shouldStopImmediately = false,
                detectedTopic = "SAFETY_NO_COMMITMENT",
                usedCloudGemini = false,
                modelSourceLabel = "Safety Policy Guard (No-Commitment / Payment Protection)"
            )
        }

        // 5. Check network connectivity before invoking Firebase AI Logic
        if (!isNetworkConnected(context)) {
            return GujaratiAiGenerationResult(
                gujaratiReply = baselineDecision.aiReplyGujarati,
                nextConsentState = baselineDecision.nextConsentState,
                shouldStopImmediately = false,
                detectedTopic = baselineDecision.detectedTopic,
                usedCloudGemini = false,
                modelSourceLabel = "On-Device Gujarati Engine (Offline Mode)",
                fallbackReason = "ઇન્ટરનેટ કનેક્શન ઉપલબ્ધ નથી — ઓન-ડિવાઇસ ગુજરાતી AI એન્જિન વપરાયું."
            )
        }

        // 6. Call Firebase AI Logic (`gemini-3.8-flash`) with timeout & safety instructions
        return try {
            val systemPrompt = buildSystemInstruction(
                userStatusInstruction = userStatusInstruction,
                isContinuousNormalMode = isContinuousNormalMode
            )
            val conversationContext = buildConversationPrompt(
                callerSpeech = trimmedSpeech,
                userStatusInstruction = userStatusInstruction,
                previousTurns = previousTurns,
                isContinuousNormalMode = isContinuousNormalMode
            )

            val rawReply = withTimeout(CLOUD_TIMEOUT_MS) {
                val model = Firebase.ai.generativeModel(
                    modelName = CLOUD_MODEL_NAME,
                    systemInstruction = content { text(systemPrompt) }
                )
                val response = model.generateContent(conversationContext)
                response.text?.trim().orEmpty()
            }

            val cleanedReply = sanitizeGujaratiModelOutput(rawReply, previousTurns)
            if (cleanedReply.isNotBlank()) {
                GujaratiAiGenerationResult(
                    gujaratiReply = cleanedReply,
                    nextConsentState = baselineDecision.nextConsentState,
                    shouldStopImmediately = false,
                    detectedTopic = baselineDecision.detectedTopic,
                    usedCloudGemini = true,
                    modelSourceLabel = "Gemini 3.8 Flash (Cloud AI • gu-IN)"
                )
            } else {
                GujaratiAiGenerationResult(
                    gujaratiReply = baselineDecision.aiReplyGujarati,
                    nextConsentState = baselineDecision.nextConsentState,
                    shouldStopImmediately = false,
                    detectedTopic = baselineDecision.detectedTopic,
                    usedCloudGemini = false,
                    modelSourceLabel = "On-Device Gujarati Engine (Fallback)",
                    fallbackReason = "Cloud AI returned an empty response; used context-aware Gujarati fallback."
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Cloud Gemini generation failed or timed out (${t.javaClass.simpleName}: ${t.message}); using on-device Gujarati fallback.")
            GujaratiAiGenerationResult(
                gujaratiReply = baselineDecision.aiReplyGujarati,
                nextConsentState = baselineDecision.nextConsentState,
                shouldStopImmediately = false,
                detectedTopic = baselineDecision.detectedTopic,
                usedCloudGemini = false,
                modelSourceLabel = "On-Device Gujarati Engine (Cloud Fallback)",
                fallbackReason = "Cloud AI unavailable (${t.javaClass.simpleName}) — ઓન-ડિવાઇસ ગુજરાતી જવાબ વપરાયો."
            )
        }
    }

    private fun buildSystemInstruction(
        userStatusInstruction: String,
        isContinuousNormalMode: Boolean
    ): String {
        return if (isContinuousNormalMode) {
            """
            You are HET Phone's intelligent Gujarati AI Voice Assistant ("AI કૉલ સહાયક").
            RULES YOU MUST ALWAYS FOLLOW:
            1. Respond ONLY in natural, polite, clear Gujarati (in Gujarati script: ગુજરાતી).
            2. Keep your response concise (1 to 2 short sentences) so it sounds natural when spoken aloud via Gujarati Text-to-Speech.
            3. Understand Gujarati script, Romanized Gujarati (e.g. "kem chho", "su chale chhe"), and mixed Gujarati-English speech seamlessly.
            4. Never pretend to be a human. Always be honest that you are an AI assistant.
            5. Never make financial promises, payments, commitments, or share sensitive codes/OTPs.
            6. Avoid robotic or repetitive phrasing; adapt directly to what the user just said and the prior conversation history.
            """.trimIndent()
        } else {
            """
            You are HET Phone's intelligent Gujarati AI Call Assistant ("AI કૉલ સહાયક") speaking on an active phone call on behalf of the phone owner.
            Phone owner's current status / instruction: "$userStatusInstruction"
            
            STRICT RULES YOU MUST FOLLOW:
            1. Respond ONLY in natural, polite, conversational Gujarati script (ગુજરાતી).
            2. Keep your spoken response concise (1 to 2 sentences, under 35 words) so it plays clearly over call audio.
            3. NEVER pretend to be the human phone owner. Speak in the third person about the phone owner ("તેઓ અત્યારે...", "હું તેમનો AI સહાયક છું અને તમારો સંદેશ નોંધી લઉં છું").
            4. NEVER make promises, financial commitments, payments, bookings, or binding decisions on behalf of the phone owner. Never share OTPs, passwords, or personal data.
            5. Understand Gujarati expressions and mixed Gujarati-English speech (e.g., "urgent kaam chhe", "parcel delivery", "call back", "kya chhe").
            6. Do NOT repeat the exact same sentence from previous AI turns; acknowledge what the caller specifically said and offer to note their message or let the owner call them back.
            """.trimIndent()
        }
    }

    private fun buildConversationPrompt(
        callerSpeech: String,
        userStatusInstruction: String,
        previousTurns: List<AiConversationTurn>,
        isContinuousNormalMode: Boolean
    ): String {
        val recentHistory = previousTurns.takeLast(8).joinToString("\n") { turn ->
            val role = if (turn.speaker == "AI") "AI Assistant (ગુજરાતી)" else "Other Speaker"
            "$role: ${turn.text}"
        }
        return buildString {
            if (!isContinuousNormalMode && userStatusInstruction.isNotBlank()) {
                appendLine("Phone Owner's Status Instruction (ગુજરાતી): $userStatusInstruction")
            }
            if (recentHistory.isNotBlank()) {
                appendLine("Recent Conversation History:")
                appendLine(recentHistory)
            }
            appendLine("Latest Recognized Speech from Speaker: \"$callerSpeech\"")
            appendLine("Generate ONLY the spoken Gujarati response text (1-2 natural Gujarati sentences, no markdown, no quotes):")
        }
    }

    private fun sanitizeGujaratiModelOutput(
        rawOutput: String,
        previousTurns: List<AiConversationTurn>
    ): String {
        val cleaned = rawOutput
            .replace("```", "")
            .replace("**", "")
            .replace("*", "")
            .trim()
            .trim('"', '\'', '“', '”')
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (cleaned.length < 4) return ""
        val hasGujaratiScript = cleaned.any { ch -> ch.code in 0x0A80..0x0AFF }
        if (!hasGujaratiScript) return ""

        val lastAiTurn = previousTurns.lastOrNull { it.speaker == "AI" }?.text?.trim()
        if (!lastAiTurn.isNullOrBlank() && cleaned == lastAiTurn) {
            return ""
        }
        return cleaned
    }
}

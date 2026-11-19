package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecom.AiAssistantStatus
import com.example.telecom.AiAssistantUiState
import com.example.telecom.AiCallAssistantManager
import com.example.telecom.AiConsentState
import com.example.telecom.AiTestMode
import com.example.telecom.ContinuousConversationStage
import com.example.telecom.ContinuousConversationState
import com.example.telecom.GujaratiAiCloudEngine
import com.example.telecom.GujaratiLanguageSupportState
import com.example.telecom.NormalModeMicTestStage
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.ColorOsWhite
import kotlinx.coroutines.delay
import java.util.Locale

private val gujaratiInstructionPresets = listOf(
    "મીટિંગમાં" to "હું અત્યારે મીટિંગમાં છું અને થોડી વારમાં સામેથી ફોન કરીશ. કૃપા કરીને તમારો સંદેશ જણાવો.",
    "ડ્રાઇવિંગમાં" to "હું અત્યારે વાહન ચલાવી રહ્યો છું. અગત્યનું કામ હોય તો સંદેશ જણાવશો.",
    "વ્યસ્ત છું" to "હું અત્યારે વ્યસ્ત છું અને થોડી વારમાં સામેથી ફોન કરીશ. કૃપા કરીને તમારો સંદેશ જણાવો.",
    "પાર્સલ/ડિલિવરી" to "હું અત્યારે બહાર છું. પાર્સલ કે ડિલિવરી હોય તો સિક્યુરિટી ગેટ પર મૂકી દેવા વિનંતી."
)

private val gujaratiCallerQuickSimulatePhrases = listOf(
    "હા બોલો",
    "ક્યાં છે? અગત્યનું કામ છે",
    "પાર્સલ ડિલિવરી છે",
    "ક્યારે ફ્રી થશો?",
    "આપણે અગાઉ શું વાત કરી?",
    "ના, વાત નથી કરવી"
)

private val diagnosticStageBadges = listOf(
    AiAssistantStatus.SPEAKING to "AI Speaking",
    AiAssistantStatus.WAITING_FOR_USER to "Waiting for User",
    AiAssistantStatus.LISTENING to "AI Listening",
    AiAssistantStatus.PROCESSING to "Processing",
    AiAssistantStatus.SPEECH_RECOGNIZED to "Speech Recognized",
    AiAssistantStatus.RECOGNITION_FAILED to "Recognition Failed"
)

private val continuousStageOrder = listOf(
    ContinuousConversationStage.LISTENING,
    ContinuousConversationStage.PROCESSING,
    ContinuousConversationStage.AI_SPEAKING,
    ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE,
    ContinuousConversationStage.PAUSED,
    ContinuousConversationStage.STOPPED
)

/**
 * Confirmation & Configuration Dialog displayed when the user taps "AI Call Assistant"
 * during an active call. Explains how the Gujarati AI Assistant participates, disclosures,
 * and Android 17 speakerphone / cellular audio restrictions before obtaining explicit user confirmation.
 */
@Composable
fun AiCallAssistantConfirmationDialog(
    initialInstruction: String,
    onConfirmActivate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var instructionDraft by remember(initialInstruction) { mutableStateOf(initialInstruction) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1230),
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(
                    text = "ગુજરાતી AI કૉલ આસિસ્ટન્ટ",
                    color = ColorOsWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "AI Call Assistant — Live Gujarati Participation",
                    color = Color(0xFFB7AED0),
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "• AI ઓળખ અને સંમતિ (Consent): AI શરૂઆતમાં જ ગુજરાતીમાં પોતાની ઓળખ આપશે અને સામેની વ્યક્તિની પરવાનગી માંગશે. જો તેઓ 'ના' કહેશે તો AI તરત જ બંધ થઈ જશે.",
                            color = Color(0xFFEAE5F6),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Text(
                            text = "• Android 17 સિમ કૉલ ઑડિયો મર્યાદા: થર્ડ-પાર્ટી એપને સિમ કૉલનો સામેનો અવાજ (VOICE_DOWNLINK / CAPTURE_AUDIO_OUTPUT) સીધો વાંચવાની પરવાનગી હોતી નથી. AI આપમેળે Speakerphone ચાલુ કરશે અને ગુજરાતી SpeechRecognizer શરૂ કરશે; જો MODE_IN_CALL દરમિયાન Android 17 કૉલ ઑડિયો બ્લોક કરે, તો તે સ્પષ્ટ ડાયગ્નોસ્ટિક્સ બતાવશે.",
                            color = Color(0xFFD0C8E6),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Text(
                    text = "તમારી સૂચના / સ્ટેટસ (ગુજરાતીમાં):",
                    color = ColorOsWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gujaratiInstructionPresets.forEach { (chipTitle, presetText) ->
                        Surface(
                            color = if (instructionDraft == presetText) {
                                ColorOsGreen.copy(alpha = 0.28f)
                            } else {
                                Color.White.copy(alpha = 0.10f)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { instructionDraft = presetText }
                        ) {
                            Text(
                                text = chipTitle,
                                color = ColorOsWhite,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = instructionDraft,
                    onValueChange = { instructionDraft = it },
                    minLines = 2,
                    maxLines = 4,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = ColorOsWhite,
                        fontSize = 13.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorOsGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = ColorOsGreen
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_instruction_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmActivate(instructionDraft) },
                colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("ai_confirm_activate_btn")
            ) {
                Text(
                    text = "સક્રિય કરો (Activate AI)",
                    color = ColorOsWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("ai_confirm_cancel_btn")
            ) {
                Text(
                    text = "રદ કરો (Cancel)",
                    color = Color(0xFFD0CADC),
                    fontSize = 13.sp
                )
            }
        }
    )
}

/**
 * Live In-Call Gujarati AI Assistant Translucent Glass Card displayed on [ActiveCallScreen]
 * (and in the Settings diagnostic tester) with strict separation between:
 * - Section A: SpeechRecognizer & Gujarati (`gu-IN`) Service Diagnostics (including Error 11 Binder handling)
 * - Section B: Local Hardware Microphone Probe (with explicit disclaimer that AudioRecord != SpeechRecognizer)
 * - Section C: Cellular SIM Call Audio Diagnostics (`VOICE_DOWNLINK` / `MODE_IN_CALL` restrictions)
 * - Section D: Standalone Normal-Mode (`MODE_NORMAL`) Gujarati Microphone Recognition Test
 */
@Composable
fun LiveAiCallAssistantPanel(
    aiState: AiAssistantUiState,
    onPauseOrResume: () -> Unit,
    onRetryMic: () -> Unit,
    onStopAi: () -> Unit,
    onSimulateCallerSpeech: (String) -> Unit,
    onSaveTranscriptToNotes: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCallerTestChips by remember { mutableStateOf(false) }
    var showPipelineDiagnostics by remember { mutableStateOf(true) }
    var currentNowMillis by remember(aiState.sessionStartTimeMillis) {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(aiState.isActiveSession, aiState.sessionStartTimeMillis) {
        if (aiState.isActiveSession && aiState.sessionStartTimeMillis > 0L) {
            while (true) {
                currentNowMillis = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val formattedSessionDuration = remember(aiState.sessionStartTimeMillis, currentNowMillis) {
        GujaratiAiCloudEngine.formatSessionDuration(
            startTimeMillis = aiState.sessionStartTimeMillis,
            nowMillis = currentNowMillis
        )
    }

    val pulseTransition = rememberInfiniteTransition(label = "ai_status_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai_status_pulse_alpha"
    )

    val statusDotColor = when (aiState.status) {
        AiAssistantStatus.CONNECTING -> Color(0xFFFFB74D)
        AiAssistantStatus.SPEAKING -> Color(0xFF64B5F6)
        AiAssistantStatus.WAITING_FOR_USER -> Color(0xFFFFD54F)
        AiAssistantStatus.LISTENING -> Color(0xFF4CD964)
        AiAssistantStatus.PROCESSING -> Color(0xFFAB47BC)
        AiAssistantStatus.SPEECH_RECOGNIZED -> Color(0xFF00E676)
        AiAssistantStatus.RECOGNITION_FAILED,
        AiAssistantStatus.ERROR -> Color(0xFFFF6B6B)
        AiAssistantStatus.PAUSED -> Color(0xFFB0AABF)
        AiAssistantStatus.IDLE -> Color.Transparent
    }

    val consentLabel = when (aiState.consentState) {
        AiConsentState.AWAITING_CONSENT -> "સંમતિની રાહ..."
        AiConsentState.GRANTED -> "સંમતિ મળી ✓"
        AiConsentState.DECLINED -> "સંમતિ નકારી"
    }

    val isFailure = aiState.status.isFailureState

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xEB261A42),
                        Color(0xEB18112B)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF7CE8A6).copy(alpha = 0.45f),
                        Color(0xFF66BDF9).copy(alpha = 0.45f),
                        Color(0xFFF37BB8).copy(alpha = 0.45f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("live_ai_assistant_panel")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Status Row: Animated Dot + Status Title + Diagnostics Toggle & Consent Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusDotColor.copy(alpha = pulseAlpha))
                    )
                    Column {
                        Text(
                            text = "${aiState.status.labelGujarati} (${aiState.status.labelEnglish})",
                            color = ColorOsWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("ai_status_indicator_text")
                        )
                        Text(
                            text = "Engine: ${aiState.aiEngineModeStatus}",
                            color = Color(0xFFB3E5FC),
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("ai_model_engine_text")
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (aiState.sessionStartTimeMillis > 0L) {
                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("ai_session_duration_badge")
                        ) {
                            Text(
                                text = "⏱ $formattedSessionDuration",
                                color = Color(0xFF7CE8A6),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showPipelineDiagnostics = !showPipelineDiagnostics }
                            .testTag("ai_toggle_diagnostics_btn")
                    ) {
                        Text(
                            text = if (showPipelineDiagnostics) "Diagnostics ▲" else "Diagnostics ▼",
                            color = Color(0xFFB3E5FC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = consentLabel,
                            color = Color(0xFFE0DAF0),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Connection & Audio Path Summary Row
            Text(
                text = "Connection: ${aiState.connectionModeSummary}",
                color = Color(0xFFD0C8E6),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ai_connection_mode_text")
            )

            // Continuous Gujarati AI Conversation Card (Normal Mode Multi-Turn Voice Loop)
            ContinuousGujaratiConversationCard(
                continuousState = aiState.continuousConversation,
                isContinuousModeActive = aiState.activeTestMode == AiTestMode.CONTINUOUS_CONVERSATION ||
                    aiState.continuousConversation.isRunning ||
                    aiState.continuousConversation.conversationHistory.isNotEmpty(),
                pulseAlpha = pulseAlpha,
                onStartContinuous = {
                    AiCallAssistantManager.startContinuousConversation(context)
                },
                onPauseOrResume = {
                    if (aiState.continuousConversation.stage == ContinuousConversationStage.PAUSED) {
                        AiCallAssistantManager.resumeSession()
                    } else {
                        AiCallAssistantManager.pauseSession()
                    }
                },
                onStopContinuous = {
                    AiCallAssistantManager.stopSession(
                        reasonNote = "વપરાશકર્તા દ્વારા સતત ગુજરાતી વાતચીત બંધ કરવામાં આવી."
                    )
                },
                onManualRetry = {
                    AiCallAssistantManager.retryListening()
                },
                onSelectRestartDelayMs = { delayMs ->
                    AiCallAssistantManager.setPostTtsRestartDelayMs(delayMs)
                }
            )

            // 5-Stage Diagnostic Status Strip:
            // AI Speaking | Waiting for User | AI Listening | Speech Recognized | Recognition Failed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("ai_diagnostic_status_strip"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                diagnosticStageBadges.forEach { (stageStatus, stageLabel) ->
                    val isSelected = aiState.status == stageStatus ||
                        (stageStatus == AiAssistantStatus.RECOGNITION_FAILED && aiState.status == AiAssistantStatus.ERROR)

                    val chipColor = when {
                        !isSelected -> Color.White.copy(alpha = 0.07f)
                        stageStatus == AiAssistantStatus.RECOGNITION_FAILED -> Color(0xFFD32F2F).copy(alpha = 0.45f)
                        stageStatus == AiAssistantStatus.SPEECH_RECOGNIZED -> Color(0xFF00C853).copy(alpha = 0.38f)
                        stageStatus == AiAssistantStatus.LISTENING -> ColorOsGreen.copy(alpha = 0.36f)
                        stageStatus == AiAssistantStatus.WAITING_FOR_USER -> Color(0xFFFFB300).copy(alpha = 0.35f)
                        else -> Color(0xFF29B6F6).copy(alpha = 0.35f)
                    }

                    val borderColor = when {
                        !isSelected -> Color.White.copy(alpha = 0.12f)
                        stageStatus == AiAssistantStatus.RECOGNITION_FAILED -> Color(0xFFFF8A80)
                        stageStatus == AiAssistantStatus.SPEECH_RECOGNIZED ||
                            stageStatus == AiAssistantStatus.LISTENING -> Color(0xFF7CE8A6)
                        stageStatus == AiAssistantStatus.WAITING_FOR_USER -> Color(0xFFFFE082)
                        else -> Color(0xFF81D4FA)
                    }

                    Surface(
                        color = chipColor,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                    ) {
                        Text(
                            text = if (isSelected) "● $stageLabel" else stageLabel,
                            color = if (isSelected) ColorOsWhite else Color(0xFFB5ADC9),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Comprehensive Separated Audio & SpeechRecognizer Diagnostics Panel
            AnimatedVisibility(visible = showPipelineDiagnostics) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.34f))
                        .border(
                            width = 0.8.dp,
                            color = if (aiState.lastErrorCode != null) {
                                Color(0xFFFF8A80).copy(alpha = 0.55f)
                            } else {
                                Color.White.copy(alpha = 0.14f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("ai_pipeline_diagnostics_box")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        // TTS Pipeline (AI Speaking)
                        Text(
                            text = "TTS (Speaking): ${if (aiState.isTtsInitialized) "Ready" else "Init..."} | Locale: ${aiState.ttsLocaleStatus} | Active: ${if (aiState.isTtsSpeaking) "YES" else "NO"}",
                            color = Color(0xFF90CAF9),
                            fontSize = 9.5.sp,
                            lineHeight = 12.sp
                        )

                        // SECTION A: SpeechRecognizer & Gujarati (gu-IN) Service Diagnostics
                        Text(
                            text = "[A] SpeechRecognizer Service & Gujarati (gu-IN) Support:",
                            color = Color(0xFF81D4FA),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Selected Service: ${aiState.recognizerServiceComponent.ifBlank { "Checking..." }} | Binding: ${aiState.activeBindingStrategyLabel}",
                            color = Color(0xFFA5D6A7),
                            fontSize = 9.sp,
                            lineHeight = 11.5.sp,
                            modifier = Modifier.testTag("ai_diag_recognizer_init")
                        )
                        Text(
                            text = "• Discovered Providers: ${aiState.discoveredRecognitionServices}",
                            color = Color(0xFFCFD8DC),
                            fontSize = 9.sp,
                            lineHeight = 11.5.sp
                        )
                        val guSupportColor = when (aiState.gujaratiSupportState) {
                            GujaratiLanguageSupportState.VERIFIED_SUPPORTED -> Color(0xFF69F0AE)
                            GujaratiLanguageSupportState.VERIFIED_NOT_SUPPORTED,
                            GujaratiLanguageSupportState.VERIFIED_PENDING_DOWNLOAD -> Color(0xFFFF8A80)
                            else -> Color(0xFFFFE082)
                        }
                        Text(
                            text = "• Gujarati (gu-IN) Support: ${aiState.gujaratiSupportState.labelGujarati} — ${aiState.gujaratiLanguageSupportStatus}",
                            color = guSupportColor,
                            fontSize = 9.sp,
                            lineHeight = 11.5.sp,
                            modifier = Modifier.testTag("ai_diag_gujarati_support")
                        )

                        val genuinelyListening = aiState.status == AiAssistantStatus.LISTENING && aiState.isRecognizerReadyForSpeech
                        Text(
                            text = "• Session: ${aiState.recognitionSessionStatus} | Listening: ${if (genuinelyListening) "YES" else "NO"} | ReadyForSpeech: ${if (aiState.isRecognizerReadyForSpeech) "YES" else "NO"} | Disconnects (Code 11): ${aiState.binderDisconnectCount}",
                            color = if (genuinelyListening) Color(0xFF81C784) else Color(0xFFFFE082),
                            fontSize = 9.sp,
                            lineHeight = 11.5.sp,
                            modifier = Modifier.testTag("ai_diag_session_status")
                        )
                        Text(
                            text = "• Real Audio Reaching SpeechRecognizer: ${aiState.realAudioReachingEngineStatus}",
                            color = if (aiState.isReceivingMicAudio) Color(0xFF69F0AE) else Color(0xFFFFCC80),
                            fontSize = 9.sp,
                            lineHeight = 11.5.sp,
                            modifier = Modifier.testTag("ai_diag_real_audio_status")
                        )

                        // SECTION B: Local Hardware Microphone Probe (Strictly Separate from SpeechRecognizer & SIM Call)
                        Text(
                            text = "[B] Local Hardware Microphone Probe (Process-Local Only):",
                            color = Color(0xFFB2DFDB),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Mic Permission: ${aiState.micPermissionStatus} | Availability: ${aiState.micAvailabilityStatus}",
                            color = Color(0xFFE0DAF0),
                            fontSize = 9.sp,
                            lineHeight = 11.5.sp,
                            modifier = Modifier.testTag("ai_diag_mic_perm_and_avail")
                        )
                        Text(
                            text = "• Local AudioRecord Init: ${aiState.audioRecordingInitStatus}",
                            color = Color(0xFFB2DFDB),
                            fontSize = 9.sp,
                            lineHeight = 11.5.sp,
                            modifier = Modifier.testTag("ai_diag_audiorecord_init")
                        )
                        Text(
                            text = "• ${aiState.audioRecordScopeDisclaimer}",
                            color = Color(0xFFB0BEC5),
                            fontSize = 8.5.sp,
                            lineHeight = 11.sp
                        )

                        // SECTION C: Cellular SIM Call Audio Diagnostics (Strictly Separate from Local Mic)
                        Text(
                            text = "[C] Cellular SIM Call Audio Diagnostics:",
                            color = Color(0xFFFFAB91),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val routingDisplay = aiState.audioRoutingInfo.ifBlank { aiState.audioContentionDetail }
                        if (routingDisplay.isNotBlank()) {
                            Text(
                                text = "• Routing: $routingDisplay",
                                color = Color(0xFFD0C8E6),
                                fontSize = 9.sp,
                                lineHeight = 11.5.sp,
                                modifier = Modifier.testTag("ai_diag_audio_routing")
                            )
                        }
                        Text(
                            text = "• ${aiState.cellularCallAudioAccessStatus}",
                            color = Color(0xFFFFAB91),
                            fontSize = 9.sp,
                            lineHeight = 11.5.sp,
                            modifier = Modifier.testTag("ai_diag_cellular_call_audio_status")
                        )

                        // Last Recognition Error and Timestamp
                        Text(
                            text = "• Last SpeechRecognizer Error: ${aiState.lastErrorWithTimestamp}",
                            color = if (aiState.lastErrorCode != null) Color(0xFFFF8A80) else Color(0xFFB0BEC5),
                            fontSize = 9.5.sp,
                            fontWeight = if (aiState.lastErrorCode != null) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.testTag("ai_error_code_text")
                        )

                        if (aiState.lastErrorCode != null && !aiState.lastErrorDetail.isNullOrBlank()) {
                            Text(
                                text = aiState.lastErrorDetail,
                                color = Color(0xFFFFCCBC),
                                fontSize = 9.sp,
                                lineHeight = 11.5.sp,
                                modifier = Modifier.testTag("ai_error_detail_text")
                            )
                        }
                    }
                }
            }

            // SECTION D: Standalone Normal-Mode Gujarati (gu-IN) Microphone Recognition Test Card
            val micTest = aiState.normalModeMicTest
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF10253A).copy(alpha = 0.72f))
                    .border(
                        width = 0.8.dp,
                        color = when (micTest.stage) {
                            NormalModeMicTestStage.SUCCEEDED -> Color(0xFF69F0AE).copy(alpha = 0.65f)
                            NormalModeMicTestStage.FAILED -> Color(0xFFFF8A80).copy(alpha = 0.65f)
                            NormalModeMicTestStage.CONNECTING,
                            NormalModeMicTestStage.STARTING,
                            NormalModeMicTestStage.LISTENING_SPEAK_NOW,
                            NormalModeMicTestStage.PROCESSING -> Color(0xFFFFD54F).copy(alpha = 0.65f)
                            NormalModeMicTestStage.STOPPED,
                            NormalModeMicTestStage.NOT_RUN -> Color.White.copy(alpha = 0.16f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("ai_normal_mode_gujarati_mic_test_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[D] નોર્મલ-મોડ ગુજરાતી માઇક ટેસ્ટ (Normal-Mode gu-IN Mic Test)",
                            color = Color(0xFF81D4FA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = if (micTest.isRunning) {
                                ColorOsRed.copy(alpha = 0.75f)
                            } else {
                                ColorOsGreen.copy(alpha = 0.75f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (micTest.isRunning) {
                                        AiCallAssistantManager.stopStandaloneNormalModeGujaratiMicTest()
                                    } else {
                                        AiCallAssistantManager.startStandaloneNormalModeGujaratiMicTest(context)
                                    }
                                }
                                .testTag("btn_normal_mode_gujarati_mic_test")
                        ) {
                            Text(
                                text = if (micTest.isRunning) "ટેસ્ટ રોકો (Stop)" else "માઇક ટેસ્ટ શરૂ કરો",
                                color = ColorOsWhite,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Status: ${micTest.stage.label}",
                        color = when (micTest.stage) {
                            NormalModeMicTestStage.SUCCEEDED -> Color(0xFF69F0AE)
                            NormalModeMicTestStage.FAILED -> Color(0xFFFF8A80)
                            NormalModeMicTestStage.CONNECTING,
                            NormalModeMicTestStage.STARTING,
                            NormalModeMicTestStage.LISTENING_SPEAK_NOW,
                            NormalModeMicTestStage.PROCESSING -> Color(0xFFFFE082)
                            NormalModeMicTestStage.STOPPED,
                            NormalModeMicTestStage.NOT_RUN -> Color(0xFFB0BEC5)
                        },
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("normal_mode_mic_test_status")
                    )

                    if (micTest.gujaratiExplanation.isNotBlank()) {
                        Text(
                            text = micTest.gujaratiExplanation,
                            color = if (micTest.stage == NormalModeMicTestStage.FAILED) {
                                Color(0xFFFFCCBC)
                            } else {
                                Color(0xFFE0F7FA)
                            },
                            fontSize = 9.5.sp,
                            lineHeight = 12.5.sp,
                            modifier = Modifier.testTag("normal_mode_mic_test_gujarati_explanation")
                        )
                    }

                    Text(
                        text = micTest.diagnosticSummary,
                        color = Color(0xFFE0DAF0),
                        fontSize = 9.sp,
                        lineHeight = 11.5.sp
                    )

                    if (micTest.partialTranscript.isNotBlank() && micTest.recognizedGujaratiText.isBlank()) {
                        Text(
                            text = "Partial Gujarati (gu-IN): \"${micTest.partialTranscript}\"",
                            color = Color(0xFFFFE082),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("normal_mode_mic_test_partial_text")
                        )
                    }

                    if (micTest.recognizedGujaratiText.isNotBlank()) {
                        Text(
                            text = "CONFIRMED ✓ — Recognized Gujarati (gu-IN): \"${micTest.recognizedGujaratiText}\"",
                            color = Color(0xFF69F0AE),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("normal_mode_mic_test_result_text")
                        )
                    }

                    if (!micTest.errorSymbolAndCode.isNullOrBlank()) {
                        Text(
                            text = "Exact Error: ${micTest.errorSymbolAndCode}",
                            color = Color(0xFFFF8A80),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("normal_mode_mic_test_error_code")
                        )
                    }
                }
            }

            // SECTION E: Independent TTS + Microphone Test Status & Final Result Card
            val ttsMicTest = aiState.ttsAndMicTest
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1F1638).copy(alpha = 0.78f))
                    .border(
                        width = 0.8.dp,
                        color = when {
                            ttsMicTest.finalRecognizedTranscript.isNotBlank() -> Color(0xFF69F0AE).copy(alpha = 0.65f)
                            !ttsMicTest.errorSymbolAndCode.isNullOrBlank() -> Color(0xFFFF8A80).copy(alpha = 0.65f)
                            ttsMicTest.isRunning -> Color(0xFF64B5F6).copy(alpha = 0.65f)
                            else -> Color.White.copy(alpha = 0.16f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("ai_tts_and_mic_test_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "[E] TTS + Microphone Test Status & Result:",
                        color = Color(0xFFB39DDB),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• 1. TTS Initialization: ${ttsMicTest.ttsInitStatus} | Speaking: ${ttsMicTest.ttsSpeakingStatus}",
                        color = Color(0xFF90CAF9),
                        fontSize = 9.sp,
                        lineHeight = 11.5.sp,
                        modifier = Modifier.testTag("tts_mic_test_tts_status")
                    )
                    Text(
                        text = "• 2. Mic Recognition: ${ttsMicTest.micRecognitionStatus}",
                        color = when {
                            ttsMicTest.finalRecognizedTranscript.isNotBlank() -> Color(0xFF69F0AE)
                            !ttsMicTest.errorSymbolAndCode.isNullOrBlank() -> Color(0xFFFF8A80)
                            ttsMicTest.readyForSpeechFired -> Color(0xFFFFE082)
                            else -> Color(0xFFE0DAF0)
                        },
                        fontSize = 9.sp,
                        lineHeight = 11.5.sp,
                        modifier = Modifier.testTag("tts_mic_test_mic_status")
                    )
                    if (ttsMicTest.partialTranscript.isNotBlank() && ttsMicTest.finalRecognizedTranscript.isBlank()) {
                        Text(
                            text = "• Partial Gujarati Transcript: \"${ttsMicTest.partialTranscript}\"",
                            color = Color(0xFFFFE082),
                            fontSize = 9.5.sp
                        )
                    }
                    if (ttsMicTest.finalRecognizedTranscript.isNotBlank()) {
                        Text(
                            text = "CONFIRMED ✓ — Recognized Gujarati: \"${ttsMicTest.finalRecognizedTranscript}\"",
                            color = Color(0xFF69F0AE),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("tts_mic_test_final_transcript")
                        )
                    }
                    if (!ttsMicTest.errorSymbolAndCode.isNullOrBlank()) {
                        Text(
                            text = "Exact Failure Reason: ${ttsMicTest.errorSymbolAndCode} — ${ttsMicTest.gujaratiExplanation}",
                            color = Color(0xFFFF8A80),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 12.sp,
                            modifier = Modifier.testTag("tts_mic_test_error_reason")
                        )
                    }
                    Text(
                        text = ttsMicTest.finalOutcomeSummary,
                        color = Color(0xFFD0C8E6),
                        fontSize = 9.sp,
                        lineHeight = 11.5.sp
                    )
                }
            }

            // Explicit Android 17 / Motorola SIM Call Audio Restriction Notice when detected
            if (aiState.isSimCallAudioRestrictionDetected && !aiState.simCallRestrictionReportGujarati.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3E2723).copy(alpha = 0.72f))
                        .border(
                            width = 1.dp,
                            color = Color(0xFFFFAB91).copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("ai_sim_call_restriction_banner")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "SIM Call Audio Restriction Detected (Android 17 / Motorola)",
                            color = Color(0xFFFFCCBC),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = aiState.simCallRestrictionReportGujarati,
                            color = Color(0xFFFFE0B2),
                            fontSize = 10.sp,
                            lineHeight = 13.5.sp
                        )
                        if (aiState.unavailableAudioSourceExplanation.isNotBlank()) {
                            Text(
                                text = aiState.unavailableAudioSourceExplanation,
                                color = Color(0xFFD7CCC8),
                                fontSize = 9.sp,
                                lineHeight = 11.5.sp
                            )
                        }
                    }
                }
            }

            // Latest AI Gujarati Speech
            if (aiState.lastAiSpokenText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Column {
                        Text(
                            text = "AI આસિસ્ટન્ટ (ગુજરાતી):",
                            color = Color(0xFF7CE8A6),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = aiState.lastAiSpokenText,
                            color = ColorOsWhite,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("ai_last_spoken_text")
                        )
                    }
                }
            }

            // Latest or Partial Caller Speech
            val callerDisplay = aiState.partialCallerText.ifBlank { aiState.lastHeardCallerText }
            if (callerDisplay.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "કૉલર: $callerDisplay",
                        color = Color(0xFFD8D2E8),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("ai_last_heard_caller_text")
                    )
                }
            }

            // Diagnostic / Audio Mode Note
            if (!aiState.diagnosticNote.isNullOrBlank()) {
                Text(
                    text = aiState.diagnosticNote,
                    color = if (isFailure) {
                        Color(0xFFFFB4AB)
                    } else {
                        Color(0xFFBDB5D2)
                    },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.testTag("ai_diagnostic_note_text")
                )
            }

            // Expandable quick caller response bar (for supported assisted replies when SIM downlink is blocked)
            AnimatedVisibility(visible = showCallerTestChips || isFailure || aiState.isSimCallAudioRestrictionDetected) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "કૉલરનો જવાબ પસંદ કરો (Assisted Gujarati Reply Trigger):",
                        color = Color(0xFFC8C0DC),
                        fontSize = 10.5.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        gujaratiCallerQuickSimulatePhrases.forEach { phrase ->
                            Surface(
                                color = Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSimulateCallerSpeech(phrase) }
                            ) {
                                Text(
                                    text = phrase,
                                    color = ColorOsWhite,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Control Row: Pause/Resume, Retry Listening, Phrase Test Toggle, Stop AI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause / Resume AI Button
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPauseOrResume() }
                            .testTag("call_btn_pause_resume_ai")
                    ) {
                        Text(
                            text = if (aiState.status == AiAssistantStatus.PAUSED) {
                                "ફરી શરૂ (Resume)"
                            } else {
                                "થોભાવો (Pause)"
                            },
                            color = ColorOsWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Always-available Retry Listening button
                    Surface(
                        color = if (isFailure) {
                            Color(0xFF3E5B8A).copy(alpha = 0.85f)
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onRetryMic() }
                            .testTag("call_btn_retry_listening_ai")
                    ) {
                        Text(
                            text = "ફરી સાંભળો",
                            color = ColorOsWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showCallerTestChips = !showCallerTestChips }
                            .testTag("call_btn_toggle_caller_phrases")
                    ) {
                        Text(
                            text = if (showCallerTestChips) "છુપાવો" else "વાક્ય ટેસ્ટ",
                            color = Color(0xFFD8D2E8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }

                // Prominent Red Stop AI Button
                Surface(
                    color = ColorOsRed,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val transcript = aiState.turns.joinToString("\n") { turn ->
                                val prefix = if (turn.speaker == "AI") "AI" else "કૉલર"
                                "$prefix: ${turn.text}"
                            }
                            if (transcript.isNotBlank()) {
                                onSaveTranscriptToNotes(transcript)
                            }
                            onStopAi()
                        }
                        .testTag("call_btn_stop_ai")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI બંધ કરો",
                            color = ColorOsWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Continuous Gujarati AI Conversation UI Card in Normal Mode (`MODE_NORMAL`).
 * Displays:
 * - 6-Stage Continuous Status Strip: `Listening`, `Processing`, `AI Speaking`,
 *   `Waiting for Your Response`, `Paused`, `Stopped`
 * - Live Microphone Activity Indicator (with real RMS dB and state description)
 * - Configurable post-TTS auto-restart delay chips (`350ms`, `500ms`, `800ms`)
 * - Recognized Gujarati text & live partial Gujarati text
 * - AI's Gujarati response
 * - Multi-turn Conversation History (User + AI turns)
 * - Start / Pause-Resume / Stop / Manual Retry buttons
 */
@Composable
fun ContinuousGujaratiConversationCard(
    continuousState: ContinuousConversationState,
    isContinuousModeActive: Boolean,
    pulseAlpha: Float,
    onStartContinuous: () -> Unit,
    onPauseOrResume: () -> Unit,
    onStopContinuous: () -> Unit,
    onManualRetry: () -> Unit,
    onSelectRestartDelayMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFullHistory by remember { mutableStateOf(true) }
    val stage = continuousState.stage

    val borderAccentColor = when (stage) {
        ContinuousConversationStage.LISTENING -> Color(0xFF4CD964)
        ContinuousConversationStage.PROCESSING -> Color(0xFFCE93D8)
        ContinuousConversationStage.AI_SPEAKING -> Color(0xFF64B5F6)
        ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE -> Color(0xFFFFD54F)
        ContinuousConversationStage.PAUSED -> Color(0xFFB0BEC5)
        ContinuousConversationStage.STOPPED -> if (continuousState.lastErrorCode != null) {
            Color(0xFFFF8A80)
        } else {
            Color.White.copy(alpha = 0.24f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF172A3A).copy(alpha = 0.90f),
                        Color(0xFF141E30).copy(alpha = 0.92f)
                    )
                )
            )
            .border(
                width = if (continuousState.isRunning) 1.2.dp else 0.8.dp,
                color = borderAccentColor.copy(alpha = 0.78f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("continuous_gujarati_conversation_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Header Row: Title + Turn Counter Badge + Start/Restart Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "સતત ગુજરાતી AI વાતચીત (Continuous Gujarati Conversation)",
                        color = Color(0xFF7CE8A6),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Stage: ${stage.labelEnglish} (${stage.labelGujarati}) • Turns: ${continuousState.completedTurnCount}",
                        color = Color(0xFFE0F2F1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("continuous_current_stage_label")
                    )
                }

                Surface(
                    color = if (continuousState.isRunning) {
                        Color(0xFF26A69A).copy(alpha = 0.35f)
                    } else {
                        ColorOsGreen
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onStartContinuous() }
                        .testTag("continuous_btn_start")
                ) {
                    Text(
                        text = if (continuousState.isRunning) {
                            "ફરી શરૂ (Restart)"
                        } else {
                            "સતત વાતચીત શરૂ કરો (Start)"
                        },
                        color = ColorOsWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // 6-Stage Continuous Conversation Status Strip:
            // Listening | Processing | AI Speaking | Waiting for Your Response | Paused | Stopped
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("continuous_stage_strip"),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                continuousStageOrder.forEach { itemStage ->
                    val isSelected = stage == itemStage
                    val chipBg = when {
                        !isSelected -> Color.White.copy(alpha = 0.07f)
                        itemStage == ContinuousConversationStage.LISTENING -> ColorOsGreen.copy(alpha = 0.42f)
                        itemStage == ContinuousConversationStage.PROCESSING -> Color(0xFF8E24AA).copy(alpha = 0.45f)
                        itemStage == ContinuousConversationStage.AI_SPEAKING -> Color(0xFF1E88E5).copy(alpha = 0.45f)
                        itemStage == ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE -> Color(0xFFFFB300).copy(alpha = 0.38f)
                        itemStage == ContinuousConversationStage.PAUSED -> Color(0xFF546E7A).copy(alpha = 0.50f)
                        ContinuousConversationStage.STOPPED == itemStage -> Color(0xFFD32F2F).copy(alpha = 0.38f)
                        else -> Color.White.copy(alpha = 0.15f)
                    }
                    Surface(
                        color = chipBg,
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.border(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = if (isSelected) borderAccentColor else Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(9.dp)
                        )
                    ) {
                        Text(
                            text = if (isSelected) "● ${itemStage.labelEnglish}" else itemStage.labelEnglish,
                            color = if (isSelected) ColorOsWhite else Color(0xFFB0BEC5),
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                        )
                    }
                }
            }

            // Live Microphone Activity Indicator + Configurable Post-TTS Delay Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.32f))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("continuous_mic_activity_indicator")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val micDotColor = when {
                                stage == ContinuousConversationStage.LISTENING && continuousState.isReceivingMicAudio ->
                                    Color(0xFF00E676)
                                stage == ContinuousConversationStage.LISTENING && continuousState.readyForSpeechFired ->
                                    Color(0xFF69F0AE).copy(alpha = pulseAlpha)
                                stage == ContinuousConversationStage.AI_SPEAKING ->
                                    Color(0xFF64B5F6)
                                stage == ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE ->
                                    Color(0xFFFFD54F)
                                else -> Color(0xFF90A4AE)
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(micDotColor)
                            )
                            Text(
                                text = "Mic: ${continuousState.micActivityLabel}",
                                color = Color(0xFFE0F7FA),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = String.format(
                                Locale.US,
                                "RMS: %.1f dB (Peak: %.1f dB)",
                                continuousState.currentMicRmsDb,
                                continuousState.peakMicRmsDb
                            ),
                            color = if (continuousState.isReceivingMicAudio) Color(0xFF69F0AE) else Color(0xFFB0BEC5),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Configurable Post-TTS Delay Selector (prevents TTS echo from triggering mic)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TTS → Mic Auto-Restart Delay:",
                            color = Color(0xFFB0BEC5),
                            fontSize = 8.8.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(350L, 500L, 800L).forEach { delayOption ->
                                val isCurrentDelay = continuousState.postTtsRestartDelayMs == delayOption
                                Surface(
                                    color = if (isCurrentDelay) {
                                        Color(0xFF26A69A).copy(alpha = 0.45f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    },
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .clickable { onSelectRestartDelayMs(delayOption) }
                                ) {
                                    Text(
                                        text = "${delayOption}ms",
                                        color = if (isCurrentDelay) ColorOsWhite else Color(0xFFB0BEC5),
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isCurrentDelay) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Status Explanation in Gujarati & English
            Text(
                text = continuousState.statusDetailGujarati,
                color = Color(0xFFE0F2F1),
                fontSize = 10.sp,
                lineHeight = 13.5.sp,
                modifier = Modifier.testTag("continuous_status_gujarati_text")
            )

            // Live Partial Gujarati Speech (while user is speaking)
            if (continuousState.partialGujaratiText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1B5E20).copy(alpha = 0.38f))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "સાંભળ્યું (Live Partial gu-IN): \"${continuousState.partialGujaratiText}\"",
                        color = Color(0xFFFFE082),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("continuous_partial_gujarati_text")
                    )
                }
            }

            // Recognized Gujarati Text (User's latest recognized speech)
            if (continuousState.lastRecognizedGujaratiText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.09f))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = "તમારો ઓળખાયેલ ગુજરાતી અવાજ (Recognized Gujarati Text):",
                            color = Color(0xFF80CBC4),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = continuousState.lastRecognizedGujaratiText,
                            color = ColorOsWhite,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("continuous_recognized_gujarati_text")
                        )
                    }
                }
            }

            // AI's Gujarati Response
            if (continuousState.lastAiGujaratiResponse.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0D47A1).copy(alpha = 0.35f))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = "AI નો ગુજરાતી જવાબ (AI's Gujarati Response):",
                            color = Color(0xFF90CAF9),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = continuousState.lastAiGujaratiResponse,
                            color = ColorOsWhite,
                            fontSize = 11.5.sp,
                            lineHeight = 15.5.sp,
                            modifier = Modifier.testTag("continuous_ai_gujarati_response")
                        )
                    }
                }
            }

            // Error Banner + Manual Retry Button when an error occurs
            if (!continuousState.lastErrorSymbolAndCode.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFB71C1C).copy(alpha = 0.32f))
                        .border(
                            width = 0.8.dp,
                            color = Color(0xFFFF8A80).copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("continuous_error_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recognition Error: ${continuousState.lastErrorSymbolAndCode}",
                                color = Color(0xFFFF8A80),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = continuousState.statusDetailGujarati,
                                color = Color(0xFFFFCCBC),
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                        }
                        if (continuousState.isManualRetryAvailable) {
                            Surface(
                                color = ColorOsGreen,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onManualRetry() }
                                    .testTag("continuous_btn_manual_retry")
                            ) {
                                Text(
                                    text = "ફરી પ્રયાસ (Retry)",
                                    color = ColorOsWhite,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Multi-Turn Conversation History Section
            if (continuousState.conversationHistory.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.30f))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("continuous_conversation_history_list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "વાતચીતનો ઇતિહાસ (Conversation History — ${continuousState.conversationHistory.size} messages):",
                            color = Color(0xFFB2DFDB),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (showFullHistory) "Hide ▲" else "Show ▼",
                            color = Color(0xFF81D4FA),
                            fontSize = 9.sp,
                            modifier = Modifier.clickable { showFullHistory = !showFullHistory }
                        )
                    }
                    AnimatedVisibility(visible = showFullHistory) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            continuousState.conversationHistory.takeLast(8).forEachIndexed { idx, turn ->
                                val isAi = turn.speaker == "AI"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isAi) {
                                                Color(0xFF1E88E5).copy(alpha = 0.18f)
                                            } else {
                                                Color(0xFF43A047).copy(alpha = 0.18f)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isAi) "AI:" else "તમે:",
                                        color = if (isAi) Color(0xFF90CAF9) else Color(0xFF81C784),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = turn.text,
                                        color = ColorOsWhite,
                                        fontSize = 9.5.sp,
                                        lineHeight = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Continuous Conversation Action Controls: Pause/Resume + Stop + Manual Retry
            if (isContinuousModeActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = if (stage == ContinuousConversationStage.PAUSED) {
                                ColorOsGreen.copy(alpha = 0.85f)
                            } else {
                                Color.White.copy(alpha = 0.16f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPauseOrResume() }
                                .testTag("continuous_btn_pause_resume")
                        ) {
                            Text(
                                text = if (stage == ContinuousConversationStage.PAUSED) {
                                    "ફરી શરૂ કરો (Resume)"
                                } else {
                                    "થોભાવો (Pause)"
                                },
                                color = ColorOsWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onManualRetry() }
                                .testTag("continuous_btn_retry_mic")
                        ) {
                            Text(
                                text = "માઇક ફરી શરૂ (Retry Mic)",
                                color = ColorOsWhite,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Surface(
                        color = ColorOsRed,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onStopContinuous() }
                            .testTag("continuous_btn_stop")
                    ) {
                        Text(
                            text = "બંધ કરો (Stop)",
                            color = ColorOsWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}


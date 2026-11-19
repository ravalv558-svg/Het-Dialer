package com.example

import android.speech.SpeechRecognizer
import android.telecom.Call
import com.example.model.ActiveCallUiState
import com.example.model.AppThemeMode
import com.example.model.BlockedNumberEntry
import com.example.model.CallDirection
import com.example.model.CallLogItem
import com.example.model.ContactItem
import com.example.model.ContactSortOrder
import com.example.model.DeletedContactEntry
import com.example.model.DuplicateContactGroup
import com.example.model.PhoneNumberEntry
import com.example.telecom.AiCallAssistantManager
import com.example.telecom.GujaratiConversationEngine
import com.example.ui.theme.AvatarBlue
import com.example.ui.theme.AvatarCyan
import com.example.ui.theme.AvatarGreen
import com.example.ui.theme.AvatarMagenta
import com.example.ui.theme.AvatarPurple
import com.example.ui.theme.avatarColorForName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun verifyContactInitialAndSectionLetterAndSubtitle() {
        val contact = ContactItem(
            id = 1L,
            displayName = "Sophia",
            jobTitle = "Designer",
            phones = listOf(
                PhoneNumberEntry(
                    dataId = 10L,
                    number = "+1 267-380-0111",
                    typeLabel = "Mobile",
                    isDefault = true,
                    isRecent = true,
                    location = "pennsylvania"
                )
            )
        )
        assertEquals("S", contact.initial)
        assertEquals("S", contact.sectionLetter)
        assertEquals("Designer", contact.subtitle)
        assertEquals("+1 267-380-0111", contact.primaryPhone)
        assertEquals(AvatarBlue, avatarColorForName("Sophia"))
        assertEquals(AvatarCyan, avatarColorForName("Aaliyah"))
        assertEquals(AvatarPurple, avatarColorForName("Agatha Roosevelt"))
        assertEquals(AvatarGreen, avatarColorForName("Bank Receipt"))
        assertEquals(AvatarMagenta, avatarColorForName("Bob"))
    }

    @Test
    fun verifyCallLogDisplayTitlesAndSubtitles() {
        val namedLog = CallLogItem(
            id = 1L,
            number = "+12673800123",
            formattedNumber = "+1 267-380-0123",
            cachedName = "Sophia",
            contactId = 1L,
            timestamp = 1700000000000L,
            durationSeconds = 56L,
            direction = CallDirection.INCOMING,
            isHd = true,
            geocodedLocation = "pennsylvania",
            simLabel = "SIM 1",
            phoneAccountId = "1"
        )
        assertEquals("Sophia", namedLog.displayTitle)
        assertEquals("+1 267-380-0123", namedLog.displaySubtitle)
        assertTrue(namedLog.isHd)

        val unknownLog = CallLogItem(
            id = 2L,
            number = "+12673801999",
            formattedNumber = "+1 267-380-1999",
            cachedName = null,
            contactId = null,
            timestamp = 1700000000000L,
            durationSeconds = 0L,
            direction = CallDirection.OUTGOING,
            isHd = true,
            geocodedLocation = "pennsylvania",
            simLabel = null,
            phoneAccountId = null
        )
        assertEquals("+1 267-380-1999", unknownLog.displayTitle)
        assertEquals("pennsylvania", unknownLog.displaySubtitle)
    }

    @Test
    fun verifyActiveCallAndOutgoingAndWaitingStateFlags() {
        val ringing = ActiveCallUiState(
            callId = "1",
            number = "+1 267-380-0123",
            displayName = "Sophia",
            state = Call.STATE_RINGING,
            isIncoming = true
        )
        assertTrue(ringing.isRinging)
        assertFalse(ringing.isOutgoingDialing)
        assertFalse(ringing.isActiveOrDialing)

        val dialing = ringing.copy(
            state = Call.STATE_DIALING,
            isIncoming = false
        )
        assertFalse(dialing.isRinging)
        assertTrue(dialing.isOutgoingDialing)
        assertTrue(dialing.isActiveOrDialing)

        val activeWithWaitingAndBluetooth = dialing.copy(
            state = Call.STATE_ACTIVE,
            isBluetoothOn = true,
            isBluetoothAvailable = true,
            isWaitingCallRinging = true,
            waitingCallerName = "Marco",
            waitingCallNumber = "+1 267-555-0199"
        )
        assertFalse(activeWithWaitingAndBluetooth.isRinging)
        assertFalse(activeWithWaitingAndBluetooth.isOutgoingDialing)
        assertTrue(activeWithWaitingAndBluetooth.isActiveOrDialing)
        assertTrue(activeWithWaitingAndBluetooth.isBluetoothOn)
        assertTrue(activeWithWaitingAndBluetooth.isWaitingCallRinging)
        assertEquals("Marco", activeWithWaitingAndBluetooth.waitingCallerName)
    }

    @Test
    fun verifyColorOs17ModelsForRecycleBinBlockedAndSorting() {
        val deleted = DeletedContactEntry(
            deletedId = "rec_1",
            deletedTimestamp = 1700000000000L,
            displayName = "Marco",
            primaryNumber = "+12675550199",
            secondaryNumber = "",
            phoneticName = "",
            email = "marco@example.com",
            company = "OPPO",
            jobTitle = "Engineer",
            address = "",
            notes = ""
        )
        assertEquals("Marco", deleted.displayName)
        assertEquals("+12675550199", deleted.primaryNumber)

        val blocked = BlockedNumberEntry(
            id = 1L,
            number = "+12675550000",
            formattedNumber = "+1 267-555-0000",
            addedTimestamp = 1700000000000L,
            label = "Spam caller"
        )
        assertEquals("+1 267-555-0000", blocked.formattedNumber)

        val dupGroup = DuplicateContactGroup(
            keyLabel = "Same name: Sophia",
            contacts = listOf(
                ContactItem(id = 1L, displayName = "Sophia"),
                ContactItem(id = 2L, displayName = "Sophia")
            )
        )
        assertEquals(2, dupGroup.contacts.size)
        assertEquals(4, ContactSortOrder.entries.size)
        assertEquals(3, AppThemeMode.entries.size)
    }

    @Test
    fun verifyIncomingCallSingleCallerTitleAndActionReceiver() {
        val savedCallerState = ActiveCallUiState(
            callId = "10",
            number = "+91 97378 92210",
            displayName = "VIJAY",
            locationLabel = "India",
            state = Call.STATE_RINGING,
            isIncoming = true
        )
        // Saved contact: MUST show ONLY the saved contact name, never the number
        assertEquals(
            "VIJAY",
            com.example.ui.screens.resolveCompactIncomingCallerTitle(savedCallerState)
        )
        assertEquals(
            "",
            com.example.ui.screens.formatIncomingBannerSubtitle(savedCallerState)
        )

        val unsavedCallerWithLocation = ActiveCallUiState(
            callId = "11",
            number = "+91 97378 92210",
            displayName = null,
            locationLabel = "India",
            state = Call.STATE_RINGING,
            isIncoming = true
        )
        // Unknown/unsaved caller: MUST show ONLY the phone number
        assertEquals(
            "+91 97378 92210",
            com.example.ui.screens.resolveCompactIncomingCallerTitle(unsavedCallerWithLocation)
        )
        assertEquals(
            "",
            com.example.ui.screens.formatIncomingBannerSubtitle(unsavedCallerWithLocation)
        )

        assertEquals(
            "com.example.telecom.ACTION_ANSWER_CALL",
            com.example.telecom.CallActionReceiver.ACTION_ANSWER_CALL
        )
        assertEquals(
            "com.example.telecom.ACTION_DECLINE_CALL",
            com.example.telecom.CallActionReceiver.ACTION_DECLINE_CALL
        )
    }

    @Test
    fun verifyIncomingCallSwipeGestureStateTransitions() {
        // 1. Swipe UP on COMPACT_BANNER -> COLLAPSED_INDICATOR
        assertEquals(
            com.example.ui.screens.IncomingCallPresentationMode.COLLAPSED_INDICATOR,
            com.example.ui.screens.resolveNextIncomingModeOnVerticalSwipe(
                current = com.example.ui.screens.IncomingCallPresentationMode.COMPACT_BANNER,
                dragDeltaY = -52f,
                thresholdPx = 38f
            )
        )

        // 2. Swipe DOWN on COMPACT_BANNER -> FULL_SCREEN
        assertEquals(
            com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN,
            com.example.ui.screens.resolveNextIncomingModeOnVerticalSwipe(
                current = com.example.ui.screens.IncomingCallPresentationMode.COMPACT_BANNER,
                dragDeltaY = 58f,
                thresholdPx = 38f
            )
        )

        // 3. Swipe DOWN on COLLAPSED_INDICATOR -> FULL_SCREEN
        assertEquals(
            com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN,
            com.example.ui.screens.resolveNextIncomingModeOnVerticalSwipe(
                current = com.example.ui.screens.IncomingCallPresentationMode.COLLAPSED_INDICATOR,
                dragDeltaY = 36f,
                thresholdPx = 38f
            )
        )

        // 4. Small accidental movement does not change state
        assertEquals(
            com.example.ui.screens.IncomingCallPresentationMode.COMPACT_BANNER,
            com.example.ui.screens.resolveNextIncomingModeOnVerticalSwipe(
                current = com.example.ui.screens.IncomingCallPresentationMode.COMPACT_BANNER,
                dragDeltaY = -12f,
                thresholdPx = 38f
            )
        )

        // 5. Swipe UP on FULL_SCREEN -> returns to COMPACT_BANNER
        assertEquals(
            com.example.ui.screens.IncomingCallPresentationMode.COMPACT_BANNER,
            com.example.ui.screens.resolveNextIncomingModeOnVerticalSwipe(
                current = com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN,
                dragDeltaY = -65f,
                thresholdPx = 38f
            )
        )
    }

    @Test
    fun verifyColorOs16LightAndDarkThemePalettes() {
        assertEquals(3, AppThemeMode.entries.size)
        assertEquals("Follow system", AppThemeMode.FOLLOW_SYSTEM.label)
        assertEquals("Light mode", AppThemeMode.LIGHT.label)
        assertEquals("Dark mode", AppThemeMode.DARK.label)
        assertTrue(com.example.ui.theme.ColorOsGreen.red < com.example.ui.theme.ColorOsGreen.green)
        assertTrue(com.example.ui.theme.ColorOsWhite.red > com.example.ui.theme.ColorOsPageBg.red)
        assertTrue(com.example.ui.theme.ColorOsDarkSurface.red > com.example.ui.theme.ColorOsDarkBg.red)
    }

    @Test
    fun verifyGujaratiAiCallAssistantConsentAndConversationEngine() {
        val engine = com.example.telecom.GujaratiConversationEngine
        val customInstruction = "હું અત્યારે મીટિંગમાં છું અને ૧૫ મિનિટમાં ફોન કરીશ."

        // 1. Initial disclosure clearly identifies AI Call Assistant in Gujarati and asks for consent
        assertTrue(engine.INITIAL_DISCLOSURE_AND_CONSENT_GUJARATI.contains("AI કૉલ આસિસ્ટન્ટ"))

        // 2. If the remote caller declines consent in Gujarati ("ના, વાત નથી કરવી"), AI stops immediately
        val declinedDecision = engine.evaluateCallerInput(
            callerSpeech = "ના, વાત નથી કરવી",
            currentConsent = com.example.telecom.AiConsentState.AWAITING_CONSENT,
            userInstruction = customInstruction,
            previousAiReplies = emptyList()
        )
        assertTrue(declinedDecision.shouldStopImmediately)
        assertEquals(
            com.example.telecom.AiConsentState.DECLINED,
            declinedDecision.nextConsentState
        )
        assertEquals(engine.DECLINE_FAREWELL_GUJARATI, declinedDecision.aiReplyGujarati)

        // 3. If the remote caller grants consent ("હા બોલો"), AI transitions to GRANTED and shares user instruction
        val grantedDecision = engine.evaluateCallerInput(
            callerSpeech = "હા બોલો",
            currentConsent = com.example.telecom.AiConsentState.AWAITING_CONSENT,
            userInstruction = customInstruction,
            previousAiReplies = listOf(engine.INITIAL_DISCLOSURE_AND_CONSENT_GUJARATI)
        )
        assertFalse(grantedDecision.shouldStopImmediately)
        assertEquals(
            com.example.telecom.AiConsentState.GRANTED,
            grantedDecision.nextConsentState
        )
        assertTrue(grantedDecision.aiReplyGujarati.contains(customInstruction))

        // 4. Multi-turn contextual Gujarati replies do not repeat the exact same response consecutively
        val urgentTurn1 = engine.evaluateCallerInput(
            callerSpeech = "મારે અર્જન્ટ કામ છે",
            currentConsent = com.example.telecom.AiConsentState.GRANTED,
            userInstruction = customInstruction,
            previousAiReplies = listOf(grantedDecision.aiReplyGujarati)
        )
        val urgentTurn2 = engine.evaluateCallerInput(
            callerSpeech = "ખૂબ જરૂરી અને અર્જન્ટ છે",
            currentConsent = com.example.telecom.AiConsentState.GRANTED,
            userInstruction = customInstruction,
            previousAiReplies = listOf(grantedDecision.aiReplyGujarati, urgentTurn1.aiReplyGujarati)
        )
        assertFalse(urgentTurn1.shouldStopImmediately)
        assertFalse(urgentTurn2.shouldStopImmediately)
        assertTrue(urgentTurn1.aiReplyGujarati != urgentTurn2.aiReplyGujarati)
    }

    @Test
    fun verifyCallRecordingModelsQualityStorageAndFormatting() {
        val standardQuality = com.example.telecom.CallRecordingQuality.fromId("STANDARD")
        val highQuality = com.example.telecom.CallRecordingQuality.fromId("HIGH")
        assertEquals(16000, standardQuality.sampleRateHz)
        assertEquals(64000, standardQuality.bitRateBps)
        assertEquals(44100, highQuality.sampleRateHz)
        assertEquals(128000, highQuality.bitRateBps)

        val externalStorage = com.example.telecom.CallRecordingStorageLocation.fromId("APP_EXTERNAL_MEDIA")
        val internalStorage = com.example.telecom.CallRecordingStorageLocation.fromId("APP_INTERNAL_PRIVATE")
        assertEquals("APP_EXTERNAL_MEDIA", externalStorage.id)
        assertEquals("APP_INTERNAL_PRIVATE", internalStorage.id)

        assertEquals("01:25", com.example.telecom.CallRecordingManager.formatDurationShort(85L))
        assertEquals("00:09", com.example.telecom.CallRecordingManager.formatDurationShort(9L))
        assertEquals("512 KB", com.example.telecom.CallRecordingManager.formatFileSize(512L * 1024L))

        val savedRec = com.example.telecom.SavedCallRecording(
            id = "rec_101",
            title = "A 4 ANKIT",
            phoneNumber = "+91 78620 65087",
            contactName = "A 4 ANKIT",
            callDirection = "Outgoing",
            simLabel = "SIM 1",
            timestampMillis = 1700000000000L,
            durationSeconds = 42L,
            fileSizeBytes = 680_000L,
            filePath = "/storage/emulated/0/Android/data/com.het.colordialer/files/Music/CallRecordings/Call_101.m4a",
            audioSourceUsed = "VOICE_COMMUNICATION (Speaker/Mic)",
            qualityLabel = highQuality.label
        )
        assertEquals("A 4 ANKIT", savedRec.title)
        assertEquals("Outgoing", savedRec.callDirection)
        assertEquals("SIM 1", savedRec.simLabel)
        assertEquals(42L, savedRec.durationSeconds)

        val defaultSession = com.example.telecom.CallRecordingSessionState()
        assertTrue(defaultSession.isFeatureEnabled)
        assertFalse(defaultSession.announceConsentEnabled) // Silent mode ON by default
        assertTrue(defaultSession.silentInstantStartEnabled) // Instant 1-tap start ON by default
        assertFalse(defaultSession.autoSpeakerForAcousticCapture)
        assertEquals(
            com.example.telecom.CallRecordingStorageLocation.APP_INTERNAL_PRIVATE,
            defaultSession.selectedStorage
        )
        assertEquals(
            com.example.telecom.CallRecordingStorageLocation.APP_INTERNAL_PRIVATE,
            com.example.telecom.CallRecordingStorageLocation.fromId(null)
        )
    }

    @Test
    fun verifyAutoHideDialPadOnCallHistoryUpwardSwipe() {
        // 1. Intentional upward swipe (negative deltaY) while Dialpad is expanded -> auto-hides Dialpad
        assertTrue(
            com.example.ui.screens.shouldAutoHideDialPadOnCallHistorySwipe(
                isDialPadExpanded = true,
                accumulatedUpwardDeltaPx = -48f,
                distanceThresholdPx = 36f
            )
        )

        // 2. Intentional upward fling (negative velocityY) while Dialpad is expanded -> auto-hides Dialpad
        assertTrue(
            com.example.ui.screens.shouldAutoHideDialPadOnCallHistorySwipe(
                isDialPadExpanded = true,
                accumulatedUpwardDeltaPx = -18f,
                flingVelocityY = -620f,
                distanceThresholdPx = 36f,
                velocityThresholdPxPerSec = 380f
            )
        )

        // 3. Tiny accidental movement during tap or info-button press does NOT collapse Dialpad
        assertFalse(
            com.example.ui.screens.shouldAutoHideDialPadOnCallHistorySwipe(
                isDialPadExpanded = true,
                accumulatedUpwardDeltaPx = -10f,
                distanceThresholdPx = 36f
            )
        )

        // 4. Downward swipe (positive deltaY / velocityY) NEVER triggers Dialpad state changes
        assertFalse(
            com.example.ui.screens.shouldAutoHideDialPadOnCallHistorySwipe(
                isDialPadExpanded = true,
                accumulatedUpwardDeltaPx = 85f,
                flingVelocityY = 720f,
                distanceThresholdPx = 36f
            )
        )
        assertFalse(
            com.example.ui.screens.shouldAutoHideDialPadOnCallHistorySwipe(
                isDialPadExpanded = false,
                accumulatedUpwardDeltaPx = 85f,
                flingVelocityY = 720f,
                distanceThresholdPx = 36f
            )
        )

        // 5. Upward swipe when Dialpad is already collapsed does nothing
        assertFalse(
            com.example.ui.screens.shouldAutoHideDialPadOnCallHistorySwipe(
                isDialPadExpanded = false,
                accumulatedUpwardDeltaPx = -64f,
                distanceThresholdPx = 36f
            )
        )
    }

    @Test
    fun verifyAiAssistantDiagnosticStatusesAndErrorReporting() {
        // 1. Verify all 5 mandatory diagnostic status indicators
        assertEquals("AI Speaking", com.example.telecom.AiAssistantStatus.SPEAKING.labelEnglish)
        assertEquals("Waiting for User", com.example.telecom.AiAssistantStatus.WAITING_FOR_USER.labelEnglish)
        assertEquals("AI Listening", com.example.telecom.AiAssistantStatus.LISTENING.labelEnglish)
        assertEquals("Speech Recognized", com.example.telecom.AiAssistantStatus.SPEECH_RECOGNIZED.labelEnglish)
        assertEquals("Recognition Failed", com.example.telecom.AiAssistantStatus.RECOGNITION_FAILED.labelEnglish)
        assertTrue(com.example.telecom.AiAssistantStatus.RECOGNITION_FAILED.isFailureState)
        assertFalse(com.example.telecom.AiAssistantStatus.LISTENING.isFailureState)

        // 2. Verify SpeechRecognizer error code 3 (ERROR_AUDIO) diagnostic breakdown
        val audioErr = com.example.telecom.describeSpeechRecognizerError(
            errorCode = android.speech.SpeechRecognizer.ERROR_AUDIO,
            hasReceivedMicAudio = false,
            isTtsSpeaking = false,
            audioContentionInfo = "Audio: MODE_IN_CALL (Cellular Call) | Speaker: ON"
        )
        assertEquals(3, audioErr.errorCode)
        assertEquals("ERROR_AUDIO", audioErr.errorSymbol)
        assertTrue(audioErr.shouldRecreateRecognizer)
        assertTrue(audioErr.userFacingDiagnostic.contains("ERROR_AUDIO (Code 3)"))
        assertTrue(audioErr.technicalDetail.contains("Zero speech audio frames"))

        // 3. Verify SpeechRecognizer error code 6 (ERROR_SPEECH_TIMEOUT) and 7 (ERROR_NO_MATCH) are never hidden
        val timeoutErr = com.example.telecom.describeSpeechRecognizerError(
            errorCode = android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            hasReceivedMicAudio = false,
            isTtsSpeaking = false,
            audioContentionInfo = "Audio: MODE_NORMAL"
        )
        assertEquals(6, timeoutErr.errorCode)
        assertEquals("ERROR_SPEECH_TIMEOUT", timeoutErr.errorSymbol)
        assertTrue(timeoutErr.isTransientSilenceOrTimeout)
        assertTrue(timeoutErr.userFacingDiagnostic.contains("ERROR_SPEECH_TIMEOUT (Code 6)"))

        val noMatchErr = com.example.telecom.describeSpeechRecognizerError(
            errorCode = android.speech.SpeechRecognizer.ERROR_NO_MATCH,
            hasReceivedMicAudio = true,
            isTtsSpeaking = false,
            audioContentionInfo = "Audio: MODE_NORMAL"
        )
        assertEquals(7, noMatchErr.errorCode)
        assertEquals("ERROR_NO_MATCH", noMatchErr.errorSymbol)
        assertTrue(noMatchErr.userFacingDiagnostic.contains("ERROR_NO_MATCH (Code 7)"))
        assertTrue(noMatchErr.technicalDetail.contains("Mic audio frames were received"))

        // 4. Verify TTS active conflict detection in error diagnostics
        val clientErrWithTts = com.example.telecom.describeSpeechRecognizerError(
            errorCode = android.speech.SpeechRecognizer.ERROR_CLIENT,
            hasReceivedMicAudio = false,
            isTtsSpeaking = true,
            audioContentionInfo = ""
        )
        assertEquals(5, clientErrWithTts.errorCode)
        assertEquals("ERROR_CLIENT", clientErrWithTts.errorSymbol)
        assertTrue(clientErrWithTts.technicalDetail.contains("TTS output was still active"))

        // 5. Verify Gujarati locale tag configuration in AiAssistantUiState
        val defaultUiState = com.example.telecom.AiAssistantUiState()
        assertEquals("gu-IN", defaultUiState.configuredRecognizerLocale)
        assertFalse(defaultUiState.isTtsSpeaking)
        assertFalse(defaultUiState.isRecognizerReadyForSpeech)
    }

    @Test
    fun verifyPersistentIncomingBannerGesturesAndFullScreenExpansion() {
        // 1. Swipe UP on banner -> dismisses visible banner without ending call
        assertEquals(
            com.example.ui.screens.IncomingBannerGestureAction.SWIPE_UP_DISMISS_BANNER,
            com.example.ui.screens.resolveIncomingBannerGesture(
                dragDeltaY = -54f,
                thresholdPx = 38f
            )
        )

        // 2. Swipe DOWN on banner -> expands incoming call into Full-Screen Incoming Call UI
        assertEquals(
            com.example.ui.screens.IncomingBannerGestureAction.SWIPE_DOWN_EXPAND_FULL_SCREEN,
            com.example.ui.screens.resolveIncomingBannerGesture(
                dragDeltaY = 58f,
                thresholdPx = 38f
            )
        )

        // 3. Tap on caller-information/card area -> opens Full-Screen Incoming Call UI
        assertEquals(
            com.example.ui.screens.IncomingBannerGestureAction.TAP_CARD_EXPAND_FULL_SCREEN,
            com.example.ui.screens.resolveIncomingBannerGesture(
                dragDeltaY = 0f,
                isCardTap = true,
                isButtonTap = false,
                thresholdPx = 38f
            )
        )

        // 4. Tap on red Decline or green Answer button -> NEVER triggers full-screen expansion
        assertEquals(
            com.example.ui.screens.IncomingBannerGestureAction.NONE,
            com.example.ui.screens.resolveIncomingBannerGesture(
                dragDeltaY = 0f,
                isCardTap = true,
                isButtonTap = true,
                thresholdPx = 38f
            )
        )

        // 5. Small accidental vertical movement does not trigger swipe up or swipe down
        assertEquals(
            com.example.ui.screens.IncomingBannerGestureAction.NONE,
            com.example.ui.screens.resolveIncomingBannerGesture(
                dragDeltaY = -10f,
                isCardTap = false,
                isButtonTap = false,
                thresholdPx = 38f
            )
        )

        // 6. Verify notification / broadcast receiver actions for expanding full screen and restoring banner
        assertEquals(
            "com.example.telecom.ACTION_EXPAND_FULL_INCOMING",
            com.example.telecom.CallActionReceiver.ACTION_EXPAND_FULL_INCOMING
        )
        assertEquals(
            "com.example.telecom.ACTION_RESTORE_INCOMING_BANNER",
            com.example.telecom.CallActionReceiver.ACTION_RESTORE_INCOMING_BANNER
        )
    }

    @Test
    fun verifySimCallAudioDiagnosticsAndErrorSeparation() {
        // 1. Diagnose the exact real SIM call failure:
        // ERROR_NO_MATCH (Code 7) + Zero speech audio frames + ReadyForSpeech: NO + Audio: MODE_IN_CALL | Speaker: ON | MicMute: UNMUTED
        val inCallZeroFrameNoMatch = com.example.telecom.describeSpeechRecognizerError(
            errorCode = android.speech.SpeechRecognizer.ERROR_NO_MATCH,
            hasReceivedMicAudio = false,
            isTtsSpeaking = false,
            audioContentionInfo = "Audio: MODE_IN_CALL (Cellular Call) | Speaker: ON | MicMute: UNMUTED",
            wasReadyForSpeech = false,
            peakRmsDb = 0f
        )
        assertEquals(7, inCallZeroFrameNoMatch.errorCode)
        assertEquals("ERROR_NO_MATCH", inCallZeroFrameNoMatch.errorSymbol)
        assertTrue(inCallZeroFrameNoMatch.shouldRecreateRecognizer)
        assertTrue(inCallZeroFrameNoMatch.technicalDetail.contains("Zero speech audio frames reached SpeechRecognizer"))
        assertTrue(inCallZeroFrameNoMatch.technicalDetail.contains("MODE_IN_CALL"))
        assertTrue(inCallZeroFrameNoMatch.summaryGujarati.contains("MODE_IN_CALL"))

        // 2. Verify ERROR_INSUFFICIENT_PERMISSIONS (Code 9) is handled separately with Gujarati summary
        val permErr = com.example.telecom.describeSpeechRecognizerError(
            errorCode = android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            hasReceivedMicAudio = false,
            isTtsSpeaking = false,
            audioContentionInfo = "Audio: MODE_IN_CALL (Cellular Call)"
        )
        assertEquals(9, permErr.errorCode)
        assertEquals("ERROR_INSUFFICIENT_PERMISSIONS", permErr.errorSymbol)
        assertTrue(permErr.shouldRecreateRecognizer)
        assertTrue(permErr.summaryGujarati.contains("માઇક્રોફોન પરવાનગી"))

        // 3. Verify SIM call audio restriction reports explain unavailable audio sources (VOICE_DOWNLINK & MODE_IN_CALL)
        val unavailableSources = com.example.telecom.AiCallAssistantManager.buildSimCallUnavailableSourcesExplanation()
        val gujaratiRestriction = com.example.telecom.AiCallAssistantManager.buildSimCallRestrictionReportGujarati()
        assertTrue(unavailableSources.contains("VOICE_DOWNLINK"))
        assertTrue(unavailableSources.contains("CAPTURE_AUDIO_OUTPUT"))
        assertTrue(unavailableSources.contains("VOICE_RECOGNITION"))
        assertTrue(gujaratiRestriction.contains("Android 17"))
        assertTrue(gujaratiRestriction.contains("VOICE_DOWNLINK"))

        // 4. Verify 8-part diagnostics state fields in AiAssistantUiState
        val diagState = com.example.telecom.AiAssistantUiState()
        assertEquals("gu-IN", diagState.configuredRecognizerLocale)
        assertEquals("IDLE (Inactive)", diagState.recognitionSessionStatus)
        assertTrue(diagState.cellularCallAudioAccessStatus.contains("VOICE_DOWNLINK"))
        assertFalse(diagState.isSimCallAudioRestrictionDetected)
    }

    @Test
    fun verifyError11ServerDisconnectedAndHonestGujaratiSupport() {
        // 1. Verify ERROR_SERVER_DISCONNECTED (Code 11) diagnostic and non-infinite-loop flags
        val err11InCall = com.example.telecom.describeSpeechRecognizerError(
            errorCode = 11,
            hasReceivedMicAudio = false,
            isTtsSpeaking = false,
            audioContentionInfo = "Audio: MODE_IN_CALL (Cellular Call) | Speaker: ON | MicMute: UNMUTED",
            wasReadyForSpeech = false,
            activeServiceComponent = "com.google.android.tts"
        )
        assertEquals(11, err11InCall.errorCode)
        assertEquals("ERROR_SERVER_DISCONNECTED", err11InCall.errorSymbol)
        assertTrue(err11InCall.shouldRecreateRecognizer)
        assertFalse(err11InCall.isTransientSilenceOrTimeout) // Prevents infinite auto-retry loop
        assertTrue(err11InCall.summaryGujarati.contains("ERROR_SERVER_DISCONNECTED"))
        assertTrue(err11InCall.technicalDetail.contains("Binder connection dropped"))
        assertTrue(err11InCall.technicalDetail.contains("com.google.android.tts"))

        // 2. Verify evaluateGenuineGujaratiLanguageSupport NEVER falsely reports support when metadata is missing
        val (unverifiedState, unverifiedMsg) = com.example.telecom.evaluateGenuineGujaratiLanguageSupport(
            installedOrSupportedLocales = emptyList(),
            pendingDownloadLocales = emptyList(),
            metadataWasReturnedByProvider = false
        )
        assertEquals(
            com.example.telecom.GujaratiLanguageSupportState.UNVERIFIED_NO_PROVIDER_METADATA,
            unverifiedState
        )
        assertFalse(unverifiedState.isGenuinelyVerified)
        assertTrue(unverifiedMsg.contains("Unverified"))

        // 3. Verify evaluateGenuineGujaratiLanguageSupport reports VERIFIED_NOT_SUPPORTED when gu-IN is absent
        val (notSupportedState, notSupportedMsg) = com.example.telecom.evaluateGenuineGujaratiLanguageSupport(
            installedOrSupportedLocales = listOf("en-US", "hi-IN"),
            pendingDownloadLocales = emptyList(),
            metadataWasReturnedByProvider = true
        )
        assertEquals(
            com.example.telecom.GujaratiLanguageSupportState.VERIFIED_NOT_SUPPORTED,
            notSupportedState
        )
        assertFalse(notSupportedState.isGenuinelyVerified)
        assertTrue(notSupportedMsg.contains("Not Supported"))

        // 4. Verify evaluateGenuineGujaratiLanguageSupport reports VERIFIED_PENDING_DOWNLOAD when gu-IN is pending
        val (pendingState, _) = com.example.telecom.evaluateGenuineGujaratiLanguageSupport(
            installedOrSupportedLocales = listOf("en-US"),
            pendingDownloadLocales = listOf("gu-IN"),
            metadataWasReturnedByProvider = true
        )
        assertEquals(
            com.example.telecom.GujaratiLanguageSupportState.VERIFIED_PENDING_DOWNLOAD,
            pendingState
        )
        assertFalse(pendingState.isGenuinelyVerified)

        // 5. Verify evaluateGenuineGujaratiLanguageSupport reports VERIFIED_SUPPORTED only when gu-IN is genuinely present
        val (supportedState, supportedMsg) = com.example.telecom.evaluateGenuineGujaratiLanguageSupport(
            installedOrSupportedLocales = listOf("en-IN", "gu-IN", "hi-IN"),
            pendingDownloadLocales = emptyList(),
            metadataWasReturnedByProvider = true
        )
        assertEquals(
            com.example.telecom.GujaratiLanguageSupportState.VERIFIED_SUPPORTED,
            supportedState
        )
        assertTrue(supportedState.isGenuinelyVerified)
        assertTrue(supportedMsg.contains("Verified Supported"))

        // 6. Verify AudioRecord scope disclaimer explicitly states AudioRecord does not prove SpeechRecognizer works
        val defaultState = com.example.telecom.AiAssistantUiState()
        assertTrue(defaultState.audioRecordScopeDisclaimer.contains("does NOT prove SpeechRecognizer"))
        assertEquals(
            com.example.telecom.NormalModeMicTestStage.NOT_RUN,
            defaultState.normalModeMicTest.stage
        )
    }

    @Test
    fun verifyNormalModeMicTestLifecycleAndStaleSessionIsolation() {
        val activeSessionId = 10L
        val staleSessionId = 9L

        // 1. Test starts in CONNECTING stage, NOT LISTENING_SPEAK_NOW
        val connectingState = com.example.telecom.transitionNormalModeMicTestToConnecting(
            sessionId = activeSessionId,
            audioModeLabel = "MODE_NORMAL",
            serviceUsed = "com.google.android.tts"
        )
        assertEquals(com.example.telecom.NormalModeMicTestStage.CONNECTING, connectingState.stage)
        assertTrue(connectingState.isRunning)
        assertFalse(connectingState.readyForSpeechFired)

        // 2. Stale callback from an older session (sessionId=9) CANNOT modify sessionId=10
        val afterStaleReady = com.example.telecom.transitionNormalModeMicTestOnReadyForSpeech(
            current = connectingState,
            callbackSessionId = staleSessionId,
            activeSessionId = activeSessionId
        )
        assertEquals(com.example.telecom.NormalModeMicTestStage.CONNECTING, afterStaleReady.stage)
        assertFalse(afterStaleReady.readyForSpeechFired)

        // 3. Valid onReadyForSpeech callback transitions to LISTENING_SPEAK_NOW
        val listeningState = com.example.telecom.transitionNormalModeMicTestOnReadyForSpeech(
            current = connectingState,
            callbackSessionId = activeSessionId,
            activeSessionId = activeSessionId
        )
        assertEquals(com.example.telecom.NormalModeMicTestStage.LISTENING_SPEAK_NOW, listeningState.stage)
        assertTrue(listeningState.readyForSpeechFired)
        assertTrue(listeningState.isRunning)

        // 4. Genuine partial Gujarati result updates partialTranscript without marking SUCCEEDED prematurely
        val partialState = com.example.telecom.transitionNormalModeMicTestOnPartialResults(
            current = listeningState,
            callbackSessionId = activeSessionId,
            activeSessionId = activeSessionId,
            partialText = "નમસ્તે કેમ"
        )
        assertEquals("નમસ્તે કેમ", partialState.partialTranscript)
        assertEquals(com.example.telecom.NormalModeMicTestStage.LISTENING_SPEAK_NOW, partialState.stage)

        // 5. Empty onResults() NEVER fabricates a transcript or claims CONFIRMED; it transitions to FAILED (Code 7)
        val emptyResultState = com.example.telecom.transitionNormalModeMicTestOnFinalResults(
            current = listeningState,
            callbackSessionId = activeSessionId,
            activeSessionId = activeSessionId,
            finalRecognizedText = "   "
        )
        assertEquals(com.example.telecom.NormalModeMicTestStage.FAILED, emptyResultState.stage)
        assertFalse(emptyResultState.isRunning)
        assertEquals("", emptyResultState.recognizedGujaratiText)
        assertEquals("ERROR_NO_MATCH (Code 7)", emptyResultState.errorSymbolAndCode)
        assertTrue(emptyResultState.diagnosticSummary.contains("NOT CONFIRMED"))

        // 6. Genuine non-empty Gujarati onResults() transitions to SUCCEEDED ("CONFIRMED ✓")
        val confirmedState = com.example.telecom.transitionNormalModeMicTestOnFinalResults(
            current = partialState,
            callbackSessionId = activeSessionId,
            activeSessionId = activeSessionId,
            finalRecognizedText = "નમસ્તે કેમ છો"
        )
        assertEquals(com.example.telecom.NormalModeMicTestStage.SUCCEEDED, confirmedState.stage)
        assertFalse(confirmedState.isRunning)
        assertEquals("નમસ્તે કેમ છો", confirmedState.recognizedGujaratiText)
        assertTrue(confirmedState.diagnosticSummary.contains("CONFIRMED ✓"))

        // 7. Stale error callback from an old session CANNOT overwrite a confirmed result!
        val staleErrorInfo = com.example.telecom.describeSpeechRecognizerError(errorCode = 11)
        val afterStaleError = com.example.telecom.transitionNormalModeMicTestOnError(
            current = confirmedState,
            callbackSessionId = staleSessionId,
            activeSessionId = activeSessionId,
            errorInfo = staleErrorInfo
        )
        assertEquals(com.example.telecom.NormalModeMicTestStage.SUCCEEDED, afterStaleError.stage)
        assertEquals("નમસ્તે કેમ છો", afterStaleError.recognizedGujaratiText)
    }

    @Test
    fun verifyTestPanelRemainsVisibleAndTestsAreIndependent() {
        // 1. Verify isTestPanelVisible defaults to true and remains true when stopping sessions
        com.example.telecom.AiCallAssistantManager.stopSession("Test stop")
        val stateAfterStop = com.example.telecom.AiCallAssistantManager.uiState.value
        assertTrue(stateAfterStop.isTestPanelVisible)
        assertEquals(com.example.telecom.AiAssistantStatus.IDLE, stateAfterStop.status)

        com.example.telecom.AiCallAssistantManager.stopStandaloneNormalModeGujaratiMicTest()
        val stateAfterNormalStop = com.example.telecom.AiCallAssistantManager.uiState.value
        assertTrue(stateAfterNormalStop.isTestPanelVisible)
        assertFalse(stateAfterNormalStop.normalModeMicTest.isRunning)

        // 2. Verify NormalModeGujaratiMicTestState and TtsAndMicTestState are independent structures
        val combinedUiState = com.example.telecom.AiAssistantUiState(
            activeSessionId = 25L,
            activeTestMode = com.example.telecom.AiTestMode.TTS_AND_MIC_TEST,
            isTestPanelVisible = true,
            status = com.example.telecom.AiAssistantStatus.RECOGNITION_FAILED,
            normalModeMicTest = com.example.telecom.NormalModeGujaratiMicTestState(
                sessionId = 24L,
                stage = com.example.telecom.NormalModeMicTestStage.SUCCEEDED,
                isRunning = false,
                recognizedGujaratiText = "હું ગુજરાતીમાં બોલું છું",
                diagnosticSummary = "CONFIRMED ✓"
            ),
            ttsAndMicTest = com.example.telecom.TtsAndMicTestState(
                sessionId = 25L,
                isRunning = false,
                ttsInitStatus = "Initialized ✓ (gu-IN Active)",
                ttsSpeakingStatus = "Finished speaking ✓",
                micRecognitionStatus = "FAILED — ERROR_NO_MATCH (Code 7)",
                errorSymbolAndCode = "ERROR_NO_MATCH (Code 7)",
                gujaratiExplanation = "કોઈ ગુજરાતી અવાજ સંભળાયો નહીં"
            )
        )
        assertTrue(combinedUiState.isTestPanelVisible)
        assertEquals("હું ગુજરાતીમાં બોલું છું", combinedUiState.normalModeMicTest.recognizedGujaratiText)
        assertEquals(com.example.telecom.NormalModeMicTestStage.SUCCEEDED, combinedUiState.normalModeMicTest.stage)
        assertEquals("Initialized ✓ (gu-IN Active)", combinedUiState.ttsAndMicTest.ttsInitStatus)
        assertEquals("ERROR_NO_MATCH (Code 7)", combinedUiState.ttsAndMicTest.errorSymbolAndCode)
    }

    /**
     * Scenario 1, 2, and 3:
     * - Scenario 1: One Gujarati question and one AI response.
     * - Scenario 2: At least 10 consecutive conversation turns (12 turns = 24 messages) with
     *   follow-up context understanding, memory recall, and non-repeating replies.
     * - Scenario 3: AI speaking followed by automatic microphone restart (`AI_SPEAKING` -> `onDone` ->
     *   `WAITING_FOR_YOUR_RESPONSE` -> `onReadyForSpeech` -> `LISTENING`).
     */
    @Test
    fun verifyContinuousGujaratiConversationTenPlusTurnsAndAutoMicRestart() {
        val sessionId = 100L
        var state = com.example.telecom.startContinuousConversationState(
            sessionId = sessionId,
            postTtsRestartDelayMs = 500L,
            activeScreenName = "Settings"
        )

        // Step 2 & 3: Wait until onReadyForSpeech() before displaying Listening
        assertEquals(com.example.telecom.ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE, state.stage)
        assertFalse(state.readyForSpeechFired)

        val userTurns = listOf(
            "નમસ્તે કેમ છો",                         // Turn 1: Greeting
            "તમે કોણ બોલો છો?",                      // Turn 2: AI Identity
            "તમે ક્યારે ફ્રી થશો?",                  // Turn 3: Availability question
            "તો ક્યારે ફોન કરશો?",                   // Turn 4: Follow-up on availability
            "મારું અગત્યનું કામ છે",                 // Turn 5: Urgent work
            "કેમ મીટિંગમાં છો?",                     // Turn 6: Follow-up on urgent/status
            "મારે પાર્સલ ડિલિવરી આપવાની છે",         // Turn 7: Delivery
            "પાર્સલ ક્યાં મૂકું?",                   // Turn 8: Follow-up on delivery
            "ઓફિસના પ્રોજેક્ટ વિશે વાત કરવી છે",     // Turn 9: Office work
            "આપણે અગાઉ શું વાત કરી?",                // Turn 10: Memory recall of previous turns
            "સારું, મારો સંદેશ નોંધી લેશો",          // Turn 11: Closing / message note
            "આભાર, પછી વાત કરીશું"                   // Turn 12: Closing follow-up
        )

        val spokenAiReplies = mutableListOf<String>()

        userTurns.forEachIndexed { index, userSpeech ->
            // Step 3: onReadyForSpeech() transitions from WAITING_FOR_YOUR_RESPONSE -> LISTENING
            state = com.example.telecom.transitionContinuousOnReadyForSpeech(
                current = state,
                callbackSessionId = sessionId,
                activeSessionId = sessionId
            )
            assertEquals(com.example.telecom.ContinuousConversationStage.LISTENING, state.stage)
            assertTrue(state.readyForSpeechFired)

            // Real microphone RMS update during Listening
            state = com.example.telecom.transitionContinuousOnRmsChanged(
                current = state,
                callbackSessionId = sessionId,
                activeSessionId = sessionId,
                rmsdB = 6.4f + (index * 0.2f)
            )
            assertTrue(state.isReceivingMicAudio)
            assertTrue(state.peakMicRmsDb >= 6.4f)

            // Step 4 & 5: Speech recognized -> Processing
            state = com.example.telecom.transitionContinuousOnEndOfSpeech(
                current = state,
                callbackSessionId = sessionId,
                activeSessionId = sessionId
            )
            assertEquals(com.example.telecom.ContinuousConversationStage.PROCESSING, state.stage)

            state = com.example.telecom.transitionContinuousOnFinalSpeechRecognized(
                current = state,
                callbackSessionId = sessionId,
                activeSessionId = sessionId,
                recognizedText = userSpeech
            )
            assertEquals(com.example.telecom.ContinuousConversationStage.PROCESSING, state.stage)
            assertEquals(userSpeech, state.lastRecognizedGujaratiText)

            // Step 6: Generate contextual Gujarati response using GujaratiConversationEngine
            val decision = GujaratiConversationEngine.evaluateContinuousTurn(
                userSpeech = userSpeech,
                conversationHistory = state.conversationHistory,
                recentTopics = state.recentTopics,
                userInstruction = GujaratiConversationEngine.DEFAULT_USER_INSTRUCTION_GUJARATI
            )
            assertTrue(decision.aiReplyGujarati.isNotBlank())
            spokenAiReplies.add(decision.aiReplyGujarati)

            // Step 7: AI Speaking via Gujarati TextToSpeech (SpeechRecognizer is off)
            state = com.example.telecom.transitionContinuousOnSpeakingStarted(
                current = state,
                callbackSessionId = sessionId,
                activeSessionId = sessionId,
                aiReplyGujarati = decision.aiReplyGujarati,
                detectedTopic = decision.detectedTopic
            )
            assertEquals(com.example.telecom.ContinuousConversationStage.AI_SPEAKING, state.stage)
            assertTrue(state.isTtsSpeaking)
            assertFalse(state.readyForSpeechFired)
            assertEquals(decision.aiReplyGujarati, state.lastAiGujaratiResponse)
            assertEquals(index + 1, state.completedTurnCount)

            // Step 8 & 9: UtteranceProgressListener.onDone() -> Waiting for Your Response -> Auto-restart mic
            state = com.example.telecom.transitionContinuousOnTtsDone(
                current = state,
                callbackSessionId = sessionId,
                activeSessionId = sessionId
            )
            assertEquals(com.example.telecom.ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE, state.stage)
            assertFalse(state.isTtsSpeaking)
            assertFalse(state.readyForSpeechFired)
        }

        // Verify 12 consecutive turns completed (24 messages in conversationHistory: 12 USER + 12 AI)
        assertEquals(12, state.completedTurnCount)
        assertEquals(24, state.conversationHistory.size)

        // Verify memory recall turn (Turn 10: index 9) referenced previous user speech
        val memoryRecallReply = spokenAiReplies[9]
        assertTrue(
            "Memory recall reply should mention earlier conversation context, got: $memoryRecallReply",
            memoryRecallReply.contains("અગાઉની વાતચીતમાં") || memoryRecallReply.contains("ઓફિસના પ્રોજેક્ટ")
        )

        // Verify no two consecutive turns repeated the exact same AI reply
        for (i in 1 until spokenAiReplies.size) {
            assertNotEquals(
                "Turn ${i + 1} should not repeat Turn $i reply verbatim",
                spokenAiReplies[i - 1],
                spokenAiReplies[i]
            )
        }
    }

    /**
     * Scenario 4 & 5:
     * - Scenario 4: User remaining silent (`ERROR_SPEECH_TIMEOUT` Code 6) triggers bounded auto-retries
     *   and stops cleanly with manual retry option after max retries (no infinite loop).
     * - Scenario 5: User speaking unclearly (`ERROR_NO_MATCH` Code 7, empty input, or 1-char unclear speech)
     *   never fabricates a transcript and handles unclear speech gracefully.
     * - Also verifies `ERROR_AUDIO` (3), `ERROR_CLIENT` (5), `ERROR_RECOGNIZER_BUSY` (8),
     *   and `ERROR_SERVER_DISCONNECTED` (11).
     */
    @Test
    fun verifyContinuousErrorRecoverySilenceUnclearAndAllErrorCodes() {
        val sessionId = 200L
        var state = com.example.telecom.startContinuousConversationState(sessionId = sessionId)

        // 1. User remaining silent (ERROR_SPEECH_TIMEOUT = 6): retries up to MAX_SILENCE_OR_NO_MATCH_AUTO_RETRIES (2), then stops
        for (attempt in 1..2) {
            val plan = com.example.telecom.computeContinuousErrorRecoveryPlan(
                errorCode = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                current = state,
                hasAlternativeBindingStrategy = true
            )
            assertTrue("Attempt $attempt should auto-retry on silence", plan.shouldAutoRetry)
            val errInfo = AiCallAssistantManager.describeSpeechRecognizerError(
                errorCode = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                hasReceivedMicAudio = false,
                isTtsSpeaking = false,
                audioContentionInfo = "Audio: MODE_NORMAL",
                wasReadyForSpeech = true
            )
            state = com.example.telecom.transitionContinuousOnError(
                current = state,
                callbackSessionId = sessionId,
                activeSessionId = sessionId,
                errorInfo = errInfo,
                recoveryPlan = plan
            )
            assertEquals(com.example.telecom.ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE, state.stage)
            assertEquals(attempt, state.consecutiveSilenceOrNoMatchCount)
            // Must never fabricate recognized text on silence
            assertEquals("", state.lastRecognizedGujaratiText)
        }

        // 3rd consecutive silence exceeds retry limit -> stops cleanly and enables manual retry
        val exhaustedSilencePlan = com.example.telecom.computeContinuousErrorRecoveryPlan(
            errorCode = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            current = state,
            hasAlternativeBindingStrategy = true
        )
        assertFalse("Should stop after max consecutive silence retries (no infinite loop)", exhaustedSilencePlan.shouldAutoRetry)
        val exhaustedErrInfo = AiCallAssistantManager.describeSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            hasReceivedMicAudio = false,
            isTtsSpeaking = false,
            audioContentionInfo = "Audio: MODE_NORMAL",
            wasReadyForSpeech = true
        )
        state = com.example.telecom.transitionContinuousOnError(
            current = state,
            callbackSessionId = sessionId,
            activeSessionId = sessionId,
            errorInfo = exhaustedErrInfo,
            recoveryPlan = exhaustedSilencePlan
        )
        assertEquals(com.example.telecom.ContinuousConversationStage.STOPPED, state.stage)
        assertFalse(state.isRunning)
        assertTrue(state.isManualRetryAvailable)
        assertEquals("ERROR_SPEECH_TIMEOUT (Code 6)", state.lastErrorSymbolAndCode)

        // 2. User speaking unclearly: ERROR_NO_MATCH (Code 7) and 1-char unclear speech
        val freshState = com.example.telecom.startContinuousConversationState(sessionId = 201L)
        val noMatchPlan = com.example.telecom.computeContinuousErrorRecoveryPlan(
            errorCode = SpeechRecognizer.ERROR_NO_MATCH,
            current = freshState,
            hasAlternativeBindingStrategy = false
        )
        assertTrue(noMatchPlan.shouldAutoRetry)

        val unclearDecision = GujaratiConversationEngine.evaluateContinuousTurn(
            userSpeech = "?",
            conversationHistory = emptyList(),
            recentTopics = emptyList(),
            userInstruction = GujaratiConversationEngine.DEFAULT_USER_INSTRUCTION_GUJARATI
        )
        assertEquals("UNCLEAR", unclearDecision.detectedTopic)
        assertTrue(unclearDecision.aiReplyGujarati.contains("સ્પષ્ટ"))

        // 3. Verify ERROR_RECOGNIZER_BUSY (8), ERROR_AUDIO (3), ERROR_CLIENT (5), and ERROR_SERVER_DISCONNECTED (11)
        val busyPlan = com.example.telecom.computeContinuousErrorRecoveryPlan(
            errorCode = SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            current = freshState,
            hasAlternativeBindingStrategy = false
        )
        assertTrue(busyPlan.shouldAutoRetry)
        assertTrue(busyPlan.retryDelayMs >= 900L)

        val audioPlan = com.example.telecom.computeContinuousErrorRecoveryPlan(
            errorCode = SpeechRecognizer.ERROR_AUDIO,
            current = freshState,
            hasAlternativeBindingStrategy = false
        )
        assertTrue(audioPlan.shouldAutoRetry)

        val clientPlan = com.example.telecom.computeContinuousErrorRecoveryPlan(
            errorCode = SpeechRecognizer.ERROR_CLIENT,
            current = freshState,
            hasAlternativeBindingStrategy = false
        )
        assertTrue(clientPlan.shouldAutoRetry)

        val disconnectPlan = com.example.telecom.computeContinuousErrorRecoveryPlan(
            errorCode = 11,
            current = freshState,
            hasAlternativeBindingStrategy = true
        )
        assertTrue(disconnectPlan.shouldAutoRetry)
        assertTrue(disconnectPlan.switchBindingStrategy)
    }

    /**
     * Scenario 6, 7, 8, 9, and 10:
     * - Scenario 6: Pause and Resume (preserves history and resumes cleanly).
     * - Scenario 7: Stop during `Listening` (`LISTENING` -> `STOPPED`).
     * - Scenario 8: Stop during `AI Speaking` (`AI_SPEAKING` -> `STOPPED`).
     * - Scenario 9: Repeated activation without duplicate sessions (old `sessionId` callbacks ignored).
     * - Scenario 10: Switching between app screens and background/foreground transitions during a session.
     */
    @Test
    fun verifyContinuousPauseResumeStopRepeatedActivationAndScreenSwitching() {
        // Start Session #301
        var state = com.example.telecom.startContinuousConversationState(
            sessionId = 301L,
            postTtsRestartDelayMs = 500L,
            activeScreenName = "Settings"
        )
        state = com.example.telecom.transitionContinuousOnReadyForSpeech(
            current = state,
            callbackSessionId = 301L,
            activeSessionId = 301L
        )
        assertEquals(com.example.telecom.ContinuousConversationStage.LISTENING, state.stage)

        // Complete 1 turn before testing Pause/Resume
        state = com.example.telecom.transitionContinuousOnFinalSpeechRecognized(
            current = state,
            callbackSessionId = 301L,
            activeSessionId = 301L,
            recognizedText = "નમસ્તે કેમ છો"
        )
        state = com.example.telecom.transitionContinuousOnSpeakingStarted(
            current = state,
            callbackSessionId = 301L,
            activeSessionId = 301L,
            aiReplyGujarati = "નમસ્તે! હું ગુજરાતી AI સહાયક છું.",
            detectedTopic = "GREETING"
        )
        state = com.example.telecom.transitionContinuousOnTtsDone(
            current = state,
            callbackSessionId = 301L,
            activeSessionId = 301L
        )

        // Scenario 10: Switch between app screens during active session
        state = com.example.telecom.transitionContinuousOnScreenSwitched(state, "Dialpad")
        assertEquals("Dialpad", state.activeScreenName)
        assertTrue(state.isRunning)
        state = com.example.telecom.transitionContinuousOnScreenSwitched(state, "Contacts")
        assertEquals("Contacts", state.activeScreenName)
        assertEquals(2, state.conversationHistory.size)

        // Scenario 6: Pause and Resume (preserve conversationHistory)
        state = com.example.telecom.transitionContinuousOnPause(state, pausedByBackground = false)
        assertEquals(com.example.telecom.ContinuousConversationStage.PAUSED, state.stage)
        assertFalse(state.readyForSpeechFired)
        assertEquals(2, state.conversationHistory.size)

        state = com.example.telecom.transitionContinuousOnResume(state, newSessionId = 302L)
        assertEquals(302L, state.sessionId)
        assertEquals(com.example.telecom.ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE, state.stage)
        assertEquals(2, state.conversationHistory.size)

        // Scenario 9: Stale callback from old session #301 must be ignored when activeSessionId is 302L
        val afterStaleReady = com.example.telecom.transitionContinuousOnReadyForSpeech(
            current = state,
            callbackSessionId = 301L,
            activeSessionId = 302L
        )
        assertEquals(com.example.telecom.ContinuousConversationStage.WAITING_FOR_YOUR_RESPONSE, afterStaleReady.stage)
        assertFalse(afterStaleReady.readyForSpeechFired)

        // Valid callback on session #302 transitions to LISTENING
        state = com.example.telecom.transitionContinuousOnReadyForSpeech(
            current = state,
            callbackSessionId = 302L,
            activeSessionId = 302L
        )
        assertEquals(com.example.telecom.ContinuousConversationStage.LISTENING, state.stage)

        // Scenario 7: Stop during Listening
        val stoppedDuringListening = com.example.telecom.transitionContinuousOnStop(state)
        assertEquals(com.example.telecom.ContinuousConversationStage.STOPPED, stoppedDuringListening.stage)
        assertFalse(stoppedDuringListening.isRunning)
        assertFalse(stoppedDuringListening.readyForSpeechFired)
        assertEquals(2, stoppedDuringListening.conversationHistory.size)

        // Scenario 8: Stop during AI Speaking
        val speakingState = com.example.telecom.transitionContinuousOnSpeakingStarted(
            current = state,
            callbackSessionId = 302L,
            activeSessionId = 302L,
            aiReplyGujarati = "હું તમારો સંદેશ નોંધી રહ્યો છું.",
            detectedTopic = "GENERAL"
        )
        assertEquals(com.example.telecom.ContinuousConversationStage.AI_SPEAKING, speakingState.stage)
        assertTrue(speakingState.isTtsSpeaking)

        val stoppedDuringSpeaking = com.example.telecom.transitionContinuousOnStop(speakingState)
        assertEquals(com.example.telecom.ContinuousConversationStage.STOPPED, stoppedDuringSpeaking.stage)
        assertFalse(stoppedDuringSpeaking.isRunning)
        assertFalse(stoppedDuringSpeaking.isTtsSpeaking)
        assertEquals(3, stoppedDuringSpeaking.conversationHistory.size)
    }

    @Test
    fun verifyActiveCallButtonsIndividualOnOffStatesAndMultiButtonCombinations() {
        val baseConnectedCall = ActiveCallUiState(
            callId = "call_1",
            state = Call.STATE_ACTIVE,
            number = "97378 92210",
            displayName = "VIJAY",
            isMuted = false,
            isSpeakerOn = false,
            isOnHold = false,
            isRecordingActive = false
        )

        // 1. Default OFF state (Reference Image 1): all 6 toggleable buttons are OFF with zero persistent neon glow or continuous animation
        val defaultOffSnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall,
            isRecordingActive = false,
            isAnnouncingRecordingConsent = false,
            aiStatus = com.example.telecom.AiAssistantStatus.IDLE,
            isAiActiveSession = false,
            isMoreSheetOpen = false,
            isKeypadOpen = false,
            isEndCallPressed = false,
            animatorDurationScale = 1f
        )
        assertEquals(0, defaultOffSnapshot.activeToggleButtonCount)
        assertFalse(defaultOffSnapshot.mute.isOn)
        assertFalse(defaultOffSnapshot.mute.hasPersistentNeonGlow)
        assertFalse(defaultOffSnapshot.mute.hasContinuousAnimation)
        assertFalse(defaultOffSnapshot.record.isOn)
        assertFalse(defaultOffSnapshot.record.hasPersistentNeonGlow)
        assertFalse(defaultOffSnapshot.aiAssistant.isOn)
        assertFalse(defaultOffSnapshot.aiAssistant.hasPersistentNeonGlow)
        assertFalse(defaultOffSnapshot.more.isOn)
        assertFalse(defaultOffSnapshot.more.hasPersistentNeonGlow)
        assertFalse(defaultOffSnapshot.speaker.isOn)
        assertFalse(defaultOffSnapshot.speaker.hasPersistentNeonGlow)
        assertFalse(defaultOffSnapshot.dialPad.isOn)
        assertFalse(defaultOffSnapshot.dialPad.hasPersistentNeonGlow)
        assertFalse(defaultOffSnapshot.endCall.isOn)
        assertFalse(defaultOffSnapshot.endCall.hasPersistentNeonGlow)

        // 2. Mute ON independently (Red glowing circular ring with subtle pulsing ripple)
        val muteOnlySnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall.copy(isMuted = true)
        )
        assertEquals(1, muteOnlySnapshot.activeToggleButtonCount)
        assertTrue(muteOnlySnapshot.mute.isOn)
        assertTrue(muteOnlySnapshot.mute.hasPersistentNeonGlow)
        assertTrue(muteOnlySnapshot.mute.hasContinuousAnimation)
        assertEquals(0xFFFF3B4EL, muteOnlySnapshot.mute.primaryNeonHex)
        assertFalse(muteOnlySnapshot.record.isOn)
        assertFalse(muteOnlySnapshot.aiAssistant.isOn)
        assertFalse(muteOnlySnapshot.more.isOn)
        assertFalse(muteOnlySnapshot.speaker.isOn)
        assertFalse(muteOnlySnapshot.dialPad.isOn)

        // 3. Record ON independently (Pink/red neon ring, animated recording dot and waveform pulses)
        val recordOnlySnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall.copy(isRecordingActive = true),
            isRecordingActive = true
        )
        assertEquals(1, recordOnlySnapshot.activeToggleButtonCount)
        assertTrue(recordOnlySnapshot.record.isOn)
        assertTrue(recordOnlySnapshot.record.hasPersistentNeonGlow)
        assertTrue(recordOnlySnapshot.record.hasContinuousAnimation)
        assertEquals(0xFFFF2E7EL, recordOnlySnapshot.record.primaryNeonHex)
        assertFalse(recordOnlySnapshot.mute.isOn)
        assertFalse(recordOnlySnapshot.aiAssistant.isOn)

        // 4. AI Call Assistant ON independently (Cyan, green/emerald and violet glowing orbital ring + status speed)
        val aiListeningSnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall,
            aiStatus = com.example.telecom.AiAssistantStatus.LISTENING,
            isAiActiveSession = true
        )
        assertEquals(1, aiListeningSnapshot.activeToggleButtonCount)
        assertTrue(aiListeningSnapshot.aiAssistant.isOn)
        assertTrue(aiListeningSnapshot.aiAssistant.hasPersistentNeonGlow)
        assertTrue(aiListeningSnapshot.aiAssistant.hasContinuousAnimation)
        assertEquals(0xFF00E5FFL, aiListeningSnapshot.aiAssistant.primaryNeonHex)
        assertEquals(0xFF00E676L, aiListeningSnapshot.aiAssistant.secondaryNeonHex)
        assertEquals(0xFFB388FFL, aiListeningSnapshot.aiAssistant.accentNeonHex)
        assertTrue(
            com.example.ui.screens.resolveAiAssistantAnimationSpeed(com.example.telecom.AiAssistantStatus.PROCESSING) >
                com.example.ui.screens.resolveAiAssistantAnimationSpeed(com.example.telecom.AiAssistantStatus.PAUSED)
        )

        // 5. More ON independently (Premium violet glow with subtle expanding ripple and three-dot wave animation)
        val moreOnlySnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall,
            isMoreSheetOpen = true
        )
        assertEquals(1, moreOnlySnapshot.activeToggleButtonCount)
        assertTrue(moreOnlySnapshot.more.isOn)
        assertTrue(moreOnlySnapshot.more.hasPersistentNeonGlow)
        assertEquals(0xFFB388FFL, moreOnlySnapshot.more.primaryNeonHex)
        assertFalse(moreOnlySnapshot.mute.isOn)

        // 6. Speaker ON independently (Electric blue glowing ring with expanding sound-wave animations)
        val speakerOnlySnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall.copy(isSpeakerOn = true)
        )
        assertEquals(1, speakerOnlySnapshot.activeToggleButtonCount)
        assertTrue(speakerOnlySnapshot.speaker.isOn)
        assertTrue(speakerOnlySnapshot.speaker.hasPersistentNeonGlow)
        assertEquals(0xFF00E5FFL, speakerOnlySnapshot.speaker.primaryNeonHex)
        assertEquals(0xFF2979FFL, speakerOnlySnapshot.speaker.secondaryNeonHex)
        assertFalse(speakerOnlySnapshot.mute.isOn)

        // 7. Dial pad ON independently (Purple neon glow with subtle sequential dot animations)
        val dialPadOnlySnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall,
            isKeypadOpen = true
        )
        assertEquals(1, dialPadOnlySnapshot.activeToggleButtonCount)
        assertTrue(dialPadOnlySnapshot.dialPad.isOn)
        assertTrue(dialPadOnlySnapshot.dialPad.hasPersistentNeonGlow)
        assertEquals(0xFFAA00FFL, dialPadOnlySnapshot.dialPad.primaryNeonHex)

        // 8. End Call button: preserves original red pill appearance with subtle press feedback only
        val endPressedSnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall,
            isEndCallPressed = true
        )
        assertFalse(endPressedSnapshot.endCall.isOn)
        assertFalse(endPressedSnapshot.endCall.hasPersistentNeonGlow)
        assertEquals(0xFFEF5040L, endPressedSnapshot.endCall.primaryNeonHex)
        assertNotEquals(defaultOffSnapshot.endCall.accentNeonHex, endPressedSnapshot.endCall.accentNeonHex)

        // 9. Multi-button combinations: Mute + Speaker simultaneously ON, then all 6 buttons ON (Reference Image 2), then back to OFF
        val muteAndSpeakerSnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall.copy(isMuted = true, isSpeakerOn = true)
        )
        assertEquals(2, muteAndSpeakerSnapshot.activeToggleButtonCount)
        assertTrue(muteAndSpeakerSnapshot.mute.isOn)
        assertTrue(muteAndSpeakerSnapshot.speaker.isOn)
        assertFalse(muteAndSpeakerSnapshot.record.isOn)
        assertFalse(muteAndSpeakerSnapshot.aiAssistant.isOn)

        val allSixActiveSnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall.copy(
                isMuted = true,
                isSpeakerOn = true,
                isOnHold = true,
                isRecordingActive = true
            ),
            isRecordingActive = true,
            aiStatus = com.example.telecom.AiAssistantStatus.SPEAKING,
            isAiActiveSession = true,
            isMoreSheetOpen = true,
            isKeypadOpen = true
        )
        assertEquals(6, allSixActiveSnapshot.activeToggleButtonCount)
        assertTrue(allSixActiveSnapshot.mute.hasContinuousAnimation)
        assertTrue(allSixActiveSnapshot.record.hasContinuousAnimation)
        assertTrue(allSixActiveSnapshot.aiAssistant.hasContinuousAnimation)
        assertTrue(allSixActiveSnapshot.more.hasContinuousAnimation)
        assertTrue(allSixActiveSnapshot.speaker.hasContinuousAnimation)
        assertTrue(allSixActiveSnapshot.dialPad.hasContinuousAnimation)

        // 10. Reduced-motion setting (animatorDurationScale = 0f) disables continuous infinite loops while preserving ON state glow
        val reducedMotionSnapshot = com.example.ui.screens.resolveActiveCallControlsVisualSnapshot(
            callState = baseConnectedCall.copy(isMuted = true, isSpeakerOn = true),
            animatorDurationScale = 0f
        )
        assertEquals(2, reducedMotionSnapshot.activeToggleButtonCount)
        assertTrue(reducedMotionSnapshot.mute.isOn)
        assertTrue(reducedMotionSnapshot.mute.hasPersistentNeonGlow)
        assertFalse(reducedMotionSnapshot.mute.hasContinuousAnimation)
        assertTrue(reducedMotionSnapshot.speaker.isOn)
        assertFalse(reducedMotionSnapshot.speaker.hasContinuousAnimation)
    }

    @Test
    fun verifyCallSmartIslandStatesGesturesLongPressAndOverlayLifecycle() {
        // 1. IDLE state when there is no ongoing call (no fake or permanently active island)
        val idleVisual = com.example.telecom.resolveSmartIslandVisualState(
            callState = null,
            isRecordingConfirmedActive = false,
            aiStatus = com.example.telecom.AiAssistantStatus.IDLE,
            isAiSessionActive = false,
            isClosingAnimationActive = false
        )
        assertEquals(com.example.telecom.SmartIslandVisualState.IDLE, idleVisual)
        assertFalse(idleVisual.isVisible)

        // 2. INCOMING CALL -> Answer -> ACTIVE CALL Island
        val incomingCall = ActiveCallUiState(
            callId = "island_call_1",
            state = Call.STATE_RINGING,
            number = "+91 97730 07356",
            displayName = "Jerry",
            isIncoming = true
        )
        val incomingVisual = com.example.telecom.resolveSmartIslandVisualState(
            callState = incomingCall
        )
        assertEquals(com.example.telecom.SmartIslandVisualState.INCOMING_CALL, incomingVisual)
        assertEquals(0xFF9C5CFFL, incomingVisual.primaryHex)
        assertTrue(incomingVisual.isVisible)

        val activeCall = incomingCall.copy(
            state = Call.STATE_ACTIVE,
            isIncoming = false
        )
        val activeVisual = com.example.telecom.resolveSmartIslandVisualState(
            callState = activeCall
        )
        assertEquals(com.example.telecom.SmartIslandVisualState.ACTIVE_CALL, activeVisual)
        assertEquals(0xFF00E676L, activeVisual.primaryHex)
        assertEquals("00:02:36", com.example.telecom.formatSmartIslandDurationHHMMSS(156L))

        // 3. CALL RECORDING state ONLY when actual recording is confirmed active
        val notRecordingVisual = com.example.telecom.resolveSmartIslandVisualState(
            callState = activeCall.copy(isRecordingActive = false),
            isRecordingConfirmedActive = false
        )
        assertEquals(com.example.telecom.SmartIslandVisualState.ACTIVE_CALL, notRecordingVisual)

        val recordingVisual = com.example.telecom.resolveSmartIslandVisualState(
            callState = activeCall.copy(isRecordingActive = true),
            isRecordingConfirmedActive = true
        )
        assertEquals(com.example.telecom.SmartIslandVisualState.CALL_RECORDING, recordingVisual)
        assertEquals(0xFFFF3B4EL, recordingVisual.primaryHex)
        assertEquals("00:00:18", com.example.telecom.formatSmartIslandDurationHHMMSS(18L))

        // 4. AI CALL ASSISTANT state transitions (Listening, Speaking, Processing, Paused)
        val aiListeningVisual = com.example.telecom.resolveSmartIslandVisualState(
            callState = activeCall,
            isRecordingConfirmedActive = false,
            aiStatus = com.example.telecom.AiAssistantStatus.LISTENING,
            isAiSessionActive = true
        )
        assertEquals(com.example.telecom.SmartIslandVisualState.AI_CALL_ASSISTANT, aiListeningVisual)
        assertEquals(0xFF00E5FFL, aiListeningVisual.primaryHex)
        assertEquals(0xFFB388FFL, aiListeningVisual.secondaryHex)
        assertEquals("Listening...", com.example.telecom.resolveSmartIslandAiStatusSubtitle(com.example.telecom.AiAssistantStatus.LISTENING))
        assertEquals("Speaking...", com.example.telecom.resolveSmartIslandAiStatusSubtitle(com.example.telecom.AiAssistantStatus.SPEAKING))
        assertEquals("Paused", com.example.telecom.resolveSmartIslandAiStatusSubtitle(com.example.telecom.AiAssistantStatus.PAUSED))
        assertEquals("Processing...", com.example.telecom.resolveSmartIslandAiStatusSubtitle(com.example.telecom.AiAssistantStatus.PROCESSING))

        // 5. CALL ENDED closing animation state
        val callEndedVisual = com.example.telecom.resolveSmartIslandVisualState(
            callState = null,
            isClosingAnimationActive = true
        )
        assertEquals(com.example.telecom.SmartIslandVisualState.CALL_ENDED, callEndedVisual)
        assertEquals(0xFFFF3B4EL, callEndedVisual.primaryHex)
        assertEquals(0xFFB388FFL, callEndedVisual.secondaryHex)

        // 6. Compact Island Single Tap -> EXPAND_ISLAND; Expanded Island Outside Tap / Swipe Up -> COLLAPSE_ISLAND
        val tapExpandOutcome = com.example.telecom.resolveSmartIslandGestureOutcome(
            visualState = com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
            expansionMode = com.example.telecom.SmartIslandExpansionMode.COMPACT,
            isSingleTap = true
        )
        assertEquals(com.example.telecom.SmartIslandGestureOutcome.EXPAND_ISLAND, tapExpandOutcome)

        val outsideTapCollapseOutcome = com.example.telecom.resolveSmartIslandGestureOutcome(
            visualState = com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
            expansionMode = com.example.telecom.SmartIslandExpansionMode.EXPANDED,
            isOutsideTap = true
        )
        assertEquals(com.example.telecom.SmartIslandGestureOutcome.COLLAPSE_ISLAND, outsideTapCollapseOutcome)

        val swipeUpCollapseOutcome = com.example.telecom.resolveSmartIslandGestureOutcome(
            visualState = com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
            expansionMode = com.example.telecom.SmartIslandExpansionMode.EXPANDED,
            dragDeltaY = -54f
        )
        assertEquals(com.example.telecom.SmartIslandGestureOutcome.COLLAPSE_ISLAND, swipeUpCollapseOutcome)

        // 7. 3-Second Long Press Circular Progress Evaluation (early release cancels vs 3000ms completes to Full-Screen)
        val midHold = com.example.telecom.evaluateSmartIslandLongPress(
            heldDurationMs = 1500L,
            isReleased = false
        )
        assertEquals(0.5f, midHold.progress, 0.01f)
        assertFalse(midHold.isCompleted)
        assertFalse(midHold.isCancelled)

        val earlyRelease = com.example.telecom.evaluateSmartIslandLongPress(
            heldDurationMs = 2100L,
            isReleased = true
        )
        assertEquals(0f, earlyRelease.progress, 0.001f)
        assertFalse(earlyRelease.isCompleted)
        assertTrue(earlyRelease.isCancelled)
        assertEquals(com.example.telecom.SmartIslandGestureOutcome.CANCEL_LONG_PRESS_PROGRESS, earlyRelease.outcome)

        val completedHold = com.example.telecom.evaluateSmartIslandLongPress(
            heldDurationMs = 3000L,
            isReleased = false
        )
        assertEquals(1f, completedHold.progress, 0.001f)
        assertTrue(completedHold.isCompleted)
        assertFalse(completedHold.isCancelled)
        assertEquals(com.example.telecom.SmartIslandGestureOutcome.OPEN_FULL_SCREEN_ACTIVE_CALL, completedHold.outcome)
    }

    @Test
    fun verifySmartIslandRealCameraCutoutPlacementAndFullScreenSuppression() {
        // 1. Verify full-screen active call visibility suppression:
        // When InCallActivity / Full-Screen Active Call Screen is visible, the Smart Island MUST be hidden.
        assertFalse(
            "Smart Island must be hidden when Full-Screen Active Call Screen is visible",
            com.example.telecom.shouldDisplaySmartIsland(
                visualState = com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
                isFullScreenCallUiVisible = true,
                isDismissedForCurrentIncomingRing = false
            )
        )
        assertFalse(
            "Incoming Smart Island must be hidden when Full-Screen Call Screen is visible",
            com.example.telecom.shouldDisplaySmartIsland(
                visualState = com.example.telecom.SmartIslandVisualState.INCOMING_CALL,
                isFullScreenCallUiVisible = true,
                isDismissedForCurrentIncomingRing = false
            )
        )
        // As soon as the user leaves the Full-Screen Active Call Screen (Home, YouTube, Chrome, ChatGPT, or Phone dialpad/tabs),
        // the Smart Island must immediately be restored if a call is ongoing.
        assertTrue(
            "Smart Island must be restored immediately when leaving Full-Screen Active Call Screen",
            com.example.telecom.shouldDisplaySmartIsland(
                visualState = com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
                isFullScreenCallUiVisible = false,
                isDismissedForCurrentIncomingRing = false
            )
        )

        // 2. Verify real punch-hole camera cutout alignment on Motorola G57 Power (1080x2400, center punch-hole at 504..576, 20..92)
        val motoCutoutMetrics = com.example.telecom.RealDisplayCutoutMetrics(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 2.75f,
            statusBarHeightPx = 104,
            hasPhysicalCutout = true,
            cutoutLeftPx = 504,
            cutoutTopPx = 20,
            cutoutRightPx = 576,
            cutoutBottomPx = 92
        )
        val compactPlacement = com.example.telecom.resolveSmartIslandCutoutPlacement(
            metrics = motoCutoutMetrics,
            visualState = com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
            expansionMode = com.example.telecom.SmartIslandExpansionMode.COMPACT
        )

        // Island must sit at the top edge around the real punch-hole camera (not pushed below the 104px status bar)
        assertTrue(
            "Smart Island must surround the real physical punch-hole camera vertically and horizontally",
            compactPlacement.surroundsPhysicalCamera
        )
        assertTrue(
            "Smart Island top offset (${compactPlacement.islandTopOffsetPx}px) must be above the camera cutout top (${motoCutoutMetrics.cutoutTopPx}px)",
            compactPlacement.islandTopOffsetPx <= motoCutoutMetrics.cutoutTopPx
        )
        assertTrue(
            "Smart Island bottom (${compactPlacement.islandTopOffsetPx + compactPlacement.islandHeightPx}px) must extend below the camera cutout bottom (${motoCutoutMetrics.cutoutBottomPx}px)",
            compactPlacement.islandTopOffsetPx + compactPlacement.islandHeightPx >= motoCutoutMetrics.cutoutBottomPx
        )
        assertFalse(
            "Compact Smart Island must not overlap status bar time (left) or network/battery icons (right)",
            compactPlacement.overlapsStatusBarIcons
        )
        assertEquals(0, compactPlacement.islandHorizontalOffsetFromCenterPx)
        assertTrue(
            "Transparent camera hole radius must cover the physical punch-hole camera radius",
            compactPlacement.cameraHoleRadiusPx >= motoCutoutMetrics.cutoutWidthPx / 2f
        )

        // 3. Verify Outgoing Call and Answered Incoming Call transition cleanly between Full-Screen Active Call UI
        // (InCallActivity visible -> island hidden) and background/other apps (InCallActivity stopped -> island restored)
        val outgoingCall = ActiveCallUiState(
            callId = "outgoing_1",
            state = Call.STATE_DIALING,
            number = "+91 73832 39510",
            displayName = "Het",
            isIncoming = false
        )
        com.example.telecom.CallStateManager.setInCallActivityVisible(true)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = outgoingCall,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 0L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertTrue(com.example.telecom.CallStateManager.isInCallActivityVisible.value)
        assertFalse(
            "Smart Island must be hidden while Full-Screen Outgoing Call UI is visible",
            com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible
        )

        // Answering an incoming call (isIncoming = true, state = STATE_ACTIVE) keeps InCallActivity visible in Full-Screen mode
        val answeredIncomingCall = outgoingCall.copy(
            callId = "incoming_1",
            state = Call.STATE_ACTIVE,
            isIncoming = true
        )
        com.example.telecom.CallStateManager.setInCallActivityVisible(true)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = answeredIncomingCall,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 12L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertEquals(
            com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
            com.example.telecom.CallSmartIslandManager.uiState.value.visualState
        )
        assertFalse(
            "Smart Island must remain hidden while Full-Screen Active Call UI is visible",
            com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible
        )

        // User leaves InCallActivity (Home / Back / other app): Smart Island restores immediately
        com.example.telecom.CallStateManager.setInCallActivityVisible(false)
        assertTrue(
            "Smart Island must restore immediately when user leaves InCallActivity during an active call",
            com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible
        )
    }

    @Test
    fun verifyAutomaticFullScreenCallUiAllSevenScenarios() {
        // Default presentation mode for incoming calls must be FULL_SCREEN
        com.example.telecom.CallStateManager.resetMinimizedStateForNewCall()
        com.example.telecom.CallStateManager.setIncomingPresentationMode(
            com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN
        )
        assertEquals(
            com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN,
            com.example.telecom.CallStateManager.incomingPresentationMode.value
        )
        assertFalse(com.example.telecom.CallStateManager.hasUserMinimizedCurrentCall.value)

        // Scenario 1: Initiate an outgoing call from HET Phone
        val outgoingFromHetPhone = ActiveCallUiState(
            callId = "scenario1_outgoing_het",
            number = "+91 73832 39510",
            displayName = "Het",
            locationLabel = "India",
            state = Call.STATE_DIALING,
            isIncoming = false
        )
        com.example.telecom.CallStateManager.resetMinimizedStateForNewCall()
        com.example.telecom.CallStateManager.setInCallActivityVisible(true)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = outgoingFromHetPhone,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 0L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertTrue(com.example.telecom.CallStateManager.isInCallActivityVisible.value)
        assertFalse(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)

        // Scenario 2: Initiate an outgoing call from another supported app (CONNECTING -> DIALING -> ACTIVE)
        val outgoingFromExternalApp = ActiveCallUiState(
            callId = "scenario2_outgoing_external",
            number = "+91 97378 92210",
            displayName = "VIJAY",
            locationLabel = "India",
            state = Call.STATE_ACTIVE,
            connectTimeMillis = System.currentTimeMillis() - 15_000L,
            isIncoming = false
        )
        com.example.telecom.CallStateManager.resetMinimizedStateForNewCall()
        com.example.telecom.CallStateManager.setInCallActivityVisible(true)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = outgoingFromExternalApp,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 15L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertTrue(com.example.telecom.CallStateManager.isInCallActivityVisible.value)
        assertFalse(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)

        // Scenario 3: Receive an incoming call while on the Home Screen
        val incomingOnHome = ActiveCallUiState(
            callId = "scenario3_incoming_home",
            number = "+91 97378 92210",
            displayName = "VIJAY",
            locationLabel = "India",
            state = Call.STATE_RINGING,
            isIncoming = true
        )
        com.example.telecom.CallStateManager.resetMinimizedStateForNewCall()
        com.example.telecom.CallStateManager.setIncomingPresentationMode(
            com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN
        )
        com.example.telecom.CallStateManager.setInCallActivityVisible(true)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = incomingOnHome,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 0L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertEquals(
            com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN,
            com.example.telecom.CallStateManager.incomingPresentationMode.value
        )
        assertTrue(com.example.telecom.CallStateManager.isInCallActivityVisible.value)
        assertFalse(
            "No duplicate Smart Island may appear while Full-Screen Incoming Call UI is visible",
            com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible
        )

        // Scenario 4: Receive an incoming call while using another app (YouTube, Chrome, WhatsApp)
        val incomingInOtherApp = incomingOnHome.copy(callId = "scenario4_incoming_other_app")
        com.example.telecom.CallStateManager.resetMinimizedStateForNewCall()
        com.example.telecom.CallStateManager.setInCallActivityVisible(true)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = incomingInOtherApp,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 0L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertTrue(com.example.telecom.CallStateManager.isInCallActivityVisible.value)
        assertFalse(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)

        // Scenario 5 & 6: Minimize the Full-Screen Call UI during an active call and verify Smart Island appears
        val activeOngoingCall = incomingInOtherApp.copy(
            state = Call.STATE_ACTIVE,
            isIncoming = true,
            connectTimeMillis = System.currentTimeMillis() - 57_000L
        )
        com.example.telecom.CallStateManager.markCallUiMinimizedByUser()
        com.example.telecom.CallStateManager.setInCallActivityVisible(false)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = activeOngoingCall,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 57L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertTrue(com.example.telecom.CallStateManager.hasUserMinimizedCurrentCall.value)
        assertFalse(com.example.telecom.CallStateManager.isInCallActivityVisible.value)
        assertTrue(
            "Smart Island must appear immediately when Full-Screen Call UI is minimized during an active call",
            com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible
        )
        assertEquals(
            com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
            com.example.telecom.CallSmartIslandManager.uiState.value.visualState
        )

        // Scenario 7: End the call and verify that both interfaces disappear
        com.example.telecom.CallStateManager.setInCallActivityVisible(false)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = null,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 0L,
            isFullScreenCallUiVisible = false
        )
        com.example.telecom.CallSmartIslandManager.dismissCallEndedAnimationImmediate()
        com.example.telecom.CallStateManager.resetMinimizedStateForNewCall()
        assertFalse(com.example.telecom.CallStateManager.isInCallActivityVisible.value)
        assertFalse(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)
        assertEquals(
            com.example.telecom.SmartIslandVisualState.IDLE,
            com.example.telecom.CallSmartIslandManager.uiState.value.visualState
        )
    }

    @Test
    fun verifyIncomingCallPresentationAndNotificationReliabilityScenariosAtoG() {
        // Verify high-importance incoming call channel constant is distinct and non-empty
        assertEquals(
            "het_phone_incoming_call_high_v4",
            com.example.telecom.ColorInCallService.CHANNEL_INCOMING_CALL_ID
        )
        assertEquals(
            "het_phone_ongoing_call_v4",
            com.example.telecom.ColorInCallService.CHANNEL_ONGOING_CALL_ID
        )

        // Scenarios A, B, C: Incoming call arrives while phone is locked, on Home Screen, or in another app
        com.example.telecom.CallStateManager.resetMinimizedStateForNewCall()
        com.example.telecom.CallStateManager.setIncomingPresentationMode(
            com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN
        )
        com.example.telecom.CallStateManager.setInCallActivityVisible(false)

        val incomingCall = ActiveCallUiState(
            callId = "sim_incoming_call_1",
            number = "+91 97378 92210",
            displayName = "VIJAY",
            locationLabel = "India",
            state = Call.STATE_RINGING,
            isIncoming = true
        )

        // G: Before InCallActivity is resumed (or if full-screen is blocked and notification is shown),
        // hasUserMinimizedCurrentCall is false so the system overlay must NOT cover the notification/screen
        assertFalse(com.example.telecom.CallStateManager.hasUserMinimizedCurrentCall.value)

        // Once InCallActivity reaches onResume() (Full-Screen Incoming Call UI visible):
        // Smart Island is hidden (isIslandVisible == false)
        com.example.telecom.CallStateManager.setInCallActivityVisible(true)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = incomingCall,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 0L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertFalse(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)

        // Scenario D & F: Answering transitions the call state to STATE_ACTIVE
        val answeredCall = incomingCall.copy(
            state = Call.STATE_ACTIVE,
            connectTimeMillis = System.currentTimeMillis() - 3_000L
        )
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = answeredCall,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 3L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertEquals(
            com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
            com.example.telecom.CallSmartIslandManager.uiState.value.visualState
        )
        assertFalse(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)

        // Scenario E & F: Declining / ending clears the call state and hides all call interfaces
        com.example.telecom.CallStateManager.setInCallActivityVisible(false)
        com.example.telecom.CallSmartIslandManager.dismissCallEndedAnimationImmediate()
        com.example.telecom.CallStateManager.resetMinimizedStateForNewCall()
        assertFalse(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)
    }

    @Test
    fun verifyIncomingCallAnswerAndDeclineSwipeUpAndTapControlTriggers() {
        // 1. Swiping UP on Answer or Decline button beyond threshold (-28px) triggers the action
        assertTrue(
            com.example.ui.screens.shouldTriggerIncomingCallControlAction(
                dragDeltaY = -42f,
                wasDragged = true,
                isTapConfirmed = false,
                swipeUpThresholdPx = 28f
            )
        )
        assertTrue(
            com.example.ui.screens.shouldTriggerIncomingCallControlAction(
                dragDeltaY = -28f,
                wasDragged = true,
                isTapConfirmed = false,
                swipeUpThresholdPx = 28f
            )
        )

        // 2. Tapping Answer or Decline button triggers the action
        assertTrue(
            com.example.ui.screens.shouldTriggerIncomingCallControlAction(
                dragDeltaY = 0f,
                wasDragged = false,
                isTapConfirmed = true,
                swipeUpThresholdPx = 28f
            )
        )

        // 3. Swiping DOWN or tiny movement below threshold does NOT trigger Answer or Decline
        assertFalse(
            com.example.ui.screens.shouldTriggerIncomingCallControlAction(
                dragDeltaY = 45f,
                wasDragged = true,
                isTapConfirmed = false,
                swipeUpThresholdPx = 28f
            )
        )
        assertFalse(
            com.example.ui.screens.shouldTriggerIncomingCallControlAction(
                dragDeltaY = -12f,
                wasDragged = true,
                isTapConfirmed = false,
                swipeUpThresholdPx = 28f
            )
        )
    }

    @Test
    fun verifyGujaratiAiSafetyGuardSelfEchoFilterAndSessionDuration() {
        val cloudEngine = com.example.telecom.GujaratiAiCloudEngine
        val convEngine = com.example.telecom.GujaratiConversationEngine

        // 1. Verify no-commitment / payment / OTP safety guard detects sensitive requests in Gujarati & mixed Gujarati-English
        assertTrue(cloudEngine.isSensitiveCommitmentOrPaymentRequest("તમારો ઓટીપી અને પાસવર્ડ આપો"))
        assertTrue(cloudEngine.isSensitiveCommitmentOrPaymentRequest("5000 rupiya gpay transfer karo"))
        assertTrue(cloudEngine.isSensitiveCommitmentOrPaymentRequest("મને પાકું વચન આપો"))
        assertFalse(cloudEngine.isSensitiveCommitmentOrPaymentRequest("હું થોડી વાર પછી ફોન કરીશ"))

        val otpRefusal = convEngine.evaluateCallerInput(
            callerSpeech = "તમારો OTP આપો",
            currentConsent = com.example.telecom.AiConsentState.GRANTED,
            userInstruction = convEngine.DEFAULT_USER_INSTRUCTION_GUJARATI,
            previousAiReplies = emptyList()
        )
        assertEquals("SAFETY_NO_COMMITMENT", otpRefusal.detectedTopic)
        assertTrue(otpRefusal.aiReplyGujarati.contains("AI કૉલ સહાયક"))
        assertTrue(otpRefusal.aiReplyGujarati.contains("OTP"))

        val paymentRefusal = convEngine.evaluateContinuousTurn(
            userSpeech = "5000 રૂપિયા ગૂગલ પે કરો",
            conversationHistory = emptyList()
        )
        assertEquals("SAFETY_NO_COMMITMENT", paymentRefusal.detectedTopic)
        assertTrue(paymentRefusal.aiReplyGujarati.contains("નાણાકીય વ્યવહાર") || paymentRefusal.aiReplyGujarati.contains("પેમેન્ટ"))

        // 2. Verify TTS self-echo suppression prevents the AI from responding to its own spoken Gujarati output
        val aiGreeting = convEngine.INITIAL_DISCLOSURE_AND_CONSENT_GUJARATI
        assertTrue(
            cloudEngine.isLikelyTtsSelfEcho(
                recognizedText = "નમસ્તે હું AI કૉલ આસિસ્ટન્ટ વાત કરી રહ્યો છું",
                lastAiSpokenText = aiGreeting,
                timeSinceTtsEndedMs = 600L
            )
        )
        assertFalse(
            cloudEngine.isLikelyTtsSelfEcho(
                recognizedText = "હા બોલો મારે અગત્યનું કામ છે",
                lastAiSpokenText = aiGreeting,
                timeSinceTtsEndedMs = 600L
            )
        )

        // 3. Verify session duration formatting (MM:SS and HH:MM:SS)
        assertEquals("00:00", cloudEngine.formatSessionDuration(0L, 10_000L))
        assertEquals("01:05", cloudEngine.formatSessionDuration(1_000L, 66_000L))
        assertEquals("01:02:03", cloudEngine.formatSessionDuration(1_000L, 3_724_000L))
    }

    @Test
    fun verifyCallScreenWallpaperManagerPersistenceAndAdjustments() {
        val manager = com.example.data.CallScreenWallpaperManager

        // 1. Verify default configuration
        val defaultCfg = manager.sanitizeConfig(com.example.data.CallWallpaperConfig())
        assertEquals(com.example.data.CallWallpaperType.SYSTEM_DEFAULT, defaultCfg.type)
        assertEquals("", defaultCfg.mediaUri)
        assertEquals(0f, defaultCfg.blurRadiusDp, 0.01f)
        assertEquals(0.20f, defaultCfg.darkenAlpha, 0.01f)
        assertTrue(defaultCfg.summarySubtitle().contains("System Default"))

        // 2. Verify Built-in Wallpaper selection & adjustments
        val builtInApplied = manager.sanitizeConfig(
            com.example.data.CallWallpaperConfig(
                type = com.example.data.CallWallpaperType.BUILT_IN,
                builtInId = "builtin_cosmic_nebula",
                blurRadiusDp = 12f,
                darkenAlpha = 0.45f
            )
        )
        assertEquals(com.example.data.CallWallpaperType.BUILT_IN, builtInApplied.type)
        assertEquals("builtin_cosmic_nebula", builtInApplied.builtInId)
        assertEquals(12f, builtInApplied.blurRadiusDp, 0.01f)
        assertEquals(0.45f, builtInApplied.darkenAlpha, 0.01f)
        assertTrue(builtInApplied.summarySubtitle().contains("Cosmic Nebula"))

        // 3. Verify Photo and Video wallpaper validation and slider clamping
        val photoApplied = manager.sanitizeConfig(
            com.example.data.CallWallpaperConfig(
                type = com.example.data.CallWallpaperType.PHOTO,
                mediaUri = "content://media/picker/0/com.android.providers.media.photopicker/media/1001",
                blurRadiusDp = 99f, // clamps to MAX_BLUR_DP (25f)
                darkenAlpha = 0.99f // clamps to MAX_DARKEN_ALPHA (0.85f)
            )
        )
        assertEquals(com.example.data.CallWallpaperType.PHOTO, photoApplied.type)
        assertEquals(25f, photoApplied.blurRadiusDp, 0.01f)
        assertEquals(0.85f, photoApplied.darkenAlpha, 0.01f)

        val videoApplied = manager.sanitizeConfig(
            com.example.data.CallWallpaperConfig(
                type = com.example.data.CallWallpaperType.VIDEO,
                mediaUri = "content://media/picker/0/com.android.providers.media.photopicker/media/2002",
                blurRadiusDp = 4f,
                darkenAlpha = 0.30f
            )
        )
        assertEquals(com.example.data.CallWallpaperType.VIDEO, videoApplied.type)
        assertEquals(4f, videoApplied.blurRadiusDp, 0.01f)
        assertEquals(0.30f, videoApplied.darkenAlpha, 0.01f)

        // 4. Verify empty media URI falls back safely to System Default
        val emptyMediaFallback = manager.sanitizeConfig(
            com.example.data.CallWallpaperConfig(
                type = com.example.data.CallWallpaperType.PHOTO,
                mediaUri = "   "
            )
        )
        assertEquals(com.example.data.CallWallpaperType.SYSTEM_DEFAULT, emptyMediaFallback.type)
        assertEquals("", emptyMediaFallback.mediaUri)
    }

    @Test
    fun verifyFastDialpadT9AndDigitMatching() {
        assertEquals("9737892210", com.example.ui.screens.extractDialSearchChars("973-789 2210"))
        assertTrue(com.example.ui.screens.containsDialDigits("+91 97378 92210", "97378"))
        assertTrue(com.example.ui.screens.containsDialDigits("(267) 380-0111", "267380"))
        assertFalse(com.example.ui.screens.containsDialDigits("+91 97378 92210", "99999"))

        // T9 matching: "VIJAY" -> 84529, "Sophia" -> 767442
        assertTrue(com.example.ui.screens.matchesT9Name("VIJAY", "84529"))
        assertTrue(com.example.ui.screens.matchesT9Name("Sophia R.", "7674"))
        assertFalse(com.example.ui.screens.matchesT9Name("Sophia", "222"))
        assertFalse(com.example.ui.screens.matchesT9Name("Sophia", "123"))
    }

    @Test
    fun verifyBluetoothAudioEndpointRoutingSequencesAtoE() {
        var latestSnapshot: com.example.telecom.CallAudioRoutingSnapshot? = null
        val routingErrors = mutableListOf<String>()

        val router = com.example.telecom.CallAudioEndpointRouter(
            onStateChanged = { latestSnapshot = it },
            onRoutingError = { routingErrors.add(it) }
        )

        val btEarbuds = com.example.model.CallAudioEndpointItem(
            id = "endpoint_bt_AA:BB:CC:11:22:33",
            name = "Galaxy Buds2 Pro",
            type = com.example.model.CallAudioEndpointType.BLUETOOTH,
            isActive = true,
            bluetoothAddress = "AA:BB:CC:11:22:33"
        )
        val speaker = com.example.model.CallAudioEndpointItem(
            id = com.example.telecom.CallAudioEndpointRouter.ID_SPEAKER,
            name = "Speaker",
            type = com.example.model.CallAudioEndpointType.SPEAKER,
            isActive = false
        )
        val earpiece = com.example.model.CallAudioEndpointItem(
            id = com.example.telecom.CallAudioEndpointRouter.ID_EARPIECE,
            name = "Phone earpiece",
            type = com.example.model.CallAudioEndpointType.EARPIECE,
            isActive = false
        )

        // Initial state: Bluetooth earbuds connected and active during call
        router.applyAvailableAndActiveEndpoints(
            available = listOf(btEarbuds, speaker, earpiece),
            confirmedActiveEndpointId = btEarbuds.id
        )
        assertEquals(com.example.model.CallAudioEndpointType.BLUETOOTH, latestSnapshot!!.activeEndpoint.type)
        assertEquals("Galaxy Buds2 Pro", latestSnapshot!!.activeEndpoint.name)
        assertTrue(latestSnapshot!!.isBluetoothAvailable)
        assertTrue(latestSnapshot!!.isBluetoothActive)
        assertFalse(latestSnapshot!!.isSpeakerActive)

        // =========================================================================
        // SEQUENCE A: Bluetooth -> Speaker -> Bluetooth
        // Also verifies that UI state ONLY updates after routing confirmation!
        // =========================================================================
        var confirmRoutingCallback: ((Boolean) -> Unit)? = null
        router.requestEndpointChange(
            service = null,
            targetEndpoint = speaker,
            customDispatcher = { _, onResult -> confirmRoutingCallback = onResult }
        )
        // Before Telecom confirmation: activeEndpoint MUST still be Bluetooth, with pendingEndpointId set
        assertEquals(com.example.model.CallAudioEndpointType.BLUETOOTH, latestSnapshot!!.activeEndpoint.type)
        assertEquals(speaker.id, latestSnapshot!!.pendingEndpointId)

        // Now Telecom confirms Speaker route succeeded
        confirmRoutingCallback!!.invoke(true)
        assertEquals(com.example.model.CallAudioEndpointType.SPEAKER, latestSnapshot!!.activeEndpoint.type)
        assertEquals(null, latestSnapshot!!.pendingEndpointId)
        assertTrue(latestSnapshot!!.isSpeakerActive)
        // Bluetooth MUST remain available while on Speaker
        assertTrue(latestSnapshot!!.isBluetoothAvailable)
        assertTrue(
            latestSnapshot!!.availableEndpoints.any {
                it.type == com.example.model.CallAudioEndpointType.BLUETOOTH && it.name == "Galaxy Buds2 Pro"
            }
        )

        // Switch back from Speaker -> Bluetooth
        router.requestEndpointChange(
            service = null,
            targetEndpoint = btEarbuds,
            customDispatcher = { _, onResult -> onResult(true) }
        )
        assertEquals(com.example.model.CallAudioEndpointType.BLUETOOTH, latestSnapshot!!.activeEndpoint.type)
        assertEquals("Galaxy Buds2 Pro", latestSnapshot!!.activeEndpoint.name)
        assertTrue(latestSnapshot!!.isBluetoothActive)

        // =========================================================================
        // SEQUENCE B: Bluetooth -> Earpiece -> Bluetooth
        // =========================================================================
        router.requestEndpointChange(
            service = null,
            targetEndpoint = earpiece,
            customDispatcher = { _, onResult -> onResult(true) }
        )
        assertEquals(com.example.model.CallAudioEndpointType.EARPIECE, latestSnapshot!!.activeEndpoint.type)
        // Bluetooth MUST still be preserved in availableEndpoints after switching to Earpiece
        assertTrue(latestSnapshot!!.isBluetoothAvailable)
        assertEquals(3, latestSnapshot!!.availableEndpoints.size)

        // Switch from Earpiece -> Bluetooth earbuds
        router.requestEndpointChange(
            service = null,
            targetEndpoint = btEarbuds,
            customDispatcher = { _, onResult -> onResult(true) }
        )
        assertEquals(com.example.model.CallAudioEndpointType.BLUETOOTH, latestSnapshot!!.activeEndpoint.type)
        assertEquals("Galaxy Buds2 Pro", latestSnapshot!!.activeEndpoint.name)

        // =========================================================================
        // SEQUENCE C: Bluetooth -> Speaker -> Earpiece -> Bluetooth
        // Also verifies that a failed routing request does NOT fake activeEndpoint
        // =========================================================================
        router.requestEndpointChange(
            service = null,
            targetEndpoint = speaker,
            customDispatcher = { _, onResult -> onResult(true) }
        )
        assertEquals(com.example.model.CallAudioEndpointType.SPEAKER, latestSnapshot!!.activeEndpoint.type)

        router.requestEndpointChange(
            service = null,
            targetEndpoint = earpiece,
            customDispatcher = { _, onResult -> onResult(true) }
        )
        assertEquals(com.example.model.CallAudioEndpointType.EARPIECE, latestSnapshot!!.activeEndpoint.type)
        assertTrue(latestSnapshot!!.isBluetoothAvailable)

        // Simulate a failed routing attempt first -> activeEndpoint must stay on Earpiece
        router.requestEndpointChange(
            service = null,
            targetEndpoint = btEarbuds,
            customDispatcher = { _, onResult -> onResult(false) }
        )
        assertEquals(com.example.model.CallAudioEndpointType.EARPIECE, latestSnapshot!!.activeEndpoint.type)
        assertTrue(routingErrors.isNotEmpty())

        // Now succeed switching Earpiece -> Bluetooth
        router.requestEndpointChange(
            service = null,
            targetEndpoint = btEarbuds,
            customDispatcher = { _, onResult -> onResult(true) }
        )
        assertEquals(com.example.model.CallAudioEndpointType.BLUETOOTH, latestSnapshot!!.activeEndpoint.type)
        assertEquals("Galaxy Buds2 Pro", latestSnapshot!!.activeEndpoint.name)

        // =========================================================================
        // SEQUENCE D: Bluetooth disconnects during a call
        // Disconnected Bluetooth endpoint is immediately removed and fallback selected
        // =========================================================================
        var fallbackSelected: com.example.model.CallAudioEndpointItem? = null
        router.applyAvailableAndActiveEndpoints(
            available = listOf(speaker, earpiece), // Bluetooth disconnected
            confirmedActiveEndpointId = btEarbuds.id, // Was on Bluetooth when it disconnected
            onFallbackRequested = { fallbackSelected = it }
        )
        assertFalse(latestSnapshot!!.isBluetoothAvailable)
        assertFalse(latestSnapshot!!.isBluetoothActive)
        assertEquals(2, latestSnapshot!!.availableEndpoints.size)
        assertEquals(com.example.model.CallAudioEndpointType.EARPIECE, fallbackSelected?.type)
        assertEquals(com.example.model.CallAudioEndpointType.EARPIECE, latestSnapshot!!.activeEndpoint.type)

        // Attempting to select the disconnected Bluetooth device must fail cleanly without faking state
        val switchedToDisconnectedBt = router.requestEndpointChange(
            service = null,
            targetEndpoint = btEarbuds,
            customDispatcher = { _, onResult -> onResult(true) }
        )
        assertFalse(switchedToDisconnectedBt)
        assertEquals(com.example.model.CallAudioEndpointType.EARPIECE, latestSnapshot!!.activeEndpoint.type)

        // =========================================================================
        // SEQUENCE E: Bluetooth reconnects during a call
        // Reconnected Bluetooth device reappears in availableEndpoints and can be selected
        // =========================================================================
        router.applyAvailableAndActiveEndpoints(
            available = listOf(btEarbuds.copy(isActive = false), speaker, earpiece),
            confirmedActiveEndpointId = earpiece.id
        )
        assertTrue(latestSnapshot!!.isBluetoothAvailable)
        assertEquals(3, latestSnapshot!!.availableEndpoints.size)

        router.requestEndpointChange(
            service = null,
            targetEndpoint = btEarbuds,
            customDispatcher = { _, onResult -> onResult(true) }
        )
        assertTrue(latestSnapshot!!.isBluetoothActive)
        assertEquals("Galaxy Buds2 Pro", latestSnapshot!!.activeEndpoint.name)
    }

    @Test
    fun verifyLiveIncomingVoiceWaveformBehaviorAudioRoutingAndRestrictions() {
        val controller = com.example.telecom.LiveIncomingVoiceWaveformController
        controller.stopMonitoring()

        val btEndpoint = com.example.model.CallAudioEndpointItem(
            id = "bt_1",
            name = "Pixel Buds Pro",
            type = com.example.model.CallAudioEndpointType.BLUETOOTH,
            isActive = true
        )
        val speakerEndpoint = com.example.model.CallAudioEndpointItem(
            id = "spk_1",
            name = "Speaker",
            type = com.example.model.CallAudioEndpointType.SPEAKER,
            isActive = true
        )
        val earpieceEndpoint = com.example.model.CallAudioEndpointItem(
            id = "ear_1",
            name = "Phone earpiece",
            type = com.example.model.CallAudioEndpointType.EARPIECE,
            isActive = true
        )

        val baseCall = ActiveCallUiState(
            callId = "waveform_call_1",
            number = "+91 98765 43210",
            displayName = "RINKU BHABHI",
            state = Call.STATE_ACTIVE,
            connectTimeMillis = System.currentTimeMillis() - 30_000L,
            activeAudioEndpoint = earpieceEndpoint
        )

        // 1. Unsupported audio-capture scenario (no privileged CAPTURE_AUDIO_OUTPUT):
        //    Reports UNAVAILABLE_PRIVILEGED_DOWNLINK_REQUIRED and stays in calm stationary state (no fake random bars)
        controller.onCallStateChanged(context = null, callState = baseCall)
        val initialUnsupportedState = controller.uiState.value
        assertTrue(initialUnsupportedState.isCallActive)
        assertEquals(
            com.example.telecom.IncomingVoiceLevelSourceStatus.UNAVAILABLE_PRIVILEGED_DOWNLINK_REQUIRED,
            initialUnsupportedState.sourceStatus
        )
        assertFalse(initialUnsupportedState.isRemoteVoiceSpeaking)
        assertEquals(0f, initialUnsupportedState.rawIncomingAmplitude, 0.0001f)
        assertEquals(
            controller.calmStationaryBarProfile(),
            initialUnsupportedState.barFractions
        )

        // 2. Local microphone input must ALWAYS be ignored (both Mute disabled and Mute enabled)
        controller.onLocalMicrophoneInputIgnored(0.95f)
        assertEquals(0f, controller.uiState.value.rawIncomingAmplitude, 0.0001f)
        assertEquals(controller.calmStationaryBarProfile(), controller.uiState.value.barFractions)

        // 3. Quiet incoming speech produces smaller bar movements above the calm baseline
        val calmPeak = initialUnsupportedState.peakBarFraction
        repeat(4) {
            controller.onIncomingRemoteAudioLevel(remoteVoiceLevel = 0.22f)
        }
        val quietSpeechState = controller.uiState.value
        assertTrue(quietSpeechState.isRemoteVoiceSpeaking)
        assertTrue("Quiet speech bars should rise above calm resting baseline", quietSpeechState.peakBarFraction > calmPeak + 0.05f)

        // 4. Loud incoming speech produces taller bars than quiet speech
        repeat(5) {
            controller.onIncomingRemoteAudioLevel(remoteVoiceLevel = 0.92f)
        }
        val loudSpeechState = controller.uiState.value
        assertTrue(loudSpeechState.isRemoteVoiceSpeaking)
        assertTrue(
            "Loud incoming speech must produce taller bars than quiet speech",
            loudSpeechState.peakBarFraction > quietSpeechState.peakBarFraction + 0.25f
        )

        // 5. Mute enabled and disabled: local mic mute does not block remote incoming voice visualization
        controller.onCallStateChanged(
            context = null,
            callState = baseCall.copy(isMuted = true)
        )
        assertTrue(controller.uiState.value.isLocalMicMuted)
        controller.onIncomingRemoteAudioLevel(remoteVoiceLevel = 0.85f)
        assertTrue(controller.uiState.value.isRemoteVoiceSpeaking)
        controller.onLocalMicrophoneInputIgnored(1.0f)

        controller.onCallStateChanged(
            context = null,
            callState = baseCall.copy(isMuted = false)
        )
        assertFalse(controller.uiState.value.isLocalMicMuted)

        // 6. Bluetooth, Speaker, and Phone earpiece audio routing preserve waveform without interference
        controller.onCallStateChanged(
            context = null,
            callState = baseCall.copy(
                isBluetoothOn = true,
                isSpeakerOn = false,
                activeAudioEndpoint = btEndpoint
            )
        )
        assertEquals(
            com.example.model.CallAudioEndpointType.BLUETOOTH,
            controller.uiState.value.activeAudioEndpointType
        )

        controller.onCallStateChanged(
            context = null,
            callState = baseCall.copy(
                isBluetoothOn = false,
                isSpeakerOn = true,
                activeAudioEndpoint = speakerEndpoint
            )
        )
        assertEquals(
            com.example.model.CallAudioEndpointType.SPEAKER,
            controller.uiState.value.activeAudioEndpointType
        )

        controller.onCallStateChanged(
            context = null,
            callState = baseCall.copy(
                isBluetoothOn = false,
                isSpeakerOn = false,
                activeAudioEndpoint = earpieceEndpoint
            )
        )
        assertEquals(
            com.example.model.CallAudioEndpointType.EARPIECE,
            controller.uiState.value.activeAudioEndpointType
        )

        // 7. Silence: when the remote person stops speaking, waveform gradually settles into calm state
        controller.advanceSilenceFrames(steps = 25)
        val settledSilenceState = controller.uiState.value
        assertFalse(settledSilenceState.isRemoteVoiceSpeaking)
        assertEquals(0f, settledSilenceState.smoothedIncomingAmplitude, 0.001f)
        assertEquals(controller.calmStationaryBarProfile(), settledSilenceState.barFractions)

        // 8. Call disconnection: stops waveform processing immediately
        controller.onIncomingRemoteAudioLevel(remoteVoiceLevel = 0.80f)
        assertTrue(controller.uiState.value.isRemoteVoiceSpeaking)
        controller.onCallStateChanged(
            context = null,
            callState = baseCall.copy(state = Call.STATE_DISCONNECTED)
        )
        val disconnectedState = controller.uiState.value
        assertFalse(disconnectedState.isCallActive)
        assertFalse(disconnectedState.isRemoteVoiceSpeaking)
        assertEquals(
            com.example.telecom.IncomingVoiceLevelSourceStatus.INACTIVE_NO_ACTIVE_CALL,
            disconnectedState.sourceStatus
        )
        assertEquals(controller.calmStationaryBarProfile(), disconnectedState.barFractions)
    }
}



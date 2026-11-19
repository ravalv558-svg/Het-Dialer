package com.example

import android.content.Context
import android.telecom.Call
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.model.ActiveCallUiState
import com.example.model.CallDirection
import com.example.model.CallLogItem
import com.example.model.ContactItem
import com.example.model.PhoneNumberEntry
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.ContactDetailScreen
import com.example.ui.screens.IncomingCallScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verifyAppNameAndPackageResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Phone", appName)
    }

    @Test
    fun verifyIncomingCallScreenMatchesColorOs17() {
        var answered = false
        var declined = false

        val incomingState = ActiveCallUiState(
            callId = "test_incoming",
            number = "+91 97378 92210",
            displayName = "VIJAY",
            locationLabel = "India",
            state = Call.STATE_RINGING,
            isIncoming = true
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                IncomingCallScreen(
                    callState = incomingState,
                    onAnswer = { answered = true },
                    onDecline = { declined = true },
                    onRejectWithSms = {}
                )
            }
        }

        // Saved contact must show ONLY the saved contact name ("VIJAY"), never the phone number
        composeTestRule.onNodeWithText("VIJAY").assertIsDisplayed()
        composeTestRule.onNodeWithTag("incoming_call_top_banner").assertIsDisplayed()

        composeTestRule.onNodeWithTag("incoming_answer_button").performClick()
        assertTrue(answered)

        composeTestRule.onNodeWithTag("incoming_decline_button").performClick()
        assertTrue(declined)
    }

    @Test
    fun verifyActiveCallScreenMatchesColorOs17() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.telecom.CallRecordingManager.setFeatureEnabled(context, false)

        var muteToggled = false
        var speakerToggled = false
        var recordClicked = false
        var aiAssistantClicked = false
        var holdToggled = false
        var ended = false

        val activeState = ActiveCallUiState(
            callId = "test_active",
            number = "+1 267-380-0123",
            displayName = "Sophia",
            locationLabel = "pennsylvania",
            state = Call.STATE_ACTIVE,
            connectTimeMillis = System.currentTimeMillis() - 56_000L
        )

        var endCallCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveCallScreen(
                    callState = activeState,
                    initialNote = "",
                    onSaveNote = {},
                    onToggleMute = { muteToggled = true },
                    onToggleSpeaker = { speakerToggled = true },
                    onToggleBluetooth = {},
                    onToggleHold = { holdToggled = true },
                    onAddCall = {},
                    onRequestVideo = {},
                    onRecordClick = { recordClicked = true },
                    onAiAssistantClick = { aiAssistantClicked = true },
                    onPlayDtmf = {},
                    onEndCall = {
                        ended = true
                        endCallCount++
                    },
                    onAcceptWaitingCall = {},
                    onDeclineWaitingCall = {},
                    onSwapCalls = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Sophia").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_incoming_voice_waveform").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mute").assertIsDisplayed()
        composeTestRule.onNodeWithText("Record").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_btn_ai_assistant").assertIsDisplayed()
        composeTestRule.onNodeWithText("More").assertIsDisplayed()
        composeTestRule.onNodeWithText("Speaker").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_btn_end").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dial pad").assertIsDisplayed()

        // 1. Mute button
        composeTestRule.onNodeWithTag("call_btn_mute").performClick()
        composeTestRule.waitForIdle()
        assertTrue(muteToggled)

        // 2. Speaker button
        composeTestRule.onNodeWithTag("call_btn_speaker").performClick()
        composeTestRule.waitForIdle()
        assertTrue(speakerToggled)

        // 3. Record button
        composeTestRule.onNodeWithTag("call_btn_record").performClick()
        composeTestRule.waitForIdle()
        assertTrue(recordClicked)
        com.example.telecom.CallRecordingManager.setFeatureEnabled(context, true)

        // 4. AI Call Assistant button
        composeTestRule.onNodeWithTag("call_btn_ai_assistant").performClick()
        composeTestRule.waitForIdle()
        assertTrue(aiAssistantClicked)

        // 5. More action button
        composeTestRule.onNodeWithTag("call_btn_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("More call controls").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_btn_hold").performClick()
        composeTestRule.waitForIdle()
        assertTrue(holdToggled)

        // 6. Dial pad action button (open and close)
        composeTestRule.onNodeWithTag("call_btn_keypad").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("call_btn_keypad").performClick()
        composeTestRule.waitForIdle()

        // Tap End Call and verify single-trigger execution after press animation completes
        composeTestRule.onNodeWithTag("call_btn_end").performClick()
        composeTestRule.onNodeWithTag("call_btn_end").performClick()
        composeTestRule.waitForIdle()
        assertTrue(ended)
        assertEquals(1, endCallCount)
    }

    @Test
    fun verifyContactDetailScreenMatchesColorOs17() {
        var favoriteClicked = false
        var calledNumber = ""

        val sampleContact = ContactItem(
            id = 10L,
            displayName = "Sophia",
            jobTitle = "Designer",
            phones = listOf(
                PhoneNumberEntry(
                    dataId = 1L,
                    number = "+1 267-380-0111",
                    typeLabel = "Mobile",
                    isDefault = true,
                    isRecent = true,
                    location = "pennsylvania"
                )
            )
        )

        val logs = listOf(
            CallLogItem(
                id = 1L,
                number = "+1 267-380-0111",
                formattedNumber = "+1 267-380-0111",
                cachedName = "Sophia",
                contactId = 10L,
                timestamp = 1640438700000L,
                durationSeconds = 58L,
                direction = CallDirection.OUTGOING,
                isHd = true,
                geocodedLocation = "pennsylvania",
                simLabel = "SIM 1",
                phoneAccountId = "1"
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                ContactDetailScreen(
                    contact = sampleContact,
                    contactCallLogs = logs,
                    isSavedContact = true,
                    isBlockedNumber = false,
                    onBack = {},
                    onPlaceCall = { calledNumber = it },
                    onSendSms = {},
                    onStartVideoCall = { _, _ -> },
                    onToggleFavorite = { favoriteClicked = true },
                    onEditContact = {},
                    onSetDefaultNumber = {},
                    onDeleteContact = {},
                    onToggleBlockNumber = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Sophia").assertIsDisplayed()
        composeTestRule.onNodeWithText("Designer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Call logs").assertIsDisplayed()

        composeTestRule.onNodeWithTag("detail_card_call").performClick()
        assertEquals("+1 267-380-0111", calledNumber)

        composeTestRule.onNodeWithTag("detail_action_favorites").performClick()
        assertTrue(favoriteClicked)
    }

    @Test
    fun verifyFullScreenIncomingAndOutgoingCallUiAndSmartIslandCoordination() {
        var answered = false
        var declined = false

        val incomingCallState = ActiveCallUiState(
            callId = "incoming_full_1",
            number = "+91 97378 92210",
            displayName = "VIJAY",
            locationLabel = "India",
            state = Call.STATE_RINGING,
            isIncoming = true
        )

        // 1. When Full-Screen Incoming Call UI is visible, Smart Island must be completely hidden
        com.example.telecom.CallStateManager.setInCallActivityVisible(true)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = incomingCallState,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 0L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        org.junit.Assert.assertFalse(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)

        composeTestRule.setContent {
            MyApplicationTheme {
                IncomingCallScreen(
                    callState = incomingCallState,
                    initialMode = com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN,
                    externalMode = com.example.ui.screens.IncomingCallPresentationMode.FULL_SCREEN,
                    onAnswer = { answered = true },
                    onDecline = { declined = true },
                    onRejectWithSms = {}
                )
            }
        }

        // Verify Full-Screen Incoming Call UI is displayed with caller details and Answer/Decline actions
        composeTestRule.onNodeWithTag("incoming_fullscreen_container").assertIsDisplayed()
        composeTestRule.onNodeWithText("VIJAY").assertIsDisplayed()
        composeTestRule.onNodeWithText("+91 97378 92210").assertIsDisplayed()
        composeTestRule.onNodeWithTag("incoming_answer_button").performClick()
        assertTrue(answered)
        composeTestRule.onNodeWithTag("incoming_decline_button").performClick()
        assertTrue(declined)

        // 2. When the user minimizes the Full-Screen Call UI during an active call, Smart Island appears immediately
        val activeCallState = incomingCallState.copy(
            state = Call.STATE_ACTIVE,
            isIncoming = false,
            connectTimeMillis = System.currentTimeMillis() - 45_000L
        )
        com.example.telecom.CallStateManager.markCallUiMinimizedByUser()
        com.example.telecom.CallStateManager.setInCallActivityVisible(false)
        com.example.telecom.CallSmartIslandManager.onExternalStatesUpdated(
            callState = activeCallState,
            recordingState = com.example.telecom.CallRecordingSessionState(),
            aiState = com.example.telecom.AiAssistantUiState(),
            isBannerDismissedBySwipe = false,
            elapsedCallSeconds = 45L,
            isFullScreenCallUiVisible = com.example.telecom.CallStateManager.isInCallActivityVisible.value
        )
        assertTrue(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)
        assertEquals(
            com.example.telecom.SmartIslandVisualState.ACTIVE_CALL,
            com.example.telecom.CallSmartIslandManager.uiState.value.visualState
        )

        // 3. When the call ends, Smart Island transitions out to IDLE and hides
        com.example.telecom.CallSmartIslandManager.dismissCallEndedAnimationImmediate()
        org.junit.Assert.assertFalse(com.example.telecom.CallSmartIslandManager.uiState.value.isIslandVisible)
        com.example.telecom.CallStateManager.resetMinimizedStateForNewCall()
    }

    @Test
    fun verifyActiveCallAudioOutputSelectorBluetoothSpeakerEarpieceSwitching() {
        val btEndpoint = com.example.model.CallAudioEndpointItem(
            id = "endpoint_bt_1",
            name = "Galaxy Buds2 Pro",
            type = com.example.model.CallAudioEndpointType.BLUETOOTH,
            isActive = true
        )
        val speakerEndpoint = com.example.model.CallAudioEndpointItem(
            id = com.example.telecom.CallAudioEndpointRouter.ID_SPEAKER,
            name = "Speaker",
            type = com.example.model.CallAudioEndpointType.SPEAKER,
            isActive = false
        )
        val earpieceEndpoint = com.example.model.CallAudioEndpointItem(
            id = com.example.telecom.CallAudioEndpointRouter.ID_EARPIECE,
            name = "Phone earpiece",
            type = com.example.model.CallAudioEndpointType.EARPIECE,
            isActive = false
        )

        var shouldConfirmSwitch = true
        val selectedEndpoints = mutableListOf<com.example.model.CallAudioEndpointItem>()
        val uiStateHolder = androidx.compose.runtime.mutableStateOf(
            ActiveCallUiState(
                callId = "bt_call_1",
                number = "+91 97378 92210",
                displayName = "VIJAY",
                state = Call.STATE_ACTIVE,
                connectTimeMillis = System.currentTimeMillis() - 25_000L,
                isBluetoothAvailable = true,
                isBluetoothOn = true,
                isSpeakerOn = false,
                activeAudioEndpoint = btEndpoint,
                availableAudioEndpoints = listOf(btEndpoint, speakerEndpoint, earpieceEndpoint)
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveCallScreen(
                    callState = uiStateHolder.value,
                    initialNote = "",
                    onSaveNote = {},
                    onToggleMute = {},
                    onToggleSpeaker = {},
                    onToggleBluetooth = {},
                    onSelectAudioEndpoint = { chosen ->
                        selectedEndpoints.add(chosen)
                        if (shouldConfirmSwitch) {
                            // Simulate confirmed Telecom endpoint update
                            val currentList = uiStateHolder.value.availableAudioEndpoints.map {
                                it.copy(isActive = it.id == chosen.id)
                            }
                            val active = currentList.first { it.id == chosen.id }
                            uiStateHolder.value = uiStateHolder.value.copy(
                                activeAudioEndpoint = active,
                                availableAudioEndpoints = currentList,
                                isBluetoothOn = active.type == com.example.model.CallAudioEndpointType.BLUETOOTH,
                                isSpeakerOn = active.type == com.example.model.CallAudioEndpointType.SPEAKER
                            )
                        }
                    },
                    onToggleHold = {},
                    onAddCall = {},
                    onRequestVideo = {},
                    onRecordClick = {},
                    onPlayDtmf = {},
                    onEndCall = {},
                    onAcceptWaitingCall = {},
                    onDeclineWaitingCall = {},
                    onSwapCalls = {}
                )
            }
        }

        // Initial state: compact upper selector displays "Galaxy Buds2 Pro" and no bottom sheet exists
        composeTestRule.onNodeWithTag("call_audio_route_pill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Galaxy Buds2 Pro").assertIsDisplayed()
        composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("call_audio_output_selector_sheet"))
            .fetchSemanticsNodes().let { assertEquals(0, it.size) }

        // Tap 1 on compact upper selector: Bluetooth -> Speaker directly without opening any popup
        composeTestRule.onNodeWithTag("call_audio_route_pill").performClick()
        assertEquals(com.example.model.CallAudioEndpointType.SPEAKER, uiStateHolder.value.activeAudioEndpoint.type)
        composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("call_audio_output_selector_sheet"))
            .fetchSemanticsNodes().let { assertEquals(0, it.size) }

        // Tap 2 on compact upper selector: Speaker -> Phone earpiece directly
        composeTestRule.onNodeWithTag("call_audio_route_pill").performClick()
        assertEquals(com.example.model.CallAudioEndpointType.EARPIECE, uiStateHolder.value.activeAudioEndpoint.type)
        composeTestRule.onNodeWithText("Phone earpiece").assertIsDisplayed()

        // Tap 3 on compact upper selector: Phone earpiece -> Bluetooth ("Galaxy Buds2 Pro") directly
        composeTestRule.onNodeWithTag("call_audio_route_pill").performClick()
        assertEquals(com.example.model.CallAudioEndpointType.BLUETOOTH, uiStateHolder.value.activeAudioEndpoint.type)
        assertEquals("Galaxy Buds2 Pro", uiStateHolder.value.activeAudioEndpoint.name)
        composeTestRule.onNodeWithText("Galaxy Buds2 Pro").assertIsDisplayed()

        // If switching fails / is not confirmed by Telecom, active display remains unchanged and no popup opens
        shouldConfirmSwitch = false
        composeTestRule.onNodeWithTag("call_audio_route_pill").performClick()
        assertEquals(com.example.model.CallAudioEndpointType.BLUETOOTH, uiStateHolder.value.activeAudioEndpoint.type)
        composeTestRule.onNodeWithText("Galaxy Buds2 Pro").assertIsDisplayed()
        shouldConfirmSwitch = true

        // Bluetooth disconnects during a call -> falls back to Phone earpiece
        uiStateHolder.value = uiStateHolder.value.copy(
            isBluetoothAvailable = false,
            isBluetoothOn = false,
            isSpeakerOn = false,
            activeAudioEndpoint = earpieceEndpoint.copy(isActive = true),
            availableAudioEndpoints = listOf(speakerEndpoint, earpieceEndpoint.copy(isActive = true))
        )
        composeTestRule.onNodeWithText("Phone earpiece").assertIsDisplayed()

        // Bluetooth reconnects during a call -> tapping compact upper selector switches back to Bluetooth
        uiStateHolder.value = uiStateHolder.value.copy(
            isBluetoothAvailable = true,
            isBluetoothOn = false,
            isSpeakerOn = false,
            activeAudioEndpoint = earpieceEndpoint.copy(isActive = true),
            availableAudioEndpoints = listOf(btEndpoint.copy(isActive = false), speakerEndpoint, earpieceEndpoint.copy(isActive = true))
        )
        composeTestRule.onNodeWithTag("call_audio_route_pill").performClick()
        assertEquals(com.example.model.CallAudioEndpointType.BLUETOOTH, uiStateHolder.value.activeAudioEndpoint.type)
        assertEquals("Galaxy Buds2 Pro", uiStateHolder.value.activeAudioEndpoint.name)
        composeTestRule.onNodeWithText("Galaxy Buds2 Pro").assertIsDisplayed()
    }

    @Test
    fun verifyOngoingCallLocalRecordingInternalVaultAndPrivacyUi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.telecom.CallRecordingManager.initialize(context)
        com.example.telecom.CallRecordingManager.setStorageLocation(
            context,
            com.example.telecom.CallRecordingStorageLocation.APP_INTERNAL_PRIVATE
        )

        // 1. Verify secure internal directory creation under context.filesDir/CallRecordings with .nomedia protection
        val internalDir = com.example.telecom.CallRecordingManager.resolveRecordingDirectory(context)
        assertTrue(internalDir.exists())
        assertTrue(internalDir.absolutePath.startsWith(context.filesDir.absolutePath))
        assertEquals("CallRecordings", internalDir.name)
        val noMediaFile = java.io.File(internalDir, ".nomedia")
        assertTrue(noMediaFile.exists())

        // 2. Verify saving a local call recording file into the secure internal directory
        val savedEntry = requireNotNull(
            com.example.telecom.CallRecordingManager.saveVerifiedInternalRecordingFile(
                context = context,
                phoneNumber = "+91 98765 43210",
                displayName = "HET PATEL",
                isIncoming = false,
                durationSeconds = 18L,
                audioPayloadBytes = ByteArray(4096) { 0x1A }
            )
        )
        assertTrue(savedEntry.isStoredInSecureInternalDir)
        assertTrue(savedEntry.filePath.startsWith(context.filesDir.absolutePath))
        val savedFile = java.io.File(savedEntry.filePath)
        assertTrue(savedFile.exists())
        assertEquals(4096L, savedFile.length())

        // 3. Render ActiveCallScreen for an ongoing call and verify the top Recording status badge & In-Call Vault
        val callStateHolder = androidx.compose.runtime.mutableStateOf(
            ActiveCallUiState(
                callId = "rec_call_1",
                number = "+91 98765 43210",
                displayName = "HET PATEL",
                state = Call.STATE_ACTIVE,
                connectTimeMillis = System.currentTimeMillis() - 18_000L,
                isRecordingActive = true
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveCallScreen(
                    callState = callStateHolder.value,
                    initialNote = "",
                    onSaveNote = {},
                    onToggleMute = {},
                    onToggleSpeaker = {},
                    onToggleBluetooth = {},
                    onSelectAudioEndpoint = {},
                    onToggleHold = {},
                    onAddCall = {},
                    onRequestVideo = {},
                    onRecordClick = {},
                    onPlayDtmf = {},
                    onEndCall = {},
                    onAcceptWaitingCall = {},
                    onDeclineWaitingCall = {},
                    onSwapCalls = {}
                )
            }
        }

        // Verify subtle 'Recording' status badge and pulse animation at the top center of the ongoing call screen
        val badgeNode = composeTestRule.onNodeWithTag("top_call_recording_status_badge")
        badgeNode.assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_call_recording_pulse_icon", useUnmergedTree = true).assertIsDisplayed()

        val badgeBounds = badgeNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Badge should be near the top of the ongoing call screen", badgeBounds.left > 40f && badgeBounds.top < 220f)

        // Open More controls -> tap Recordings to open the In-Call Local Recording Vault
        composeTestRule.onNodeWithTag("call_btn_more").performClick()
        composeTestRule.onNodeWithTag("call_btn_recordings_vault").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_btn_recordings_vault").performClick()

        // Verify the In-Call Local Recording Vault & Android Privacy card are displayed
        composeTestRule.onNodeWithTag("in_call_recordings_vault_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("in_call_recording_privacy_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Secure Internal Directory · files/CallRecordings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("in_call_vault_recording_${savedEntry.id}").assertIsDisplayed()
    }

    @Test
    fun verifyOngoingCallContactPhotoDisplayWithCoil() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samplePhotoUri = "content://com.android.contacts/contacts/42/photo"

        // 1. Verify high-quality Coil ImageRequest configuration for circular contact avatars
        val imageRequest = com.example.ui.components.buildHighQualityContactPhotoRequest(
            context = context,
            photoUri = samplePhotoUri,
            targetPixelSize = 304
        )
        assertEquals(samplePhotoUri, imageRequest.data)
        assertEquals(coil.size.Precision.EXACT, imageRequest.precision)
        assertEquals(coil.size.Scale.FILL, imageRequest.scale)
        assertEquals(android.graphics.Bitmap.Config.ARGB_8888, imageRequest.bitmapConfig)

        // 2. Verify CallStateManager contact photo URI resolution and fallback
        val fallbackUri = android.net.Uri.parse(samplePhotoUri)
        val resolvedUri = com.example.telecom.CallStateManager.resolveContactPhotoUri(
            context = context,
            rawNumber = "+91 97730 07356",
            fallbackDetailsPhotoUri = fallbackUri
        )
        assertEquals(samplePhotoUri, resolvedUri)

        // 3. Render ActiveCallScreen and verify the circular contact avatar and Coil photo image node
        val callStateHolder = androidx.compose.runtime.mutableStateOf(
            ActiveCallUiState(
                callId = "photo_call_1",
                number = "+91 97730 07356",
                displayName = "RINKU BHABHI",
                photoUri = resolvedUri,
                locationLabel = "India",
                state = Call.STATE_ACTIVE,
                connectTimeMillis = System.currentTimeMillis() - 160_000L
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                ActiveCallScreen(
                    callState = callStateHolder.value,
                    initialNote = "",
                    onSaveNote = {},
                    onToggleMute = {},
                    onToggleSpeaker = {},
                    onToggleBluetooth = {},
                    onSelectAudioEndpoint = {},
                    onToggleHold = {},
                    onAddCall = {},
                    onRequestVideo = {},
                    onRecordClick = {},
                    onPlayDtmf = {},
                    onEndCall = {},
                    onAcceptWaitingCall = {},
                    onDeclineWaitingCall = {},
                    onSwapCalls = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ongoing_call_contact_avatar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ongoing_call_contact_photo_image", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("RINKU BHABHI photo", useUnmergedTree = true).assertIsDisplayed()
    }
}




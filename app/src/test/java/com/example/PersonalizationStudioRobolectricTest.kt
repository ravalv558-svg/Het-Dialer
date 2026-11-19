package com.example

import android.content.Context
import android.telecom.Call
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.PersonalizationManager
import com.example.model.ActiveCallUiState
import com.example.model.AppBackgroundStyle
import com.example.model.AppThemeMode
import com.example.model.ButtonShapeStyle
import com.example.model.CallDirection
import com.example.model.CallHistoryLayoutStyle
import com.example.model.CallLogItem
import com.example.model.ContactItem
import com.example.model.ContactSortOrder
import com.example.model.ContactFormData
import com.example.model.OngoingButtonVisualStyle
import com.example.model.PersonalizationCategory
import com.example.model.PersonalizationConfig
import com.example.model.StudioFontFamily
import com.example.ui.personalization.PersonalizationStudioHost
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.CallsTabScreen
import com.example.ui.screens.ContactsTabScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PersonalizationStudioRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("het_personalization_studio_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        PersonalizationManager.reloadFromStorage(context)
    }

    @Test
    fun dashboardRendersHeaderLivePreviewQuickActionsAndAllTenCategories() {
        composeTestRule.setContent {
            val applied by PersonalizationManager.appliedConfig.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = applied.themeMode, personalization = applied) {
                PersonalizationStudioHost(
                    onBackToApp = {},
                    onOpenSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("studio_header_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("HET Personalization Studio").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_header_subtitle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Make Every Call Yours.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_theme_preview_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_customize_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_action_save_theme").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_action_reset_default").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_action_apply_everywhere").assertIsDisplayed()

        PersonalizationCategory.entries.forEach { category ->
            composeTestRule.onNodeWithTag(category.testTag)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun navigatingAllTenCategoriesOpensDedicatedScreensAndReturnsToDashboard() {
        composeTestRule.setContent {
            val applied by PersonalizationManager.appliedConfig.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = applied.themeMode, personalization = applied) {
                PersonalizationStudioHost(
                    onBackToApp = {},
                    onOpenSettings = {}
                )
            }
        }

        PersonalizationCategory.entries.forEach { category ->
            composeTestRule.onNodeWithTag(category.testTag)
                .performScrollTo()
                .performClick()
            composeTestRule.onNodeWithTag("category_screen_title")
                .assertIsDisplayed()
                .assertTextContains(category.title)
            composeTestRule.onNodeWithTag("category_back_button").performClick()
            composeTestRule.onNodeWithTag("studio_header_title").assertIsDisplayed()
        }
    }

    @Test
    fun customColorPickerAndTypographyImmediatelyUpdateDraftAndLivePreview() {
        composeTestRule.setContent {
            val applied by PersonalizationManager.appliedConfig.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = applied.themeMode, personalization = applied) {
                PersonalizationStudioHost(
                    onBackToApp = {},
                    onOpenSettings = {}
                )
            }
        }

        // Open Home & Dialer -> open custom color picker for Primary Color -> set #00B4FF
        composeTestRule.onNodeWithTag(PersonalizationCategory.HOME_DIALER.testTag)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("swatch_primary_color")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithTag("color_picker_hex_input")
            .performTextReplacement("#00B4FF")
        composeTestRule.onNodeWithTag("color_picker_confirm_button").performClick()

        assertTrue(PersonalizationManager.draftConfig.value.useCustomColors)
        assertEquals(0xFF00B4FFL, PersonalizationManager.draftConfig.value.primaryColorArgb)

        // Return to dashboard and open Typography -> select Space Grotesk
        composeTestRule.onNodeWithTag("category_back_button").performClick()
        composeTestRule.onNodeWithTag(PersonalizationCategory.TYPOGRAPHY.testTag)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("font_option_space_grotesk")
            .performScrollTo()
            .performClick()

        assertEquals(
            StudioFontFamily.SPACE_GROTESK,
            PersonalizationManager.draftConfig.value.typography.fontFamily
        )
        composeTestRule.onNodeWithTag("typography_live_specimen_title").assertIsDisplayed()
    }

    @Test
    fun saveRenameApplyDeleteAndPersistenceAcrossReload() {
        composeTestRule.setContent {
            val applied by PersonalizationManager.appliedConfig.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = applied.themeMode, personalization = applied) {
                PersonalizationStudioHost(
                    onBackToApp = {},
                    onOpenSettings = {}
                )
            }
        }

        // Customize draft and save two custom themes via Quick Action dialog
        PersonalizationManager.updateDraft {
            it.copy(
                themeMode = AppThemeMode.DARK,
                useCustomColors = true,
                primaryColorArgb = 0xFF00C2FFL,
                homeDialer = it.homeDialer.copy(
                    backgroundStyle = AppBackgroundStyle.LIQUID_MESH,
                    dialpadGlassSheet = true
                )
            )
        }

        composeTestRule.onNodeWithTag("quick_action_save_theme").performClick()
        composeTestRule.onNodeWithTag("save_theme_name_input").performTextReplacement("Ocean Alpha")
        composeTestRule.onNodeWithTag("save_theme_confirm_btn").performClick()

        val savedFirst = PersonalizationManager.savedThemes.value.firstOrNull { it.name == "Ocean Alpha" }
        assertTrue(savedFirst != null)
        assertEquals(AppThemeMode.DARK, PersonalizationManager.appliedConfig.value.themeMode)

        // Navigate to Themes category, rename and verify persistence after simulated app restart
        composeTestRule.onNodeWithTag(PersonalizationCategory.THEMES.testTag)
            .performScrollTo()
            .performClick()

        val firstId = savedFirst!!.id
        composeTestRule.onNodeWithTag("rename_theme_btn_$firstId")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("rename_theme_input").performTextReplacement("Ocean Beta")
        composeTestRule.onNodeWithTag("rename_theme_confirm_btn").performClick()

        // Reload from SharedPreferences to verify persistence across process restarts
        val reloaded = PersonalizationManager.reloadFromStorage(context)
        assertEquals(AppThemeMode.DARK, reloaded.themeMode)
        assertEquals(0xFF00C2FFL, reloaded.primaryColorArgb)
        assertTrue(PersonalizationManager.savedThemes.value.any { it.id == firstId && it.name == "Ocean Beta" })

        // Delete saved theme
        composeTestRule.onNodeWithTag("delete_theme_btn_$firstId")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("delete_theme_confirm_btn").performClick()
        assertFalse(PersonalizationManager.savedThemes.value.any { it.id == firstId })
    }

    @Test
    fun resetToDefaultAndApplyEverywhereUpdatesAppScreens() {
        PersonalizationManager.updateDraft {
            it.copy(
                activeThemeName = "Frosted Studio",
                themeMode = AppThemeMode.DARK,
                useCustomColors = true,
                primaryColorArgb = 0xFF0A84FFL,
                homeDialer = it.homeDialer.copy(
                    backgroundStyle = AppBackgroundStyle.ARCTIC_CYAN,
                    dialpadButtonShape = ButtonShapeStyle.ROUNDED_SQUIRCLE,
                    dialpadGlassSheet = true
                ),
                callHistory = it.callHistory.copy(
                    layoutStyle = CallHistoryLayoutStyle.GLASS_CARDS
                ),
                ongoingCall = it.ongoingCall.copy(
                    buttonShape = ButtonShapeStyle.ROUNDED_SQUIRCLE,
                    buttonStyle = OngoingButtonVisualStyle.LIQUID_GLASS
                )
            )
        }
        PersonalizationManager.applyEverywhere(context)

        val sampleLog = CallLogItem(
            id = 101L,
            number = "+91 98765 43210",
            formattedNumber = "+91 98765 43210",
            cachedName = "Aarav Patel",
            contactId = 77L,
            timestamp = System.currentTimeMillis(),
            durationSeconds = 95L,
            direction = CallDirection.INCOMING,
            isHd = true,
            geocodedLocation = "Mumbai, India",
            simLabel = "SIM 1",
            phoneAccountId = "sim1"
        )
        val sampleContact = ContactItem(
            id = 77L,
            displayName = "Aarav Patel",
            phones = listOf(
                com.example.model.PhoneNumberEntry(
                    dataId = 1L,
                    number = "+91 98765 43210",
                    normalizedNumber = "919876543210",
                    typeLabel = "Mobile"
                )
            ),
            company = "HET Labs"
        )
        val sampleActiveCall = ActiveCallUiState(
            callId = "call_pers",
            number = "+91 98765 43210",
            displayName = "Aarav Patel",
            state = Call.STATE_ACTIVE,
            isIncoming = false
        )

        val screenStep = androidx.compose.runtime.mutableIntStateOf(0)
        composeTestRule.setContent {
            val applied by PersonalizationManager.appliedConfig.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = applied.themeMode, personalization = applied) {
                when (screenStep.intValue) {
                    0 -> CallsTabScreen(
                        callLogs = listOf(sampleLog),
                        contacts = listOf(sampleContact),
                        dialInput = "",
                        formattedDialInput = "",
                        isDialPadExpanded = true,
                        hasCallLogPermission = true,
                        isDefaultDialer = true,
                        onDialDigit = {},
                        onSetDialInput = {},
                        onBackspace = {},
                        onClearDialInput = {},
                        onToggleDialPad = {},
                        onPlaceCall = {},
                        onOpenCallDetails = {},
                        onOpenContactDetails = {},
                        onDeleteCallLogItem = {},
                        onClearAllCallHistory = {},
                        onCreateContactWithNumber = {},
                        onOpenSettings = {},
                        onOpenBlockedNumbers = {},
                        onRequestCallLogPermission = {},
                        onRequestDefaultDialerRole = {}
                    )
                    1 -> ActiveCallScreen(
                        callState = sampleActiveCall,
                        initialNote = "",
                        onSaveNote = {},
                        onToggleMute = {},
                        onToggleSpeaker = {},
                        onToggleHold = {},
                        onAddCall = {},
                        onRequestVideo = {},
                        onRecordClick = {},
                        onPlayDtmf = {},
                        onEndCall = {}
                    )
                    else -> ContactsTabScreen(
                        contacts = listOf(sampleContact),
                        recentlyViewedContacts = emptyList(),
                        duplicateGroups = emptyList(),
                        sortOrder = ContactSortOrder.NAME_ASC,
                        myProfile = ContactFormData(),
                        recycleBinCount = 0,
                        hasContactsPermission = true,
                        onChangeSortOrder = {},
                        onOpenContact = {},
                        onAddContactClick = {},
                        onSaveMyProfile = {},
                        onMergeDuplicateGroup = {},
                        onImportContactsVcf = {},
                        onExportContactsVcf = {},
                        onManualSyncContacts = {},
                        onOpenRecycleBin = {},
                        onOpenSettings = {},
                        onRequestContactsPermission = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("call_log_row_101").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialpad_sheet").assertIsDisplayed()

        // Verify ActiveCallScreen renders cleanly with applied Liquid Glass ongoing button config
        screenStep.intValue = 1
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("call_btn_mute").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_btn_end").assertIsDisplayed()

        // Verify ContactsTabScreen renders cleanly with applied backdrop & avatar shapes
        screenStep.intValue = 2
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("contact_row_77").assertIsDisplayed()

        // Reset to Default and confirm default values restored
        val resetConfig = PersonalizationManager.resetToDefault(context)
        assertEquals(PersonalizationConfig.DEFAULT_THEME_ID, resetConfig.activeThemeId)
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, resetConfig.themeMode)
        assertFalse(resetConfig.useCustomColors)
        assertEquals(AppBackgroundStyle.THEME_SOLID, resetConfig.homeDialer.backgroundStyle)
    }
}

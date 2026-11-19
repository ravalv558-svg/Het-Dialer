package com.example.model

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * The 10 primary customization categories on the HET Personalization Studio dashboard.
 */
enum class PersonalizationCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val testTag: String
) {
    HOME_DIALER(
        id = "home_dialer",
        title = "Home & Dialer",
        subtitle = "Wallpapers, dialpad keys & palette",
        testTag = "category_card_home_dialer"
    ),
    CONTACTS(
        id = "contacts",
        title = "Contacts",
        subtitle = "Avatar shapes, cards & profile style",
        testTag = "category_card_contacts"
    ),
    CALL_HISTORY(
        id = "call_history",
        title = "Call History",
        subtitle = "Row layouts, direction icons & badges",
        testTag = "category_card_call_history"
    ),
    INCOMING_CALL(
        id = "incoming_call",
        title = "Incoming Call",
        subtitle = "Ring screen, answer/decline & glow",
        testTag = "category_card_incoming_call"
    ),
    OUTGOING_CALL(
        id = "outgoing_call",
        title = "Outgoing Call",
        subtitle = "Dialing header, waves & backdrop",
        testTag = "category_card_outgoing_call"
    ),
    ONGOING_CALL(
        id = "ongoing_call",
        title = "Ongoing Call",
        subtitle = "Control buttons, styles & waveform",
        testTag = "category_card_ongoing_call"
    ),
    TYPOGRAPHY(
        id = "typography",
        title = "Typography",
        subtitle = "Fonts, text scale & letter spacing",
        testTag = "category_card_typography"
    ),
    ANIMATIONS(
        id = "animations",
        title = "Animations",
        subtitle = "Fluid motion styles & speed",
        testTag = "category_card_animations"
    ),
    VISUAL_EFFECTS(
        id = "visual_effects",
        title = "Visual Effects",
        subtitle = "Glassmorphism, blur, opacity & glow",
        testTag = "category_card_visual_effects"
    ),
    THEMES(
        id = "themes",
        title = "Themes",
        subtitle = "Light, dark, presets & saved themes",
        testTag = "category_card_themes"
    );

    companion object {
        fun fromId(id: String?): PersonalizationCategory? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

enum class AppBackgroundStyle(val label: String) {
    THEME_SOLID("Clean Theme Surface"),
    LIQUID_MESH("Liquid Glass Blue Mesh"),
    ARCTIC_CYAN("Arctic Cyan Frost"),
    DEEP_SAPPHIRE("Deep Sapphire Mist"),
    CUSTOM_PHOTO("Custom Wallpaper Photo");

    companion object {
        fun fromName(value: String?): AppBackgroundStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: THEME_SOLID
    }
}

enum class ButtonShapeStyle(val label: String, val cornerPercent: Int) {
    CIRCLE("Circle", 50),
    ROUNDED_SQUIRCLE("Squircle", 28),
    PILL("Soft Pill", 42),
    SOFT_SQUARE("Rounded Tile", 18);

    companion object {
        fun fromName(value: String?): ButtonShapeStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CIRCLE
    }
}

enum class DialpadButtonVisualStyle(val label: String) {
    CLEAN_SOLID("Clean Minimal"),
    LIQUID_GLASS("Liquid Glass"),
    OUTLINED_GLOW("Glass Rim Glow"),
    SOFT_TINT("Tinted Frost");

    companion object {
        fun fromName(value: String?): DialpadButtonVisualStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CLEAN_SOLID
    }
}

enum class ButtonSizeScale(val label: String, val multiplier: Float) {
    COMPACT("Compact", 0.88f),
    STANDARD("Standard", 1.0f),
    LARGE("Large", 1.12f);

    companion object {
        fun fromName(value: String?): ButtonSizeScale =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STANDARD
    }
}

enum class ContactHeaderStyle(val label: String) {
    CENTERED_CLASSIC("Classic Centered"),
    HERO_GLASS_CARD("Liquid Glass Hero"),
    COMPACT_BANNER("Minimal Header");

    companion object {
        fun fromName(value: String?): ContactHeaderStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CENTERED_CLASSIC
    }
}

enum class CallHistoryLayoutStyle(val label: String) {
    CLASSIC_LIST("Classic List"),
    GLASS_CARDS("Liquid Glass Cards"),
    COMPACT_DENSE("Compact View");

    companion object {
        fun fromName(value: String?): CallHistoryLayoutStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CLASSIC_LIST
    }
}

enum class CallDirectionIconStyle(val label: String) {
    CLASSIC_ARROWS("Classic Arrows"),
    BADGE_PILLS("Tinted Pills"),
    MINIMAL_DOTS("Minimal Dots");

    companion object {
        fun fromName(value: String?): CallDirectionIconStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CLASSIC_ARROWS
    }
}

enum class IncomingCallLayoutStyle(val label: String) {
    DEFAULT_COLOROS("Classic Full Screen"),
    LIQUID_GLASS_CARD("Liquid Glass Card"),
    MINIMAL_ZEN("Minimal Focused");

    companion object {
        fun fromName(value: String?): IncomingCallLayoutStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT_COLOROS
    }
}

enum class OutgoingCallLayoutStyle(val label: String) {
    CENTERED_WAVES("Balanced Header"),
    GLASS_HERO_CARD("Liquid Glass Panel"),
    COMPACT_HEADER("Minimal Dialing");

    companion object {
        fun fromName(value: String?): OutgoingCallLayoutStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CENTERED_WAVES
    }
}

enum class OngoingButtonVisualStyle(val label: String) {
    DEFAULT_PURPLE_GLASS("Original Glass"),
    LIQUID_GLASS("Liquid Blue Glass"),
    FROSTED_SOLID("Frosted Crystal"),
    NEON_RIM("Luminous Rim");

    companion object {
        fun fromName(value: String?): OngoingButtonVisualStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT_PURPLE_GLASS
    }
}

enum class WaveformAccentStyle(val label: String) {
    BLUE_CYAN("Ocean Blue-Cyan"),
    MATCH_THEME_ACCENT("Match Theme Accent"),
    AURORA_SPECTRUM("Aurora Ice");

    companion object {
        fun fromName(value: String?): WaveformAccentStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BLUE_CYAN
    }
}

enum class StudioFontFamily(val label: String, val previewTagline: String) {
    SYSTEM("System Default", "Clean native Android readability"),
    INTER("Inter", "Crisp, balanced geometric UI"),
    PLUS_JAKARTA_SANS("Plus Jakarta Sans", "Modern refined curves"),
    SPACE_GROTESK("Space Grotesk", "Distinctive tech precision"),
    OUTFIT("Outfit", "Friendly sculpted clarity"),
    PLAYFAIR_DISPLAY("Playfair Display", "Editorial serif elegance"),
    JETBRAINS_MONO("JetBrains Mono", "Structured tabular clarity");

    companion object {
        fun fromName(value: String?): StudioFontFamily =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

enum class AnimationMotionStyle(val label: String, val description: String) {
    FLUID_SPRING("Fluid Spring", "Bouncy Apple-like spring physics"),
    CRISP_EASE("Crisp Ease", "Snappy decelerated transitions"),
    GENTLE_FLOAT("Gentle Float", "Silky smooth ambient motion"),
    REDUCED_MINIMAL("Minimal Motion", "Subtle, low-motion accessibility");

    companion object {
        fun fromName(value: String?): AnimationMotionStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FLUID_SPRING
    }
}

data class HomeDialerConfig(
    val backgroundStyle: AppBackgroundStyle = AppBackgroundStyle.THEME_SOLID,
    val customBackgroundPhotoUri: String = "",
    val dialpadButtonShape: ButtonShapeStyle = ButtonShapeStyle.CIRCLE,
    val dialpadButtonStyle: DialpadButtonVisualStyle = DialpadButtonVisualStyle.CLEAN_SOLID,
    val dialpadButtonSize: ButtonSizeScale = ButtonSizeScale.STANDARD,
    val dialpadGlassSheet: Boolean = false
)

data class ContactsAppearanceConfig(
    val avatarShape: ButtonShapeStyle = ButtonShapeStyle.CIRCLE,
    val cardCornerRadiusDp: Float = 20f,
    val profileHeaderStyle: ContactHeaderStyle = ContactHeaderStyle.CENTERED_CLASSIC,
    val showCompanySubtitle: Boolean = true,
    val showAvatarGlowRing: Boolean = false
)

data class CallHistoryAppearanceConfig(
    val layoutStyle: CallHistoryLayoutStyle = CallHistoryLayoutStyle.CLASSIC_LIST,
    val directionIconStyle: CallDirectionIconStyle = CallDirectionIconStyle.CLASSIC_ARROWS,
    val showVoLteAndHdBadges: Boolean = true,
    val showCallDuration: Boolean = true,
    val rowCornerRadiusDp: Float = 18f,
    val missedCallColorArgb: Long = 0xFFE53935L
)

data class IncomingCallAppearanceConfig(
    val layoutStyle: IncomingCallLayoutStyle = IncomingCallLayoutStyle.DEFAULT_COLOROS,
    val buttonShape: ButtonShapeStyle = ButtonShapeStyle.CIRCLE,
    val buttonSize: ButtonSizeScale = ButtonSizeScale.STANDARD,
    val showPulseGlow: Boolean = true,
    val showSmsReplyPill: Boolean = true
)

data class OutgoingCallAppearanceConfig(
    val layoutStyle: OutgoingCallLayoutStyle = OutgoingCallLayoutStyle.CENTERED_WAVES,
    val waveIntensity: Float = 0.75f,
    val useGlassHeaderCard: Boolean = false
)

data class OngoingCallAppearanceConfig(
    val buttonShape: ButtonShapeStyle = ButtonShapeStyle.CIRCLE,
    val buttonStyle: OngoingButtonVisualStyle = OngoingButtonVisualStyle.DEFAULT_PURPLE_GLASS,
    val buttonSize: ButtonSizeScale = ButtonSizeScale.STANDARD,
    val showLiveWaveform: Boolean = true,
    val waveformAccentStyle: WaveformAccentStyle = WaveformAccentStyle.BLUE_CYAN
)

data class TypographyConfig(
    val fontFamily: StudioFontFamily = StudioFontFamily.SYSTEM,
    val fontScale: Float = 1.0f,
    val letterSpacingSp: Float = 0.0f
)

data class AnimationConfig(
    val style: AnimationMotionStyle = AnimationMotionStyle.FLUID_SPRING,
    val speedMultiplier: Float = 1.0f,
    val microInteractionsEnabled: Boolean = true
)

data class VisualEffectsConfig(
    val glassmorphismEnabled: Boolean = true,
    val blurIntensityDp: Float = 16f,
    val glassTransparency: Float = 0.24f,
    val highlightReflection: Float = 0.55f,
    val glowIntensity: Float = 0.35f
)

/**
 * Centralized single source of truth for all HET Phone & Personalization Studio appearance settings.
 */
data class PersonalizationConfig(
    val activeThemeId: String = DEFAULT_THEME_ID,
    val activeThemeName: String = DEFAULT_THEME_NAME,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val useCustomColors: Boolean = false,
    val primaryColorArgb: Long = 0xFF0A84FFL,
    val secondaryColorArgb: Long = 0xFF38BDF8L,
    val accentColorArgb: Long = 0xFF00D4FFL,
    val textColorArgb: Long = 0xFF141414L,
    val useCustomTextColor: Boolean = false,
    val homeDialer: HomeDialerConfig = HomeDialerConfig(),
    val contacts: ContactsAppearanceConfig = ContactsAppearanceConfig(),
    val callHistory: CallHistoryAppearanceConfig = CallHistoryAppearanceConfig(),
    val incomingCall: IncomingCallAppearanceConfig = IncomingCallAppearanceConfig(),
    val outgoingCall: OutgoingCallAppearanceConfig = OutgoingCallAppearanceConfig(),
    val ongoingCall: OngoingCallAppearanceConfig = OngoingCallAppearanceConfig(),
    val typography: TypographyConfig = TypographyConfig(),
    val animations: AnimationConfig = AnimationConfig(),
    val visualEffects: VisualEffectsConfig = VisualEffectsConfig()
) {
    val primaryColor: Color get() = Color(primaryColorArgb)
    val secondaryColor: Color get() = Color(secondaryColorArgb)
    val accentColor: Color get() = Color(accentColorArgb)
    val customTextColor: Color get() = Color(textColorArgb)
    val missedCallColor: Color get() = Color(callHistory.missedCallColorArgb)

    fun sanitize(): PersonalizationConfig {
        return copy(
            activeThemeId = activeThemeId.ifBlank { DEFAULT_THEME_ID },
            activeThemeName = activeThemeName.ifBlank { DEFAULT_THEME_NAME },
            contacts = contacts.copy(
                cardCornerRadiusDp = contacts.cardCornerRadiusDp.coerceIn(8f, 32f)
            ),
            callHistory = callHistory.copy(
                rowCornerRadiusDp = callHistory.rowCornerRadiusDp.coerceIn(8f, 28f)
            ),
            outgoingCall = outgoingCall.copy(
                waveIntensity = outgoingCall.waveIntensity.coerceIn(0f, 1f)
            ),
            typography = typography.copy(
                fontScale = typography.fontScale.coerceIn(0.85f, 1.25f),
                letterSpacingSp = typography.letterSpacingSp.coerceIn(-0.5f, 1.5f)
            ),
            animations = animations.copy(
                speedMultiplier = animations.speedMultiplier.coerceIn(0.5f, 1.75f)
            ),
            visualEffects = visualEffects.copy(
                blurIntensityDp = visualEffects.blurIntensityDp.coerceIn(0f, 28f),
                glassTransparency = visualEffects.glassTransparency.coerceIn(0.10f, 0.85f),
                highlightReflection = visualEffects.highlightReflection.coerceIn(0f, 1f),
                glowIntensity = visualEffects.glowIntensity.coerceIn(0f, 1f)
            )
        )
    }

    fun toJson(): JSONObject {
        val s = sanitize()
        return JSONObject().apply {
            put("activeThemeId", s.activeThemeId)
            put("activeThemeName", s.activeThemeName)
            put("themeMode", s.themeMode.name)
            put("useCustomColors", s.useCustomColors)
            put("primaryColorArgb", s.primaryColorArgb)
            put("secondaryColorArgb", s.secondaryColorArgb)
            put("accentColorArgb", s.accentColorArgb)
            put("textColorArgb", s.textColorArgb)
            put("useCustomTextColor", s.useCustomTextColor)

            put("homeDialer", JSONObject().apply {
                put("backgroundStyle", s.homeDialer.backgroundStyle.name)
                put("customBackgroundPhotoUri", s.homeDialer.customBackgroundPhotoUri)
                put("dialpadButtonShape", s.homeDialer.dialpadButtonShape.name)
                put("dialpadButtonStyle", s.homeDialer.dialpadButtonStyle.name)
                put("dialpadButtonSize", s.homeDialer.dialpadButtonSize.name)
                put("dialpadGlassSheet", s.homeDialer.dialpadGlassSheet)
            })

            put("contacts", JSONObject().apply {
                put("avatarShape", s.contacts.avatarShape.name)
                put("cardCornerRadiusDp", s.contacts.cardCornerRadiusDp.toDouble())
                put("profileHeaderStyle", s.contacts.profileHeaderStyle.name)
                put("showCompanySubtitle", s.contacts.showCompanySubtitle)
                put("showAvatarGlowRing", s.contacts.showAvatarGlowRing)
            })

            put("callHistory", JSONObject().apply {
                put("layoutStyle", s.callHistory.layoutStyle.name)
                put("directionIconStyle", s.callHistory.directionIconStyle.name)
                put("showVoLteAndHdBadges", s.callHistory.showVoLteAndHdBadges)
                put("showCallDuration", s.callHistory.showCallDuration)
                put("rowCornerRadiusDp", s.callHistory.rowCornerRadiusDp.toDouble())
                put("missedCallColorArgb", s.callHistory.missedCallColorArgb)
            })

            put("incomingCall", JSONObject().apply {
                put("layoutStyle", s.incomingCall.layoutStyle.name)
                put("buttonShape", s.incomingCall.buttonShape.name)
                put("buttonSize", s.incomingCall.buttonSize.name)
                put("showPulseGlow", s.incomingCall.showPulseGlow)
                put("showSmsReplyPill", s.incomingCall.showSmsReplyPill)
            })

            put("outgoingCall", JSONObject().apply {
                put("layoutStyle", s.outgoingCall.layoutStyle.name)
                put("waveIntensity", s.outgoingCall.waveIntensity.toDouble())
                put("useGlassHeaderCard", s.outgoingCall.useGlassHeaderCard)
            })

            put("ongoingCall", JSONObject().apply {
                put("buttonShape", s.ongoingCall.buttonShape.name)
                put("buttonStyle", s.ongoingCall.buttonStyle.name)
                put("buttonSize", s.ongoingCall.buttonSize.name)
                put("showLiveWaveform", s.ongoingCall.showLiveWaveform)
                put("waveformAccentStyle", s.ongoingCall.waveformAccentStyle.name)
            })

            put("typography", JSONObject().apply {
                put("fontFamily", s.typography.fontFamily.name)
                put("fontScale", s.typography.fontScale.toDouble())
                put("letterSpacingSp", s.typography.letterSpacingSp.toDouble())
            })

            put("animations", JSONObject().apply {
                put("style", s.animations.style.name)
                put("speedMultiplier", s.animations.speedMultiplier.toDouble())
                put("microInteractionsEnabled", s.animations.microInteractionsEnabled)
            })

            put("visualEffects", JSONObject().apply {
                put("glassmorphismEnabled", s.visualEffects.glassmorphismEnabled)
                put("blurIntensityDp", s.visualEffects.blurIntensityDp.toDouble())
                put("glassTransparency", s.visualEffects.glassTransparency.toDouble())
                put("highlightReflection", s.visualEffects.highlightReflection.toDouble())
                put("glowIntensity", s.visualEffects.glowIntensity.toDouble())
            })
        }
    }

    companion object {
        const val DEFAULT_THEME_ID = "het_default"
        const val DEFAULT_THEME_NAME = "HET Classic Default"

        fun fromJson(json: JSONObject?): PersonalizationConfig {
            if (json == null) return PersonalizationConfig()
            val defaults = PersonalizationConfig()
            val hd = json.optJSONObject("homeDialer")
            val ct = json.optJSONObject("contacts")
            val ch = json.optJSONObject("callHistory")
            val ic = json.optJSONObject("incomingCall")
            val oc = json.optJSONObject("outgoingCall")
            val og = json.optJSONObject("ongoingCall")
            val ty = json.optJSONObject("typography")
            val an = json.optJSONObject("animations")
            val ve = json.optJSONObject("visualEffects")

            val parsedThemeMode = runCatching {
                AppThemeMode.valueOf(json.optString("themeMode", defaults.themeMode.name))
            }.getOrDefault(defaults.themeMode)

            return PersonalizationConfig(
                activeThemeId = json.optString("activeThemeId", defaults.activeThemeId),
                activeThemeName = json.optString("activeThemeName", defaults.activeThemeName),
                themeMode = parsedThemeMode,
                useCustomColors = json.optBoolean("useCustomColors", defaults.useCustomColors),
                primaryColorArgb = json.optLong("primaryColorArgb", defaults.primaryColorArgb),
                secondaryColorArgb = json.optLong("secondaryColorArgb", defaults.secondaryColorArgb),
                accentColorArgb = json.optLong("accentColorArgb", defaults.accentColorArgb),
                textColorArgb = json.optLong("textColorArgb", defaults.textColorArgb),
                useCustomTextColor = json.optBoolean("useCustomTextColor", defaults.useCustomTextColor),
                homeDialer = HomeDialerConfig(
                    backgroundStyle = AppBackgroundStyle.fromName(hd?.optString("backgroundStyle")),
                    customBackgroundPhotoUri = hd?.optString("customBackgroundPhotoUri", "").orEmpty(),
                    dialpadButtonShape = ButtonShapeStyle.fromName(hd?.optString("dialpadButtonShape")),
                    dialpadButtonStyle = DialpadButtonVisualStyle.fromName(hd?.optString("dialpadButtonStyle")),
                    dialpadButtonSize = ButtonSizeScale.fromName(hd?.optString("dialpadButtonSize")),
                    dialpadGlassSheet = hd?.optBoolean("dialpadGlassSheet", false) ?: false
                ),
                contacts = ContactsAppearanceConfig(
                    avatarShape = ButtonShapeStyle.fromName(ct?.optString("avatarShape")),
                    cardCornerRadiusDp = (ct?.optDouble("cardCornerRadiusDp", 20.0) ?: 20.0).toFloat(),
                    profileHeaderStyle = ContactHeaderStyle.fromName(ct?.optString("profileHeaderStyle")),
                    showCompanySubtitle = ct?.optBoolean("showCompanySubtitle", true) ?: true,
                    showAvatarGlowRing = ct?.optBoolean("showAvatarGlowRing", false) ?: false
                ),
                callHistory = CallHistoryAppearanceConfig(
                    layoutStyle = CallHistoryLayoutStyle.fromName(ch?.optString("layoutStyle")),
                    directionIconStyle = CallDirectionIconStyle.fromName(ch?.optString("directionIconStyle")),
                    showVoLteAndHdBadges = ch?.optBoolean("showVoLteAndHdBadges", true) ?: true,
                    showCallDuration = ch?.optBoolean("showCallDuration", true) ?: true,
                    rowCornerRadiusDp = (ch?.optDouble("rowCornerRadiusDp", 18.0) ?: 18.0).toFloat(),
                    missedCallColorArgb = ch?.optLong("missedCallColorArgb", 0xFFE53935L) ?: 0xFFE53935L
                ),
                incomingCall = IncomingCallAppearanceConfig(
                    layoutStyle = IncomingCallLayoutStyle.fromName(ic?.optString("layoutStyle")),
                    buttonShape = ButtonShapeStyle.fromName(ic?.optString("buttonShape")),
                    buttonSize = ButtonSizeScale.fromName(ic?.optString("buttonSize")),
                    showPulseGlow = ic?.optBoolean("showPulseGlow", true) ?: true,
                    showSmsReplyPill = ic?.optBoolean("showSmsReplyPill", true) ?: true
                ),
                outgoingCall = OutgoingCallAppearanceConfig(
                    layoutStyle = OutgoingCallLayoutStyle.fromName(oc?.optString("layoutStyle")),
                    waveIntensity = (oc?.optDouble("waveIntensity", 0.75) ?: 0.75).toFloat(),
                    useGlassHeaderCard = oc?.optBoolean("useGlassHeaderCard", false) ?: false
                ),
                ongoingCall = OngoingCallAppearanceConfig(
                    buttonShape = ButtonShapeStyle.fromName(og?.optString("buttonShape")),
                    buttonStyle = OngoingButtonVisualStyle.fromName(og?.optString("buttonStyle")),
                    buttonSize = ButtonSizeScale.fromName(og?.optString("buttonSize")),
                    showLiveWaveform = og?.optBoolean("showLiveWaveform", true) ?: true,
                    waveformAccentStyle = WaveformAccentStyle.fromName(og?.optString("waveformAccentStyle"))
                ),
                typography = TypographyConfig(
                    fontFamily = StudioFontFamily.fromName(ty?.optString("fontFamily")),
                    fontScale = (ty?.optDouble("fontScale", 1.0) ?: 1.0).toFloat(),
                    letterSpacingSp = (ty?.optDouble("letterSpacingSp", 0.0) ?: 0.0).toFloat()
                ),
                animations = AnimationConfig(
                    style = AnimationMotionStyle.fromName(an?.optString("style")),
                    speedMultiplier = (an?.optDouble("speedMultiplier", 1.0) ?: 1.0).toFloat(),
                    microInteractionsEnabled = an?.optBoolean("microInteractionsEnabled", true) ?: true
                ),
                visualEffects = VisualEffectsConfig(
                    glassmorphismEnabled = ve?.optBoolean("glassmorphismEnabled", true) ?: true,
                    blurIntensityDp = (ve?.optDouble("blurIntensityDp", 16.0) ?: 16.0).toFloat(),
                    glassTransparency = (ve?.optDouble("glassTransparency", 0.24) ?: 0.24).toFloat(),
                    highlightReflection = (ve?.optDouble("highlightReflection", 0.55) ?: 0.55).toFloat(),
                    glowIntensity = (ve?.optDouble("glowIntensity", 0.35) ?: 0.35).toFloat()
                )
            ).sanitize()
        }
    }
}

data class SavedCustomTheme(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isBuiltInPreset: Boolean = false,
    val config: PersonalizationConfig
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("isBuiltInPreset", isBuiltInPreset)
        put("config", config.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject?): SavedCustomTheme? {
            if (json == null) return null
            val id = json.optString("id").trim()
            val name = json.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) return null
            val config = PersonalizationConfig.fromJson(json.optJSONObject("config"))
                .copy(activeThemeId = id, activeThemeName = name)
            return SavedCustomTheme(
                id = id,
                name = name,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                isBuiltInPreset = json.optBoolean("isBuiltInPreset", false),
                config = config
            )
        }

        fun listToJsonString(list: List<SavedCustomTheme>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJsonString(raw: String?): List<SavedCustomTheme> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        fromJson(arr.optJSONObject(i))?.let { add(it) }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

/**
 * Built-in curated Liquid Glass themes that users can preview, customize, and apply immediately.
 */
object BuiltInPersonalizationPresets {
    val all: List<SavedCustomTheme> = listOf(
        SavedCustomTheme(
            id = "preset_liquid_ocean",
            name = "Liquid Ocean Glass",
            createdAt = 1L,
            updatedAt = 1L,
            isBuiltInPreset = true,
            config = PersonalizationConfig(
                activeThemeId = "preset_liquid_ocean",
                activeThemeName = "Liquid Ocean Glass",
                themeMode = AppThemeMode.DARK,
                useCustomColors = true,
                primaryColorArgb = 0xFF0A84FFL,
                secondaryColorArgb = 0xFF38BDF8L,
                accentColorArgb = 0xFF00E5FFL,
                homeDialer = HomeDialerConfig(
                    backgroundStyle = AppBackgroundStyle.LIQUID_MESH,
                    dialpadButtonShape = ButtonShapeStyle.ROUNDED_SQUIRCLE,
                    dialpadButtonStyle = DialpadButtonVisualStyle.LIQUID_GLASS,
                    dialpadGlassSheet = true
                ),
                contacts = ContactsAppearanceConfig(
                    avatarShape = ButtonShapeStyle.ROUNDED_SQUIRCLE,
                    cardCornerRadiusDp = 22f,
                    profileHeaderStyle = ContactHeaderStyle.HERO_GLASS_CARD,
                    showAvatarGlowRing = true
                ),
                callHistory = CallHistoryAppearanceConfig(
                    layoutStyle = CallHistoryLayoutStyle.GLASS_CARDS,
                    directionIconStyle = CallDirectionIconStyle.BADGE_PILLS
                ),
                incomingCall = IncomingCallAppearanceConfig(
                    layoutStyle = IncomingCallLayoutStyle.LIQUID_GLASS_CARD,
                    buttonShape = ButtonShapeStyle.ROUNDED_SQUIRCLE
                ),
                outgoingCall = OutgoingCallAppearanceConfig(
                    layoutStyle = OutgoingCallLayoutStyle.GLASS_HERO_CARD,
                    useGlassHeaderCard = true
                ),
                ongoingCall = OngoingCallAppearanceConfig(
                    buttonShape = ButtonShapeStyle.ROUNDED_SQUIRCLE,
                    buttonStyle = OngoingButtonVisualStyle.LIQUID_GLASS,
                    waveformAccentStyle = WaveformAccentStyle.BLUE_CYAN
                ),
                typography = TypographyConfig(
                    fontFamily = StudioFontFamily.INTER
                ),
                visualEffects = VisualEffectsConfig(
                    glassmorphismEnabled = true,
                    blurIntensityDp = 20f,
                    glassTransparency = 0.26f,
                    highlightReflection = 0.68f,
                    glowIntensity = 0.48f
                )
            )
        ),
        SavedCustomTheme(
            id = "preset_arctic_frost_light",
            name = "Arctic Frost Light",
            createdAt = 2L,
            updatedAt = 2L,
            isBuiltInPreset = true,
            config = PersonalizationConfig(
                activeThemeId = "preset_arctic_frost_light",
                activeThemeName = "Arctic Frost Light",
                themeMode = AppThemeMode.LIGHT,
                useCustomColors = true,
                primaryColorArgb = 0xFF007AFFL,
                secondaryColorArgb = 0xFF0EA5E9L,
                accentColorArgb = 0xFF06B6D4L,
                homeDialer = HomeDialerConfig(
                    backgroundStyle = AppBackgroundStyle.ARCTIC_CYAN,
                    dialpadButtonShape = ButtonShapeStyle.CIRCLE,
                    dialpadButtonStyle = DialpadButtonVisualStyle.SOFT_TINT,
                    dialpadGlassSheet = true
                ),
                contacts = ContactsAppearanceConfig(
                    avatarShape = ButtonShapeStyle.CIRCLE,
                    cardCornerRadiusDp = 24f,
                    profileHeaderStyle = ContactHeaderStyle.HERO_GLASS_CARD
                ),
                callHistory = CallHistoryAppearanceConfig(
                    layoutStyle = CallHistoryLayoutStyle.GLASS_CARDS,
                    directionIconStyle = CallDirectionIconStyle.BADGE_PILLS
                ),
                ongoingCall = OngoingCallAppearanceConfig(
                    buttonStyle = OngoingButtonVisualStyle.FROSTED_SOLID,
                    waveformAccentStyle = WaveformAccentStyle.AURORA_SPECTRUM
                ),
                typography = TypographyConfig(
                    fontFamily = StudioFontFamily.PLUS_JAKARTA_SANS
                ),
                visualEffects = VisualEffectsConfig(
                    glassmorphismEnabled = true,
                    blurIntensityDp = 18f,
                    glassTransparency = 0.34f,
                    highlightReflection = 0.72f,
                    glowIntensity = 0.30f
                )
            )
        ),
        SavedCustomTheme(
            id = "preset_midnight_sapphire",
            name = "Midnight Sapphire",
            createdAt = 3L,
            updatedAt = 3L,
            isBuiltInPreset = true,
            config = PersonalizationConfig(
                activeThemeId = "preset_midnight_sapphire",
                activeThemeName = "Midnight Sapphire",
                themeMode = AppThemeMode.DARK,
                useCustomColors = true,
                primaryColorArgb = 0xFF2563EBL,
                secondaryColorArgb = 0xFF22D3EEL,
                accentColorArgb = 0xFF38BDF8L,
                homeDialer = HomeDialerConfig(
                    backgroundStyle = AppBackgroundStyle.DEEP_SAPPHIRE,
                    dialpadButtonShape = ButtonShapeStyle.PILL,
                    dialpadButtonStyle = DialpadButtonVisualStyle.OUTLINED_GLOW,
                    dialpadGlassSheet = true
                ),
                contacts = ContactsAppearanceConfig(
                    avatarShape = ButtonShapeStyle.CIRCLE,
                    cardCornerRadiusDp = 20f,
                    profileHeaderStyle = ContactHeaderStyle.HERO_GLASS_CARD,
                    showAvatarGlowRing = true
                ),
                ongoingCall = OngoingCallAppearanceConfig(
                    buttonShape = ButtonShapeStyle.ROUNDED_SQUIRCLE,
                    buttonStyle = OngoingButtonVisualStyle.NEON_RIM,
                    waveformAccentStyle = WaveformAccentStyle.MATCH_THEME_ACCENT
                ),
                typography = TypographyConfig(
                    fontFamily = StudioFontFamily.SPACE_GROTESK,
                    letterSpacingSp = 0.25f
                )
            )
        ),
        SavedCustomTheme(
            id = "preset_het_classic",
            name = PersonalizationConfig.DEFAULT_THEME_NAME,
            createdAt = 4L,
            updatedAt = 4L,
            isBuiltInPreset = true,
            config = PersonalizationConfig()
        )
    )
}

package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.model.AnimationConfig
import com.example.model.AnimationMotionStyle
import com.example.model.AppBackgroundStyle
import com.example.model.AppThemeMode
import com.example.model.BuiltInPersonalizationPresets
import com.example.model.ButtonShapeStyle
import com.example.model.PersonalizationConfig
import com.example.model.SavedCustomTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Centralized single source of truth managing HET Personalization Studio state,
 * theme persistence, saved custom themes, live draft preview editing, and Apply Everywhere sync.
 */
object PersonalizationManager {

    private const val PREFS_NAME = "het_personalization_studio_v1"
    private const val KEY_APPLIED_CONFIG = "applied_config_json"
    private const val KEY_SAVED_THEMES = "saved_themes_json"
    private const val LEGACY_DIALER_PREFS = "color_dialer_prefs"
    private const val LEGACY_THEME_MODE_KEY = "app_theme_mode"
    private const val STUDIO_BG_DIR = "studio_backgrounds"

    @Volatile
    private var isInitialized = false

    private val _appliedConfig = MutableStateFlow(PersonalizationConfig())
    val appliedConfig: StateFlow<PersonalizationConfig> = _appliedConfig.asStateFlow()

    private val _draftConfig = MutableStateFlow(PersonalizationConfig())
    val draftConfig: StateFlow<PersonalizationConfig> = _draftConfig.asStateFlow()

    private val _savedThemes = MutableStateFlow<List<SavedCustomTheme>>(emptyList())
    val savedThemes: StateFlow<List<SavedCustomTheme>> = _savedThemes.asStateFlow()

    fun ensureInitialized(context: Context): PersonalizationConfig {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    reloadFromStorage(context.applicationContext)
                }
            }
        }
        return _appliedConfig.value
    }

    /**
     * Reloads persisted applied config and saved themes from SharedPreferences.
     * Safe against malformed JSON or deleted custom background files.
     */
    fun reloadFromStorage(context: Context): PersonalizationConfig {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawApplied = prefs.getString(KEY_APPLIED_CONFIG, null)

        var loadedConfig = if (!rawApplied.isNullOrBlank()) {
            runCatching {
                PersonalizationConfig.fromJson(JSONObject(rawApplied))
            }.getOrDefault(PersonalizationConfig())
        } else {
            // Migrate existing AppThemeMode on first run
            val legacyModeRaw = appContext.getSharedPreferences(LEGACY_DIALER_PREFS, Context.MODE_PRIVATE)
                .getString(LEGACY_THEME_MODE_KEY, AppThemeMode.FOLLOW_SYSTEM.name)
            val legacyMode = runCatching {
                AppThemeMode.valueOf(legacyModeRaw ?: AppThemeMode.FOLLOW_SYSTEM.name)
            }.getOrDefault(AppThemeMode.FOLLOW_SYSTEM)
            PersonalizationConfig(themeMode = legacyMode)
        }

        // Validate custom background file URI if set
        val customUri = loadedConfig.homeDialer.customBackgroundPhotoUri
        if (loadedConfig.homeDialer.backgroundStyle == AppBackgroundStyle.CUSTOM_PHOTO &&
            (customUri.isBlank() || !CallScreenWallpaperManager.isMediaUriAccessible(appContext, customUri))
        ) {
            loadedConfig = loadedConfig.copy(
                homeDialer = loadedConfig.homeDialer.copy(
                    backgroundStyle = AppBackgroundStyle.THEME_SOLID,
                    customBackgroundPhotoUri = ""
                )
            )
        }

        val loadedSavedThemes = SavedCustomTheme.listFromJsonString(
            prefs.getString(KEY_SAVED_THEMES, null)
        )

        val sanitized = loadedConfig.sanitize()
        _appliedConfig.value = sanitized
        _draftConfig.value = sanitized
        _savedThemes.value = loadedSavedThemes
        isInitialized = true
        return sanitized
    }

    fun syncDraftToApplied() {
        _draftConfig.value = _appliedConfig.value
    }

    fun updateDraft(transform: (PersonalizationConfig) -> PersonalizationConfig) {
        _draftConfig.value = transform(_draftConfig.value).sanitize()
    }

    fun updateThemeModeEverywhere(context: Context, mode: AppThemeMode): PersonalizationConfig {
        ensureInitialized(context)
        val updatedDraft = _draftConfig.value.copy(themeMode = mode)
        _draftConfig.value = updatedDraft
        val updatedApplied = _appliedConfig.value.copy(themeMode = mode)
        return applyEverywhere(context, updatedApplied)
    }

    /**
     * Persists and applies the given [config] across all supported HET Phone screens
     * (`MainActivity`, `InCallActivity`, `CallsTabScreen`, `ContactsTabScreen`, `SettingsScreen`, etc.).
     */
    fun applyEverywhere(
        context: Context,
        config: PersonalizationConfig = _draftConfig.value
    ): PersonalizationConfig {
        val appContext = context.applicationContext
        val sanitized = config.sanitize()

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APPLIED_CONFIG, sanitized.toJson().toString())
            .commit()

        appContext.getSharedPreferences(LEGACY_DIALER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LEGACY_THEME_MODE_KEY, sanitized.themeMode.name)
            .commit()

        _appliedConfig.value = sanitized
        _draftConfig.value = sanitized
        isInitialized = true
        return sanitized
    }

    /**
     * Resets both active and draft studio customization back to clean HET Phone defaults
     * and resets Call Screen Wallpaper back to System Default.
     */
    fun resetToDefault(context: Context): PersonalizationConfig {
        val appContext = context.applicationContext
        val defaultConfig = PersonalizationConfig()
        CallScreenWallpaperManager.resetToDefault(appContext)
        return applyEverywhere(appContext, defaultConfig)
    }

    /**
     * Saves the provided [config] as a named custom theme in persistent storage.
     */
    fun saveCustomTheme(
        context: Context,
        themeName: String,
        config: PersonalizationConfig = _draftConfig.value
    ): Result<SavedCustomTheme> {
        val trimmed = themeName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter a valid theme name."))
        }
        val appContext = context.applicationContext
        ensureInitialized(appContext)

        val now = System.currentTimeMillis()
        val id = "custom_${UUID.randomUUID().toString().take(8)}"
        val resolvedConfig = config.sanitize().copy(
            activeThemeId = id,
            activeThemeName = trimmed
        )
        val entry = SavedCustomTheme(
            id = id,
            name = trimmed,
            createdAt = now,
            updatedAt = now,
            isBuiltInPreset = false,
            config = resolvedConfig
        )

        val nextList = listOf(entry) + _savedThemes.value
        persistSavedThemesList(appContext, nextList)
        _savedThemes.value = nextList
        _draftConfig.value = resolvedConfig
        return Result.success(entry)
    }

    fun renameCustomTheme(context: Context, themeId: String, newName: String): Result<SavedCustomTheme> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Theme name cannot be empty."))
        }
        val appContext = context.applicationContext
        ensureInitialized(appContext)

        val currentList = _savedThemes.value
        val existing = currentList.firstOrNull { it.id == themeId }
            ?: return Result.failure(IllegalArgumentException("Saved theme not found."))

        val updatedEntry = existing.copy(
            name = trimmed,
            updatedAt = System.currentTimeMillis(),
            config = existing.config.copy(activeThemeName = trimmed)
        )
        val nextList = currentList.map { if (it.id == themeId) updatedEntry else it }
        persistSavedThemesList(appContext, nextList)
        _savedThemes.value = nextList

        if (_appliedConfig.value.activeThemeId == themeId) {
            applyEverywhere(appContext, _appliedConfig.value.copy(activeThemeName = trimmed))
        }
        if (_draftConfig.value.activeThemeId == themeId) {
            _draftConfig.value = _draftConfig.value.copy(activeThemeName = trimmed)
        }
        return Result.success(updatedEntry)
    }

    fun deleteCustomTheme(context: Context, themeId: String): Boolean {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        val currentList = _savedThemes.value
        val nextList = currentList.filterNot { it.id == themeId }
        if (nextList.size == currentList.size) return false

        persistSavedThemesList(appContext, nextList)
        _savedThemes.value = nextList
        return true
    }

    fun applySavedOrPresetTheme(context: Context, themeId: String): Result<PersonalizationConfig> {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        val target = _savedThemes.value.firstOrNull { it.id == themeId }
            ?: BuiltInPersonalizationPresets.all.firstOrNull { it.id == themeId }
            ?: return Result.failure(IllegalArgumentException("Theme not found."))

        val applied = applyEverywhere(
            appContext,
            target.config.copy(
                activeThemeId = target.id,
                activeThemeName = target.name
            )
        )
        return Result.success(applied)
    }

    fun previewSavedOrPresetTheme(theme: SavedCustomTheme) {
        _draftConfig.value = theme.config.copy(
            activeThemeId = theme.id,
            activeThemeName = theme.name
        ).sanitize()
    }

    private fun persistSavedThemesList(context: Context, themes: List<SavedCustomTheme>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_THEMES, SavedCustomTheme.listToJsonString(themes))
            .commit()
    }

    /**
     * Copies a picked photo from Photo Picker into app-private storage so app/dialer backgrounds persist reliably.
     */
    fun importAppBackgroundPhotoUri(context: Context, sourceUri: Uri): Result<String> {
        val appContext = context.applicationContext
        try {
            appContext.contentResolver.takePersistableUriPermission(
                sourceUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {
        }

        val mime = runCatching {
            appContext.contentResolver.getType(sourceUri)?.lowercase(Locale.US)
        }.getOrNull()

        if (mime != null && mime.startsWith("video/")) {
            return Result.failure(IllegalArgumentException("Please select an image file for app background."))
        }

        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        return try {
            val dir = File(appContext.filesDir, STUDIO_BG_DIR).apply { mkdirs() }
            val targetFile = File(dir, "studio_app_bg.$ext")
            val copied = appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output -> input.copyTo(output) }
            } ?: 0L

            if (copied > 0L && targetFile.exists()) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(targetFile.absolutePath, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    targetFile.delete()
                    return Result.failure(IllegalArgumentException("Selected file is not a valid image."))
                }
                Result.success(Uri.fromFile(targetFile).toString())
            } else {
                Result.success(sourceUri.toString())
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(e.localizedMessage ?: "Failed to import background image."))
        }
    }

    fun shapeFor(style: ButtonShapeStyle, sizeDp: Float = 56f): Shape {
        return when (style) {
            ButtonShapeStyle.CIRCLE -> CircleShape
            ButtonShapeStyle.ROUNDED_SQUIRCLE -> RoundedCornerShape((sizeDp * 0.30f).dp)
            ButtonShapeStyle.PILL -> RoundedCornerShape(percent = 44)
            ButtonShapeStyle.SOFT_SQUARE -> RoundedCornerShape((sizeDp * 0.18f).dp)
        }
    }

    fun resolveDurationMs(baseMs: Int, anim: AnimationConfig): Int {
        if (anim.style == AnimationMotionStyle.REDUCED_MINIMAL) {
            return (baseMs * 0.25f).roundToInt().coerceAtLeast(1)
        }
        val speed = anim.speedMultiplier.coerceIn(0.5f, 1.75f)
        return (baseMs / speed).roundToInt().coerceIn(50, 1400)
    }

    fun <T> resolveAnimationSpec(baseMs: Int, anim: AnimationConfig): FiniteAnimationSpec<T> {
        return when (anim.style) {
            AnimationMotionStyle.FLUID_SPRING -> {
                val stiffness = Spring.StiffnessMediumLow * anim.speedMultiplier.coerceIn(0.5f, 1.75f)
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = stiffness
                )
            }
            AnimationMotionStyle.CRISP_EASE -> tween(
                durationMillis = resolveDurationMs(baseMs, anim),
                easing = LinearOutSlowInEasing
            )
            AnimationMotionStyle.GENTLE_FLOAT -> tween(
                durationMillis = resolveDurationMs((baseMs * 1.25f).roundToInt(), anim),
                easing = FastOutSlowInEasing
            )
            AnimationMotionStyle.REDUCED_MINIMAL -> tween(
                durationMillis = 1,
                easing = LinearOutSlowInEasing
            )
        }
    }
}

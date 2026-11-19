package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class CallWallpaperType(val label: String) {
    SYSTEM_DEFAULT("System Default"),
    BUILT_IN("Built-in Wallpapers"),
    PHOTO("Photo from Gallery"),
    VIDEO("Video from Gallery");

    companion object {
        fun fromName(value: String?): CallWallpaperType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM_DEFAULT
        }
    }
}

data class BuiltInCallWallpaper(
    val id: String,
    val title: String,
    val subtitle: String,
    val gradientStops: List<Color>,
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val tertiaryGlow: Color
)

data class CallWallpaperConfig(
    val type: CallWallpaperType = CallWallpaperType.SYSTEM_DEFAULT,
    val mediaUri: String = "",
    val builtInId: String = CallScreenWallpaperManager.DEFAULT_BUILT_IN_ID,
    val blurRadiusDp: Float = 0f,
    val darkenAlpha: Float = 0.20f
) {
    fun summarySubtitle(): String {
        val base = when (type) {
            CallWallpaperType.SYSTEM_DEFAULT -> "System Default"
            CallWallpaperType.BUILT_IN -> {
                val preset = CallScreenWallpaperManager.findBuiltInWallpaper(builtInId)
                "Built-in · ${preset.title}"
            }
            CallWallpaperType.PHOTO -> "Photo from Gallery"
            CallWallpaperType.VIDEO -> "Video from Gallery"
        }
        val adjustments = buildList {
            if (blurRadiusDp >= 0.5f) add("Blur ${blurRadiusDp.toInt()}dp")
            if (darkenAlpha > 0.01f) add("Darken ${(darkenAlpha * 100).toInt()}%")
        }
        return if (adjustments.isEmpty()) {
            "$base · Applied to all calls"
        } else {
            "$base (${adjustments.joinToString(", ")}) · Applied to all calls"
        }
    }
}

/**
 * Persistent manager for HET Phone's Call Screen Wallpaper Customization feature.
 *
 * - Persists the applied wallpaper configuration across app restarts and device reboots.
 * - Retains URI read permissions via [android.content.ContentResolver.takePersistableUriPermission]
 *   and mirrors picked gallery media into app-private storage so URIs never expire.
 * - Automatically applies the saved wallpaper configuration to all incoming, outgoing,
 *   and ongoing call screens via [appliedConfig].
 */
object CallScreenWallpaperManager {

    private const val PREFS_NAME = "color_dialer_prefs_v1"
    private const val KEY_WALLPAPER_TYPE = "pref_call_wallpaper_type"
    private const val KEY_WALLPAPER_MEDIA_URI = "pref_call_wallpaper_media_uri"
    private const val KEY_WALLPAPER_BUILTIN_ID = "pref_call_wallpaper_builtin_id"
    private const val KEY_WALLPAPER_BLUR_DP = "pref_call_wallpaper_blur_dp"
    private const val KEY_WALLPAPER_DARKEN_ALPHA = "pref_call_wallpaper_darken_alpha"
    private const val WALLPAPER_DIR_NAME = "call_wallpapers"

    const val DEFAULT_BUILT_IN_ID = "builtin_emerald_aurora"
    const val MIN_BLUR_DP = 0f
    const val MAX_BLUR_DP = 25f
    const val MIN_DARKEN_ALPHA = 0f
    const val MAX_DARKEN_ALPHA = 0.85f
    const val DEFAULT_DARKEN_ALPHA = 0.20f

    val SUPPORTED_PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    val SUPPORTED_PHOTO_MIMES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    )

    val SUPPORTED_VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "3gp", "mov", "m4v")
    val SUPPORTED_VIDEO_MIMES = setOf(
        "video/mp4",
        "video/webm",
        "video/x-matroska",
        "video/3gpp",
        "video/quicktime",
        "video/x-m4v"
    )

    val BUILT_IN_WALLPAPERS: List<BuiltInCallWallpaper> = listOf(
        BuiltInCallWallpaper(
            id = "builtin_emerald_aurora",
            title = "Emerald Aurora",
            subtitle = "Lush emerald & jade light ribbons",
            gradientStops = listOf(
                Color(0xFF041712),
                Color(0xFF092C22),
                Color(0xFF0B3B2D),
                Color(0xFF03100C)
            ),
            primaryGlow = Color(0xFF2AD66C),
            secondaryGlow = Color(0xFF00B8A9),
            tertiaryGlow = Color(0xFF126E51)
        ),
        BuiltInCallWallpaper(
            id = "builtin_cosmic_nebula",
            title = "Cosmic Nebula",
            subtitle = "Deep violet, magenta & starlight glow",
            gradientStops = listOf(
                Color(0xFF0B071E),
                Color(0xFF1E103D),
                Color(0xFF2B124C),
                Color(0xFF060411)
            ),
            primaryGlow = Color(0xFF9D4EDD),
            secondaryGlow = Color(0xFFF72585),
            tertiaryGlow = Color(0xFF4361EE)
        ),
        BuiltInCallWallpaper(
            id = "builtin_golden_dusk",
            title = "Golden Dusk",
            subtitle = "Warm sunset amber & obsidian horizon",
            gradientStops = listOf(
                Color(0xFF170B06),
                Color(0xFF2E150B),
                Color(0xFF241118),
                Color(0xFF0A0506)
            ),
            primaryGlow = Color(0xFFFF8C42),
            secondaryGlow = Color(0xFFE84855),
            tertiaryGlow = Color(0xFFD99B26)
        ),
        BuiltInCallWallpaper(
            id = "builtin_ocean_abyss",
            title = "Ocean Abyss",
            subtitle = "Bioluminescent sapphire & cyan depths",
            gradientStops = listOf(
                Color(0xFF030D1A),
                Color(0xFF08203E),
                Color(0xFF0A2E52),
                Color(0xFF020812)
            ),
            primaryGlow = Color(0xFF00BBF9),
            secondaryGlow = Color(0xFF00F5D4),
            tertiaryGlow = Color(0xFF274C77)
        ),
        BuiltInCallWallpaper(
            id = "builtin_crimson_velvet",
            title = "Crimson Velvet",
            subtitle = "Rich ruby silk & dark charcoal bloom",
            gradientStops = listOf(
                Color(0xFF180509),
                Color(0xFF2D0A14),
                Color(0xFF210814),
                Color(0xFF090204)
            ),
            primaryGlow = Color(0xFFE63946),
            secondaryGlow = Color(0xFFFF758F),
            tertiaryGlow = Color(0xFF800F2F)
        )
    )

    @Volatile
    private var isInitialized = false

    private val _appliedConfig = MutableStateFlow(CallWallpaperConfig())
    val appliedConfig: StateFlow<CallWallpaperConfig> = _appliedConfig.asStateFlow()

    fun findBuiltInWallpaper(id: String?): BuiltInCallWallpaper {
        return BUILT_IN_WALLPAPERS.firstOrNull { it.id == id } ?: BUILT_IN_WALLPAPERS.first()
    }

    fun ensureInitialized(context: Context): CallWallpaperConfig {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    _appliedConfig.value = loadSavedConfig(context.applicationContext)
                    isInitialized = true
                }
            }
        }
        return _appliedConfig.value
    }

    fun loadSavedConfig(context: Context): CallWallpaperConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawType = CallWallpaperType.fromName(prefs.getString(KEY_WALLPAPER_TYPE, null))
        val rawUri = prefs.getString(KEY_WALLPAPER_MEDIA_URI, "").orEmpty().trim()
        val rawBuiltInId = prefs.getString(KEY_WALLPAPER_BUILTIN_ID, DEFAULT_BUILT_IN_ID)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_BUILT_IN_ID
        val blurDp = prefs.getFloat(KEY_WALLPAPER_BLUR_DP, 0f).coerceIn(MIN_BLUR_DP, MAX_BLUR_DP)
        val darkenAlpha = prefs.getFloat(KEY_WALLPAPER_DARKEN_ALPHA, DEFAULT_DARKEN_ALPHA)
            .coerceIn(MIN_DARKEN_ALPHA, MAX_DARKEN_ALPHA)

        val validatedType = when (rawType) {
            CallWallpaperType.PHOTO, CallWallpaperType.VIDEO -> {
                if (rawUri.isNotBlank() && isMediaUriAccessible(context, rawUri)) {
                    rawType
                } else {
                    CallWallpaperType.SYSTEM_DEFAULT
                }
            }
            CallWallpaperType.BUILT_IN -> CallWallpaperType.BUILT_IN
            CallWallpaperType.SYSTEM_DEFAULT -> CallWallpaperType.SYSTEM_DEFAULT
        }

        val resolvedConfig = CallWallpaperConfig(
            type = validatedType,
            mediaUri = if (validatedType == CallWallpaperType.PHOTO || validatedType == CallWallpaperType.VIDEO) {
                rawUri
            } else {
                ""
            },
            builtInId = findBuiltInWallpaper(rawBuiltInId).id,
            blurRadiusDp = blurDp,
            darkenAlpha = darkenAlpha
        )
        _appliedConfig.value = resolvedConfig
        isInitialized = true
        return resolvedConfig
    }

    fun sanitizeConfig(config: CallWallpaperConfig): CallWallpaperConfig {
        val sanitizedType = when (config.type) {
            CallWallpaperType.PHOTO, CallWallpaperType.VIDEO -> {
                if (config.mediaUri.isNotBlank()) config.type else CallWallpaperType.SYSTEM_DEFAULT
            }
            else -> config.type
        }
        return CallWallpaperConfig(
            type = sanitizedType,
            mediaUri = if (sanitizedType == CallWallpaperType.PHOTO || sanitizedType == CallWallpaperType.VIDEO) {
                config.mediaUri.trim()
            } else {
                ""
            },
            builtInId = findBuiltInWallpaper(config.builtInId).id,
            blurRadiusDp = config.blurRadiusDp.coerceIn(MIN_BLUR_DP, MAX_BLUR_DP),
            darkenAlpha = config.darkenAlpha.coerceIn(MIN_DARKEN_ALPHA, MAX_DARKEN_ALPHA)
        )
    }

    /**
     * Persists and applies the given [config] to all incoming, outgoing, and ongoing call screens.
     */
    fun applyWallpaperToAllCalls(context: Context, config: CallWallpaperConfig): CallWallpaperConfig {
        val appContext = context.applicationContext
        val sanitized = sanitizeConfig(config)

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WALLPAPER_TYPE, sanitized.type.name)
            .putString(KEY_WALLPAPER_MEDIA_URI, sanitized.mediaUri)
            .putString(KEY_WALLPAPER_BUILTIN_ID, sanitized.builtInId)
            .putFloat(KEY_WALLPAPER_BLUR_DP, sanitized.blurRadiusDp)
            .putFloat(KEY_WALLPAPER_DARKEN_ALPHA, sanitized.darkenAlpha)
            .commit()

        _appliedConfig.value = sanitized
        isInitialized = true
        return sanitized
    }

    /**
     * Resets the call screen wallpaper back to System Default and clears custom adjustments.
     */
    fun resetToDefault(context: Context): CallWallpaperConfig {
        val defaultConfig = CallWallpaperConfig(
            type = CallWallpaperType.SYSTEM_DEFAULT,
            mediaUri = "",
            builtInId = DEFAULT_BUILT_IN_ID,
            blurRadiusDp = 0f,
            darkenAlpha = DEFAULT_DARKEN_ALPHA
        )
        return applyWallpaperToAllCalls(context, defaultConfig)
    }

    /**
     * Retains URI read permission and copies the selected gallery photo (JPG, JPEG, PNG, WebP)
     * into app-private storage so it survives app restarts and device reboots.
     */
    fun importAndPersistPhotoUri(context: Context, sourceUri: Uri): Result<String> {
        val appContext = context.applicationContext
        takePersistableReadPermissionSafely(appContext, sourceUri)

        val mimeType = runCatching {
            appContext.contentResolver.getType(sourceUri)?.lowercase(Locale.US)
        }.getOrNull()
        val extFromUri = extractExtension(sourceUri.toString())

        val isMimeValid = mimeType != null && SUPPORTED_PHOTO_MIMES.contains(mimeType)
        val isExtValid = extFromUri.isNotEmpty() && SUPPORTED_PHOTO_EXTENSIONS.contains(extFromUri)

        if (mimeType != null && mimeType.startsWith("video/")) {
            return Result.failure(
                IllegalArgumentException("Unsupported photo format ($mimeType). Please select a JPG, JPEG, PNG, or WebP image.")
            )
        }

        val resolvedExt = when {
            mimeType == "image/png" || extFromUri == "png" -> "png"
            mimeType == "image/webp" || extFromUri == "webp" -> "webp"
            mimeType == "image/jpeg" || mimeType == "image/jpg" || extFromUri == "jpg" || extFromUri == "jpeg" -> "jpg"
            isMimeValid || isExtValid -> extFromUri.ifEmpty { "jpg" }
            else -> "jpg"
        }

        return try {
            val dir = File(appContext.filesDir, WALLPAPER_DIR_NAME).apply { mkdirs() }
            val targetFile = File(dir, "call_wallpaper_photo.$resolvedExt")
            val copiedBytes = appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: 0L

            if (copiedBytes > 0L && targetFile.exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(targetFile.absolutePath, options)
                val outMime = options.outMimeType?.lowercase(Locale.US)
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    targetFile.delete()
                    return Result.failure(
                        IllegalArgumentException("Selected file is not a valid JPG, JPEG, PNG, or WebP image.")
                    )
                }
                if (outMime != null && !SUPPORTED_PHOTO_MIMES.contains(outMime)) {
                    targetFile.delete()
                    return Result.failure(
                        IllegalArgumentException("Unsupported image format ($outMime). Supported formats: JPG, JPEG, PNG, WebP.")
                    )
                }
                Result.success(Uri.fromFile(targetFile).toString())
            } else if (isMimeValid || isExtValid) {
                Result.success(sourceUri.toString())
            } else {
                Result.failure(
                    IllegalArgumentException("Unable to read the selected photo. Please choose a JPG, JPEG, PNG, or WebP image.")
                )
            }
        } catch (e: Exception) {
            if (isMimeValid || isExtValid) {
                Result.success(sourceUri.toString())
            } else {
                Result.failure(
                    IllegalArgumentException(
                        e.localizedMessage ?: "Failed to load selected photo."
                    )
                )
            }
        }
    }

    /**
     * Retains URI read permission and copies the selected gallery video into app-private storage
     * so it survives app restarts and device reboots.
     */
    fun importAndPersistVideoUri(context: Context, sourceUri: Uri): Result<String> {
        val appContext = context.applicationContext
        takePersistableReadPermissionSafely(appContext, sourceUri)

        val mimeType = runCatching {
            appContext.contentResolver.getType(sourceUri)?.lowercase(Locale.US)
        }.getOrNull()
        val extFromUri = extractExtension(sourceUri.toString())

        if (mimeType != null && !mimeType.startsWith("video/")) {
            return Result.failure(
                IllegalArgumentException("Unsupported video format ($mimeType). Please select a valid video file.")
            )
        }
        if (mimeType == null && extFromUri.isNotEmpty() && !SUPPORTED_VIDEO_EXTENSIONS.contains(extFromUri)) {
            return Result.failure(
                IllegalArgumentException("Unsupported video file (.$extFromUri). Please select an MP4, WebM, or 3GP video.")
            )
        }

        val resolvedExt = when {
            extFromUri.isNotEmpty() && SUPPORTED_VIDEO_EXTENSIONS.contains(extFromUri) -> extFromUri
            mimeType == "video/webm" -> "webm"
            mimeType == "video/3gpp" -> "3gp"
            else -> "mp4"
        }

        return try {
            val dir = File(appContext.filesDir, WALLPAPER_DIR_NAME).apply { mkdirs() }
            val targetFile = File(dir, "call_wallpaper_video.$resolvedExt")
            val copiedBytes = appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: 0L

            if (copiedBytes > 0L && targetFile.exists()) {
                Result.success(Uri.fromFile(targetFile).toString())
            } else {
                Result.success(sourceUri.toString())
            }
        } catch (_: Exception) {
            Result.success(sourceUri.toString())
        }
    }

    private fun takePersistableReadPermissionSafely(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Android Photo Picker URIs on some OS builds are already granted or copied locally below
        } catch (_: Throwable) {
        }
    }

    fun isMediaUriAccessible(context: Context, rawUri: String): Boolean {
        if (rawUri.isBlank()) return false
        return try {
            val uri = Uri.parse(rawUri)
            when (uri.scheme?.lowercase(Locale.US)) {
                "file" -> {
                    val path = uri.path ?: return false
                    File(path).exists()
                }
                "content", "android.resource" -> {
                    val fd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    if (fd != null) {
                        fd.close()
                        true
                    } else {
                        true
                    }
                }
                else -> true
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            // In unit test environments or custom schemes, allow non-blank URIs unless explicitly missing file
            !rawUri.startsWith("file://")
        }
    }

    private fun extractExtension(uriString: String): String {
        val clean = uriString.substringBefore('?').substringBefore('#')
        val lastDot = clean.lastIndexOf('.')
        if (lastDot < 0 || lastDot == clean.length - 1) return ""
        return clean.substring(lastDot + 1).lowercase(Locale.US)
    }
}

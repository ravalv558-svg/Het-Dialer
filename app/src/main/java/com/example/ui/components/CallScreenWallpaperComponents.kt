package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.BuiltInCallWallpaper
import com.example.data.CallScreenWallpaperManager
import com.example.data.CallWallpaperConfig
import com.example.data.CallWallpaperType
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsIncomingGreen
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.ColorOsWhite
import kotlin.math.roundToInt

/**
 * Renders the Call Screen Wallpaper (System Default, Built-in Wallpaper, Photo from Gallery,
 * or Looping Muted Video from Gallery) with Blur and Darken adjustments behind call controls.
 *
 * When [overrideConfig] is null, this Composable automatically observes
 * [CallScreenWallpaperManager.appliedConfig] so that all incoming, outgoing, and ongoing call
 * screens display the user's applied wallpaper automatically.
 */
@Composable
fun CallScreenWallpaperBackdrop(
    modifier: Modifier = Modifier,
    overrideConfig: CallWallpaperConfig? = null,
    onMediaLoadError: ((String) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        CallScreenWallpaperManager.ensureInitialized(context)
    }
    val appliedConfig by CallScreenWallpaperManager.appliedConfig.collectAsState()
    val activeConfig = overrideConfig ?: appliedConfig

    var mediaErrorFallback by remember(activeConfig.type, activeConfig.mediaUri) {
        mutableStateOf(false)
    }

    val blurDp = activeConfig.blurRadiusDp.coerceIn(
        CallScreenWallpaperManager.MIN_BLUR_DP,
        CallScreenWallpaperManager.MAX_BLUR_DP
    )
    val darkenAlpha = activeConfig.darkenAlpha.coerceIn(
        CallScreenWallpaperManager.MIN_DARKEN_ALPHA,
        CallScreenWallpaperManager.MAX_DARKEN_ALPHA
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF07050E))
    ) {
        // Wallpaper Layer with Blur Adjustment
        val wallpaperModifier = Modifier
            .fillMaxSize()
            .then(
                if (blurDp > 0.2f) {
                    Modifier.blur(
                        radius = blurDp.dp,
                        edgeTreatment = BlurredEdgeTreatment.Rectangle
                    )
                } else {
                    Modifier
                }
            )

        Box(modifier = wallpaperModifier) {
            when {
                mediaErrorFallback || activeConfig.type == CallWallpaperType.SYSTEM_DEFAULT -> {
                    SystemDefaultCallBackdropCanvas(modifier = Modifier.fillMaxSize())
                }

                activeConfig.type == CallWallpaperType.BUILT_IN -> {
                    val preset = remember(activeConfig.builtInId) {
                        CallScreenWallpaperManager.findBuiltInWallpaper(activeConfig.builtInId)
                    }
                    BuiltInCallWallpaperCanvas(
                        wallpaper = preset,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                activeConfig.type == CallWallpaperType.PHOTO -> {
                    if (activeConfig.mediaUri.isBlank()) {
                        SystemDefaultCallBackdropCanvas(modifier = Modifier.fillMaxSize())
                    } else {
                        var isPhotoLoaded by remember(activeConfig.mediaUri) { mutableStateOf(false) }
                        if (!isPhotoLoaded) {
                            SystemDefaultCallBackdropCanvas(modifier = Modifier.fillMaxSize())
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(Uri.parse(activeConfig.mediaUri))
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            onSuccess = {
                                isPhotoLoaded = true
                            },
                            onError = {
                                isPhotoLoaded = false
                                mediaErrorFallback = true
                                onMediaLoadError?.invoke(
                                    "Unable to display the selected photo. Reverted to default background."
                                )
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("call_wallpaper_photo_layer")
                        )
                    }
                }

                activeConfig.type == CallWallpaperType.VIDEO -> {
                    if (activeConfig.mediaUri.isBlank()) {
                        SystemDefaultCallBackdropCanvas(modifier = Modifier.fillMaxSize())
                    } else {
                        LoopingMutedCenterCropVideoWallpaper(
                            videoUriString = activeConfig.mediaUri,
                            onVideoError = { errorMsg ->
                                mediaErrorFallback = true
                                onMediaLoadError?.invoke(errorMsg)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("call_wallpaper_video_layer")
                        )
                    }
                }
            }
        }

        // Cross-API Frosted Diffusion Veil when Blur > 0 (provides smooth visual blur even on pre-Android 12)
        if (blurDp > 0.2f) {
            val frostAlpha = (blurDp / CallScreenWallpaperManager.MAX_BLUR_DP) * 0.22f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1429).copy(alpha = frostAlpha),
                                Color(0xFF0E0B18).copy(alpha = frostAlpha * 0.85f),
                                Color(0xFF1A1429).copy(alpha = frostAlpha)
                            )
                        )
                    )
            )
        }

        // Darken Background Scrim Layer
        if (darkenAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = darkenAlpha))
                    .testTag("call_wallpaper_darken_layer")
            )
        }

        // Foreground Call Controls
        content()
    }
}

/**
 * Default OPPO ColorOS Dark Purple & Black Atmospheric Gradient Backdrop.
 */
@Composable
fun SystemDefaultCallBackdropCanvas(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E0A1C),
                        Color(0xFF1A1133),
                        Color(0xFF140D28),
                        Color(0xFF07050E)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4A3380).copy(alpha = 0.52f),
                        Color(0xFF28194B).copy(alpha = 0.24f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.58f, h * 0.22f),
                    radius = (w * 0.78f).coerceAtLeast(1f)
                ),
                radius = (w * 0.78f).coerceAtLeast(1f),
                center = Offset(w * 0.58f, h * 0.22f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF9C4B22).copy(alpha = 0.55f),
                        Color(0xFF6B3116).copy(alpha = 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.78f, h * 0.36f),
                    radius = (w * 0.62f).coerceAtLeast(1f)
                ),
                radius = (w * 0.62f).coerceAtLeast(1f),
                center = Offset(w * 0.78f, h * 0.36f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8C411D).copy(alpha = 0.50f),
                        Color(0xFF542511).copy(alpha = 0.24f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.22f, h * 0.48f),
                    radius = (w * 0.64f).coerceAtLeast(1f)
                ),
                radius = (w * 0.64f).coerceAtLeast(1f),
                center = Offset(w * 0.22f, h * 0.48f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF392669).copy(alpha = 0.48f),
                        Color(0xFF1D1238).copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.52f, h * 0.68f),
                    radius = (w * 0.75f).coerceAtLeast(1f)
                ),
                radius = (w * 0.75f).coerceAtLeast(1f),
                center = Offset(w * 0.52f, h * 0.68f)
            )
        }
    }
}

/**
 * Built-in atmospheric wallpaper renderer.
 */
@Composable
fun BuiltInCallWallpaperCanvas(
    wallpaper: BuiltInCallWallpaper,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = wallpaper.gradientStops))
            .testTag("call_wallpaper_builtin_${wallpaper.id}")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        wallpaper.primaryGlow.copy(alpha = 0.58f),
                        wallpaper.primaryGlow.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.75f, h * 0.24f),
                    radius = (w * 0.82f).coerceAtLeast(1f)
                ),
                radius = (w * 0.82f).coerceAtLeast(1f),
                center = Offset(w * 0.75f, h * 0.24f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        wallpaper.secondaryGlow.copy(alpha = 0.52f),
                        wallpaper.secondaryGlow.copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.24f, h * 0.50f),
                    radius = (w * 0.76f).coerceAtLeast(1f)
                ),
                radius = (w * 0.76f).coerceAtLeast(1f),
                center = Offset(w * 0.24f, h * 0.50f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        wallpaper.tertiaryGlow.copy(alpha = 0.55f),
                        wallpaper.tertiaryGlow.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.56f, h * 0.78f),
                    radius = (w * 0.85f).coerceAtLeast(1f)
                ),
                radius = (w * 0.85f).coerceAtLeast(1f),
                center = Offset(w * 0.56f, h * 0.78f)
            )
        }
    }
}

private class VideoWallpaperHolder {
    var mediaPlayer: MediaPlayer? = null
    var surface: Surface? = null
    var isLifecycleStarted: Boolean = true

    fun release() {
        val player = mediaPlayer
        mediaPlayer = null
        val surf = surface
        surface = null
        if (player != null) {
            try {
                player.setOnPreparedListener(null)
                player.setOnErrorListener(null)
                player.setOnVideoSizeChangedListener(null)
            } catch (_: Throwable) {
            }
            try {
                player.setSurface(null)
            } catch (_: Throwable) {
            }
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Throwable) {
            }
            try {
                player.release()
            } catch (_: Throwable) {
            }
        }
        if (surf != null) {
            try {
                surf.release()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Looping, muted, center-crop video background player with lifecycle-aware pause/resume
 * and graceful error recovery.
 */
@Composable
private fun LoopingMutedCenterCropVideoWallpaper(
    videoUriString: String,
    onVideoError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { VideoWallpaperHolder() }

    DisposableEffect(lifecycleOwner, videoUriString) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                    holder.isLifecycleStarted = true
                    try {
                        val mp = holder.mediaPlayer
                        if (mp != null && !mp.isPlaying) {
                            mp.setVolume(0f, 0f)
                            mp.start()
                        }
                    } catch (_: Throwable) {
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    holder.isLifecycleStarted = false
                    try {
                        val mp = holder.mediaPlayer
                        if (mp != null && mp.isPlaying) {
                            mp.pause()
                        }
                    } catch (_: Throwable) {
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
            }
        },
        update = { textureView ->
            val currentTag = textureView.tag as? String
            if (currentTag == videoUriString) return@AndroidView
            textureView.tag = videoUriString

            fun startVideoOnSurface(surfaceTexture: SurfaceTexture, viewWidth: Int, viewHeight: Int) {
                holder.release()
                val surface = Surface(surfaceTexture)
                val player = MediaPlayer()
                holder.surface = surface
                holder.mediaPlayer = player
                var currentVideoW = 0
                var currentVideoH = 0

                try {
                    player.setSurface(surface)
                    player.isLooping = true
                    player.setVolume(0f, 0f)
                    player.setDataSource(context, Uri.parse(videoUriString))
                    player.setOnVideoSizeChangedListener { _, vWidth, vHeight ->
                        currentVideoW = vWidth
                        currentVideoH = vHeight
                        applyCenterCropTransform(
                            textureView = textureView,
                            viewWidth = textureView.width.takeIf { it > 0 } ?: viewWidth,
                            viewHeight = textureView.height.takeIf { it > 0 } ?: viewHeight,
                            videoWidth = vWidth,
                            videoHeight = vHeight
                        )
                    }
                    player.setOnPreparedListener { mp ->
                        try {
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            applyCenterCropTransform(
                                textureView = textureView,
                                viewWidth = textureView.width.takeIf { it > 0 } ?: viewWidth,
                                viewHeight = textureView.height.takeIf { it > 0 } ?: viewHeight,
                                videoWidth = mp.videoWidth.takeIf { it > 0 } ?: currentVideoW,
                                videoHeight = mp.videoHeight.takeIf { it > 0 } ?: currentVideoH
                            )
                            if (holder.isLifecycleStarted) {
                                mp.start()
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    player.setOnErrorListener { _, _, _ ->
                        holder.release()
                        onVideoError(
                            "Unsupported video format or playback error. Reverted to default background."
                        )
                        true
                    }
                    player.prepareAsync()
                } catch (_: Throwable) {
                    holder.release()
                    onVideoError(
                        "Unable to play the selected video. Please select a valid MP4 or WebM video."
                    )
                }
            }

            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    startVideoOnSurface(surface, width, height)
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    val mp = holder.mediaPlayer ?: return
                    try {
                        applyCenterCropTransform(
                            textureView = textureView,
                            viewWidth = width,
                            viewHeight = height,
                            videoWidth = mp.videoWidth,
                            videoHeight = mp.videoHeight
                        )
                    } catch (_: Throwable) {
                    }
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    holder.release()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }

            if (textureView.isAvailable) {
                val st = textureView.surfaceTexture
                if (st != null) {
                    startVideoOnSurface(st, textureView.width, textureView.height)
                }
            }
        }
    )
}

/**
 * Computes and applies a Center-Crop [Matrix] transform to [textureView] so that the video
 * fills the entire call screen while preserving its native aspect ratio without stretching.
 */
private fun applyCenterCropTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int
) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()

    val scaleX: Float
    val scaleY: Float
    if (videoAspect > viewAspect) {
        // Video is wider than the view: scale X up to crop left/right edges
        scaleX = videoAspect / viewAspect
        scaleY = 1f
    } else {
        // Video is taller than the view: scale Y up to crop top/bottom edges
        scaleX = 1f
        scaleY = viewAspect / videoAspect
    }

    val matrix = Matrix().apply {
        setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    }
    textureView.setTransform(matrix)
}

/**
 * Complete "Call Screen Background" Settings Screen allowing users to:
 * - Select Photo from Gallery (JPG, JPEG, PNG, WebP) via Android Photo Picker
 * - Select Video from Gallery (looping, muted, center-crop) via Android Photo Picker
 * - Select Built-in Wallpapers
 * - Select System Default
 * - Preview the selected wallpaper live behind realistic Incoming, Outgoing, and Ongoing call controls
 * - Adjust Blur Background slider and Darken Background slider, or Reset to Default
 * - Tap the prominent "Apply to All Calls" button to persist and apply across all call screens
 */
@Composable
fun CallScreenBackgroundSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        CallScreenWallpaperManager.ensureInitialized(context)
    }
    val appliedConfig by CallScreenWallpaperManager.appliedConfig.collectAsState()

    var draftConfig by remember(appliedConfig) { mutableStateOf(appliedConfig) }
    var previewCallStage by remember { mutableIntStateOf(2) } // 0 = Incoming, 1 = Outgoing, 2 = Ongoing
    var statusBannerMessage by remember { mutableStateOf<String?>(null) }
    var isErrorBanner by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = CallScreenWallpaperManager.importAndPersistPhotoUri(context, uri)
            result.onSuccess { persistedUri ->
                draftConfig = draftConfig.copy(
                    type = CallWallpaperType.PHOTO,
                    mediaUri = persistedUri
                )
                isErrorBanner = false
                statusBannerMessage = "Photo selected. Tap 'Apply to All Calls' below to save."
            }.onFailure { err ->
                isErrorBanner = true
                statusBannerMessage = err.localizedMessage ?: "Unsupported photo format."
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = CallScreenWallpaperManager.importAndPersistVideoUri(context, uri)
            result.onSuccess { persistedUri ->
                draftConfig = draftConfig.copy(
                    type = CallWallpaperType.VIDEO,
                    mediaUri = persistedUri
                )
                isErrorBanner = false
                statusBannerMessage = "Video selected (muted & looping). Tap 'Apply to All Calls' to save."
            }.onFailure { err ->
                isErrorBanner = true
                statusBannerMessage = err.localizedMessage ?: "Unsupported video format."
            }
        }
    }

    val pageBg = MaterialTheme.colorScheme.surfaceVariant
    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val isApplied = draftConfig == appliedConfig

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("call_screen_background_settings_screen")
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("wallpaper_settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Call Screen Background",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                color = if (isApplied) ColorOsGreen.copy(alpha = 0.14f) else Color(0xFFFF9800).copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(end = 10.dp)
            ) {
                Text(
                    text = if (isApplied) "Applied" else "Previewing",
                    color = if (isApplied) ColorOsGreen else Color(0xFFE65100),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status / Feedback Banner
            statusBannerMessage?.let { msg ->
                Surface(
                    color = if (isErrorBanner) ColorOsRed.copy(alpha = 0.14f) else ColorOsGreen.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallpaper_status_banner")
                ) {
                    Text(
                        text = msg,
                        color = if (isErrorBanner) ColorOsRed else ColorOsGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // Realistic Call Screen Live Preview Card
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Call Screen Preview",
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Preview Stage Tabs: Incoming | Outgoing | Ongoing
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(pageBg)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf("Incoming", "Outgoing", "Ongoing").forEachIndexed { idx, label ->
                                val selected = previewCallStage == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) ColorOsGreen else Color.Transparent)
                                        .clickable { previewCallStage = idx }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                        .testTag("wallpaper_preview_tab_${label.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) ColorOsWhite else textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Realistic Miniature Call Screen Frame
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(310.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(22.dp)
                            )
                            .testTag("wallpaper_live_preview_container")
                    ) {
                        CallScreenWallpaperBackdrop(
                            overrideConfig = draftConfig,
                            onMediaLoadError = { errMsg ->
                                isErrorBanner = true
                                statusBannerMessage = errMsg
                                draftConfig = draftConfig.copy(
                                    type = CallWallpaperType.SYSTEM_DEFAULT,
                                    mediaUri = ""
                                )
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Top Caller Info + Avatar inside Preview
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Het",
                                            color = ColorOsWhite,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "+91 73832 39510 · India",
                                            color = Color(0xFFEAE4F5),
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = when (previewCallStage) {
                                                0 -> "HD Vo5G  Incoming call"
                                                1 -> "HD Vo5G  Calling..."
                                                else -> "HD Vo5G  01:24"
                                            },
                                            color = ColorOsWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2F6FE4)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "H",
                                            color = ColorOsWhite,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Bottom Controls inside Preview
                                if (previewCallStage == 0) {
                                    // Incoming Call Preview Controls
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(ColorOsRed),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CallEnd,
                                                contentDescription = null,
                                                tint = ColorOsWhite,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Surface(
                                            color = Color.White.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = "SMS reply",
                                                color = ColorOsWhite,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(ColorOsIncomingGreen),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Call,
                                                contentDescription = null,
                                                tint = ColorOsWhite,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                } else {
                                    // Outgoing / Ongoing Call Preview Controls
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            listOf(
                                                Icons.Outlined.MicOff to "Mute",
                                                Icons.Filled.AutoAwesome to "AI",
                                                Icons.Filled.MoreVert to "More"
                                            ).forEach { (iconVec, label) ->
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.White.copy(alpha = 0.15f))
                                                            .border(0.8.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = iconVec,
                                                            contentDescription = null,
                                                            tint = ColorOsWhite,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = label,
                                                        color = ColorOsWhite.copy(alpha = 0.85f),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(ColorOsWhite),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = null,
                                                    tint = Color(0xFF1B1B1F),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .width(112.dp)
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(22.dp))
                                                    .background(ColorOsRed),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.CallEnd,
                                                    contentDescription = null,
                                                    tint = ColorOsWhite,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                TenDotKeypadIcon(tint = ColorOsWhite)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Wallpaper Options Card (Photo, Video, Built-in, System Default)
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Wallpaper Source",
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WallpaperOptionSourceButton(
                            title = "Photo from Gallery",
                            subtitle = "JPG, PNG, WebP",
                            icon = Icons.Filled.Image,
                            selected = draftConfig.type == CallWallpaperType.PHOTO,
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            testTag = "wallpaper_option_photo_gallery",
                            modifier = Modifier.weight(1f)
                        )

                        WallpaperOptionSourceButton(
                            title = "Video from Gallery",
                            subtitle = "Looping & Muted",
                            icon = Icons.Filled.Videocam,
                            selected = draftConfig.type == CallWallpaperType.VIDEO,
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            testTag = "wallpaper_option_video_gallery",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WallpaperOptionSourceButton(
                            title = "Built-in Wallpapers",
                            subtitle = "${CallScreenWallpaperManager.BUILT_IN_WALLPAPERS.size} curated themes",
                            icon = Icons.Filled.Palette,
                            selected = draftConfig.type == CallWallpaperType.BUILT_IN,
                            onClick = {
                                draftConfig = draftConfig.copy(
                                    type = CallWallpaperType.BUILT_IN,
                                    mediaUri = ""
                                )
                                isErrorBanner = false
                                statusBannerMessage = null
                            },
                            testTag = "wallpaper_option_builtin",
                            modifier = Modifier.weight(1f)
                        )

                        WallpaperOptionSourceButton(
                            title = "System Default",
                            subtitle = "Original backdrop",
                            icon = Icons.Filled.AutoAwesome,
                            selected = draftConfig.type == CallWallpaperType.SYSTEM_DEFAULT,
                            onClick = {
                                draftConfig = draftConfig.copy(
                                    type = CallWallpaperType.SYSTEM_DEFAULT,
                                    mediaUri = ""
                                )
                                isErrorBanner = false
                                statusBannerMessage = null
                            },
                            testTag = "wallpaper_option_system_default",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Built-in Wallpaper Preset Swatches
                    Text(
                        text = "Built-in Wallpapers",
                        color = textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CallScreenWallpaperManager.BUILT_IN_WALLPAPERS.forEach { preset ->
                            val isSelectedPreset = draftConfig.type == CallWallpaperType.BUILT_IN &&
                                draftConfig.builtInId == preset.id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(88.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        draftConfig = draftConfig.copy(
                                            type = CallWallpaperType.BUILT_IN,
                                            builtInId = preset.id,
                                            mediaUri = ""
                                        )
                                        isErrorBanner = false
                                        statusBannerMessage = null
                                    }
                                    .testTag("builtin_wallpaper_item_${preset.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (isSelectedPreset) 2.5.dp else 1.dp,
                                            color = if (isSelectedPreset) ColorOsGreen else Color.White.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    BuiltInCallWallpaperCanvas(
                                        wallpaper = preset,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isSelectedPreset) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(ColorOsGreen),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                tint = ColorOsWhite,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = preset.title,
                                    color = if (isSelectedPreset) ColorOsGreen else textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelectedPreset) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Background Adjustments Card: Blur Background slider, Darken Background slider, Reset to Default
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Background Adjustments",
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Blur Background Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Blur Background",
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${draftConfig.blurRadiusDp.roundToInt()} dp",
                            color = ColorOsGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = draftConfig.blurRadiusDp,
                        onValueChange = { nextBlur ->
                            draftConfig = draftConfig.copy(blurRadiusDp = nextBlur)
                        },
                        valueRange = CallScreenWallpaperManager.MIN_BLUR_DP..CallScreenWallpaperManager.MAX_BLUR_DP,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorOsGreen,
                            activeTrackColor = ColorOsGreen
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wallpaper_blur_slider")
                    )

                    // Darken Background Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Darken Background",
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${(draftConfig.darkenAlpha * 100f).roundToInt()}%",
                            color = ColorOsGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = draftConfig.darkenAlpha,
                        onValueChange = { nextDarken ->
                            draftConfig = draftConfig.copy(darkenAlpha = nextDarken)
                        },
                        valueRange = CallScreenWallpaperManager.MIN_DARKEN_ALPHA..CallScreenWallpaperManager.MAX_DARKEN_ALPHA,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorOsGreen,
                            activeTrackColor = ColorOsGreen
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wallpaper_darken_slider")
                    )

                    // Reset to Default Button
                    OutlinedButton(
                        onClick = {
                            val resetCfg = CallScreenWallpaperManager.resetToDefault(context)
                            draftConfig = resetCfg
                            isErrorBanner = false
                            statusBannerMessage = "Reset to System Default for all calls."
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("wallpaper_reset_default_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset to Default",
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Prominent "Apply to All Calls" Button
            Button(
                onClick = {
                    val saved = CallScreenWallpaperManager.applyWallpaperToAllCalls(context, draftConfig)
                    draftConfig = saved
                    isErrorBanner = false
                    statusBannerMessage =
                        "Wallpaper applied to all incoming, outgoing, and ongoing calls."
                },
                colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("wallpaper_apply_all_calls_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ColorOsWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Apply to All Calls",
                    color = ColorOsWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WallpaperOptionSourceButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) {
        ColorOsGreen.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val borderColor = if (selected) {
        ColorOsGreen
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = if (selected) 1.5.dp else 0.8.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) ColorOsGreen else textSecondary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ColorOsWhite else textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

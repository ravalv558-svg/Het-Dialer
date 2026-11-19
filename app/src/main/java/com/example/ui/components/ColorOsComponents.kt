package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import com.example.data.PersonalizationManager
import com.example.model.AppBackgroundStyle
import com.example.ui.theme.LocalPersonalizationConfig
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.model.CallDirection
import com.example.ui.theme.CallBgDarkCorner
import com.example.ui.theme.CallBgDeepNavy
import com.example.ui.theme.CallBgMidBlue
import com.example.ui.theme.CallBgSoftBlue
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsHdBadgeBg
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.ColorOsTextPrimary
import com.example.ui.theme.ColorOsTextSecondary
import com.example.ui.theme.ColorOsWhite
import com.example.ui.theme.avatarColorForName

/**
 * Opening 3D Phone Logo Animation with smooth rotation and subtle lighting.
 */
@Composable
fun ColorOsOpening3DSplash(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val rotationY = (1f - progress) * 58f
    val scale = 0.86f + (0.14f * progress)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .graphicsLayer {
                        this.rotationY = rotationY
                        this.scaleX = scale
                        this.scaleY = scale
                        this.cameraDistance = 14f * density
                    }
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = ColorOsGreen.copy(alpha = 0.45f),
                        spotColor = ColorOsGreen.copy(alpha = 0.55f)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF43E365),
                                Color(0xFF28B848),
                                Color(0xFF1A8F34)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(220f, 220f)
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Phone",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Phone",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Animated Contact Avatar with Concentric Calling Waves (used on Outgoing & Incoming Call Screens).
 */
@Composable
fun PremiumCallAvatarWithWaves(
    name: String,
    photoUri: String?,
    animateWaves: Boolean,
    avatarSize: Dp = 86.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "calling_waves")
    val wave1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_1"
    )
    val wave2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_2"
    )

    Box(
        modifier = modifier.size(avatarSize * 1.65f),
        contentAlignment = Alignment.Center
    ) {
        if (animateWaves) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseRadius = (avatarSize.toPx() / 2f)
                val maxExpand = baseRadius * 0.62f

                drawCircle(
                    color = Color.White.copy(alpha = (1f - wave1) * 0.28f),
                    radius = baseRadius + (maxExpand * wave1),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = (1f - wave2) * 0.22f),
                    radius = baseRadius + (maxExpand * wave2),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        } else {
            // Subtle static glass ring
            Box(
                modifier = Modifier
                    .size(avatarSize + 12.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            )
        }

        ContactCircleAvatar(
            name = name,
            photoUri = photoUri,
            size = avatarSize,
            fontSize = 34.sp
        )
    }
}

/**
 * Unified atmospheric or custom wallpaper background for Call Screens.
 */
@Composable
fun ColorOsCallBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    CallScreenWallpaperBackdrop(
        modifier = modifier,
        content = content
    )
}

/**
 * Builds a high-quality Coil [ImageRequest] optimized for circular contact avatar rendering.
 */
fun buildHighQualityContactPhotoRequest(
    context: Context,
    photoUri: String,
    targetPixelSize: Int
): ImageRequest {
    val cleanUri = photoUri.trim()
    val resolvedSize = targetPixelSize.coerceAtLeast(256)
    return ImageRequest.Builder(context)
        .data(cleanUri)
        .size(resolvedSize, resolvedSize)
        .precision(Precision.EXACT)
        .scale(Scale.FILL)
        .bitmapConfig(Bitmap.Config.ARGB_8888)
        .crossfade(180)
        .memoryCacheKey("contact_avatar_${cleanUri}_$resolvedSize")
        .diskCacheKey("contact_avatar_$cleanUri")
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
}

/**
 * Circular contact avatar showing a high-quality contact photo via Coil if available,
 * or a colorful initial circle fallback when no photo is present or loading fails.
 */
@Composable
fun ContactCircleAvatar(
    name: String,
    photoUri: String?,
    size: Dp = 44.dp,
    fontSize: TextUnit = 20.sp,
    modifier: Modifier = Modifier,
    imageTestTag: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bgColor = remember(name) { avatarColorForName(name) }
    val initial = remember(name) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) "#" else trimmed.first().uppercaseChar().toString()
    }
    val normalizedPhotoUri = remember(photoUri) {
        photoUri?.trim()?.takeIf { it.isNotEmpty() }
    }
    val targetPixelSize = remember(size, density) {
        with(density) { (size * 2f).roundToPx().coerceAtLeast(256) }
    }
    val imageRequest = remember(context, normalizedPhotoUri, targetPixelSize) {
        normalizedPhotoUri?.let { uri ->
            buildHighQualityContactPhotoRequest(
                context = context,
                photoUri = uri,
                targetPixelSize = targetPixelSize
            )
        }
    }
    var photoLoadedSuccessfully by remember(normalizedPhotoUri) { mutableStateOf(false) }
    val personalization = LocalPersonalizationConfig.current
    val avatarShape = remember(personalization.contacts.avatarShape, size) {
        PersonalizationManager.shapeFor(personalization.contacts.avatarShape, size.value)
    }
    val showGlowRing = personalization.contacts.showAvatarGlowRing
    val ringAccent = if (personalization.useCustomColors) {
        personalization.primaryColor
    } else {
        Color(0xFF38BDF8)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(avatarShape)
            .background(bgColor)
            .let { base ->
                if (showGlowRing) {
                    base.border(1.8.dp, ringAccent.copy(alpha = 0.78f), avatarShape)
                } else {
                    base
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!photoLoadedSuccessfully) {
            Text(
                text = initial,
                color = ColorOsWhite,
                fontSize = fontSize,
                fontWeight = FontWeight.Normal
            )
        }
        if (imageRequest != null) {
            val imageModifier = Modifier
                .fillMaxSize()
                .clip(avatarShape)
                .let { base ->
                    if (!imageTestTag.isNullOrBlank()) base.testTag(imageTestTag) else base
                }
            AsyncImage(
                model = imageRequest,
                contentDescription = "$name photo",
                contentScale = ContentScale.Crop,
                onState = { state ->
                    photoLoadedSuccessfully = state is AsyncImagePainter.State.Success
                },
                modifier = imageModifier
            )
        }
    }
}

/**
 * Diagonal Call Direction Arrow (`↘` for incoming/missed, `↖` for outgoing, `⊘` for blocked/rejected).
 */
@Composable
fun CallDirectionArrowIcon(
    direction: CallDirection,
    modifier: Modifier = Modifier
) {
    val tint = when (direction) {
        CallDirection.MISSED -> ColorOsRed
        else -> Color(0xFF757575)
    }
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (direction) {
            CallDirection.OUTGOING -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.80f, h * 0.80f),
                    end = Offset(w * 0.20f, h * 0.20f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                val path = Path().apply {
                    moveTo(w * 0.68f, h * 0.20f)
                    lineTo(w * 0.20f, h * 0.20f)
                    lineTo(w * 0.20f, h * 0.68f)
                }
                drawPath(path = path, color = tint, style = stroke)
            }
            CallDirection.BLOCKED -> {
                val r = size.minDimension * 0.42f
                drawCircle(
                    color = tint,
                    radius = r,
                    style = Stroke(width = 1.6.dp.toPx())
                )
                drawLine(
                    color = tint,
                    start = Offset(center.x - r * 0.68f, center.y - r * 0.68f),
                    end = Offset(center.x + r * 0.68f, center.y + r * 0.68f),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            else -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.20f, h * 0.20f),
                    end = Offset(w * 0.80f, h * 0.80f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                val path = Path().apply {
                    moveTo(w * 0.80f, h * 0.32f)
                    lineTo(w * 0.80f, h * 0.80f)
                    lineTo(w * 0.32f, h * 0.80f)
                }
                drawPath(path = path, color = tint, style = stroke)
            }
        }
    }
}

/**
 * Stacked "Vo" over "LTE" subtle badge matching OPPO ColorOS 16 call history rows.
 */
@Composable
fun VoLteBadge(
    tint: Color = Color(0xFFA0A0A0),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Vo",
            color = tint,
            fontSize = 6.5.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 6.5.sp
        )
        Text(
            text = "LTE",
            color = tint,
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 6.5.sp
        )
    }
}

/**
 * Small gray HD badge shown when call features support HD voice.
 */
@Composable
fun HdBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(ColorOsHdBadgeBg, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.5.dp, vertical = 0.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "HD",
            color = ColorOsWhite,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 9.sp
        )
    }
}

/**
 * Voicemail dual-reel icon (`مه`) used on key `1` of the ColorOS 16 dialpad and call history.
 */
@Composable
fun VoicemailReelIcon(
    tint: Color = Color(0xFF8C8C8C),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 16.dp, height = 9.dp)) {
        val r = size.height * 0.36f
        val leftCenter = Offset(size.width * 0.26f, size.height * 0.50f)
        val rightCenter = Offset(size.width * 0.74f, size.height * 0.50f)
        val stroke = 1.2.dp.toPx()

        drawCircle(
            color = tint,
            radius = r,
            center = leftCenter,
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = tint,
            radius = r,
            center = rightCenter,
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(leftCenter.x, leftCenter.y + r),
            end = Offset(rightCenter.x, rightCenter.y + r),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Outlined info circle icon `(i)`.
 */
@Composable
fun OutlinedInfoIcon(
    tint: Color = ColorOsTextPrimary,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val r = size.minDimension / 2f
        val strokePx = 1.5.dp.toPx()
        drawCircle(
            color = tint,
            radius = r - strokePx / 2f,
            style = Stroke(width = strokePx)
        )
        drawCircle(
            color = tint,
            radius = 1.1.dp.toPx(),
            center = Offset(center.x, size.height * 0.31f)
        )
        drawLine(
            color = tint,
            start = Offset(center.x, size.height * 0.45f),
            end = Offset(center.x, size.height * 0.72f),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 10-dot Dialpad Grid Icon (3x3 + 1 bottom dot).
 */
@Composable
fun TenDotKeypadIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val dotRadius = 1.8.dp.toPx()
        val cols = listOf(size.width * 0.24f, size.width * 0.50f, size.width * 0.76f)
        val rows = listOf(
            size.height * 0.16f,
            size.height * 0.39f,
            size.height * 0.62f
        )
        for (ry in rows) {
            for (cx in cols) {
                drawCircle(color = tint, radius = dotRadius, center = Offset(cx, ry))
            }
        }
        drawCircle(
            color = tint,
            radius = dotRadius,
            center = Offset(size.width * 0.50f, size.height * 0.85f)
        )
    }
}

/**
 * ColorOS 16 Filled Backspace Tag Icon (`⌫` grey tag with white `×` inside) matching `IMG-20261107-WA0014`.
 */
@Composable
fun OutlinedBackspaceIcon(
    tint: Color = Color(0xFFB5B5B5),
    crossTint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 28.dp, height = 22.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.32f, h * 0.14f)
            lineTo(w * 0.90f, h * 0.14f)
            quadraticTo(w * 0.96f, h * 0.14f, w * 0.96f, h * 0.24f)
            lineTo(w * 0.96f, h * 0.76f)
            quadraticTo(w * 0.96f, h * 0.86f, w * 0.90f, h * 0.86f)
            lineTo(w * 0.32f, h * 0.86f)
            lineTo(w * 0.08f, h * 0.50f)
            close()
        }
        drawPath(path = path, color = tint)

        val strokeW = 1.7.dp.toPx()
        drawLine(
            color = crossTint,
            start = Offset(w * 0.48f, h * 0.35f),
            end = Offset(w * 0.72f, h * 0.65f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = crossTint,
            start = Offset(w * 0.72f, h * 0.35f),
            end = Offset(w * 0.48f, h * 0.65f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Upward Triple Chevrons (`⌃`x3) above Incoming Call Decline & Answer buttons.
 */
@Composable
fun UpwardTripleChevrons(
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "chevron_pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chevron_phase"
    )

    Canvas(modifier = modifier.size(width = 28.dp, height = 34.dp)) {
        val w = size.width
        val h = size.height
        val alphas = listOf(0.26f, 0.56f, 0.95f)
        val yPositions = listOf(h * 0.22f, h * 0.50f, h * 0.78f)
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        yPositions.forEachIndexed { index, cy ->
            val pulseBoost = ((1f - kotlin.math.abs(phase - (2 - index) * 0.35f)).coerceIn(0f, 1f)) * 0.2f
            val alpha = (alphas[index] + pulseBoost).coerceIn(0.18f, 1f)
            val path = Path().apply {
                moveTo(w * 0.16f, cy + h * 0.14f)
                lineTo(w * 0.50f, cy - h * 0.08f)
                lineTo(w * 0.84f, cy + h * 0.14f)
            }
            drawPath(
                path = path,
                color = baseColor.copy(alpha = alpha),
                style = stroke
            )
        }
    }
}

/**
 * Contact Book Icon matching the OPPO ColorOS 16 Bottom Navigation Bar.
 */
@Composable
fun ContactsBookNavIcon(
    selected: Boolean,
    tint: Color,
    cutoutColor: Color = ColorOsWhite,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()

        if (selected) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.15f, h * 0.10f),
                size = Size(w * 0.70f, h * 0.82f),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawCircle(
                color = cutoutColor,
                radius = w * 0.10f,
                center = Offset(w * 0.50f, h * 0.42f)
            )
            val shoulderPath = Path().apply {
                moveTo(w * 0.32f, h * 0.72f)
                quadraticTo(w * 0.50f, h * 0.54f, w * 0.68f, h * 0.72f)
                close()
            }
            drawPath(path = shoulderPath, color = cutoutColor)
        } else {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.15f, h * 0.10f),
                size = Size(w * 0.70f, h * 0.82f),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.25f, h * 0.22f),
                end = Offset(w * 0.75f, h * 0.22f),
                strokeWidth = 1.2.dp.toPx()
            )
            drawCircle(
                color = tint,
                radius = w * 0.09f,
                center = Offset(w * 0.50f, h * 0.46f),
                style = Stroke(width = 1.3.dp.toPx())
            )
            val shoulderPath = Path().apply {
                moveTo(w * 0.34f, h * 0.74f)
                quadraticTo(w * 0.50f, h * 0.58f, w * 0.66f, h * 0.74f)
            }
            drawPath(
                path = shoulderPath,
                color = tint,
                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * OPPO ColorOS 16 Bottom Navigation Bar ("Phone" & "Contacts").
 */
@Composable
fun ColorOsBottomNavigationBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val personalization = LocalPersonalizationConfig.current
    val useGlassNav = personalization.homeDialer.backgroundStyle != AppBackgroundStyle.THEME_SOLID &&
        personalization.visualEffects.glassmorphismEnabled
    val surfaceColor = if (useGlassNav) {
        MaterialTheme.colorScheme.surface.copy(
            alpha = (1f - personalization.visualEffects.glassTransparency * 0.45f).coerceIn(0.62f, 0.94f)
        )
    } else {
        MaterialTheme.colorScheme.surface
    }
    val activeColor = if (personalization.useCustomColors) {
        personalization.primaryColor
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider(
            color = if (useGlassNav) {
                Color.White.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            },
            thickness = 0.5.dp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Phone Tab
            val callsSelected = selectedTab == 0
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTab(0)
                    }
                    .testTag("nav_tab_calls"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Phone",
                    tint = if (callsSelected) activeColor else inactiveColor.copy(alpha = 0.75f),
                    modifier = Modifier.size(23.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Phone",
                    color = if (callsSelected) activeColor else inactiveColor,
                    fontSize = 11.sp,
                    fontWeight = if (callsSelected) FontWeight.Medium else FontWeight.Normal
                )
            }

            // Contacts Tab
            val contactsSelected = selectedTab == 1
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTab(1)
                    }
                    .testTag("nav_tab_contacts"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ContactsBookNavIcon(
                    selected = contactsSelected,
                    tint = if (contactsSelected) activeColor else inactiveColor.copy(alpha = 0.75f),
                    cutoutColor = surfaceColor
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Contacts",
                    color = if (contactsSelected) activeColor else inactiveColor,
                    fontSize = 11.sp,
                    fontWeight = if (contactsSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

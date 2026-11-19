package com.example.ui.components

import android.provider.Settings
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecom.CallSmartIslandManager
import com.example.telecom.SmartIslandCutoutPlacement
import com.example.telecom.SmartIslandExpansionMode
import com.example.telecom.SmartIslandGestureOutcome
import com.example.telecom.SmartIslandUiState
import com.example.telecom.SmartIslandVisualState
import com.example.telecom.evaluateSmartIslandLongPress
import com.example.telecom.inspectRealDisplayCutoutMetrics
import com.example.telecom.resolveSmartIslandCutoutPlacement
import com.example.telecom.resolveSmartIslandGestureOutcome
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Top-level HET Phone Call Smart Island Host.
 * - Renders nothing when [SmartIslandUiState.isIslandVisible] is false (`IDLE` or when
 *   `InCallActivity` Full-Screen Active Call Screen is visible), keeping the normal status bar
 *   and physical punch-hole camera area unchanged.
 * - Dynamically inspects the device's real `WindowInsets` / `DisplayCutout` (`boundingRects`
 *   and `safeInsetTop`) to position the pill directly around the real physical punch-hole camera
 *   without drawing any fake camera/lens icon and without overlapping status bar icons.
 */
@Composable
fun CallSmartIslandHost(
    islandState: SmartIslandUiState,
    onAnswerCall: () -> Unit,
    onDeclineOrEndCall: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleMute: () -> Unit,
    onExpandIsland: () -> Unit,
    onCollapseIsland: () -> Unit,
    onDismissIncomingKeepRinging: () -> Unit,
    onOpenFullScreenCall: () -> Unit,
    onUpdateLongPressProgress: (Float, Boolean) -> Unit,
    onCancelLongPressProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    var cutoutMetrics by remember {
        mutableStateOf(inspectRealDisplayCutoutMetrics(context, view))
    }

    DisposableEffect(view) {
        cutoutMetrics = inspectRealDisplayCutoutMetrics(context, view)
        val layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val updated = inspectRealDisplayCutoutMetrics(context, view)
            if (updated != cutoutMetrics) {
                cutoutMetrics = updated
            }
        }
        val observer = view.viewTreeObserver
        if (observer.isAlive) {
            observer.addOnGlobalLayoutListener(layoutListener)
        }
        onDispose {
            val currentObserver = view.viewTreeObserver
            if (currentObserver.isAlive) {
                currentObserver.removeOnGlobalLayoutListener(layoutListener)
            }
        }
    }

    val placement = remember(cutoutMetrics, islandState.visualState, islandState.expansionMode) {
        resolveSmartIslandCutoutPlacement(
            metrics = cutoutMetrics,
            visualState = islandState.visualState,
            expansionMode = islandState.expansionMode
        )
    }

    val isReducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) <= 0.01f
        } catch (_: Throwable) {
            false
        }
    }

    AnimatedVisibility(
        visible = islandState.isIslandVisible,
        enter = fadeIn(tween(if (isReducedMotion) 0 else 180)) +
            scaleIn(
                initialScale = 0.88f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) +
            expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut(tween(if (isReducedMotion) 0 else 180)) +
            scaleOut(targetScale = 0.85f, animationSpec = tween(if (isReducedMotion) 0 else 180)) +
            shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        x = placement.islandHorizontalOffsetFromCenterPx,
                        y = placement.islandTopOffsetPx
                    )
                }
                .testTag("call_smart_island_container"),
            contentAlignment = Alignment.TopCenter
        ) {
            CallSmartIslandPillSurface(
                islandState = islandState,
                placement = placement,
                isReducedMotion = isReducedMotion,
                onAnswerCall = onAnswerCall,
                onDeclineOrEndCall = onDeclineOrEndCall,
                onToggleSpeaker = onToggleSpeaker,
                onToggleMute = onToggleMute,
                onExpandIsland = onExpandIsland,
                onCollapseIsland = onCollapseIsland,
                onDismissIncomingKeepRinging = onDismissIncomingKeepRinging,
                onOpenFullScreenCall = onOpenFullScreenCall,
                onUpdateLongPressProgress = onUpdateLongPressProgress,
                onCancelLongPressProgress = onCancelLongPressProgress
            )
        }
    }
}

@Composable
private fun CallSmartIslandPillSurface(
    islandState: SmartIslandUiState,
    placement: SmartIslandCutoutPlacement,
    isReducedMotion: Boolean,
    onAnswerCall: () -> Unit,
    onDeclineOrEndCall: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleMute: () -> Unit,
    onExpandIsland: () -> Unit,
    onCollapseIsland: () -> Unit,
    onDismissIncomingKeepRinging: () -> Unit,
    onOpenFullScreenCall: () -> Unit,
    onUpdateLongPressProgress: (Float, Boolean) -> Unit,
    onCancelLongPressProgress: () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val visualState = islandState.visualState
    val isExpanded = islandState.expansionMode == SmartIslandExpansionMode.EXPANDED &&
        visualState != SmartIslandVisualState.CALL_ENDED &&
        visualState != SmartIslandVisualState.INCOMING_CALL

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "smart_island_drag_y"
    )

    val shouldAnimateWave = visualState.isVisible && !isReducedMotion
    val wavePhase: Float
    val glowPulse: Float
    if (shouldAnimateWave) {
        val infiniteTransition = rememberInfiniteTransition(label = "smart_island_wave_${visualState.name}")
        val animatedWave by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1250, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "smart_island_wave_phase"
        )
        val animatedGlow by infiniteTransition.animateFloat(
            initialValue = 0.68f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 920, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "smart_island_glow_pulse"
        )
        wavePhase = animatedWave
        glowPulse = animatedGlow
    } else {
        wavePhase = 0f
        glowPulse = 0.85f
    }

    var closingProgressTarget by remember(visualState) { mutableFloatStateOf(0f) }
    LaunchedEffect(visualState) {
        closingProgressTarget = if (visualState == SmartIslandVisualState.CALL_ENDED) 1f else 0f
    }
    val closingProgress by animateFloatAsState(
        targetValue = closingProgressTarget,
        animationSpec = tween(durationMillis = if (isReducedMotion) 0 else 780, easing = FastOutSlowInEasing),
        label = "smart_island_closing_progress"
    )

    val primaryColor = Color(visualState.primaryHex)
    val secondaryColor = Color(visualState.secondaryHex)
    val glowColor = Color(visualState.borderGlowHex)

    val islandWidthDp = with(density) { placement.islandWidthPx.toDp() }
    val islandHeightDp = with(density) { placement.islandHeightPx.toDp() }
    val cornerRadiusDp = if (isExpanded) 28.dp else (islandHeightDp / 2f)
    val islandShape = RoundedCornerShape(cornerRadiusDp)

    Box(
        modifier = Modifier
            .offset { IntOffset(0, animatedDragY.roundToInt()) }
            .width(islandWidthDp)
            .let { mod ->
                if (isExpanded) mod else mod.height(islandHeightDp)
            }
            .graphicsLayer {
                if (visualState == SmartIslandVisualState.CALL_ENDED) {
                    val shrink = 1f - (closingProgress * 0.14f)
                    scaleX = shrink
                    scaleY = shrink
                    alpha = (1f - (closingProgress * 0.45f)).coerceIn(0f, 1f)
                }
            }
            .pointerInput(visualState, islandState.expansionMode) {
                val swipeThresholdPx = 28.dp.toPx()
                val touchSlopPx = 14.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTimeMs = System.currentTimeMillis()
                    var totalDy = 0f
                    var totalDx = 0f
                    var isDragging = false
                    var holdCompleted = false
                    var holdStarted = false

                    val canHoldForFullScreen =
                        islandState.expansionMode == SmartIslandExpansionMode.COMPACT &&
                            visualState != SmartIslandVisualState.IDLE &&
                            visualState != SmartIslandVisualState.CALL_ENDED

                    var holdJob: Job? = null
                    if (canHoldForFullScreen) {
                        holdJob = scope.launch {
                            delay(180L)
                            holdStarted = true
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            while (true) {
                                val elapsed = System.currentTimeMillis() - downTimeMs
                                val eval = evaluateSmartIslandLongPress(
                                    heldDurationMs = elapsed,
                                    isReleased = false,
                                    requiredHoldMs = CallSmartIslandManager.LONG_PRESS_DURATION_MS
                                )
                                onUpdateLongPressProgress(eval.progress, true)
                                if (eval.isCompleted) {
                                    holdCompleted = true
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onUpdateLongPressProgress(0f, false)
                                    onOpenFullScreenCall()
                                    break
                                }
                                delay(32L)
                            }
                        }
                    }

                    var pointerUp = false
                    var childConsumedTap = false
                    while (!pointerUp) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) {
                            childConsumedTap = true
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            pointerUp = true
                        } else {
                            val delta = change.position - change.previousPosition
                            totalDx += delta.x
                            totalDy += delta.y
                            if (!isDragging && (abs(totalDy) > touchSlopPx || abs(totalDx) > touchSlopPx)) {
                                isDragging = true
                                holdJob?.cancel()
                                if (holdStarted) {
                                    onCancelLongPressProgress()
                                    holdStarted = false
                                }
                            }
                            if (isDragging) {
                                change.consume()
                                dragOffsetY = totalDy.coerceIn(-72f, 92f)
                            }
                        }
                    }

                    holdJob?.cancel()
                    val heldDuration = System.currentTimeMillis() - downTimeMs
                    dragOffsetY = 0f

                    if (holdCompleted) {
                        return@awaitEachGesture
                    }

                    if (holdStarted) {
                        onCancelLongPressProgress()
                        return@awaitEachGesture
                    }

                    if (childConsumedTap && !isDragging) {
                        return@awaitEachGesture
                    }

                    val outcome = resolveSmartIslandGestureOutcome(
                        visualState = visualState,
                        expansionMode = islandState.expansionMode,
                        isSingleTap = !isDragging && heldDuration < 500L,
                        isOutsideTap = false,
                        dragDeltaY = if (isDragging) totalDy else 0f,
                        wasLongPressStarted = false,
                        longPressDurationMs = heldDuration,
                        swipeThresholdPx = swipeThresholdPx
                    )

                    when (outcome) {
                        SmartIslandGestureOutcome.EXPAND_ISLAND -> {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            onExpandIsland()
                        }
                        SmartIslandGestureOutcome.COLLAPSE_ISLAND -> {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            onCollapseIsland()
                        }
                        SmartIslandGestureOutcome.DISMISS_INCOMING_BANNER_KEEP_RINGING -> {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                            onDismissIncomingKeepRinging()
                        }
                        SmartIslandGestureOutcome.OPEN_FULL_SCREEN_ACTIVE_CALL -> {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                            onOpenFullScreenCall()
                        }
                        SmartIslandGestureOutcome.CANCEL_LONG_PRESS_PROGRESS -> {
                            onCancelLongPressProgress()
                        }
                        SmartIslandGestureOutcome.NONE -> {}
                    }
                }
            }
            .testTag("call_smart_island_pill")
    ) {
        // Outer Neon Aura & Cutout-Transparent Pill Background Canvas
        // Uses CompositingStrategy.Offscreen + BlendMode.Clear at the exact physical camera cutout
        // coordinates so the real punch-hole camera is never covered by any opaque background or fake lens.
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            val w = size.width
            val h = size.height
            val cornerPx = cornerRadiusDp.toPx()

            // 1. Soft outer neon glow around the pill border
            for (layer in 1..2) {
                val expand = layer * 2.2.dp.toPx()
                val layerAlpha = (0.26f / layer) * glowPulse
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = layerAlpha),
                            glowColor.copy(alpha = layerAlpha * 0.95f),
                            secondaryColor.copy(alpha = layerAlpha)
                        )
                    ),
                    topLeft = Offset(-expand, -expand * 0.55f),
                    size = Size(w + expand * 2f, h + expand * 1.1f),
                    cornerRadius = CornerRadius(cornerPx + expand, cornerPx + expand),
                    style = Stroke(width = (2.6f - layer * 0.6f).dp.toPx())
                )
            }

            // 2. Incoming Call subtle concentric side ripple arcs
            if (visualState == SmartIslandVisualState.INCOMING_CALL) {
                val offsetPx = 4.5.dp.toPx() + (sin(wavePhase) * 1.5.dp.toPx())
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.28f * glowPulse),
                    topLeft = Offset(-offsetPx, -offsetPx * 0.4f),
                    size = Size(w + offsetPx * 2f, h + offsetPx * 0.8f),
                    cornerRadius = CornerRadius(cornerPx + offsetPx, cornerPx + offsetPx),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 3. Call Ended right-side purple/magenta sparkling particle dispersion
            if (visualState == SmartIslandVisualState.CALL_ENDED) {
                val particleOrigins = listOf(
                    Triple(0.84f, 0.22f, 2.0f),
                    Triple(0.90f, 0.48f, 2.4f),
                    Triple(0.87f, 0.76f, 1.8f),
                    Triple(0.95f, 0.30f, 1.5f),
                    Triple(0.97f, 0.66f, 2.1f),
                    Triple(0.82f, 0.86f, 1.7f),
                    Triple(1.02f, 0.46f, 1.9f)
                )
                particleOrigins.forEachIndexed { idx, (bx, by, radiusDp) ->
                    val driftX = (closingProgress * (10f + idx * 3.5f)).dp.toPx()
                    val driftY = ((idx % 3 - 1) * closingProgress * 6f).dp.toPx()
                    val pAlpha = (1f - closingProgress * 0.85f).coerceIn(0f, 1f)
                    val pColor = if (idx % 2 == 0) Color(0xFFD580FF) else Color(0xFFFF6EA7)
                    drawCircle(
                        color = pColor.copy(alpha = pAlpha * 0.85f),
                        radius = radiusDp.dp.toPx() * (1f - closingProgress * 0.35f),
                        center = Offset(w * bx + driftX, h * by + driftY)
                    )
                }
            }

            // 4. Translucent black glass pill body
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xEE08090E),
                        Color(0xF205060A),
                        Color(0xEE090A11)
                    )
                ),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )

            // 5. Thin state-colored neon border around the pill
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.96f),
                        glowColor.copy(alpha = 0.86f),
                        secondaryColor.copy(alpha = 0.96f)
                    )
                ),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 6. Transparent physical punch-hole camera aperture (BlendMode.Clear)
            // Keeps the actual physical camera completely unobstructed without drawing any fake lens.
            drawCircle(
                color = Color.Transparent,
                radius = placement.cameraHoleRadiusPx,
                center = Offset(
                    x = placement.cameraHoleCenterXInIslandPx.coerceIn(0f, w),
                    y = placement.cameraHoleCenterYInIslandPx.coerceIn(0f, h)
                ),
                blendMode = BlendMode.Clear
            )
        }

        // Foreground Content Layer positioned around the unobstructed physical camera cutout gap
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(islandShape)
                .padding(
                    horizontal = if (isExpanded) 14.dp else 8.dp,
                    vertical = if (isExpanded) 10.dp else 3.dp
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedContent(
                    targetState = Pair(visualState, isExpanded),
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(130))
                    },
                    label = "smart_island_content_transition"
                ) { (targetVisual, targetExpanded) ->
                    if (targetExpanded) {
                        ExpandedSmartIslandContent(
                            islandState = islandState,
                            placement = placement,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            wavePhase = wavePhase,
                            onToggleSpeaker = onToggleSpeaker,
                            onToggleMute = onToggleMute,
                            onEndCall = onDeclineOrEndCall,
                            onOpenFullScreenCall = onOpenFullScreenCall
                        )
                    } else {
                        CompactSmartIslandRow(
                            visualState = targetVisual,
                            islandState = islandState,
                            placement = placement,
                            wavePhase = wavePhase,
                            glowPulse = glowPulse,
                            onAnswerCall = onAnswerCall,
                            onDeclineCall = onDeclineOrEndCall
                        )
                    }
                }
            }

            // 3-Second Long-Press Circular Progress Overlay Ring when user holds the Compact Island
            if (islandState.isLongPressActive && islandState.longPressProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .size(28.dp)
                        .background(Color(0xE60B0D16), CircleShape)
                        .testTag("smart_island_long_press_progress_indicator"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(2.5.dp)) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            style = Stroke(width = 2.2.dp.toPx())
                        )
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(primaryColor, secondaryColor, ColorOsWhite)
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * islandState.longPressProgress.coerceIn(0f, 1f),
                            useCenter = false,
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    val remainingSec = ((1f - islandState.longPressProgress) * 3f).coerceAtLeast(0.1f)
                    Text(
                        text = String.format(java.util.Locale.US, "%.0fs", ceil(remainingSec)),
                        color = ColorOsWhite,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSmartIslandRow(
    visualState: SmartIslandVisualState,
    islandState: SmartIslandUiState,
    placement: SmartIslandCutoutPlacement,
    wavePhase: Float,
    glowPulse: Float,
    onAnswerCall: () -> Unit,
    onDeclineCall: () -> Unit
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val cameraGapWidthDp = with(density) { placement.cameraHoleWidthPx.toDp() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .testTag("smart_island_compact_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Wing (to the left of the physical punch-hole camera): State Badge + Caller Name & Live Timer
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmartIslandLeftBadge(
                visualState = visualState,
                glowPulse = glowPulse,
                isCompact = true
            )

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                val primaryHeadline = when (visualState) {
                    SmartIslandVisualState.CALL_RECORDING -> "Recording"
                    SmartIslandVisualState.AI_CALL_ASSISTANT -> "AI Assistant"
                    SmartIslandVisualState.CALL_ENDED -> "Call Ended"
                    else -> islandState.callerTitle.ifBlank { visualState.titleDefault }
                }
                Text(
                    text = primaryHeadline,
                    color = ColorOsWhite,
                    fontSize = 11.5.sp,
                    lineHeight = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("smart_island_primary_title")
                )

                if (visualState != SmartIslandVisualState.CALL_ENDED && islandState.subtitleText.isNotBlank()) {
                    Text(
                        text = islandState.subtitleText,
                        color = Color(0xFFD2D6E2),
                        fontSize = 9.5.sp,
                        lineHeight = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("smart_island_subtitle")
                    )
                }
            }
        }

        // Unobstructed Transparent Gap for the Real Physical Punch-Hole Camera (NO artificial camera lens drawn)
        Spacer(
            modifier = Modifier
                .width(cameraGapWidthDp)
                .testTag("smart_island_real_cutout_gap")
        )

        // Right Wing (to the right of the physical punch-hole camera): Controls or Live Animated Waveform
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            when (visualState) {
                SmartIslandVisualState.INCOMING_CALL -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEA3829))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDeclineCall()
                                }
                                .testTag("smart_island_incoming_decline_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "Decline call",
                                tint = ColorOsWhite,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C953))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAnswerCall()
                                }
                                .testTag("smart_island_incoming_answer_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = "Answer call",
                                tint = ColorOsWhite,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                SmartIslandVisualState.ACTIVE_CALL -> {
                    SmartIslandEqualizerWaveform(
                        barColor = Color(0xFF00E676),
                        accentColor = Color(0xFF69F0AE),
                        wavePhase = wavePhase,
                        modifier = Modifier
                            .width(50.dp)
                            .height(18.dp)
                            .testTag("smart_island_active_waveform")
                    )
                }

                SmartIslandVisualState.CALL_RECORDING -> {
                    SmartIslandEqualizerWaveform(
                        barColor = Color(0xFFFF3B4E),
                        accentColor = Color(0xFFFF8A80),
                        wavePhase = wavePhase * 1.15f,
                        modifier = Modifier
                            .width(50.dp)
                            .height(18.dp)
                            .testTag("smart_island_recording_waveform")
                    )
                }

                SmartIslandVisualState.AI_CALL_ASSISTANT -> {
                    SmartIslandAiRibbonWaveform(
                        wavePhase = wavePhase,
                        modifier = Modifier
                            .width(52.dp)
                            .height(18.dp)
                            .testTag("smart_island_ai_waveform")
                    )
                }

                SmartIslandVisualState.CALL_ENDED -> {
                    Spacer(modifier = Modifier.width(16.dp))
                }

                SmartIslandVisualState.IDLE -> {}
            }
        }
    }
}

/**
 * Expanded Smart Island Content with caller details at the top (split around the real physical
 * punch-hole camera with zero artificial camera lens drawn) and three functional pill controls at the bottom:
 * 1. Speaker toggle (`smart_island_btn_speaker`)
 * 2. Mute toggle (`smart_island_btn_mute`)
 * 3. End Call (`smart_island_btn_end`)
 */
@Composable
private fun ExpandedSmartIslandContent(
    islandState: SmartIslandUiState,
    placement: SmartIslandCutoutPlacement,
    primaryColor: Color,
    secondaryColor: Color,
    wavePhase: Float,
    onToggleSpeaker: () -> Unit,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    onOpenFullScreenCall: () -> Unit
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val cameraGapWidthDp = with(density) { placement.cameraHoleWidthPx.toDp() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_island_expanded_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header aligned around the unobstructed physical punch-hole camera gap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onOpenFullScreenCall()
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                SmartIslandLeftBadge(
                    visualState = islandState.visualState,
                    glowPulse = 0.95f,
                    isCompact = false
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = islandState.callerTitle.ifBlank { "Active Call" },
                        color = ColorOsWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("smart_island_expanded_caller_name")
                    )
                    val statusLine = when (islandState.visualState) {
                        SmartIslandVisualState.CALL_RECORDING ->
                            "REC ${islandState.formattedRecordingTimer} • ${islandState.formattedCallTimer}"
                        SmartIslandVisualState.AI_CALL_ASSISTANT ->
                            "AI ${islandState.aiStatusLabel} • ${islandState.formattedCallTimer}"
                        else -> islandState.formattedCallTimer
                    }
                    Text(
                        text = statusLine,
                        color = primaryColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("smart_island_expanded_timer")
                    )
                }
            }

            // Unobstructed gap for the real physical punch-hole camera
            Spacer(modifier = Modifier.width(cameraGapWidthDp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (islandState.visualState == SmartIslandVisualState.AI_CALL_ASSISTANT) {
                    SmartIslandAiRibbonWaveform(
                        wavePhase = wavePhase,
                        modifier = Modifier
                            .width(52.dp)
                            .height(22.dp)
                    )
                } else {
                    SmartIslandEqualizerWaveform(
                        barColor = primaryColor,
                        accentColor = secondaryColor,
                        wavePhase = wavePhase,
                        modifier = Modifier
                            .width(52.dp)
                            .height(22.dp)
                    )
                }
            }
        }

        // Bottom 3 Functional Pill Controls: 1. Speaker Toggle, 2. Mute Toggle, 3. End Call
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val speakerActive = islandState.isSpeakerOn
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (speakerActive) {
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF00B4D8), Color(0xFF2979FF))
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF282A32), Color(0xFF23252C))
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (speakerActive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSpeaker()
                    }
                    .testTag("smart_island_btn_speaker"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (speakerActive) "Speaker On" else "Speaker Off",
                    tint = ColorOsWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            val muteActive = islandState.isMuted
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (muteActive) {
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFD52B46), Color(0xFFFF3B4E))
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF282A32), Color(0xFF23252C))
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (muteActive) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleMute()
                    }
                    .testTag("smart_island_btn_mute"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MicOff,
                    contentDescription = if (muteActive) "Muted" else "Unmuted",
                    tint = ColorOsWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFFF3B30))
                    .border(
                        width = 1.dp,
                        color = Color(0xFFFF8A80).copy(alpha = 0.55f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEndCall()
                    }
                    .testTag("smart_island_btn_end"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "End Call",
                    tint = ColorOsWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SmartIslandLeftBadge(
    visualState: SmartIslandVisualState,
    glowPulse: Float,
    isCompact: Boolean
) {
    val badgeSize = if (isCompact) 24.dp else 30.dp
    val iconSize = if (isCompact) 13.dp else 16.dp

    when (visualState) {
        SmartIslandVisualState.INCOMING_CALL,
        SmartIslandVisualState.ACTIVE_CALL -> {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(Color(0xFF0E2C1F))
                    .border(1.dp, Color(0xFF00E676).copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        SmartIslandVisualState.CALL_RECORDING -> {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(Color(0xFF2B1016))
                    .border(1.dp, Color(0xFFFF3B4E).copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 10.dp else 12.dp)
                        .graphicsLayer {
                            val s = 0.88f + (glowPulse - 0.68f) * 0.45f
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(Color(0xFFFF2E44))
                )
            }
        }

        SmartIslandVisualState.AI_CALL_ASSISTANT -> {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(Color(0xFF0C2332))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(if (isCompact) 14.dp else 18.dp)) {
                    val w = size.width
                    val h = size.height
                    fun drawFourPointStar(cx: Float, cy: Float, r: Float, color: Color) {
                        val path = Path().apply {
                            moveTo(cx, cy - r)
                            quadraticTo(cx, cy, cx + r, cy)
                            quadraticTo(cx, cy, cx, cy + r)
                            quadraticTo(cx, cy, cx - r, cy)
                            quadraticTo(cx, cy, cx, cy - r)
                            close()
                        }
                        drawPath(path = path, color = color)
                    }
                    drawFourPointStar(w * 0.44f, h * 0.46f, w * 0.42f, Color(0xFF26E8FF))
                    drawFourPointStar(w * 0.78f, h * 0.76f, w * 0.20f, Color(0xFF80F4FF))
                }
            }
        }

        SmartIslandVisualState.CALL_ENDED -> {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(Color(0xFF2A1118))
                    .border(1.dp, Color(0xFFFF3B4E).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = null,
                    tint = Color(0xFFFF4B55),
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        SmartIslandVisualState.IDLE -> {}
    }
}

/**
 * Multi-bar equalizer waveform with outer dots (`•|||•|||•`) matching Reference Image 1 & 2
 * for `ACTIVE_CALL` (green) and `CALL_RECORDING` (red).
 */
@Composable
private fun SmartIslandEqualizerWaveform(
    barColor: Color,
    accentColor: Color,
    wavePhase: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val barMultipliers = floatArrayOf(0.14f, 0.72f, 0.44f, 0.88f, 0.36f, 0.62f, 0.94f, 0.50f, 0.68f, 0.38f, 0.14f)
        val count = barMultipliers.size
        val stepX = w / (count + 1)
        val strokeW = 2.0.dp.toPx()

        for (i in 0 until count) {
            val x = stepX * (i + 1)
            if (i == 0 || i == count - 1) {
                drawCircle(
                    color = barColor,
                    radius = 1.6.dp.toPx(),
                    center = Offset(x, cy)
                )
            } else {
                val baseRatio = barMultipliers[i]
                val dynamicMod = 0.62f + 0.38f * abs(sin(wavePhase + i * 0.68f))
                val barHalfH = (h * 0.45f * baseRatio * dynamicMod).coerceAtLeast(1.8.dp.toPx())
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor, barColor, accentColor)
                    ),
                    start = Offset(x, cy - barHalfH),
                    end = Offset(x, cy + barHalfH),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Cyan-to-purple layered ribbon wave visual matching the `AI Call Assistant (Active)` row in Reference Image 1.
 */
@Composable
private fun SmartIslandAiRibbonWaveform(
    wavePhase: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2f

        val primaryPath = Path()
        val secondaryPath = Path()
        val steps = 28
        for (i in 0..steps) {
            val fraction = i.toFloat() / steps.toFloat()
            val x = w * fraction
            val envelope = sin(fraction * PI).toFloat()
            val y1 = cy + sin(fraction * 3.2f * PI + wavePhase).toFloat() * (h * 0.38f) * envelope
            val y2 = cy + cos(fraction * 2.6f * PI - wavePhase * 0.9f).toFloat() * (h * 0.28f) * envelope
            if (i == 0) {
                primaryPath.moveTo(x, y1)
                secondaryPath.moveTo(x, y2)
            } else {
                primaryPath.lineTo(x, y1)
                secondaryPath.lineTo(x, y2)
            }
        }

        drawPath(
            path = secondaryPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.45f),
                    Color(0xFF7C4DFF).copy(alpha = 0.55f),
                    Color(0xFFE040FB).copy(alpha = 0.45f)
                )
            ),
            style = Stroke(width = 3.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawPath(
            path = primaryPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF65B2FF),
                    Color(0xFFB388FF),
                    Color(0xFFE040FB)
                )
            ),
            style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Clear, user-controlled Cross-App Smart Island Overlay Permission banner shown when an ongoing call
 * is active (or in Settings) if `Settings.canDrawOverlays(context)` is not yet granted.
 */
@Composable
fun SmartIslandOverlayPermissionBanner(
    onGrantClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF141826),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.6f), Color(0xFFB388FF).copy(alpha = 0.6f))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("smart_island_overlay_permission_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Display Call Smart Island Over Other Apps",
                    color = ColorOsWhite,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Allow 'Display over other apps' so the Call Smart Island stays visible on your Home Screen and other apps during calls.",
                    color = Color(0xFFCFD4E4),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDismissClick() }
                        .testTag("smart_island_overlay_NotNow_btn")
                ) {
                    Text(
                        text = "Later",
                        color = Color(0xFFD2D6E2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                Surface(
                    color = ColorOsGreen,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onGrantClick() }
                        .testTag("smart_island_overlay_enable_btn")
                ) {
                    Text(
                        text = "Enable",
                        color = ColorOsWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

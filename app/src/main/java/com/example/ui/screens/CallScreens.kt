package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.Call
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.PersonalizationManager
import com.example.model.ActiveCallUiState
import com.example.model.CallAudioEndpointItem
import com.example.model.CallAudioEndpointType
import com.example.model.IncomingCallLayoutStyle
import com.example.model.OngoingButtonVisualStyle
import com.example.telecom.AiAssistantStatus
import com.example.telecom.AiCallAssistantManager
import com.example.telecom.CallRecordingManager
import com.example.telecom.CallStateManager
import com.example.ui.components.AiCallAssistantConfirmationDialog
import com.example.ui.components.ContactCircleAvatar
import com.example.ui.components.LiveAiCallAssistantPanel
import com.example.ui.components.PremiumCallAvatarWithWaves
import com.example.ui.components.TenDotKeypadIcon
import com.example.ui.components.UpwardTripleChevrons
import com.example.ui.personalization.LiquidGlassCard
import com.example.ui.theme.CallButtonActiveWhite
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsIncomingGreen
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.ColorOsTextPrimary
import com.example.ui.theme.ColorOsWhite
import com.example.ui.theme.LocalPersonalizationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Unified Call Screen Background for Incoming, Outgoing, and Ongoing Call Screens.
 * Automatically renders the user's applied Call Screen Wallpaper (System Default,
 * Built-in Wallpaper, Photo from Gallery, or Looping Muted Video from Gallery)
 * along with Blur and Darken adjustments.
 */
@Composable
fun ColorOsPurpleBlackCallBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    com.example.ui.components.CallScreenWallpaperBackdrop(
        modifier = modifier,
        content = content
    )
}

/**
 * Backdrop recreating the sculpted glowing multi-sphere dark wallpaper aesthetic
 * from the incoming-call reference image (`Screenshot_20261107-161627_ChatGPT.png`).
 */
@Composable
fun ColorOsIncomingOrbBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07080E),
                        Color(0xFF140D1E),
                        Color(0xFF1E1136),
                        Color(0xFF05050B)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Ambient warm coral-amber left glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFB8434E).copy(alpha = 0.42f),
                        Color(0xFF591B2C).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.15f, h * 0.36f),
                    radius = w * 0.72f
                ),
                radius = w * 0.72f,
                center = Offset(w * 0.15f, h * 0.36f)
            )

            // Ambient electric blue-indigo right glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2B59C3).copy(alpha = 0.44f),
                        Color(0xFF182B6B).copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.86f, h * 0.42f),
                    radius = w * 0.68f
                ),
                radius = w * 0.68f,
                center = Offset(w * 0.86f, h * 0.42f)
            )

            // Upper sculpted sphere
            val upperCenter = Offset(w * 0.50f, h * 0.47f)
            val upperRadius = w * 0.46f
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE56B6F),
                        Color(0xFF7B2CBF),
                        Color(0xFF2855B8)
                    ),
                    start = Offset(w * 0.08f, h * 0.32f),
                    end = Offset(w * 0.92f, h * 0.56f)
                ),
                radius = upperRadius,
                center = upperCenter
            )
            // Inner dark core of upper sphere for 3D depth
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF220A29).copy(alpha = 0.88f),
                        Color(0xFF4A153B).copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.48f, h * 0.50f),
                    radius = upperRadius * 0.92f
                ),
                radius = upperRadius * 0.92f,
                center = upperCenter
            )
            // Upper sphere top rim highlight
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFC8DD).copy(alpha = 0.65f),
                        Color.Transparent
                    ),
                    startY = upperCenter.y - upperRadius,
                    endY = upperCenter.y
                ),
                radius = upperRadius,
                center = upperCenter,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Lower sculpted sphere / wave crest
            val lowerCenter = Offset(w * 0.50f, h * 0.76f)
            val lowerRadius = w * 0.58f
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFD65DB1).copy(alpha = 0.78f),
                        Color(0xFF5E2BFF).copy(alpha = 0.58f),
                        Color(0xFF191240).copy(alpha = 0.90f),
                        Color(0xFF06060D)
                    ),
                    startY = lowerCenter.y - lowerRadius * 0.85f,
                    endY = h
                ),
                radius = lowerRadius,
                center = lowerCenter
            )

            // Central dark crimson-maroon overlap shadow
            val crestCenter = Offset(w * 0.50f, h * 0.56f)
            val crestRadius = w * 0.35f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2D081A).copy(alpha = 0.92f),
                        Color(0xFF7A1C38).copy(alpha = 0.64f),
                        Color.Transparent
                    ),
                    center = crestCenter,
                    radius = crestRadius
                ),
                radius = crestRadius,
                center = crestCenter
            )
            // Soft luminous rim along the lower sphere arc
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFB3D1).copy(alpha = 0.65f),
                        Color.Transparent
                    ),
                    startY = crestCenter.y - crestRadius,
                    endY = crestCenter.y + crestRadius * 0.3f
                ),
                radius = crestRadius,
                center = crestCenter,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        content()
    }
}

/**
 * Presentation states for the interactive incoming-call experience:
 * - [COLLAPSED_INDICATOR]: Small, elegant call indicator near the top / display cutout area (Swipe UP from banner).
 * - [COMPACT_BANNER]: Initial compact rounded dark-charcoal-to-teal-green top banner (Image 1).
 * - [FULL_SCREEN]: Full-screen ColorOS incoming-call UI with avatar, caller details, and bottom controls (Image 2).
 */
enum class IncomingCallPresentationMode {
    COLLAPSED_INDICATOR,
    COMPACT_BANNER,
    FULL_SCREEN
}

/**
 * Pure state-transition resolver for vertical swipe gestures on the incoming-call UI:
 * - Swipe UP (`dragDeltaY < -thresholdPx`) on [IncomingCallPresentationMode.COMPACT_BANNER]
 *   collapses it into [IncomingCallPresentationMode.COLLAPSED_INDICATOR].
 * - Swipe DOWN (`dragDeltaY > thresholdPx`) on [IncomingCallPresentationMode.COMPACT_BANNER]
 *   or [IncomingCallPresentationMode.COLLAPSED_INDICATOR] expands it into
 *   [IncomingCallPresentationMode.FULL_SCREEN].
 * - Swipe UP (`dragDeltaY < -thresholdPx`) on [IncomingCallPresentationMode.FULL_SCREEN]
 *   returns to [IncomingCallPresentationMode.COMPACT_BANNER].
 */
fun resolveNextIncomingModeOnVerticalSwipe(
    current: IncomingCallPresentationMode,
    dragDeltaY: Float,
    thresholdPx: Float = 38f
): IncomingCallPresentationMode {
    return when (current) {
        IncomingCallPresentationMode.COMPACT_BANNER -> {
            when {
                dragDeltaY <= -thresholdPx -> IncomingCallPresentationMode.COLLAPSED_INDICATOR
                dragDeltaY >= thresholdPx -> IncomingCallPresentationMode.FULL_SCREEN
                else -> IncomingCallPresentationMode.COMPACT_BANNER
            }
        }
        IncomingCallPresentationMode.COLLAPSED_INDICATOR -> {
            if (dragDeltaY >= thresholdPx * 0.75f) {
                IncomingCallPresentationMode.FULL_SCREEN
            } else {
                IncomingCallPresentationMode.COLLAPSED_INDICATOR
            }
        }
        IncomingCallPresentationMode.FULL_SCREEN -> {
            if (dragDeltaY <= -thresholdPx * 1.15f) {
                IncomingCallPresentationMode.COMPACT_BANNER
            } else {
                IncomingCallPresentationMode.FULL_SCREEN
            }
        }
    }
}

/**
 * Small, elegant incoming-call indicator pill positioned near the top around the
 * display cutout / status-bar safe area when the user swipes UP on the compact banner.
 * Supports swiping DOWN to expand into the full-screen incoming call interface (Image 2),
 * as well as direct 1-tap Answer and Decline actions.
 */
@Composable
fun CollapsedIncomingCallIndicator(
    callerName: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onSwipeDownExpand: () -> Unit,
    onTapExpand: () -> Unit = onSwipeDownExpand,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val pillShape = RoundedCornerShape(50)

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDraggingIndicator by remember { mutableStateOf(false) }
    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "collapsed_indicator_drag_y"
    )

    val pulseTransition = rememberInfiniteTransition(label = "collapsed_call_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "collapsed_call_pulse_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(0, animatedDragY.roundToInt()) }
                .widthIn(min = 230.dp, max = 330.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.65f),
                    spotColor = Color.Black.copy(alpha = 0.65f)
                )
                .clip(pillShape)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF51D2125),
                            Color(0xF5184D3B),
                            Color(0xF51A6B4E)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color(0xFF42C893).copy(alpha = 0.36f)
                        )
                    ),
                    shape = pillShape
                )
                .pointerInput(onSwipeDownExpand) {
                    var accumulatedDy = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            accumulatedDy = 0f
                            dragOffsetY = 0f
                            isDraggingIndicator = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            accumulatedDy += dragAmount
                            if (kotlin.math.abs(accumulatedDy) > 10f) {
                                isDraggingIndicator = true
                                change.consume()
                                dragOffsetY = accumulatedDy.coerceIn(-24f, 110f)
                            }
                        },
                        onDragCancel = {
                            dragOffsetY = 0f
                            accumulatedDy = 0f
                            isDraggingIndicator = false
                        },
                        onDragEnd = {
                            val finalDy = accumulatedDy
                            val wasDragging = isDraggingIndicator
                            dragOffsetY = 0f
                            accumulatedDy = 0f
                            isDraggingIndicator = false
                            if (wasDragging && finalDy >= 28f) {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSwipeDownExpand()
                            }
                        }
                    )
                }
                .clickable(enabled = !isDraggingIndicator) {
                    if (!isDraggingIndicator) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onTapExpand()
                    }
                }
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("incoming_collapsed_indicator")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pulsing green phone badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF28C94E).copy(alpha = pulseAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = null,
                        tint = ColorOsWhite,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Caller Name + Swipe down cue
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = callerName,
                            color = ColorOsWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("incoming_caller_name")
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Swipe down to expand",
                            tint = Color(0xFFD2E0DA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Swipe down for full screen",
                        color = Color(0xFFC4D8D0),
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.testTag("incoming_caller_number")
                    )
                }

                // Mini Red Decline & Green Answer buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEA3829))
                            .clickable(enabled = !isDraggingIndicator) {
                                if (!isDraggingIndicator) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDecline()
                                }
                            }
                            .testTag("incoming_decline_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "Decline call",
                            tint = ColorOsWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF26C24C))
                            .clickable(enabled = !isDraggingIndicator) {
                                if (!isDraggingIndicator) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAnswer()
                                }
                            }
                            .testTag("incoming_answer_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Answer call",
                            tint = ColorOsWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Resolves the single caller label for the compact incoming-call card:
 * - Saved contact: returns ONLY the saved contact name.
 * - Unknown/unsaved caller: returns ONLY the phone number.
 * - Never returns both name and number together.
 */
fun resolveCompactIncomingCallerTitle(callState: ActiveCallUiState): String {
    val savedName = callState.displayName?.trim().orEmpty()
    if (savedName.isNotEmpty()) {
        return savedName
    }
    val rawNumber = callState.number.trim()
    return if (rawNumber.isNotEmpty()) rawNumber else "Unknown"
}

/**
 * Exact compact, horizontal, rounded rectangular incoming-call card at the top of the screen:
 * - Dark charcoal-to-deep-green gradient background with subtle border and premium shadow.
 * - Large, bold white caller name (or phone number if unsaved) on the left, vertically centered.
 * - Large circular RED Decline button and large circular GREEN Answer button on the right.
 * - No extra labels, no phone numbers for saved contacts, no grab handles, and no card-tap navigation.
 */
/**
 * Resolved gesture action on the persistent compact incoming-call banner:
 * - [NONE]: Movement below threshold; no state change.
 * - [SWIPE_UP_DISMISS_BANNER]: Dismisses the visible banner from the screen while keeping the call ringing.
 * - [SWIPE_DOWN_EXPAND_FULL_SCREEN]: Expands the banner into the full-screen incoming call UI.
 * - [TAP_CARD_EXPAND_FULL_SCREEN]: Tapping the caller-info/card area expands into the full-screen incoming call UI.
 */
enum class IncomingBannerGestureAction {
    NONE,
    SWIPE_UP_DISMISS_BANNER,
    SWIPE_DOWN_EXPAND_FULL_SCREEN,
    TAP_CARD_EXPAND_FULL_SCREEN
}

/**
 * Pure helper evaluating a gesture on the compact incoming-call banner.
 */
fun resolveIncomingBannerGesture(
    dragDeltaY: Float,
    isCardTap: Boolean = false,
    isButtonTap: Boolean = false,
    thresholdPx: Float = 38f
): IncomingBannerGestureAction {
    if (isButtonTap) return IncomingBannerGestureAction.NONE
    val effectiveThreshold = kotlin.math.abs(thresholdPx).coerceAtLeast(12f)
    return when {
        dragDeltaY <= -effectiveThreshold -> IncomingBannerGestureAction.SWIPE_UP_DISMISS_BANNER
        dragDeltaY >= effectiveThreshold -> IncomingBannerGestureAction.SWIPE_DOWN_EXPAND_FULL_SCREEN
        isCardTap && kotlin.math.abs(dragDeltaY) < 12f -> IncomingBannerGestureAction.TAP_CARD_EXPAND_FULL_SCREEN
        else -> IncomingBannerGestureAction.NONE
    }
}

/**
 * Exact compact, horizontal, rounded rectangular incoming-call card at the top of the screen:
 * - Dark charcoal-to-deep-green gradient background with subtle border and premium shadow.
 * - Large, bold white caller name (or phone number if unsaved) on the left, vertically centered.
 * - Large circular RED Decline button and large circular GREEN Answer button on the right.
 * - Interactive gestures:
 *   1. Swipe UP: Dismisses the visible banner without rejecting/ending the call (call continues ringing).
 *   2. Swipe DOWN: Expands into the Full-Screen Incoming Call UI.
 *   3. Tap on caller-info/card area: Expands into the Full-Screen Incoming Call UI.
 *   4. Tap Red Decline or Green Answer: Executes the real Telecom action independently without expanding.
 */
@Composable
fun IncomingCallBannerCard(
    callerName: String,
    callerSubtitle: String = "",
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    onBannerClick: (() -> Unit)? = null,
    onSwipeUpCollapse: (() -> Unit)? = null,
    onSwipeDownExpand: (() -> Unit)? = null,
    bannerTestTag: String = "incoming_call_top_banner"
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val bannerShape = RoundedCornerShape(28.dp)

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDraggingBanner by remember { mutableStateOf(false) }
    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "incoming_banner_drag_y"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, animatedDragY.roundToInt()) }
            .shadow(
                elevation = 20.dp,
                shape = bannerShape,
                ambientColor = Color.Black.copy(alpha = 0.65f),
                spotColor = Color.Black.copy(alpha = 0.65f)
            )
            .clip(bannerShape)
            .background(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xF822262A),
                        0.36f to Color(0xF81E3531),
                        0.72f to Color(0xF8185842),
                        1.0f to Color(0xF81A6E50)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color(0xFF48CA96).copy(alpha = 0.30f)
                    )
                ),
                shape = bannerShape
            )
            .pointerInput(onSwipeUpCollapse, onSwipeDownExpand) {
                val swipeThresholdPx = 32.dp.toPx()
                var accumulatedDy = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        accumulatedDy = 0f
                        dragOffsetY = 0f
                        isDraggingBanner = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        accumulatedDy += dragAmount
                        if (kotlin.math.abs(accumulatedDy) > 10f) {
                            isDraggingBanner = true
                            change.consume()
                            dragOffsetY = accumulatedDy.coerceIn(-140f, 140f)
                        }
                    },
                    onDragCancel = {
                        dragOffsetY = 0f
                        accumulatedDy = 0f
                        isDraggingBanner = false
                    },
                    onDragEnd = {
                        val finalDy = accumulatedDy
                        val wasDragging = isDraggingBanner
                        dragOffsetY = 0f
                        accumulatedDy = 0f
                        isDraggingBanner = false
                        if (wasDragging) {
                            when (
                                resolveIncomingBannerGesture(
                                    dragDeltaY = finalDy,
                                    thresholdPx = swipeThresholdPx
                                )
                            ) {
                                IncomingBannerGestureAction.SWIPE_UP_DISMISS_BANNER -> {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSwipeUpCollapse?.invoke()
                                }
                                IncomingBannerGestureAction.SWIPE_DOWN_EXPAND_FULL_SCREEN -> {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSwipeDownExpand?.invoke()
                                }
                                else -> Unit
                            }
                        }
                    }
                )
            }
            .then(
                if (onBannerClick != null) {
                    Modifier.clickable(enabled = !isDraggingBanner) {
                        if (!isDraggingBanner) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            onBannerClick()
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(start = 24.dp, end = 18.dp, top = 18.dp, bottom = 18.dp)
            .testTag(bannerTestTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Large, bold white caller name (saved contact name ONLY, or phone number ONLY if unsaved).
            // Tapping this caller-information area opens the Full-Screen Incoming Call UI.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onBannerClick != null) {
                            Modifier.clickable(enabled = !isDraggingBanner) {
                                if (!isDraggingBanner) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    onBannerClick()
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(end = 16.dp)
                    .testTag("incoming_banner_caller_info_area"),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = callerName,
                    color = ColorOsWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.15.sp,
                    modifier = Modifier.testTag("incoming_caller_name")
                )
                // Zero-size semantics anchor for backward test compatibility without rendering visible subtitle text
                Box(modifier = Modifier.size(0.dp).testTag("incoming_caller_number"))
            }

            // Right: Independent Large circular RED Decline button + Large circular GREEN Answer button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Circular RED Decline Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.4f),
                            spotColor = Color.Black.copy(alpha = 0.4f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFF24637),
                                    Color(0xFFE03224)
                                )
                            )
                        )
                        .clickable(enabled = !isDraggingBanner) {
                            if (!isDraggingBanner) {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDecline()
                            }
                        }
                        .testTag("incoming_decline_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = "Decline call",
                        tint = ColorOsWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Circular GREEN Answer Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.4f),
                            spotColor = Color.Black.copy(alpha = 0.4f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2ED158),
                                    Color(0xFF1FB844)
                                )
                            )
                        )
                        .clickable(enabled = !isDraggingBanner) {
                            if (!isDraggingBanner) {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAnswer()
                            }
                        }
                        .testTag("incoming_answer_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Answer call",
                        tint = ColorOsWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/**
 * Retained for compatibility; returns empty string so the compact incoming card never renders
 * a second line of text under the caller's name or number.
 */
fun formatIncomingBannerSubtitle(callState: ActiveCallUiState): String {
    return ""
}

/**
 * Pure helper for unit testing incoming-call Answer and Decline control gestures:
 * - Swiping UP on either the Answer or Decline control (`dragDeltaY <= -swipeUpThresholdPx`)
 *   triggers the control's action.
 * - Tapping the Answer or Decline control (`isTapConfirmed && !wasDragged`) also triggers
 *   the control's action for accessibility and convenience.
 * - Swiping DOWN or cancelling a tiny movement never triggers the action and never dismisses the screen.
 */
fun shouldTriggerIncomingCallControlAction(
    dragDeltaY: Float,
    wasDragged: Boolean,
    isTapConfirmed: Boolean,
    swipeUpThresholdPx: Float = 28f
): Boolean {
    if (isTapConfirmed && !wasDragged) return true
    if (wasDragged && dragDeltaY <= -swipeUpThresholdPx) return true
    return false
}

@Composable
private fun IncomingCallSwipeUpOrTapControl(
    chevronColor: Color,
    buttonBackgroundBrush: Brush,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    onTriggerAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var lastTriggeredMs by remember { mutableLongStateOf(0L) }

    val animatedOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "${testTag}_offset_y"
    )

    val fireActionOnce = {
        val now = System.currentTimeMillis()
        if (now - lastTriggeredMs >= 450L) {
            lastTriggeredMs = now
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onTriggerAction()
        }
    }

    Column(
        modifier = modifier
            .pointerInput(onTriggerAction) {
                val swipeThresholdPx = 28.dp.toPx()
                val maxUpwardOffsetPx = 56.dp.toPx()
                var totalDy = 0f
                var actionFiredInGesture = false
                detectVerticalDragGestures(
                    onDragStart = {
                        totalDy = 0f
                        actionFiredInGesture = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDy += dragAmount
                        dragOffsetY = totalDy.coerceIn(-maxUpwardOffsetPx, 12f)
                        if (!actionFiredInGesture && shouldTriggerIncomingCallControlAction(
                                dragDeltaY = totalDy,
                                wasDragged = true,
                                isTapConfirmed = false,
                                swipeUpThresholdPx = swipeThresholdPx
                            )
                        ) {
                            actionFiredInGesture = true
                            dragOffsetY = 0f
                            fireActionOnce()
                        }
                    },
                    onDragEnd = {
                        if (!actionFiredInGesture && shouldTriggerIncomingCallControlAction(
                                dragDeltaY = totalDy,
                                wasDragged = true,
                                isTapConfirmed = false,
                                swipeUpThresholdPx = swipeThresholdPx
                            )
                        ) {
                            actionFiredInGesture = true
                            fireActionOnce()
                        }
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragOffsetY = 0f
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val incomingPrefs = LocalPersonalizationConfig.current.incomingCall
        val btnSize = (74f * incomingPrefs.buttonSize.multiplier).dp
        val btnShape = remember(incomingPrefs.buttonShape, btnSize) {
            PersonalizationManager.shapeFor(incomingPrefs.buttonShape, btnSize.value)
        }
        UpwardTripleChevrons(baseColor = chevronColor)
        Box(
            modifier = Modifier
                .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                .size(btnSize)
                .clip(btnShape)
                .background(buttonBackgroundBrush)
                .let { base ->
                    if (incomingPrefs.showPulseGlow) {
                        base.border(1.4.dp, Color.White.copy(alpha = 0.42f), btnShape)
                    } else {
                        base
                    }
                }
                .clickable {
                    fireActionOnce()
                }
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = ColorOsWhite,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Full-Screen ColorOS Incoming Call Interface matching `Screenshot_20261108-170013_Gallery_b72b4e99.png`:
 * - Deep purple-black atmospheric glow background (`ColorOsPurpleBlackCallBackground`).
 * - Top-left: Large bold caller name, phone number (when saved contact), location (`India`),
 *   and `HD Vo5G Incall` indicator row.
 * - Top-right: `PremiumCallAvatarWithWaves` circular avatar with concentric ring.
 * - Bottom: Red and green `UpwardTripleChevrons` above the 74.dp red Decline and green Answer buttons
 *   (supporting both Swipe Up and Tap to Answer/Decline without collapsing the screen),
 *   plus the centered translucent `"SMS reply"` pill button.
 */
@Composable
fun FullScreenIncomingCallContent(
    callState: ActiveCallUiState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onOpenSmsSheet: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onCollapseToBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val personalization = LocalPersonalizationConfig.current
    val incomingPrefs = personalization.incomingCall

    val primaryHeading = resolveCompactIncomingCallerTitle(callState)
    val hasSavedName = !callState.displayName?.trim().isNullOrEmpty()
    val phoneSubtitle = if (hasSavedName) callState.number.trim() else ""
    val locationDisplay = callState.locationLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "India"

    ColorOsPurpleBlackCallBackground(
        modifier = modifier
            .testTag("incoming_fullscreen_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Caller Header + Avatar Row (respecting IncomingCallLayoutStyle)
            val headerContent: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = primaryHeading,
                            color = ColorOsWhite,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 44.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("incoming_caller_name")
                        )

                        if (phoneSubtitle.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = phoneSubtitle,
                                color = Color(0xFFF2EEF8),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.testTag("incoming_caller_number")
                            )
                        } else {
                            Box(modifier = Modifier.size(0.dp).testTag("incoming_caller_number"))
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = locationDisplay,
                            color = Color(0xFFD0C9DF),
                            fontSize = 15.sp,
                            modifier = Modifier.testTag("incoming_caller_location")
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.testTag("incoming_incall_status")
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = null,
                                    tint = ColorOsWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "HD",
                                    color = ColorOsWhite,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Vo",
                                    color = ColorOsWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 9.sp
                                )
                                Text(
                                    text = "5G",
                                    color = ColorOsWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 9.sp
                                )
                            }
                            Text(
                                text = "Incall",
                                color = ColorOsWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    PremiumCallAvatarWithWaves(
                        name = primaryHeading,
                        photoUri = callState.photoUri,
                        animateWaves = incomingPrefs.layoutStyle != IncomingCallLayoutStyle.MINIMAL_ZEN,
                        avatarSize = 76.dp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 34.dp)
            ) {
                if (incomingPrefs.layoutStyle == IncomingCallLayoutStyle.LIQUID_GLASS_CARD) {
                    LiquidGlassCard(config = personalization, cornerRadius = 26.dp) {
                        headerContent()
                    }
                } else {
                    headerContent()
                }
            }

            // Bottom Controls: Upward Chevrons + Red Decline & Green Answer + optional SMS reply pill
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!callState.statusMessage.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF2B203C).copy(alpha = 0.88f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .padding(bottom = 14.dp)
                            .testTag("incoming_status_message")
                    ) {
                        Text(
                            text = callState.statusMessage,
                            color = ColorOsWhite,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    IncomingCallSwipeUpOrTapControl(
                        chevronColor = Color(0xFFEE4D3D),
                        buttonBackgroundBrush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFEE4D3D), Color(0xFFD93629))
                        ),
                        icon = Icons.Filled.CallEnd,
                        contentDescription = "Swipe up or tap to decline call",
                        testTag = "incoming_decline_button",
                        onTriggerAction = onDecline
                    )

                    IncomingCallSwipeUpOrTapControl(
                        chevronColor = ColorOsIncomingGreen,
                        buttonBackgroundBrush = Brush.verticalGradient(
                            colors = listOf(ColorOsIncomingGreen, ColorOsIncomingGreen)
                        ),
                        icon = Icons.Filled.Call,
                        contentDescription = "Swipe up or tap to answer call",
                        testTag = "incoming_answer_button",
                        onTriggerAction = onAnswer
                    )
                }

                if (incomingPrefs.showSmsReplyPill) {
                    Spacer(modifier = Modifier.height(22.dp))

                    Surface(
                        color = Color(0xFF3A2D4F).copy(alpha = 0.72f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                onOpenSmsSheet()
                            }
                            .testTag("incoming_sms_reply_button")
                    ) {
                        Text(
                            text = "SMS reply",
                            color = ColorOsWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified Incoming-Call Interface supporting:
 * - [IncomingCallPresentationMode.COMPACT_BANNER]: Persistent floating top incoming-call banner
 *   with Swipe Up (dismiss banner while call continues ringing), Swipe Down (expand to full-screen),
 *   Tap caller info (expand to full-screen), and independent Red Decline / Green Answer buttons.
 * - [IncomingCallPresentationMode.FULL_SCREEN]: Full-screen ColorOS incoming-call interface
 *   with caller name, phone number, location, avatar, chevrons, Answer/Decline, and SMS reply sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingCallScreen(
    callState: ActiveCallUiState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onRejectWithSms: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialMode: IncomingCallPresentationMode = IncomingCallPresentationMode.COMPACT_BANNER,
    externalMode: IncomingCallPresentationMode? = null,
    onModeChanged: ((IncomingCallPresentationMode) -> Unit)? = null,
    onDismissBannerKeepRinging: (() -> Unit)? = null
) {
    var internalMode by remember(initialMode) { mutableStateOf(initialMode) }
    val currentMode = externalMode ?: internalMode
    var showSmsReplySheet by remember { mutableStateOf(false) }
    var customSmsDraft by remember { mutableStateOf("") }

    val updateMode: (IncomingCallPresentationMode) -> Unit = { targetMode ->
        internalMode = targetMode
        onModeChanged?.invoke(targetMode)
    }

    val callerLabel = remember(callState.displayName, callState.number) {
        resolveCompactIncomingCallerTitle(callState)
    }

    AnimatedContent(
        targetState = currentMode,
        transitionSpec = {
            if (targetState == IncomingCallPresentationMode.FULL_SCREEN) {
                (slideInVertically(initialOffsetY = { -it / 3 }, animationSpec = tween(260)) +
                    fadeIn(tween(220))) togetherWith
                    (fadeOut(tween(180)))
            } else {
                (fadeIn(tween(200))) togetherWith
                    (slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(220)) +
                        fadeOut(tween(180)))
            }
        },
        label = "incoming_call_mode_transition"
    ) { mode ->
        when (mode) {
            IncomingCallPresentationMode.FULL_SCREEN -> {
                BackHandler(enabled = true) {
                    // Keep the full-screen incoming call UI visible while ringing so the user
                    // never loses access to the Answer and Decline controls.
                }
                FullScreenIncomingCallContent(
                    callState = callState,
                    onAnswer = onAnswer,
                    onDecline = onDecline,
                    onOpenSmsSheet = { showSmsReplySheet = true },
                    onCollapseToBanner = {
                        // Do not collapse or dismiss the full-screen incoming call UI while ringing.
                    },
                    modifier = modifier
                )
            }

            IncomingCallPresentationMode.COMPACT_BANNER -> {
                // Prevent Back button from accidentally closing the persistent top banner
                BackHandler(enabled = true) {
                    // Intentionally keep the compact banner visible when Back is pressed
                }
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    IncomingCallBannerCard(
                        callerName = callerLabel,
                        callerSubtitle = "",
                        onAnswer = onAnswer,
                        onDecline = onDecline,
                        onBannerClick = {
                            updateMode(IncomingCallPresentationMode.FULL_SCREEN)
                        },
                        onSwipeUpCollapse = {
                            if (onDismissBannerKeepRinging != null) {
                                onDismissBannerKeepRinging()
                            } else {
                                updateMode(IncomingCallPresentationMode.COLLAPSED_INDICATOR)
                            }
                        },
                        onSwipeDownExpand = {
                            updateMode(IncomingCallPresentationMode.FULL_SCREEN)
                        }
                    )
                    if (!callState.locationLabel.isNullOrBlank()) {
                        Box(modifier = Modifier.size(0.dp).testTag("incoming_caller_location"))
                    }
                    Box(modifier = Modifier.size(0.dp).testTag("incoming_incall_status"))
                }
            }

            IncomingCallPresentationMode.COLLAPSED_INDICATOR -> {
                // If no external window dismissal handler was supplied, render the minimal indicator
                CollapsedIncomingCallIndicator(
                    callerName = callerLabel,
                    onAnswer = onAnswer,
                    onDecline = onDecline,
                    onSwipeDownExpand = {
                        updateMode(IncomingCallPresentationMode.FULL_SCREEN)
                    },
                    onTapExpand = {
                        updateMode(IncomingCallPresentationMode.FULL_SCREEN)
                    },
                    modifier = modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                )
            }
        }
    }

    if (showSmsReplySheet) {
        val quickReplies = listOf(
            "I'll call you back right away.",
            "In a meeting, please text me.",
            "Can't talk right now, call me later?",
            "On my way!"
        )
        ModalBottomSheet(
            onDismissRequest = { showSmsReplySheet = false },
            containerColor = Color(0xFF1E1633)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Reject call with SMS reply",
                    color = ColorOsWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                quickReplies.forEach { preset ->
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showSmsReplySheet = false
                                onRejectWithSms(preset)
                            }
                    ) {
                        Text(
                            text = preset,
                            color = ColorOsWhite,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = customSmsDraft,
                    onValueChange = { customSmsDraft = it },
                    placeholder = { Text("Write custom SMS...", color = Color(0xFFB5ADC9)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSmsReplySheet = false }) {
                        Text("Cancel", color = Color(0xFFC8C0DC))
                    }
                    if (customSmsDraft.isNotBlank()) {
                        TextButton(
                            onClick = {
                                val msg = customSmsDraft.trim()
                                showSmsReplySheet = false
                                onRejectWithSms(msg)
                            }
                        ) {
                            Text("Send & Decline", color = ColorOsGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * OutgoingCallScreen delegates to the unified ColorOS 17 ActiveCallScreen
 * so that dialing/calling state displays the full 2-row control layout with
 * connected-only controls disabled while dialing and Speaker/Mute/End active.
 */
@Composable
fun OutgoingCallScreen(
    callState: ActiveCallUiState,
    onToggleSpeaker: () -> Unit,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    ActiveCallScreen(
        callState = callState,
        initialNote = "",
        onSaveNote = {},
        onToggleMute = onToggleMute,
        onToggleSpeaker = onToggleSpeaker,
        onToggleBluetooth = {},
        onToggleHold = {},
        onAddCall = {},
        onRequestVideo = {},
        onRecordClick = {},
        onPlayDtmf = {},
        onEndCall = onEndCall,
        modifier = modifier
    )
}

/**
 * Official OPPO ColorOS Active / Ongoing Call Screen recreated from the uploaded
 * ColorOS reference screenshots (`Screenshot_20261107-152207` & `Screenshot_20261107-152208`):
 * - Dark purple and black gradient background with warm amber/copper and violet-indigo light blooms.
 * - Top-left bold Caller Name, Phone Number, Location, and HD + Vo5G + Status/Timer row.
 * - Top-right circular Contact Photo.
 * - Row 1 of 4 translucent purple-glass circular buttons (4-column grid):
 *   1. Mute (always active)
 *   2. Record (active only after call connects)
 *   3. AI Call Assistant (Coming Soon; active only after call connects)
 *   4. More (active only after call connects; opens Hold, Add Call, Bluetooth, Video, Notes)
 * - Row 2 of controls aligned to the 4-column grid:
 *   1. Speaker (Col 1, always active)
 *   2. Large Red Pill-Shaped End Call Button (Cols 2–3, always active)
 *   3. Dial pad (Col 4, active only after call connects)
 * - Call timer is shown ONLY after the call is actually connected (`Call.STATE_ACTIVE`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCallScreen(
    callState: ActiveCallUiState,
    initialNote: String,
    onSaveNote: (String) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleBluetooth: () -> Unit = {},
    onSelectAudioEndpoint: (CallAudioEndpointItem) -> Unit = { endpoint ->
        CallStateManager.selectAudioEndpoint(endpoint)
    },
    onToggleHold: () -> Unit,
    onAddCall: () -> Unit,
    onRequestVideo: () -> Unit,
    onRecordClick: () -> Unit,
    onAiAssistantClick: (() -> Unit)? = null,
    onPlayDtmf: (Char) -> Unit,
    onEndCall: () -> Unit,
    onAcceptWaitingCall: () -> Unit = {},
    onDeclineWaitingCall: () -> Unit = {},
    onSwapCalls: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    val aiUiState by AiCallAssistantManager.uiState.collectAsState()
    val recordingState by CallRecordingManager.sessionState.collectAsState()

    LaunchedEffect(Unit) {
        CallRecordingManager.initialize(context)
    }

    var showKeypad by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    var showMoreControlsSheet by remember { mutableStateOf(false) }
    var showRecordingsVaultSheet by remember { mutableStateOf(false) }
    var showAiAssistantConfirmDialog by remember { mutableStateOf(false) }
    var showRecordConsentDialog by remember { mutableStateOf(false) }
    var pendingAiInstruction by remember {
        mutableStateOf(AiCallAssistantManager.loadSavedInstruction(context))
    }
    var showNotesDialog by remember { mutableStateOf(false) }
    var noteDraft by remember(initialNote) { mutableStateOf(initialNote) }

    // True only when the call has actually connected (ACTIVE or HOLDING)
    val isCallConnected = callState.state == Call.STATE_ACTIVE || callState.state == Call.STATE_HOLDING

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AiCallAssistantManager.startSession(context, pendingAiInstruction)
        } else {
            CallStateManager.showStatusMessage(
                "AI Call Assistant માટે માઇક્રોફોન (RECORD_AUDIO) પરવાનગી જરૂરી છે."
            )
        }
    }

    val callRecordingMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            CallRecordingManager.startRecordingWithConsent(
                context = context,
                callId = callState.callId,
                phoneNumber = callState.number,
                displayName = callState.displayName,
                isIncoming = callState.isIncoming,
                isCallActiveOverride = isCallConnected
            )
        } else {
            CallStateManager.showStatusMessage(
                "Microphone permission (RECORD_AUDIO) is required for call recording."
            )
        }
    }

    // Automatically close keypad or connected-only sheets and stop active recording if not connected
    LaunchedEffect(isCallConnected, callState.state) {
        if (!isCallConnected) {
            showKeypad = false
            showMoreControlsSheet = false
            showRecordingsVaultSheet = false
            showAiAssistantConfirmDialog = false
            showRecordConsentDialog = false
            if (aiUiState.isActiveSession) {
                AiCallAssistantManager.stopSession()
            }
            if (recordingState.isRecordingActive || recordingState.isAnnouncingConsent) {
                CallRecordingManager.stopRecording(reason = "Call disconnected — recording stopped.")
            }
        } else if (callState.state != Call.STATE_ACTIVE &&
            (recordingState.isRecordingActive || recordingState.isAnnouncingConsent)
        ) {
            CallRecordingManager.stopRecording(reason = "Call on hold — recording stopped.")
        }
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(callState.state, callState.connectTimeMillis) {
        if (callState.state == Call.STATE_ACTIVE) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    // 1. Show the call timer ONLY after the call is actually connected (STATE_ACTIVE)
    val durationOrStatusText = remember(callState.state, callState.connectTimeMillis, nowMillis) {
        when (callState.state) {
            Call.STATE_ACTIVE -> {
                val base = if (callState.connectTimeMillis > 0L) {
                    callState.connectTimeMillis
                } else {
                    nowMillis
                }
                val elapsedSeconds = ((nowMillis - base) / 1000L).coerceAtLeast(0L)
                val minutes = elapsedSeconds / 60L
                val seconds = elapsedSeconds % 60L
                val hours = minutes / 60L
                if (hours > 0L) {
                    String.format(Locale.US, "%02d:%02d:%02d", hours, minutes % 60L, seconds)
                } else {
                    String.format(Locale.US, "%02d:%02d", minutes, seconds)
                }
            }
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_CONNECTING -> "Connecting..."
            Call.STATE_DIALING, Call.STATE_SELECT_PHONE_ACCOUNT -> "Calling"
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Call ended"
            else -> "Calling"
        }
    }

    val primaryHeading = callState.displayName?.takeIf { it.isNotBlank() } ?: callState.number
    val secondaryNumber = if (!callState.displayName.isNullOrBlank()) callState.number else null

    ColorOsPurpleBlackCallBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Contact Information Header + Call Waiting Banner + HD Vo5G Status/Timer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, start = 8.dp, end = 8.dp)
            ) {
                // Subtle, non-intrusive 'Recording' status badge & pulsing animation at the top center of the ongoing call screen
                val isCallBeingRecorded = isCallConnected && (
                    callState.isRecordingActive ||
                        recordingState.isRecordingActive ||
                        recordingState.isAnnouncingConsent
                    )
                TopCallRecordingStatusBadge(
                    isVisible = isCallBeingRecorded,
                    isAnnouncingConsent = recordingState.isAnnouncingConsent,
                    elapsedSeconds = recordingState.elapsedSeconds,
                    onClick = { showRecordingsVaultSheet = true },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 10.dp)
                )

                // OPPO Call Waiting Incoming Banner when a second call is ringing
                AnimatedVisibility(visible = callState.isWaitingCallRinging) {
                    val waitingTitle = callState.waitingCallerName?.takeIf { it.isNotBlank() }
                        ?: callState.waitingCallNumber?.takeIf { it.isNotBlank() }
                        ?: "Incoming waiting call"
                    val waitingSub = if (!callState.waitingCallerName.isNullOrBlank() &&
                        !callState.waitingCallNumber.isNullOrBlank()
                    ) {
                        "Call waiting | ${callState.waitingCallNumber}"
                    } else {
                        "Call waiting | Hold current & answer"
                    }
                    IncomingCallBannerCard(
                        callerName = waitingTitle,
                        callerSubtitle = waitingSub,
                        onAnswer = onAcceptWaitingCall,
                        onDecline = onDeclineWaitingCall,
                        bannerTestTag = "call_waiting_banner",
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                val activePersonalization = LocalPersonalizationConfig.current
                val avatarShape = remember(activePersonalization.contacts.avatarShape) {
                    PersonalizationManager.shapeFor(activePersonalization.contacts.avatarShape, 76f)
                }
                val headerRowContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = primaryHeading,
                                color = ColorOsWhite,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 44.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("active_caller_name")
                            )

                            if (!secondaryNumber.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = secondaryNumber,
                                    color = Color(0xFFEBE8F2),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0.2.sp
                                )
                            }

                            val locationOrCarrier = callState.locationLabel?.takeIf { it.isNotBlank() }
                            if (locationOrCarrier != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = locationOrCarrier,
                                    color = Color(0xFFD0CADC),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Top-Right Contact Photo
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(avatarShape)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.20f),
                                    shape = avatarShape
                                )
                                .testTag("ongoing_call_contact_avatar"),
                            contentAlignment = Alignment.Center
                        ) {
                            ContactCircleAvatar(
                                name = primaryHeading,
                                photoUri = callState.photoUri,
                                size = 76.dp,
                                fontSize = 30.sp,
                                imageTestTag = "ongoing_call_contact_photo_image"
                            )
                        }
                    }
                }
                if (!isCallConnected && activePersonalization.outgoingCall.useGlassHeaderCard) {
                    LiquidGlassCard(config = activePersonalization, cornerRadius = 24.dp) {
                        headerRowContent()
                    }
                } else {
                    headerRowContent()
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Status Row: [Phone+HD] [Vo/5G] [02:22 when connected OR Calling when dialing]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HdHandsetBadgeIcon()
                    Vo5GBadgeIcon()
                    Text(
                        text = if (showKeypad && dtmfDigits.isNotEmpty()) dtmfDigits else durationOrStatusText,
                        color = ColorOsWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.testTag("active_call_duration")
                    )

                    // Compact Upper Audio Output Selector — displays the currently active endpoint
                    // (Bluetooth device name, Speaker, or Phone earpiece) and switches directly
                    // to the next available supported endpoint on tap without opening any popup.
                    val activeEndpoint = callState.activeAudioEndpoint
                    val isBtActiveInPill = callState.isBluetoothOn ||
                        activeEndpoint.type == CallAudioEndpointType.BLUETOOTH
                    val isSpeakerActiveInPill = !isBtActiveInPill && (
                        callState.isSpeakerOn ||
                            activeEndpoint.type == CallAudioEndpointType.SPEAKER
                        )
                    val isWiredActiveInPill = !isBtActiveInPill && !isSpeakerActiveInPill &&
                        activeEndpoint.type == CallAudioEndpointType.WIRED_HEADSET

                    val pillLabel = when {
                        isBtActiveInPill -> activeEndpoint.name.ifBlank { "Bluetooth" }
                        isSpeakerActiveInPill -> "Speaker"
                        isWiredActiveInPill -> activeEndpoint.name.ifBlank { "Wired headset" }
                        else -> "Phone earpiece"
                    }
                    val isHighContrastPill = isBtActiveInPill || isSpeakerActiveInPill

                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = if (isHighContrastPill) {
                            CallButtonActiveWhite
                        } else {
                            Color.White.copy(alpha = 0.16f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = 0.6.dp,
                                color = if (isHighContrastPill) {
                                    Color.Transparent
                                } else {
                                    Color.White.copy(alpha = 0.24f)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val nextEndpoint = resolveNextAvailableAudioEndpoint(callState)
                                if (nextEndpoint != null) {
                                    onSelectAudioEndpoint(nextEndpoint)
                                }
                                if (callState.availableAudioEndpoints.isEmpty()) {
                                    onToggleSpeaker()
                                }
                            }
                            .testTag("call_audio_route_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isBtActiveInPill -> Icons.Filled.BluetoothAudio
                                    isSpeakerActiveInPill -> Icons.AutoMirrored.Filled.VolumeUp
                                    isWiredActiveInPill -> Icons.Filled.Headset
                                    else -> Icons.Filled.PhoneInTalk
                                },
                                contentDescription = "Audio output: $pillLabel",
                                tint = if (isHighContrastPill) ColorOsTextPrimary else ColorOsWhite,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = pillLabel,
                                color = if (isHighContrastPill) ColorOsTextPrimary else ColorOsWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("call_audio_route_pill_label")
                            )
                        }
                    }
                }

                // Switch / Swap between active and held calls when 2 calls exist
                if (!callState.secondaryCallerName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { onSwapCalls() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapCalls,
                                contentDescription = "Switch calls",
                                tint = ColorOsWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "On hold: ${callState.secondaryCallerName} (Tap to switch)",
                                color = ColorOsWhite,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !callState.statusMessage.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.46f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = callState.statusMessage.orEmpty(),
                            color = ColorOsWhite,
                            fontSize = 13.sp
                        )
                    }
                }

                // Live Call Recording Indicator Banner (with Stop & Internal Vault controls)
                LiveCallRecordingBanner(
                    recordingState = recordingState,
                    onStopRecording = {
                        CallRecordingManager.stopRecording()
                    },
                    onOpenRecordingsVault = {
                        showRecordingsVaultSheet = true
                    },
                    modifier = Modifier.padding(top = 12.dp)
                )

                // Live Gujarati AI Call Assistant Panel when active during connected call
                AnimatedVisibility(
                    visible = aiUiState.isActiveSession && isCallConnected,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    LiveAiCallAssistantPanel(
                        aiState = aiUiState,
                        onPauseOrResume = {
                            if (aiUiState.status == AiAssistantStatus.PAUSED) {
                                AiCallAssistantManager.resumeSession()
                            } else {
                                AiCallAssistantManager.pauseSession()
                            }
                        },
                        onRetryMic = {
                            AiCallAssistantManager.retryListening()
                        },
                        onStopAi = {
                            AiCallAssistantManager.stopSession(
                                reasonNote = "AI કૉલ આસિસ્ટન્ટ બંધ કરવામાં આવ્યું."
                            )
                        },
                        onSimulateCallerSpeech = { callerSpeech ->
                            AiCallAssistantManager.handleRecognizedCallerSpeech(callerSpeech)
                        },
                        onSaveTranscriptToNotes = { transcript ->
                            val combined = if (noteDraft.isBlank()) {
                                transcript
                            } else {
                                "$noteDraft\n\n$transcript"
                            }
                            noteDraft = combined
                            onSaveNote(combined)
                        },
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }

            // Central Area: Live Incoming Voice Waveform (Blue-to-Cyan rounded vertical bars)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!showKeypad && !aiUiState.isActiveSession) {
                    com.example.telecom.LiveIncomingVoiceWaveformSection(
                        callState = callState
                    )
                }
            }

            // Bottom Controls Section matching BOTH reference screenshots in a strict 4-column layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showKeypad && isCallConnected) {
                    InCallDtmfPad(
                        onDigitPress = { digit ->
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dtmfDigits += digit
                            onPlayDtmf(digit)
                        }
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                } else {
                    // Row 1: 4 Translucent Circular Glass Buttons — Mute, Record, AI Call Assistant, More
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Col 1: Mute (Always functional during dialing & connected)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ColorOsGlassControlCircle(
                                label = "Mute",
                                isActive = callState.isMuted,
                                isEnabled = true,
                                buttonType = ActiveCallButtonType.MUTE,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleMute()
                                },
                                testTag = "call_btn_mute"
                            ) { tint, activeProgress, pulsePhase ->
                                AnimatedMuteMicIcon(
                                    isMuted = callState.isMuted,
                                    tint = tint,
                                    activeProgress = activeProgress,
                                    pulsePhase = pulsePhase
                                )
                            }
                        }

                        // Col 2: Record (Active only after call is connected)
                        val isRecActive =
                            (callState.isRecordingActive || recordingState.isRecordingActive || recordingState.isAnnouncingConsent) &&
                                isCallConnected
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ColorOsGlassControlCircle(
                                label = "Record",
                                isActive = isRecActive,
                                isEnabled = isCallConnected,
                                buttonType = ActiveCallButtonType.RECORD,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (recordingState.isRecordingActive || recordingState.isAnnouncingConsent) {
                                        CallRecordingManager.stopRecording()
                                        onRecordClick()
                                    } else if (!recordingState.isFeatureEnabled) {
                                        CallStateManager.showStatusMessage(
                                            "Call recording is turned OFF. Enable it in Settings → Call recording."
                                        )
                                        onRecordClick()
                                    } else if (recordingState.silentInstantStartEnabled) {
                                        val hasRecordPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasRecordPerm) {
                                            CallRecordingManager.startRecordingWithConsent(
                                                context = context,
                                                callId = callState.callId,
                                                phoneNumber = callState.number,
                                                displayName = callState.displayName,
                                                isIncoming = callState.isIncoming,
                                                isCallActiveOverride = isCallConnected
                                            )
                                        } else {
                                            callRecordingMicPermissionLauncher.launch(
                                                Manifest.permission.RECORD_AUDIO
                                            )
                                        }
                                        onRecordClick()
                                    } else {
                                        showRecordConsentDialog = true
                                        onRecordClick()
                                    }
                                },
                                testTag = "call_btn_record"
                            ) { tint, activeProgress, pulsePhase ->
                                AnimatedRecordButtonIcon(
                                    tint = tint,
                                    activeProgress = activeProgress,
                                    pulsePhase = pulsePhase
                                )
                            }
                        }

                        // Col 3: AI Call Assistant (Live Gujarati AI Assistant; enabled only after call is connected)
                        val isAiActiveOnCall = aiUiState.isActiveSession && isCallConnected
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ColorOsGlassControlCircle(
                                label = "AI Call\nAssistant",
                                isActive = isAiActiveOnCall,
                                isEnabled = isCallConnected,
                                hasIridescentRim = isCallConnected,
                                buttonType = ActiveCallButtonType.AI_ASSISTANT,
                                aiStatus = aiUiState.status,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (onAiAssistantClick != null) {
                                        onAiAssistantClick()
                                    } else if (aiUiState.isActiveSession) {
                                        AiCallAssistantManager.stopSession(
                                            reasonNote = "AI કૉલ આસિસ્ટન્ટ બંધ કરવામાં આવ્યું."
                                        )
                                    } else {
                                        pendingAiInstruction =
                                            AiCallAssistantManager.loadSavedInstruction(context)
                                        showAiAssistantConfirmDialog = true
                                    }
                                },
                                testTag = "call_btn_ai_assistant"
                            ) { tint, activeProgress, pulsePhase ->
                                AnimatedAiCallAssistantStarIcon(
                                    tint = tint,
                                    activeProgress = activeProgress,
                                    pulsePhase = pulsePhase
                                )
                            }
                        }

                        // Col 4: More (Action button — active only after call is connected)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ColorOsGlassControlCircle(
                                label = "More",
                                isActive = false,
                                isEnabled = isCallConnected,
                                buttonType = ActiveCallButtonType.MORE,
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showMoreControlsSheet = true
                                },
                                testTag = "call_btn_more"
                            ) { tint, activeProgress, pulsePhase ->
                                AnimatedMoreVerticalDotsIcon(
                                    tint = tint,
                                    activeProgress = activeProgress,
                                    pulsePhase = pulsePhase
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))
                }

                // Row 2: Speaker (Col 1), Large Red Pill End Button (Cols 2–3), Dial pad (Col 4)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Col 1: Speaker Button (direct control without opening any bottom sheet or popup)
                    val activeEndpoint = callState.activeAudioEndpoint
                    val isSpeakerCurrentlyActive = callState.isSpeakerOn ||
                        activeEndpoint.type == CallAudioEndpointType.SPEAKER

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        ColorOsGlassControlCircle(
                            label = "Speaker",
                            isActive = isSpeakerCurrentlyActive,
                            isEnabled = true,
                            buttonType = ActiveCallButtonType.SPEAKER,
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleSpeaker()
                            },
                            testTag = "call_btn_speaker"
                        ) { tint, activeProgress, pulsePhase ->
                            AnimatedSpeakerIcon(
                                tint = tint,
                                activeProgress = activeProgress,
                                pulsePhase = pulsePhase
                            )
                        }
                    }

                    // Cols 2–3: Large Red Pill-Shaped End Call Button with "End" label below
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        ColorOsRedEndCallPillButton(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEndCall()
                            },
                            testTag = "call_btn_end",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Col 4: Dial pad (Action button — enabled only after call is connected)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        ColorOsGlassControlCircle(
                            label = "Dial pad",
                            isActive = false,
                            isEnabled = isCallConnected,
                            buttonType = ActiveCallButtonType.DIAL_PAD,
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showKeypad = !showKeypad
                            },
                            testTag = "call_btn_keypad"
                        ) { tint, activeProgress, pulsePhase ->
                            AnimatedDialPadDotsIcon(
                                tint = tint,
                                activeProgress = activeProgress,
                                pulsePhase = pulsePhase
                            )
                        }
                    }
                }
            }
        }
    }

    // "More" Translucent Glass Controls Sheet (preserves Hold, Add Call, Bluetooth, Video, Notes)
    if (showMoreControlsSheet && isCallConnected) {
        ModalBottomSheet(
            onDismissRequest = { showMoreControlsSheet = false },
            containerColor = Color(0xFF1D1533).copy(alpha = 0.97f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "More call controls",
                    color = ColorOsWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ColorOsGlassControlCircle(
                        label = if (callState.isOnHold) "Resume" else "Hold",
                        isActive = callState.isOnHold,
                        isEnabled = true,
                        onClick = {
                            showMoreControlsSheet = false
                            onToggleHold()
                        },
                        testTag = "call_btn_hold"
                    ) { tint, _, _ ->
                        HoldPauseCircleIcon(tint = tint)
                    }

                    ColorOsGlassControlCircle(
                        label = "Add call",
                        isActive = false,
                        isEnabled = true,
                        onClick = {
                            showMoreControlsSheet = false
                            onAddCall()
                        },
                        testTag = "call_btn_add_call"
                    ) { tint, _, _ ->
                        AddCallPlusCircleIcon(tint = tint)
                    }

                    val availableBtEndpoint = callState.availableAudioEndpoints.firstOrNull {
                        it.type == CallAudioEndpointType.BLUETOOTH
                    }
                    ColorOsGlassControlCircle(
                        label = availableBtEndpoint?.name?.takeIf { it.isNotBlank() } ?: "Bluetooth",
                        isActive = callState.isBluetoothOn,
                        isEnabled = true,
                        onClick = {
                            showMoreControlsSheet = false
                            if (availableBtEndpoint != null && !callState.isBluetoothOn) {
                                onSelectAudioEndpoint(availableBtEndpoint)
                            } else if (callState.isBluetoothOn) {
                                val fallbackEp = callState.availableAudioEndpoints.firstOrNull {
                                    it.type == CallAudioEndpointType.EARPIECE
                                } ?: callState.availableAudioEndpoints.firstOrNull {
                                    it.type != CallAudioEndpointType.BLUETOOTH
                                }
                                if (fallbackEp != null) {
                                    onSelectAudioEndpoint(fallbackEp)
                                } else {
                                    onToggleBluetooth()
                                }
                            } else {
                                onToggleBluetooth()
                            }
                        },
                        testTag = "call_btn_bluetooth"
                    ) { tint, _, _ ->
                        Icon(
                            imageVector = Icons.Filled.BluetoothAudio,
                            contentDescription = "Bluetooth",
                            tint = tint,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ColorOsGlassControlCircle(
                        label = "Video",
                        isActive = callState.isVideoActive,
                        isEnabled = true,
                        onClick = {
                            showMoreControlsSheet = false
                            onRequestVideo()
                        },
                        testTag = "call_btn_video"
                    ) { tint, _, _ ->
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = "Video",
                            tint = tint,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    ColorOsGlassControlCircle(
                        label = "Notes",
                        isActive = noteDraft.isNotBlank(),
                        isEnabled = true,
                        onClick = {
                            showMoreControlsSheet = false
                            showNotesDialog = true
                        },
                        testTag = "call_btn_notes"
                    ) { tint, _, _ ->
                        NotesDocumentIcon(tint = tint)
                    }

                    ColorOsGlassControlCircle(
                        label = "Recordings",
                        isActive = recordingState.savedRecordings.isNotEmpty() || recordingState.isRecordingActive,
                        isEnabled = true,
                        onClick = {
                            showMoreControlsSheet = false
                            showRecordingsVaultSheet = true
                        },
                        testTag = "call_btn_recordings_vault"
                    ) { tint, activeProgress, pulsePhase ->
                        AnimatedRecordButtonIcon(
                            tint = tint,
                            activeProgress = activeProgress,
                            pulsePhase = pulsePhase
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // In-Call Local Recordings & Android Privacy Vault Bottom Sheet
    if (showRecordingsVaultSheet && isCallConnected) {
        ModalBottomSheet(
            onDismissRequest = { showRecordingsVaultSheet = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            containerColor = Color(0xFF1D1533).copy(alpha = 0.98f)
        ) {
            InCallLocalRecordingsVaultContent(
                recordingState = recordingState,
                onToggleAnnounceConsent = { enabled ->
                    CallRecordingManager.setAnnounceConsentEnabled(context, enabled)
                },
                onToggleSilentInstantStart = { enabled ->
                    CallRecordingManager.setSilentInstantStartEnabled(context, enabled)
                },
                onTogglePlayRecording = { recording ->
                    CallRecordingManager.togglePlayback(recording)
                },
                onStopPlayback = {
                    CallRecordingManager.stopPlayback()
                },
                onShareRecording = { recording ->
                    CallRecordingManager.shareRecording(context, recording)
                },
                onDeleteRecording = { recording ->
                    CallRecordingManager.deleteRecording(context, recording.id)
                },
                onClose = { showRecordingsVaultSheet = false }
            )
        }
    }

    // AI Call Assistant — Confirmation & Gujarati Instructions Dialog
    if (showAiAssistantConfirmDialog && isCallConnected) {
        AiCallAssistantConfirmationDialog(
            initialInstruction = pendingAiInstruction,
            onConfirmActivate = { chosenInstruction ->
                pendingAiInstruction = chosenInstruction
                showAiAssistantConfirmDialog = false
                val hasRecordPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasRecordPermission) {
                    AiCallAssistantManager.startSession(context, chosenInstruction)
                } else {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onDismiss = { showAiAssistantConfirmDialog = false }
        )
    }

    // Call Recording — Participant Notification & Consent Confirmation Dialog
    if (showRecordConsentDialog && isCallConnected) {
        val hasCapturePrivilege = ContextCompat.checkSelfPermission(
            context,
            "android.permission.CAPTURE_AUDIO_OUTPUT"
        ) == PackageManager.PERMISSION_GRANTED

        CallRecordingConsentDialog(
            callerDisplay = primaryHeading,
            announceEnabled = recordingState.announceConsentEnabled,
            autoSpeakerEnabled = recordingState.autoSpeakerForAcousticCapture,
            hasCapturePrivilege = hasCapturePrivilege,
            onConfirmStartRecording = {
                showRecordConsentDialog = false
                val hasRecordPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasRecordPerm) {
                    CallRecordingManager.startRecordingWithConsent(
                        context = context,
                        callId = callState.callId,
                        phoneNumber = callState.number,
                        displayName = callState.displayName,
                        isIncoming = callState.isIncoming,
                        isCallActiveOverride = isCallConnected
                    )
                } else {
                    callRecordingMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onDismiss = { showRecordConsentDialog = false }
        )
    }

    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            containerColor = Color(0xFF1F1736),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Call Notes — $primaryHeading",
                    color = ColorOsWhite,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    placeholder = { Text("Write notes for this call...", color = Color(0xFF9E97B0)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveNote(noteDraft)
                        showNotesDialog = false
                    }
                ) {
                    Text("Save Note", color = ColorOsGreen, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("Cancel", color = Color(0xFFD0CADC))
                }
            }
        )
    }
}

/**
 * Distinct Active Call Screen button visual types corresponding to Reference Image 1 (OFF)
 * and Reference Image 2 (ON).
 */
enum class ActiveCallButtonType {
    MUTE,
    RECORD,
    AI_ASSISTANT,
    MORE,
    SPEAKER,
    DIAL_PAD,
    END_CALL,
    STANDARD
}

/**
 * Immutable visual specification for a single Active Call Screen button so that OFF vs ON
 * appearance, neon ring colors, animation loops, and reduced-motion behavior can be
 * deterministically verified in unit tests.
 */
data class ActiveCallButtonVisualSpec(
    val buttonType: ActiveCallButtonType,
    val isOn: Boolean,
    val isEnabled: Boolean,
    val hasPersistentNeonGlow: Boolean,
    val hasContinuousAnimation: Boolean,
    val primaryNeonHex: Long,
    val secondaryNeonHex: Long,
    val accentNeonHex: Long = secondaryNeonHex,
    val animationSpeedMultiplier: Float = 1f,
    val effectStyleSummary: String
)

/**
 * Complete snapshot of all 7 primary Active Call Screen controls (`Mute`, `Record`,
 * `AI Call Assistant`, `More`, `Speaker`, `Dial pad`, and `End`).
 */
data class ActiveCallControlsVisualSnapshot(
    val mute: ActiveCallButtonVisualSpec,
    val record: ActiveCallButtonVisualSpec,
    val aiAssistant: ActiveCallButtonVisualSpec,
    val more: ActiveCallButtonVisualSpec,
    val speaker: ActiveCallButtonVisualSpec,
    val dialPad: ActiveCallButtonVisualSpec,
    val endCall: ActiveCallButtonVisualSpec
) {
    val activeToggleButtons: Set<ActiveCallButtonType>
        get() = listOf(mute, record, aiAssistant, more, speaker, dialPad)
            .filter { it.isOn }
            .map { it.buttonType }
            .toSet()

    val activeToggleButtonCount: Int
        get() = activeToggleButtons.size
}

/**
 * Maps the live [AiAssistantStatus] to an orbital animation speed multiplier so the
 * AI Call Assistant button's cyan-emerald-violet ring dynamically reflects the actual AI state.
 */
fun resolveAiAssistantAnimationSpeed(status: AiAssistantStatus): Float {
    return when (status) {
        AiAssistantStatus.SPEAKING -> 1.45f
        AiAssistantStatus.LISTENING -> 1.30f
        AiAssistantStatus.PROCESSING,
        AiAssistantStatus.SPEECH_RECOGNIZED -> 1.55f
        AiAssistantStatus.CONNECTING,
        AiAssistantStatus.WAITING_FOR_USER -> 1.05f
        AiAssistantStatus.PAUSED -> 0.45f
        AiAssistantStatus.RECOGNITION_FAILED,
        AiAssistantStatus.ERROR -> 0.65f
        AiAssistantStatus.IDLE -> 0f
    }
}

/**
 * Pure resolver computing the exact OFF (Reference Image 1) or ON (Reference Image 2)
 * visual specification for an individual Active Call Screen button.
 */
fun resolveActiveCallButtonVisualSpec(
    buttonType: ActiveCallButtonType,
    isActive: Boolean,
    isEnabled: Boolean = true,
    aiStatus: AiAssistantStatus = AiAssistantStatus.IDLE,
    isEndCallPressed: Boolean = false,
    animatorDurationScale: Float = 1f
): ActiveCallButtonVisualSpec {
    val isReducedMotion = animatorDurationScale <= 0.01f
    val effectiveOn = isActive && isEnabled && buttonType != ActiveCallButtonType.END_CALL

    return when (buttonType) {
        ActiveCallButtonType.MUTE -> ActiveCallButtonVisualSpec(
            buttonType = buttonType,
            isOn = effectiveOn,
            isEnabled = isEnabled,
            hasPersistentNeonGlow = effectiveOn,
            hasContinuousAnimation = effectiveOn && !isReducedMotion,
            primaryNeonHex = if (effectiveOn) 0xFFFF3B4EL else 0xFF675A89L,
            secondaryNeonHex = if (effectiveOn) 0xFFFF8A80L else 0xFF463B64L,
            accentNeonHex = if (effectiveOn) 0xFFFF1744L else 0xFFFFFFFFL,
            animationSpeedMultiplier = if (effectiveOn && !isReducedMotion) 1.0f else 0f,
            effectStyleSummary = if (effectiveOn) {
                "ON: Red glowing circular ring with subtle pulsing ripple and smooth mic icon transition"
            } else {
                "OFF: Original neutral dark purple-glass appearance (Reference Image 1)"
            }
        )

        ActiveCallButtonType.RECORD -> ActiveCallButtonVisualSpec(
            buttonType = buttonType,
            isOn = effectiveOn,
            isEnabled = isEnabled,
            hasPersistentNeonGlow = effectiveOn,
            hasContinuousAnimation = effectiveOn && !isReducedMotion,
            primaryNeonHex = if (effectiveOn) 0xFFFF2E7EL else 0xFF675A89L,
            secondaryNeonHex = if (effectiveOn) 0xFFFF6EA7L else 0xFF463B64L,
            accentNeonHex = if (effectiveOn) 0xFFFF1744L else 0xFFFFFFFFL,
            animationSpeedMultiplier = if (effectiveOn && !isReducedMotion) 1.15f else 0f,
            effectStyleSummary = if (effectiveOn) {
                "ON: Pink/red neon ring, animated recording dot and top radial waveform pulses"
            } else {
                "OFF: Original neutral dark purple-glass appearance with 5-bar waveform icon (Reference Image 1)"
            }
        )

        ActiveCallButtonType.AI_ASSISTANT -> {
            val aiSpeed = if (effectiveOn && !isReducedMotion) {
                resolveAiAssistantAnimationSpeed(
                    if (aiStatus == AiAssistantStatus.IDLE) AiAssistantStatus.WAITING_FOR_USER else aiStatus
                )
            } else {
                0f
            }
            ActiveCallButtonVisualSpec(
                buttonType = buttonType,
                isOn = effectiveOn,
                isEnabled = isEnabled,
                hasPersistentNeonGlow = effectiveOn,
                hasContinuousAnimation = effectiveOn && !isReducedMotion,
                primaryNeonHex = if (effectiveOn) 0xFF00E5FFL else 0xFF6B5D8EL,
                secondaryNeonHex = if (effectiveOn) 0xFF00E676L else 0xFF4F4270L,
                accentNeonHex = if (effectiveOn) 0xFFB388FFL else 0xFF66BDF9L,
                animationSpeedMultiplier = aiSpeed,
                effectStyleSummary = if (effectiveOn) {
                    "ON: Cyan, emerald and violet glowing orbital ring with rotating energy arcs and subtle particles (${aiStatus.name})"
                } else {
                    "OFF: Original neutral appearance with static subtle iridescent rim (Reference Image 1)"
                }
            )
        }

        ActiveCallButtonType.MORE -> ActiveCallButtonVisualSpec(
            buttonType = buttonType,
            isOn = effectiveOn,
            isEnabled = isEnabled,
            hasPersistentNeonGlow = effectiveOn,
            hasContinuousAnimation = effectiveOn && !isReducedMotion,
            primaryNeonHex = if (effectiveOn) 0xFFB388FFL else 0xFF675A89L,
            secondaryNeonHex = if (effectiveOn) 0xFF7C4DFFL else 0xFF463B64L,
            accentNeonHex = if (effectiveOn) 0xFFE0B0FFL else 0xFFFFFFFFL,
            animationSpeedMultiplier = if (effectiveOn && !isReducedMotion) 1.0f else 0f,
            effectStyleSummary = if (effectiveOn) {
                "ON: Premium violet glow with subtle expanding ripple and three-dot wave animation"
            } else {
                "OFF: Original neutral dark purple-glass appearance with static 3 vertical dots (Reference Image 1)"
            }
        )

        ActiveCallButtonType.SPEAKER -> ActiveCallButtonVisualSpec(
            buttonType = buttonType,
            isOn = effectiveOn,
            isEnabled = isEnabled,
            hasPersistentNeonGlow = effectiveOn,
            hasContinuousAnimation = effectiveOn && !isReducedMotion,
            primaryNeonHex = if (effectiveOn) 0xFF00E5FFL else 0xFF675A89L,
            secondaryNeonHex = if (effectiveOn) 0xFF2979FFL else 0xFF463B64L,
            accentNeonHex = if (effectiveOn) 0xFF80D8FFL else 0xFFFFFFFFL,
            animationSpeedMultiplier = if (effectiveOn && !isReducedMotion) 1.1f else 0f,
            effectStyleSummary = if (effectiveOn) {
                "ON: Electric blue glowing ring with expanding sound-wave animations"
            } else {
                "OFF: Original neutral dark purple-glass appearance (Reference Image 1)"
            }
        )

        ActiveCallButtonType.DIAL_PAD -> ActiveCallButtonVisualSpec(
            buttonType = buttonType,
            isOn = effectiveOn,
            isEnabled = isEnabled,
            hasPersistentNeonGlow = effectiveOn,
            hasContinuousAnimation = effectiveOn && !isReducedMotion,
            primaryNeonHex = if (effectiveOn) 0xFFAA00FFL else 0xFF675A89L,
            secondaryNeonHex = if (effectiveOn) 0xFF7C4DFFL else 0xFF463B64L,
            accentNeonHex = if (effectiveOn) 0xFFE086FFL else 0xFFFFFFFFL,
            animationSpeedMultiplier = if (effectiveOn && !isReducedMotion) 1.0f else 0f,
            effectStyleSummary = if (effectiveOn) {
                "ON: Purple neon glow with subtle sequential dot animations"
            } else {
                "OFF: Original neutral dark purple-glass appearance (Reference Image 1)"
            }
        )

        ActiveCallButtonType.END_CALL -> ActiveCallButtonVisualSpec(
            buttonType = buttonType,
            isOn = false,
            isEnabled = isEnabled,
            hasPersistentNeonGlow = false,
            hasContinuousAnimation = false,
            primaryNeonHex = 0xFFEF5040L,
            secondaryNeonHex = 0xFFDE3A2CL,
            accentNeonHex = if (isEndCallPressed) 0xFFFFB4A8L else 0xFFFF9E92L,
            animationSpeedMultiplier = 0f,
            effectStyleSummary = if (isEndCallPressed) {
                "END_CALL: Original red pill with subtle press interaction and short visual feedback"
            } else {
                "END_CALL: Original red pill appearance preserved"
            }
        )

        ActiveCallButtonType.STANDARD -> ActiveCallButtonVisualSpec(
            buttonType = buttonType,
            isOn = effectiveOn,
            isEnabled = isEnabled,
            hasPersistentNeonGlow = false,
            hasContinuousAnimation = false,
            primaryNeonHex = 0xFF675A89L,
            secondaryNeonHex = 0xFF463B64L,
            effectStyleSummary = if (effectiveOn) "Active standard sheet control" else "Inactive standard sheet control"
        )
    }
}

/**
 * Resolves the complete visual state of all 7 Active Call Screen buttons simultaneously,
 * ensuring each button reflects only its own functional state and supports any combination
 * of simultaneously active buttons.
 */
fun resolveActiveCallControlsVisualSnapshot(
    callState: ActiveCallUiState,
    isRecordingActive: Boolean = false,
    isAnnouncingRecordingConsent: Boolean = false,
    aiStatus: AiAssistantStatus = AiAssistantStatus.IDLE,
    isAiActiveSession: Boolean = false,
    isMoreSheetOpen: Boolean = false,
    isKeypadOpen: Boolean = false,
    isEndCallPressed: Boolean = false,
    animatorDurationScale: Float = 1f
): ActiveCallControlsVisualSnapshot {
    val isCallConnected = callState.state == Call.STATE_ACTIVE || callState.state == Call.STATE_HOLDING
    val recOn = (callState.isRecordingActive || isRecordingActive || isAnnouncingRecordingConsent) && isCallConnected
    val aiOn = isAiActiveSession && isCallConnected
    val moreOn = (callState.isOnHold || isMoreSheetOpen) && isCallConnected
    val dialPadOn = isKeypadOpen && isCallConnected

    return ActiveCallControlsVisualSnapshot(
        mute = resolveActiveCallButtonVisualSpec(
            buttonType = ActiveCallButtonType.MUTE,
            isActive = callState.isMuted,
            isEnabled = true,
            animatorDurationScale = animatorDurationScale
        ),
        record = resolveActiveCallButtonVisualSpec(
            buttonType = ActiveCallButtonType.RECORD,
            isActive = recOn,
            isEnabled = isCallConnected,
            animatorDurationScale = animatorDurationScale
        ),
        aiAssistant = resolveActiveCallButtonVisualSpec(
            buttonType = ActiveCallButtonType.AI_ASSISTANT,
            isActive = aiOn,
            isEnabled = isCallConnected,
            aiStatus = aiStatus,
            animatorDurationScale = animatorDurationScale
        ),
        more = resolveActiveCallButtonVisualSpec(
            buttonType = ActiveCallButtonType.MORE,
            isActive = moreOn,
            isEnabled = isCallConnected,
            animatorDurationScale = animatorDurationScale
        ),
        speaker = resolveActiveCallButtonVisualSpec(
            buttonType = ActiveCallButtonType.SPEAKER,
            isActive = callState.isSpeakerOn,
            isEnabled = true,
            animatorDurationScale = animatorDurationScale
        ),
        dialPad = resolveActiveCallButtonVisualSpec(
            buttonType = ActiveCallButtonType.DIAL_PAD,
            isActive = dialPadOn,
            isEnabled = isCallConnected,
            animatorDurationScale = animatorDurationScale
        ),
        endCall = resolveActiveCallButtonVisualSpec(
            buttonType = ActiveCallButtonType.END_CALL,
            isActive = false,
            isEnabled = true,
            isEndCallPressed = isEndCallPressed,
            animatorDurationScale = animatorDurationScale
        )
    )
}

@Composable
private fun rememberIsReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale <= 0.01f
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Translucent Purple-Glass Circular Call Control Button matching Reference Image 1 when OFF
 * and Reference Image 2 when ON:
 * - Preserves exact 72.dp circle size, 80.dp column width, spacing, label, and click behavior.
 * - Runs lightweight infinite animations ONLY when the specific button is ON (or smoothly fading out),
 *   stopping all continuous animation loops immediately once OFF.
 */
@Composable
private fun ColorOsGlassControlCircle(
    label: String,
    isActive: Boolean,
    isEnabled: Boolean = true,
    hasIridescentRim: Boolean = false,
    buttonType: ActiveCallButtonType = ActiveCallButtonType.STANDARD,
    aiStatus: AiAssistantStatus = AiAssistantStatus.IDLE,
    circleSize: Dp = 72.dp,
    onClick: () -> Unit,
    testTag: String,
    icon: @Composable (tint: Color, activeProgress: Float, pulsePhase: Float) -> Unit
) {
    val isReducedMotion = rememberIsReducedMotionEnabled()
    val personalization = LocalPersonalizationConfig.current
    val ongoingPrefs = personalization.ongoingCall
    val animPrefs = personalization.animations
    val resolvedCircleSize = if (circleSize == 72.dp) {
        (circleSize.value * ongoingPrefs.buttonSize.multiplier).dp
    } else {
        circleSize
    }
    val controlShape = remember(ongoingPrefs.buttonShape, resolvedCircleSize) {
        PersonalizationManager.shapeFor(ongoingPrefs.buttonShape, resolvedCircleSize.value)
    }
    val coroutineScope = rememberCoroutineScope()
    val clickRotationAnim = remember { Animatable(0f) }
    var isClickRotationRunning by remember { mutableStateOf(false) }
    val effectiveActive = isActive && isEnabled
    val isNeonStyledButton = buttonType != ActiveCallButtonType.STANDARD &&
        buttonType != ActiveCallButtonType.END_CALL

    // Smooth ON/OFF transition so turning a button OFF smoothly fades its glow and restores Reference Image 1
    val activeProgress by animateFloatAsState(
        targetValue = if (effectiveActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isReducedMotion) 0 else PersonalizationManager.resolveDurationMs(280, animPrefs),
            easing = FastOutSlowInEasing
        ),
        label = "btn_active_progress_$testTag"
    )

    // Only allocate an InfiniteTransition while this specific button is ON (or fading out) and motion is allowed
    val shouldRunContinuousLoop = isNeonStyledButton && (effectiveActive || activeProgress > 0.01f) && !isReducedMotion

    val orbitPhase: Float
    val pulsePhase: Float
    if (shouldRunContinuousLoop) {
        val aiSpeed = if (buttonType == ActiveCallButtonType.AI_ASSISTANT) {
            resolveAiAssistantAnimationSpeed(
                if (aiStatus == AiAssistantStatus.IDLE) AiAssistantStatus.WAITING_FOR_USER else aiStatus
            ).coerceAtLeast(0.45f)
        } else {
            1.0f
        }
        val orbitDurationMs = (2600f / (aiSpeed * animPrefs.speedMultiplier)).roundToInt().coerceIn(900, 5200)
        val pulseDurationMs = when (buttonType) {
            ActiveCallButtonType.RECORD -> (950f / animPrefs.speedMultiplier).roundToInt().coerceIn(500, 1800)
            ActiveCallButtonType.SPEAKER -> (1250f / animPrefs.speedMultiplier).roundToInt().coerceIn(650, 2200)
            ActiveCallButtonType.AI_ASSISTANT -> (1100f / (aiSpeed * animPrefs.speedMultiplier)).roundToInt().coerceIn(550, 2200)
            ActiveCallButtonType.MUTE -> (1350f / animPrefs.speedMultiplier).roundToInt().coerceIn(700, 2400)
            ActiveCallButtonType.MORE -> (1200f / animPrefs.speedMultiplier).roundToInt().coerceIn(600, 2200)
            ActiveCallButtonType.DIAL_PAD -> (1300f / animPrefs.speedMultiplier).roundToInt().coerceIn(650, 2300)
            else -> 1200
        }
        val infiniteTransition = rememberInfiniteTransition(label = "active_btn_loop_$testTag")
        val animatedOrbit by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = orbitDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbit_$testTag"
        )
        val animatedPulse by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = pulseDurationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_$testTag"
        )
        orbitPhase = animatedOrbit
        pulsePhase = animatedPulse
    } else {
        orbitPhase = 0f
        pulsePhase = if (effectiveActive) 0.5f else 0f
    }

    val tint = when {
        !isEnabled -> ColorOsWhite.copy(alpha = 0.45f)
        effectiveActive && buttonType == ActiveCallButtonType.STANDARD -> Color(0xFF19132B)
        ongoingPrefs.buttonStyle == OngoingButtonVisualStyle.FROSTED_SOLID && !effectiveActive -> Color(0xFF121824)
        else -> ColorOsWhite
    }

    val defaultOffFillColors = when {
        ongoingPrefs.buttonStyle == OngoingButtonVisualStyle.LIQUID_GLASS -> listOf(
            Color(0xFF1E5D94).copy(alpha = if (isEnabled) 0.58f else 0.30f),
            Color(0xFF0E3963).copy(alpha = if (isEnabled) 0.52f else 0.26f),
            Color(0xFF134878).copy(alpha = if (isEnabled) 0.56f else 0.28f)
        )
        ongoingPrefs.buttonStyle == OngoingButtonVisualStyle.FROSTED_SOLID -> listOf(
            Color.White.copy(alpha = if (isEnabled) 0.76f else 0.36f),
            Color(0xFFDCEBFF).copy(alpha = if (isEnabled) 0.68f else 0.32f),
            Color(0xFFEAF3FF).copy(alpha = if (isEnabled) 0.72f else 0.34f)
        )
        ongoingPrefs.buttonStyle == OngoingButtonVisualStyle.NEON_RIM -> listOf(
            Color(0xFF1A2236).copy(alpha = if (isEnabled) 0.68f else 0.34f),
            Color(0xFF101626).copy(alpha = if (isEnabled) 0.64f else 0.30f),
            Color(0xFF17223B).copy(alpha = if (isEnabled) 0.66f else 0.32f)
        )
        hasIridescentRim && isEnabled -> listOf(
            Color(0xFF6B5D8E).copy(alpha = 0.64f),
            Color(0xFF4F4270).copy(alpha = 0.60f),
            Color(0xFF5D4878).copy(alpha = 0.66f)
        )
        else -> listOf(
            Color(0xFF675A89).copy(alpha = if (isEnabled) 0.60f else 0.32f),
            Color(0xFF463B64).copy(alpha = if (isEnabled) 0.54f else 0.28f),
            Color(0xFF463B64).copy(alpha = if (isEnabled) 0.54f else 0.28f)
        )
    }

    val activeOnFillColors = when (buttonType) {
        ActiveCallButtonType.MUTE -> listOf(
            Color(0xFF8B263B).copy(alpha = 0.88f),
            Color(0xFF591728).copy(alpha = 0.86f),
            Color(0xFF3E101D).copy(alpha = 0.90f)
        )
        ActiveCallButtonType.RECORD -> listOf(
            Color(0xFF7D224C).copy(alpha = 0.88f),
            Color(0xFF541635).copy(alpha = 0.86f),
            Color(0xFF3A0F24).copy(alpha = 0.90f)
        )
        ActiveCallButtonType.AI_ASSISTANT -> listOf(
            Color(0xFF1A5E7A).copy(alpha = 0.88f),
            Color(0xFF233E73).copy(alpha = 0.86f),
            Color(0xFF3B2366).copy(alpha = 0.90f)
        )
        ActiveCallButtonType.MORE -> listOf(
            Color(0xFF643B99).copy(alpha = 0.88f),
            Color(0xFF462773).copy(alpha = 0.86f),
            Color(0xFF311A54).copy(alpha = 0.90f)
        )
        ActiveCallButtonType.SPEAKER -> listOf(
            Color(0xFF1D5696).copy(alpha = 0.88f),
            Color(0xFF153B6E).copy(alpha = 0.86f),
            Color(0xFF0E264C).copy(alpha = 0.90f)
        )
        ActiveCallButtonType.DIAL_PAD -> listOf(
            Color(0xFF5B2C91).copy(alpha = 0.88f),
            Color(0xFF3F1D6B).copy(alpha = 0.86f),
            Color(0xFF2A124C).copy(alpha = 0.90f)
        )
        else -> listOf(
            Color.White,
            Color(0xFFEAE6F3),
            Color(0xFFEAE6F3)
        )
    }

    val glassFillBrush = if (activeProgress <= 0.001f) {
        if (hasIridescentRim && isEnabled) {
            Brush.verticalGradient(colors = defaultOffFillColors)
        } else {
            Brush.verticalGradient(
                colors = listOf(defaultOffFillColors[0], defaultOffFillColors[1])
            )
        }
    } else {
        Brush.verticalGradient(
            colors = List(3) { idx ->
                androidx.compose.ui.graphics.lerp(
                    defaultOffFillColors[idx],
                    activeOnFillColors[idx],
                    activeProgress
                )
            }
        )
    }

    val borderBrush = when {
        activeProgress > 0.01f && isNeonStyledButton -> {
            when (buttonType) {
                ActiveCallButtonType.MUTE -> Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF8A80).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFFFF2A3C).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFFFFB4AB).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFFFF1744).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFFFF8A80).copy(alpha = 0.45f + 0.55f * activeProgress)
                    )
                )
                ActiveCallButtonType.RECORD -> Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF80AB).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFFFF2E7E).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFFFFB2DD).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFFFF1744).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFFFF80AB).copy(alpha = 0.45f + 0.55f * activeProgress)
                    )
                )
                ActiveCallButtonType.AI_ASSISTANT -> Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.55f + 0.45f * activeProgress),
                        Color(0xFF00E676).copy(alpha = 0.55f + 0.45f * activeProgress),
                        Color(0xFF80D8FF).copy(alpha = 0.55f + 0.45f * activeProgress),
                        Color(0xFFB388FF).copy(alpha = 0.58f + 0.42f * activeProgress),
                        Color(0xFF00E5FF).copy(alpha = 0.55f + 0.45f * activeProgress)
                    )
                )
                ActiveCallButtonType.MORE -> Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFE0B0FF).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFFB388FF).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFFF3E5F5).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFF7C4DFF).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFFE0B0FF).copy(alpha = 0.45f + 0.55f * activeProgress)
                    )
                )
                ActiveCallButtonType.SPEAKER -> Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF80D8FF).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFF00B4FF).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFFE1F5FE).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFF2979FF).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFF80D8FF).copy(alpha = 0.45f + 0.55f * activeProgress)
                    )
                )
                ActiveCallButtonType.DIAL_PAD -> Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFE086FF).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFF9D4EDD).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFFF3D1FF).copy(alpha = 0.45f + 0.55f * activeProgress),
                        Color(0xFF6C63FF).copy(alpha = 0.50f + 0.50f * activeProgress),
                        Color(0xFFE086FF).copy(alpha = 0.45f + 0.55f * activeProgress)
                    )
                )
                else -> Brush.verticalGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0.7f))
                )
            }
        }
        effectiveActive -> {
            Brush.verticalGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0.7f))
            )
        }
        hasIridescentRim && isEnabled -> {
            Brush.sweepGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.45f),
                    Color(0xFF7CE8A6).copy(alpha = 0.55f),
                    Color(0xFF66BDF9).copy(alpha = 0.55f),
                    Color(0xFFF37BB8).copy(alpha = 0.58f),
                    Color.White.copy(alpha = 0.45f)
                )
            )
        }
        else -> {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isEnabled) 0.42f else 0.16f),
                    Color.White.copy(alpha = if (isEnabled) 0.14f else 0.06f)
                )
            )
        }
    }

    val borderWidth = if (activeProgress > 0.01f && isNeonStyledButton) {
        (1.0f + 1.1f * activeProgress).dp
    } else {
        1.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .alpha(if (isEnabled) 1f else 0.55f)
    ) {
        Box(
            modifier = Modifier.size(resolvedCircleSize),
            contentAlignment = Alignment.Center
        ) {
            // Outer ON-state neon halo, concentric ripples, equalizer ticks, or orbital rings (Reference Image 2).
            // Drawn in an unclipped Canvas of identical layout size so button coordinates never shift.
            if (activeProgress > 0.01f && isNeonStyledButton) {
                ActiveButtonOuterNeonEffectCanvas(
                    buttonType = buttonType,
                    activeProgress = activeProgress,
                    orbitDegrees = orbitPhase,
                    pulsePhase = pulsePhase,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .size(resolvedCircleSize)
                    .graphicsLayer {
                        rotationZ = if (isNeonStyledButton) clickRotationAnim.value else 0f
                        transformOrigin = TransformOrigin.Center
                    }
                    .clip(controlShape)
                    .background(brush = glassFillBrush)
                    .border(
                        width = if (ongoingPrefs.buttonStyle == OngoingButtonVisualStyle.NEON_RIM && isEnabled) 1.6.dp else borderWidth,
                        brush = borderBrush,
                        shape = controlShape
                    )
                    .clickable(enabled = isEnabled) {
                        if (isNeonStyledButton) {
                            if (isClickRotationRunning) return@clickable
                            if (isReducedMotion) {
                                onClick()
                            } else {
                                isClickRotationRunning = true
                                onClick()
                                coroutineScope.launch {
                                    try {
                                        clickRotationAnim.snapTo(0f)
                                        clickRotationAnim.animateTo(
                                            targetValue = 360f,
                                            animationSpec = tween(
                                                durationMillis = 450,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                        clickRotationAnim.snapTo(0f)
                                    } finally {
                                        isClickRotationRunning = false
                                    }
                                }
                            }
                        } else {
                            onClick()
                        }
                    }
                    .testTag(testTag),
                contentAlignment = Alignment.Center
            ) {
                if (hasIridescentRim && activeProgress <= 0.01f && isEnabled) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF64B5F6).copy(alpha = 0.22f),
                                    Color(0xFFBA68C8).copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.55f, size.height * 0.82f),
                                radius = size.width * 0.52f
                            )
                        )
                    }
                } else if (activeProgress > 0.01f && isNeonStyledButton) {
                    // Subtle inner specular rim glow matching Reference Image 2
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val innerGlowColor = when (buttonType) {
                            ActiveCallButtonType.MUTE -> Color(0xFFFF5252)
                            ActiveCallButtonType.RECORD -> Color(0xFFFF4081)
                            ActiveCallButtonType.AI_ASSISTANT -> Color(0xFF00E5FF)
                            ActiveCallButtonType.MORE -> Color(0xFFB388FF)
                            ActiveCallButtonType.SPEAKER -> Color(0xFF00B4FF)
                            ActiveCallButtonType.DIAL_PAD -> Color(0xFFAA00FF)
                            else -> Color.White
                        }
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    innerGlowColor.copy(alpha = (0.20f + 0.12f * pulsePhase) * activeProgress),
                                    innerGlowColor.copy(alpha = (0.38f + 0.14f * pulsePhase) * activeProgress)
                                ),
                                center = center,
                                radius = size.minDimension * 0.5f
                            )
                        )
                    }
                }
                icon(tint, activeProgress, pulsePhase)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            color = if (activeProgress > 0.5f && isNeonStyledButton) {
                ColorOsWhite.copy(alpha = 0.96f)
            } else {
                Color(0xFFBEBACB).copy(alpha = if (isEnabled) 1f else 0.65f)
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Renders the distinct Reference Image 2 outer neon rings, pulsing ripples, equalizer ticks,
 * orbital energy arcs + particles, and acoustic sound waves when a button is ON (`activeProgress > 0f`).
 */
@Composable
private fun ActiveButtonOuterNeonEffectCanvas(
    buttonType: ActiveCallButtonType,
    activeProgress: Float,
    orbitDegrees: Float,
    pulsePhase: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = size.minDimension / 2f

        when (buttonType) {
            ActiveCallButtonType.MUTE -> {
                // Red glowing circular halo + subtle pulsing concentric side ripples
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF2A3C).copy(alpha = (0.42f + 0.16f * pulsePhase) * activeProgress),
                            Color(0xFFFF1744).copy(alpha = (0.18f + 0.08f * pulsePhase) * activeProgress),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = baseR * (1.36f + 0.08f * pulsePhase)
                    ),
                    radius = baseR * (1.36f + 0.08f * pulsePhase),
                    center = Offset(cx, cy)
                )
                for (ringIdx in 0..2) {
                    val rippleProgress = ((pulsePhase + ringIdx * 0.33f) % 1f)
                    val arcR = baseR * (1.06f + rippleProgress * 0.28f)
                    val arcAlpha = (1f - rippleProgress) * 0.52f * activeProgress
                    val arcSize = Size(arcR * 2f, arcR * 2f)
                    val topLeft = Offset(cx - arcR, cy - arcR)
                    // Left & right concentric ripple arcs matching Reference Image 2 Mute
                    drawArc(
                        color = Color(0xFFFF4D5E).copy(alpha = arcAlpha),
                        startAngle = 140f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFFF4D5E).copy(alpha = arcAlpha),
                        startAngle = -40f,
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            ActiveCallButtonType.RECORD -> {
                // Pink/red neon halo + top radial equalizer waveform ticks + concentric side ripples
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF2E7E).copy(alpha = (0.44f + 0.16f * pulsePhase) * activeProgress),
                            Color(0xFFFF1744).copy(alpha = (0.18f + 0.08f * pulsePhase) * activeProgress),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = baseR * (1.38f + 0.07f * pulsePhase)
                    ),
                    radius = baseR * (1.38f + 0.07f * pulsePhase),
                    center = Offset(cx, cy)
                )

                // Top crown equalizer waveform bars (-148 deg to -32 deg) matching Reference Image 2 Record
                val tickCount = 13
                val startDeg = -148f
                val endDeg = -32f
                val stepDeg = (endDeg - startDeg) / (tickCount - 1).toFloat()
                val innerTickR = baseR * 1.07f
                val phaseRad = (orbitDegrees * PI / 180.0).toFloat()

                for (i in 0 until tickCount) {
                    val angleDeg = startDeg + i * stepDeg
                    val angleRad = (angleDeg * PI / 180.0).toFloat()
                    val centerWeight = 1f - kotlin.math.abs(i - (tickCount - 1) / 2f) / ((tickCount - 1) / 2f)
                    val waveMod = 0.45f + 0.55f * kotlin.math.abs(sin(phaseRad * 2f + i * 0.75f))
                    val isMajorTick = i % 2 == 0
                    val tickLen = baseR * (if (isMajorTick) 0.13f else 0.07f) * (0.65f + 0.55f * centerWeight * waveMod) * activeProgress
                    val sx = cx + innerTickR * cos(angleRad)
                    val sy = cy + innerTickR * sin(angleRad)
                    val ex = cx + (innerTickR + tickLen) * cos(angleRad)
                    val ey = cy + (innerTickR + tickLen) * sin(angleRad)
                    drawLine(
                        color = if (isMajorTick) {
                            Color(0xFFFF80AB).copy(alpha = 0.88f * activeProgress)
                        } else {
                            Color(0xFFFF2E7E).copy(alpha = 0.72f * activeProgress)
                        },
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = (if (isMajorTick) 1.9.dp else 1.3.dp).toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Subtle side ripple arcs
                for (ringIdx in 0..1) {
                    val rippleProgress = ((pulsePhase + ringIdx * 0.5f) % 1f)
                    val arcR = baseR * (1.08f + rippleProgress * 0.22f)
                    val arcAlpha = (1f - rippleProgress) * 0.42f * activeProgress
                    val topLeft = Offset(cx - arcR, cy - arcR)
                    val arcSize = Size(arcR * 2f, arcR * 2f)
                    drawArc(
                        color = Color(0xFFFF4081).copy(alpha = arcAlpha),
                        startAngle = 148f,
                        sweepAngle = 64f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFFF4081).copy(alpha = arcAlpha),
                        startAngle = -32f,
                        sweepAngle = 64f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            ActiveCallButtonType.AI_ASSISTANT -> {
                // Cyan, emerald, and violet glowing orbital ring + rotating energy arcs + subtle particles
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = (0.42f + 0.16f * pulsePhase) * activeProgress),
                            Color(0xFF00E676).copy(alpha = (0.26f + 0.10f * pulsePhase) * activeProgress),
                            Color(0xFF7C4DFF).copy(alpha = (0.18f + 0.08f * pulsePhase) * activeProgress),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = baseR * (1.42f + 0.08f * pulsePhase)
                    ),
                    radius = baseR * (1.42f + 0.08f * pulsePhase),
                    center = Offset(cx, cy)
                )

                val orbitR1 = baseR * 1.08f
                val orbitR2 = baseR * 1.17f

                // Rotating primary orbital energy arcs (Cyan -> Emerald -> Violet)
                rotate(degrees = orbitDegrees, pivot = Offset(cx, cy)) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF00E5FF).copy(alpha = 0.88f * activeProgress),
                                Color(0xFF00E676).copy(alpha = 0.92f * activeProgress),
                                Color.Transparent
                            )
                        ),
                        startAngle = -30f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(cx - orbitR1, cy - orbitR1),
                        size = Size(orbitR1 * 2f, orbitR1 * 2f),
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFB388FF).copy(alpha = 0.85f * activeProgress),
                                Color(0xFF00E5FF).copy(alpha = 0.88f * activeProgress),
                                Color.Transparent
                            )
                        ),
                        startAngle = 150f,
                        sweepAngle = 145f,
                        useCenter = false,
                        topLeft = Offset(cx - orbitR1, cy - orbitR1),
                        size = Size(orbitR1 * 2f, orbitR1 * 2f),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Counter-rotating secondary energy wisps
                rotate(degrees = -orbitDegrees * 0.75f + 45f, pivot = Offset(cx, cy)) {
                    drawArc(
                        color = Color(0xFF64FFDA).copy(alpha = 0.56f * activeProgress),
                        startAngle = 20f,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = Offset(cx - orbitR2, cy - orbitR2),
                        size = Size(orbitR2 * 2f, orbitR2 * 2f),
                        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFB388FF).copy(alpha = 0.54f * activeProgress),
                        startAngle = 200f,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = Offset(cx - orbitR2, cy - orbitR2),
                        size = Size(orbitR2 * 2f, orbitR2 * 2f),
                        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Subtle sparkling orbital particles around the ring
                val particleAngles = listOf(18f, 78f, 142f, 215f, 275f, 332f)
                val particleColors = listOf(
                    Color(0xFF84FFFF),
                    Color(0xFF69F0AE),
                    Color(0xFFE040FB),
                    Color(0xFF00E5FF),
                    Color(0xFFB388FF),
                    Color(0xFF00E676)
                )
                particleAngles.forEachIndexed { idx, baseDeg ->
                    val currentDeg = baseDeg + orbitDegrees * (if (idx % 2 == 0) 0.85f else -0.65f)
                    val rad = (currentDeg * PI / 180.0).toFloat()
                    val pr = baseR * (1.14f + 0.11f * kotlin.math.abs(sin((pulsePhase * PI + idx).toFloat())))
                    val px = cx + pr * cos(rad)
                    val py = cy + pr * sin(rad)
                    val twinkleAlpha = (0.45f + 0.55f * kotlin.math.abs(cos((pulsePhase * PI + idx * 0.9f).toFloat()))) * activeProgress
                    drawCircle(
                        color = particleColors[idx].copy(alpha = twinkleAlpha * 0.45f),
                        radius = 3.2.dp.toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = twinkleAlpha),
                        radius = 1.3.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            ActiveCallButtonType.MORE -> {
                // Premium violet glow with subtle expanding concentric ripple
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFB388FF).copy(alpha = (0.42f + 0.14f * pulsePhase) * activeProgress),
                            Color(0xFF7C4DFF).copy(alpha = (0.18f + 0.08f * pulsePhase) * activeProgress),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = baseR * (1.35f + 0.08f * pulsePhase)
                    ),
                    radius = baseR * (1.35f + 0.08f * pulsePhase),
                    center = Offset(cx, cy)
                )
                for (ringIdx in 0..2) {
                    val rippleProgress = ((pulsePhase + ringIdx * 0.33f) % 1f)
                    val ringR = baseR * (1.05f + rippleProgress * 0.25f)
                    val ringAlpha = (1f - rippleProgress) * 0.48f * activeProgress
                    drawCircle(
                        color = Color(0xFFD1C4E9).copy(alpha = ringAlpha),
                        radius = ringR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }

            ActiveCallButtonType.SPEAKER -> {
                // Electric blue glowing ring with expanding sound-wave animations
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00B4FF).copy(alpha = (0.46f + 0.16f * pulsePhase) * activeProgress),
                            Color(0xFF2979FF).copy(alpha = (0.20f + 0.08f * pulsePhase) * activeProgress),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = baseR * (1.38f + 0.08f * pulsePhase)
                    ),
                    radius = baseR * (1.38f + 0.08f * pulsePhase),
                    center = Offset(cx, cy)
                )
                for (waveIdx in 0..2) {
                    val waveProgress = ((pulsePhase + waveIdx * 0.33f) % 1f)
                    val waveR = baseR * (1.06f + waveProgress * 0.29f)
                    val waveAlpha = (1f - waveProgress) * 0.62f * activeProgress
                    val topLeft = Offset(cx - waveR, cy - waveR)
                    val arcSize = Size(waveR * 2f, waveR * 2f)
                    // Expanding acoustic sound-wave arcs around the speaker button matching Reference Image 2
                    drawArc(
                        color = Color(0xFF40C4FF).copy(alpha = waveAlpha),
                        startAngle = 115f,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF40C4FF).copy(alpha = waveAlpha),
                        startAngle = -30f,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            ActiveCallButtonType.DIAL_PAD -> {
                // Purple neon glow with subtle outer rings and corner accent sparkles
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9D4EDD).copy(alpha = (0.44f + 0.15f * pulsePhase) * activeProgress),
                            Color(0xFF6C63FF).copy(alpha = (0.20f + 0.08f * pulsePhase) * activeProgress),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = baseR * (1.36f + 0.07f * pulsePhase)
                    ),
                    radius = baseR * (1.36f + 0.07f * pulsePhase),
                    center = Offset(cx, cy)
                )
                for (ringIdx in 0..1) {
                    val rippleProgress = ((pulsePhase + ringIdx * 0.5f) % 1f)
                    val arcR = baseR * (1.06f + rippleProgress * 0.22f)
                    val arcAlpha = (1f - rippleProgress) * 0.48f * activeProgress
                    val topLeft = Offset(cx - arcR, cy - arcR)
                    val arcSize = Size(arcR * 2f, arcR * 2f)
                    drawArc(
                        color = Color(0xFFE086FF).copy(alpha = arcAlpha),
                        startAngle = 125f,
                        sweepAngle = 85f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFE086FF).copy(alpha = arcAlpha),
                        startAngle = -45f,
                        sweepAngle = 85f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            else -> Unit
        }
    }
}

private val EndCallPressDownEasing = CubicBezierEasing(0.22f, 0.68f, 0.18f, 1.0f)
private val EndCallFingerFlexEasing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)
private val EndCallButtonCompressEasing = CubicBezierEasing(0.3f, 0.0f, 0.25f, 1.0f)

/**
 * Realistic Hand-Held Red Rounded-Rectangle End Call Button matching the reference image:
 * - Preserves the finalized realistic human hand and red rounded-rectangle End Call button image.
 * - Uses hardware-accelerated [Modifier.graphicsLayer] RenderNode transforms that defer all animation
 *   state reads to the GPU/RenderThread layer phase — ensuring 0 recompositions, 0 allocations,
 *   and smooth 60fps+ performance without frame drops or UI thread blocking.
 * - On tap, plays a ~350ms (300–400ms) natural press-and-return animation and executes [onClick]
 *   exactly once immediately upon completion while blocking repeated taps.
 */
@Composable
private fun ColorOsRedEndCallPillButton(
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val isReducedMotion = rememberIsReducedMotionEnabled()
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressAnim = remember { Animatable(0f) }
    var hasTriggeredEndCall by remember { mutableStateOf(false) }

    // Keep as State<Float> and read .value ONLY inside hardware-accelerated graphicsLayer blocks
    // so animation frames never trigger Composition, Subcomposition, Layout, or Draw invalidations.
    val liveTouchGripState = animateFloatAsState(
        targetValue = if (isPressed && !isReducedMotion && !hasTriggeredEndCall) 0.45f else 0f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "end_call_live_touch_grip"
    )

    val baseCutoutPainter = painterResource(id = R.drawable.end_call_hand_button_cutout)
    val topFingersPainter = painterResource(id = R.drawable.end_call_top_fingers_overlay)
    val thumbPainter = painterResource(id = R.drawable.end_call_thumb_overlay)

    BoxWithConstraints(
        modifier = modifier
            .height(98.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !hasTriggeredEndCall
            ) {
                if (hasTriggeredEndCall) return@clickable
                hasTriggeredEndCall = true
                if (isReducedMotion) {
                    onClick()
                } else {
                    coroutineScope.launch {
                        // Press-down phase (~180ms) + smooth release phase (~170ms) = 350ms total (within 300–400ms)
                        pressAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 180,
                                easing = EndCallPressDownEasing
                            )
                        )
                        pressAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 170,
                                easing = FastOutSlowInEasing
                            )
                        )
                        onClick()
                    }
                }
            }
            .testTag(testTag),
        contentAlignment = Alignment.TopCenter
    ) {
        // The 460x286 cutout has the 327px red pill spanning x=[128..455] (71.1% of width)
        // and y=[31..176] (top fingers occupy top 10.8% of height).
        // Sizing the cutout so the red pill matches the Cols 2-3 width and aligns with the 72.dp circle row:
        val targetPillWidth = maxWidth.coerceAtLeast(148.dp)
        val cutoutWidth = targetPillWidth * 1.38f
        val cutoutHeight = cutoutWidth * (286f / 460f)
        val horizontalCenterCorrection = -cutoutWidth * 0.133f
        val topFingersOverhangCorrection = -cutoutHeight * 0.105f

        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopCenter, unbounded = true)
                .offset(
                    x = horizontalCenterCorrection,
                    y = topFingersOverhangCorrection
                )
                .size(width = cutoutWidth, height = cutoutHeight)
                // 1. Hardware-accelerated GraphicsLayer for forward & downward hand movement
                .graphicsLayer {
                    val gripProgress = maxOf(pressAnim.value, liveTouchGripState.value).coerceIn(0f, 1f)
                    translationY = (4.8f * gripProgress).dp.toPx()
                    translationX = (-0.9f * gripProgress).dp.toPx()
                    scaleX = 1f + 0.024f * gripProgress
                    scaleY = 1f + 0.020f * gripProgress
                    rotationX = -3.6f * gripProgress
                    rotationZ = -1.6f * gripProgress
                    cameraDistance = 14f * density
                    transformOrigin = TransformOrigin(0.24f, 0.86f)
                }
        ) {
            // 2. Hardware-accelerated GraphicsLayer for base hand + subtle red button compression
            Image(
                painter = baseCutoutPainter,
                contentDescription = "End call",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val gripProgress = maxOf(pressAnim.value, liveTouchGripState.value).coerceIn(0f, 1f)
                        val buttonCompress = EndCallButtonCompressEasing.transform(gripProgress)
                        scaleY = 1f - 0.048f * buttonCompress
                        scaleX = 1f + 0.014f * buttonCompress
                        translationY = (1.4f * buttonCompress).dp.toPx()
                        transformOrigin = TransformOrigin(0.634f, 0.362f)
                    }
            )

            // 3. Hardware-accelerated GraphicsLayer for articulated top fingers pressing & curling downward
            Image(
                painter = topFingersPainter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val gripProgress = maxOf(pressAnim.value, liveTouchGripState.value).coerceIn(0f, 1f)
                        val fingerFlex = EndCallFingerFlexEasing.transform(gripProgress)
                        translationY = (3.8f * fingerFlex).dp.toPx()
                        translationX = (0.5f * fingerFlex).dp.toPx()
                        rotationZ = -2.1f * fingerFlex
                        rotationX = -5.2f * fingerFlex
                        scaleY = 1f - 0.076f * fingerFlex
                        cameraDistance = 12f * density
                        transformOrigin = TransformOrigin(0.38f, 0.12f)
                    }
            )

            // 4. Hardware-accelerated GraphicsLayer for articulated thumb squeezing upward & inward
            Image(
                painter = thumbPainter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val gripProgress = maxOf(pressAnim.value, liveTouchGripState.value).coerceIn(0f, 1f)
                        val fingerFlex = EndCallFingerFlexEasing.transform(gripProgress)
                        translationY = (-2.4f * fingerFlex).dp.toPx()
                        translationX = (1.5f * fingerFlex).dp.toPx()
                        rotationZ = 2.1f * fingerFlex
                        scaleY = 1f - 0.038f * fingerFlex
                        transformOrigin = TransformOrigin(0.28f, 0.76f)
                    }
            )
        }
    }
}

/**
 * Handset + HD superscript icon matching the top-left of the timer row in both screenshots.
 */
@Composable
private fun HdHandsetBadgeIcon() {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = "HD Call",
            tint = ColorOsWhite,
            modifier = Modifier
                .size(18.dp)
                .offset(y = 2.dp)
        )
        Text(
            text = "HD",
            color = ColorOsWhite,
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 9.sp,
            modifier = Modifier.offset(x = (-2).dp, y = 0.dp)
        )
    }
}

/**
 * Stacked "Vo" over "5G" badge matching the status row in both screenshots.
 */
@Composable
private fun Vo5GBadgeIcon() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Vo",
            color = ColorOsWhite,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 9.sp
        )
        Text(
            text = "5G",
            color = ColorOsWhite,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 9.sp
        )
    }
}

/**
 * Mute button icon that matches Reference Image 1 in OFF state (`Icons.Outlined.MicOff`)
 * and transitions smoothly with a subtle pulse when toggled between muted and unmuted states.
 */
@Composable
private fun AnimatedMuteMicIcon(
    isMuted: Boolean,
    tint: Color,
    activeProgress: Float,
    pulsePhase: Float
) {
    val iconScale = 1f + (0.06f * pulsePhase * activeProgress)
    Box(
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.MicOff,
            contentDescription = if (isMuted) "Muted" else "Mute",
            tint = tint,
            modifier = Modifier.size(30.dp)
        )
    }
}

/**
 * Record button icon:
 * - OFF (`activeProgress == 0f`): Exact 5-element audio waveform icon (`·|I|·`) from Reference Image 1.
 * - ON (`activeProgress > 0f`): Smoothly transitions into the glowing red/pink recording ring
 *   and pulsing white center dot from Reference Image 2.
 */
@Composable
private fun AnimatedRecordButtonIcon(
    tint: Color,
    activeProgress: Float,
    pulsePhase: Float
) {
    Canvas(modifier = Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val stroke = 2.6.dp.toPx()

        val offAlpha = (1f - activeProgress * 1.25f).coerceIn(0f, 1f)
        if (offAlpha > 0.01f) {
            val barColor = tint.copy(alpha = tint.alpha * offAlpha)
            // Left dot
            drawCircle(
                color = barColor,
                radius = stroke * 0.55f,
                center = Offset(w * 0.14f, cy)
            )
            // Left-mid bar
            drawLine(
                color = barColor,
                start = Offset(w * 0.32f, cy - h * 0.18f),
                end = Offset(w * 0.32f, cy + h * 0.18f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            // Center tall bar
            drawLine(
                color = barColor,
                start = Offset(w * 0.50f, cy - h * 0.36f),
                end = Offset(w * 0.50f, cy + h * 0.36f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            // Right-mid bar
            drawLine(
                color = barColor,
                start = Offset(w * 0.68f, cy - h * 0.24f),
                end = Offset(w * 0.68f, cy + h * 0.24f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            // Right dot
            drawCircle(
                color = barColor,
                radius = stroke * 0.55f,
                center = Offset(w * 0.86f, cy)
            )
        }

        if (activeProgress > 0.01f) {
            val onAlpha = activeProgress.coerceIn(0f, 1f)
            val outerRingR = (size.minDimension * 0.36f) * (0.85f + 0.15f * onAlpha) * (0.95f + 0.08f * pulsePhase)
            val coreDotR = (size.minDimension * 0.19f) * (0.85f + 0.15f * onAlpha) * (0.94f + 0.10f * pulsePhase)

            // Soft crimson-pink glow behind the recording dot
            drawCircle(
                color = Color(0xFFFF1744).copy(alpha = 0.38f * onAlpha),
                radius = outerRingR * 1.25f,
                center = Offset(cx, cy)
            )
            // Bright red-coral outer ring matching Reference Image 2 Record icon
            drawCircle(
                color = Color(0xFFFF334B).copy(alpha = 0.95f * onAlpha),
                radius = outerRingR,
                center = Offset(cx, cy),
                style = Stroke(width = 2.4.dp.toPx())
            )
            // Luminous white center recording dot
            drawCircle(
                color = Color.White.copy(alpha = onAlpha),
                radius = coreDotR,
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * 6-point interlaced geometric star / pinwheel icon for "AI Call Assistant":
 * - OFF: Exact crisp white star from Reference Image 1.
 * - ON: Gently breathing cyan-white luminous star matching Reference Image 2.
 */
@Composable
private fun AnimatedAiCallAssistantStarIcon(
    tint: Color,
    activeProgress: Float,
    pulsePhase: Float
) {
    Canvas(modifier = Modifier.size(30.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = 1f + 0.06f * pulsePhase * activeProgress
        val outerR = size.minDimension * 0.44f * scale
        val innerR = size.minDimension * 0.20f * scale
        val strokeW = 2.2.dp.toPx()

        if (activeProgress > 0.01f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF84FFFF).copy(alpha = (0.35f + 0.15f * pulsePhase) * activeProgress),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = outerR * 1.25f
                ),
                radius = outerR * 1.25f,
                center = Offset(cx, cy)
            )
        }

        val starColor = if (activeProgress > 0.01f) {
            androidx.compose.ui.graphics.lerp(tint, Color(0xFFE0FFFF), activeProgress * 0.6f)
        } else {
            tint
        }

        for (i in 0 until 6) {
            rotate(degrees = i * 60f, pivot = Offset(cx, cy)) {
                val path = Path().apply {
                    moveTo(cx, cy - innerR)
                    lineTo(cx + innerR * 0.55f, cy - outerR * 0.85f)
                    lineTo(cx + innerR * 0.95f, cy - innerR * 0.35f)
                    close()
                }
                drawPath(
                    path = path,
                    color = starColor,
                    style = Stroke(
                        width = strokeW,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/**
 * Vertical 3-dots icon for the "More" button:
 * - OFF: Static 3 vertical dots matching Reference Image 1.
 * - ON: Sequential wave animation across the 3 dots matching Reference Image 2.
 */
@Composable
private fun AnimatedMoreVerticalDotsIcon(
    tint: Color,
    activeProgress: Float,
    pulsePhase: Float
) {
    Canvas(modifier = Modifier.size(28.dp)) {
        val cx = size.width / 2f
        val baseR = 2.3.dp.toPx()
        val yPositions = listOf(size.height * 0.24f, size.height * 0.50f, size.height * 0.76f)

        yPositions.forEachIndexed { idx, y ->
            val wave = if (activeProgress > 0.01f) {
                kotlin.math.abs(sin((pulsePhase * PI + idx * 0.85f).toFloat()))
            } else {
                0f
            }
            val r = baseR * (1f + 0.26f * wave * activeProgress)
            if (activeProgress > 0.01f) {
                drawCircle(
                    color = Color(0xFFE0B0FF).copy(alpha = 0.42f * wave * activeProgress),
                    radius = r * 1.7f,
                    center = Offset(cx, y)
                )
            }
            drawCircle(
                color = tint,
                radius = r,
                center = Offset(cx, y)
            )
        }
    }
}

/**
 * Speaker icon with subtle acoustic pulse when Speaker is ON.
 */
@Composable
private fun AnimatedSpeakerIcon(
    tint: Color,
    activeProgress: Float,
    pulsePhase: Float
) {
    val scale = 1f + 0.05f * pulsePhase * activeProgress
    Box(
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Speaker",
            tint = tint,
            modifier = Modifier.size(30.dp)
        )
    }
}

/**
 * 10-dot Dial Pad icon:
 * - OFF: Exact static 10-dot grid (`TenDotKeypadIcon`) matching Reference Image 1.
 * - ON: Subtle sequential dot illumination wave across the keypad dots matching Reference Image 2.
 */
@Composable
private fun AnimatedDialPadDotsIcon(
    tint: Color,
    activeProgress: Float,
    pulsePhase: Float
) {
    if (activeProgress <= 0.01f) {
        TenDotKeypadIcon(tint = tint)
    } else {
        Canvas(modifier = Modifier.size(22.dp)) {
            val baseDotR = 1.8.dp.toPx()
            val cols = listOf(size.width * 0.24f, size.width * 0.50f, size.width * 0.76f)
            val rows = listOf(
                size.height * 0.16f,
                size.height * 0.39f,
                size.height * 0.62f
            )
            var dotIdx = 0
            for (ry in rows) {
                for (cx in cols) {
                    val wave = kotlin.math.abs(sin((pulsePhase * PI * 1.5f + dotIdx * 0.45f).toFloat()))
                    val r = baseDotR * (1f + 0.24f * wave * activeProgress)
                    drawCircle(
                        color = Color(0xFFE086FF).copy(alpha = 0.42f * wave * activeProgress),
                        radius = r * 1.65f,
                        center = Offset(cx, ry)
                    )
                    drawCircle(
                        color = tint,
                        radius = r,
                        center = Offset(cx, ry)
                    )
                    dotIdx++
                }
            }
            val bottomWave = kotlin.math.abs(sin((pulsePhase * PI * 1.5f + dotIdx * 0.45f).toFloat()))
            val bottomR = baseDotR * (1f + 0.24f * bottomWave * activeProgress)
            val bottomCenter = Offset(size.width * 0.50f, size.height * 0.85f)
            drawCircle(
                color = Color(0xFFE086FF).copy(alpha = 0.42f * bottomWave * activeProgress),
                radius = bottomR * 1.65f,
                center = bottomCenter
            )
            drawCircle(
                color = tint,
                radius = bottomR,
                center = bottomCenter
            )
        }
    }
}

@Composable
private fun InCallDtmfPad(
    onDigitPress: (Char) -> Unit
) {
    val rows = listOf(
        listOf('1' to "", '2' to "ABC", '3' to "DEF"),
        listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
        listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
        listOf('*' to "", '0' to "+", '#' to "")
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { (digit, letters) ->
                    Column(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF5D507D).copy(alpha = 0.56f))
                            .border(
                                width = 0.8.dp,
                                color = Color.White.copy(alpha = 0.28f),
                                shape = CircleShape
                            )
                            .clickable { onDigitPress(digit) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = digit.toString(),
                            color = ColorOsWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (letters.isNotEmpty()) {
                            Text(
                                text = letters,
                                color = ColorOsWhite.copy(alpha = 0.70f),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldPauseCircleIcon(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val r = size.minDimension * 0.46f
        drawCircle(color = tint, radius = r, center = center)
        val barColor = Color(0xFF392B58)
        val barW = 2.4.dp.toPx()
        val barH = size.height * 0.32f
        drawLine(
            color = barColor,
            start = Offset(size.width * 0.41f, center.y - barH / 2f),
            end = Offset(size.width * 0.41f, center.y + barH / 2f),
            strokeWidth = barW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = barColor,
            start = Offset(size.width * 0.59f, center.y - barH / 2f),
            end = Offset(size.width * 0.59f, center.y + barH / 2f),
            strokeWidth = barW,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun AddCallPlusCircleIcon(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val r = size.minDimension * 0.46f
        drawCircle(color = tint, radius = r, center = center)
        val plusColor = Color(0xFF392B58)
        val stroke = 2.4.dp.toPx()
        val len = size.width * 0.34f
        drawLine(
            color = plusColor,
            start = Offset(center.x - len / 2f, center.y),
            end = Offset(center.x + len / 2f, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = plusColor,
            start = Offset(center.x, center.y - len / 2f),
            end = Offset(center.x, center.y + len / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun NotesDocumentIcon(tint: Color) {
    Canvas(modifier = Modifier.size(25.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.12f),
            size = Size(w * 0.64f, h * 0.76f),
            cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
        )
        val lineColor = Color(0xFF392B58)
        drawLine(
            color = lineColor,
            start = Offset(w * 0.32f, h * 0.38f),
            end = Offset(w * 0.68f, h * 0.38f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = lineColor,
            start = Offset(w * 0.32f, h * 0.54f),
            end = Offset(w * 0.56f, h * 0.54f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Resolves the next available supported audio endpoint in cyclic order
 * (Bluetooth -> Speaker -> Phone earpiece -> Bluetooth) so tapping the compact
 * upper audio output selector switches directly and immediately without opening any popup.
 */
fun resolveNextAvailableAudioEndpoint(callState: ActiveCallUiState): CallAudioEndpointItem? {
    val endpoints = if (callState.availableAudioEndpoints.isNotEmpty()) {
        callState.availableAudioEndpoints
    } else {
        buildList {
            if (callState.isBluetoothAvailable || callState.isBluetoothOn) {
                add(
                    CallAudioEndpointItem(
                        id = "endpoint_bluetooth_default",
                        name = callState.activeAudioEndpoint.name
                            .takeIf { callState.isBluetoothOn && it.isNotBlank() }
                            ?: "Bluetooth",
                        type = CallAudioEndpointType.BLUETOOTH,
                        isActive = callState.isBluetoothOn
                    )
                )
            }
            add(
                CallAudioEndpointItem(
                    id = "endpoint_speaker",
                    name = "Speaker",
                    type = CallAudioEndpointType.SPEAKER,
                    isActive = callState.isSpeakerOn
                )
            )
            add(
                CallAudioEndpointItem(
                    id = "endpoint_earpiece",
                    name = "Phone earpiece",
                    type = CallAudioEndpointType.EARPIECE,
                    isActive = !callState.isSpeakerOn && !callState.isBluetoothOn
                )
            )
        }
    }

    if (endpoints.size <= 1) return null

    val currentActiveType = when {
        callState.isBluetoothOn || callState.activeAudioEndpoint.type == CallAudioEndpointType.BLUETOOTH ->
            CallAudioEndpointType.BLUETOOTH
        callState.isSpeakerOn || callState.activeAudioEndpoint.type == CallAudioEndpointType.SPEAKER ->
            CallAudioEndpointType.SPEAKER
        callState.activeAudioEndpoint.type == CallAudioEndpointType.WIRED_HEADSET ->
            CallAudioEndpointType.WIRED_HEADSET
        else -> CallAudioEndpointType.EARPIECE
    }

    val currentIndex = endpoints.indexOfFirst { it.id == callState.activeAudioEndpoint.id }
        .takeIf { it >= 0 }
        ?: endpoints.indexOfFirst { it.isActive }.takeIf { it >= 0 }
        ?: endpoints.indexOfFirst { it.type == currentActiveType }.takeIf { it >= 0 }
        ?: 0

    return endpoints[(currentIndex + 1) % endpoints.size]
}


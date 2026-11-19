package com.example.telecom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.Call
import android.view.View
import android.view.WindowManager
import com.example.model.ActiveCallUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Distinct visual states of the HET Phone Call Smart Island matching the reference image:
 * - [IDLE]: No call active. Renders nothing so the normal status bar and camera punch-hole remain untouched.
 * - [INCOMING_CALL]: Purple neon glow (`#9C5CFF` / `#B388FF`), caller name + "Incoming call...", Answer & Decline buttons.
 * - [ACTIVE_CALL]: Green neon glow (`#00E676` / `#1EF2A6`), caller name + live `HH:MM:SS` timer, animated green waveform.
 * - [CALL_RECORDING]: Red neon glow (`#FF3B4E`), pulsing red dot, "Recording" + elapsed `HH:MM:SS` timer, live red waveform.
 *   Strictly shown ONLY when genuine call recording is confirmed active.
 * - [AI_CALL_ASSISTANT]: Cyan-to-purple gradient neon glow (`#00E5FF` -> `#B388FF`), sparkle icon,
 *   "AI Assistant" + live AI status ("Listening...", "Speaking...", "Paused", etc.), animated cyan-violet wave.
 * - [CALL_ENDED]: Short (`850ms`) closing animation with red hangup badge, "Call Ended", and subtle particle dispersion
 *   before smoothly collapsing to [IDLE]. Never delays or blocks ending a call.
 */
enum class SmartIslandVisualState(
    val primaryHex: Long,
    val secondaryHex: Long,
    val borderGlowHex: Long,
    val titleDefault: String
) {
    IDLE(
        primaryHex = 0xFF000000L,
        secondaryHex = 0xFF000000L,
        borderGlowHex = 0xFF000000L,
        titleDefault = ""
    ),
    INCOMING_CALL(
        primaryHex = 0xFF9C5CFFL,
        secondaryHex = 0xFFB388FFL,
        borderGlowHex = 0xFFA66BFFL,
        titleDefault = "Incoming call..."
    ),
    ACTIVE_CALL(
        primaryHex = 0xFF00E676L,
        secondaryHex = 0xFF1EF2A6L,
        borderGlowHex = 0xFF00E676L,
        titleDefault = "Call Active"
    ),
    CALL_RECORDING(
        primaryHex = 0xFFFF3B4EL,
        secondaryHex = 0xFFFF1744L,
        borderGlowHex = 0xFFFF455BL,
        titleDefault = "Recording"
    ),
    AI_CALL_ASSISTANT(
        primaryHex = 0xFF00E5FFL,
        secondaryHex = 0xFFB388FFL,
        borderGlowHex = 0xFF7C4DFFL,
        titleDefault = "AI Assistant"
    ),
    CALL_ENDED(
        primaryHex = 0xFFFF3B4EL,
        secondaryHex = 0xFFB388FFL,
        borderGlowHex = 0xFF9C5CFFL,
        titleDefault = "Call Ended"
    );

    val isVisible: Boolean
        get() = this != IDLE
}

/**
 * Expansion state for the Call Smart Island:
 * - [COMPACT]: Pill-shaped island positioned around the real physical punch-hole camera cutout.
 * - [EXPANDED]: Expanded glass card revealing Speaker toggle, Mute toggle, and End Call controls.
 */
enum class SmartIslandExpansionMode {
    COMPACT,
    EXPANDED
}

/**
 * Resolved gesture outcome on the Call Smart Island so a single gesture never triggers conflicting actions.
 */
enum class SmartIslandGestureOutcome {
    NONE,
    EXPAND_ISLAND,
    COLLAPSE_ISLAND,
    DISMISS_INCOMING_BANNER_KEEP_RINGING,
    OPEN_FULL_SCREEN_ACTIVE_CALL,
    CANCEL_LONG_PRESS_PROGRESS
}

/**
 * Result of evaluating a 3-second (`3000ms`) long-press gesture on the compact Smart Island.
 */
data class SmartIslandLongPressEvaluation(
    val progress: Float,
    val isCompleted: Boolean,
    val isCancelled: Boolean,
    val outcome: SmartIslandGestureOutcome
)

/**
 * Real hardware display cutout and status-bar metrics extracted from Android [android.view.WindowInsets]
 * and [android.view.DisplayCutout] in actual screen coordinates (e.g. Motorola G57 Power punch-hole).
 */
data class RealDisplayCutoutMetrics(
    val screenWidthPx: Int = 1080,
    val screenHeightPx: Int = 2400,
    val density: Float = 2.75f,
    val statusBarHeightPx: Int = 104,
    val hasPhysicalCutout: Boolean = true,
    val cutoutLeftPx: Int = 504,
    val cutoutTopPx: Int = 20,
    val cutoutRightPx: Int = 576,
    val cutoutBottomPx: Int = 92
) {
    val cutoutWidthPx: Int
        get() = (cutoutRightPx - cutoutLeftPx).coerceAtLeast(0)

    val cutoutHeightPx: Int
        get() = (cutoutBottomPx - cutoutTopPx).coerceAtLeast(0)

    val cutoutCenterXPx: Int
        get() = if (hasPhysicalCutout && cutoutWidthPx > 0) {
            (cutoutLeftPx + cutoutRightPx) / 2
        } else {
            screenWidthPx / 2
        }

    val cutoutCenterYPx: Int
        get() = if (hasPhysicalCutout && cutoutHeightPx > 0) {
            (cutoutTopPx + cutoutBottomPx) / 2
        } else {
            (statusBarHeightPx / 2).coerceAtLeast((16f * density).roundToInt())
        }
}

/**
 * Calculated screen-coordinate placement for the Smart Island around the real physical punch-hole camera:
 * - Vertically aligns the compact island's center with [RealDisplayCutoutMetrics.cutoutCenterYPx].
 * - Horizontally centers the island on [RealDisplayCutoutMetrics.cutoutCenterXPx].
 * - Constrains the compact island width so `[islandLeftPx, islandRightPx]` never overlaps the left status-bar
 *   clock/notification icons or the right network/battery status-bar icons.
 * - Computes the exact transparent aperture (`cameraHoleCenterXInIslandPx`, `cameraHoleCenterYInIslandPx`,
 *   `cameraHoleRadiusPx`) so the real physical camera remains 100% unobstructed with zero artificial lens drawn.
 */
data class SmartIslandCutoutPlacement(
    val islandTopOffsetPx: Int,
    val islandWidthPx: Int,
    val islandHeightPx: Int,
    val islandLeftPx: Int,
    val islandRightPx: Int,
    val islandHorizontalOffsetFromCenterPx: Int,
    val leftWingWidthPx: Int,
    val rightWingWidthPx: Int,
    val cameraHoleWidthPx: Int,
    val cameraHoleHeightPx: Int,
    val cameraHoleCenterXInIslandPx: Float,
    val cameraHoleCenterYInIslandPx: Float,
    val cameraHoleRadiusPx: Float,
    val statusBarLeftSafeLimitPx: Int,
    val statusBarRightSafeLimitPx: Int,
    val overlapsStatusBarIcons: Boolean,
    val surroundsPhysicalCamera: Boolean
)

/**
 * Pure deterministic calculator positioning the Smart Island around the real physical punch-hole camera
 * using actual [RealDisplayCutoutMetrics] without device-specific hardcoded coordinates.
 */
fun resolveSmartIslandCutoutPlacement(
    metrics: RealDisplayCutoutMetrics,
    visualState: SmartIslandVisualState = SmartIslandVisualState.ACTIVE_CALL,
    expansionMode: SmartIslandExpansionMode = SmartIslandExpansionMode.COMPACT
): SmartIslandCutoutPlacement {
    val d = metrics.density.coerceAtLeast(1f)
    val screenW = metrics.screenWidthPx.coerceAtLeast((320f * d).roundToInt())
    val statusH = metrics.statusBarHeightPx.coerceAtLeast((28f * d).roundToInt())

    val rawCutoutW = if (metrics.hasPhysicalCutout && metrics.cutoutWidthPx > 0) {
        metrics.cutoutWidthPx
    } else {
        (26f * d).roundToInt()
    }
    val rawCutoutH = if (metrics.hasPhysicalCutout && metrics.cutoutHeightPx > 0) {
        metrics.cutoutHeightPx
    } else {
        (26f * d).roundToInt()
    }

    val cutoutCenterX = metrics.cutoutCenterXPx.coerceIn(screenW / 4, (screenW * 3) / 4)
    val cutoutCenterY = metrics.cutoutCenterYPx.coerceIn(
        (12f * d).roundToInt(),
        max(statusH - (6f * d).roundToInt(), (18f * d).roundToInt())
    )

    // Reserve safe horizontal zones for status bar clock (left) and VoNR/5G+/battery icons (right)
    val statusBarLeftSafeLimitPx = max((74f * d).roundToInt(), (screenW * 0.21f).roundToInt())
    val statusBarRightSafeLimitPx = min(
        screenW - max((88f * d).roundToInt(), (screenW * 0.24f).roundToInt()),
        screenW - (76f * d).roundToInt()
    )

    val maxHalfSpanBeforeStatusIcons = min(
        (cutoutCenterX - statusBarLeftSafeLimitPx).coerceAtLeast((80f * d).roundToInt()),
        (statusBarRightSafeLimitPx - cutoutCenterX).coerceAtLeast((80f * d).roundToInt())
    )
    val maxCompactWidthPx = (maxHalfSpanBeforeStatusIcons * 2).coerceAtLeast((176f * d).roundToInt())

    // Unobstructed transparent camera gap around the real physical punch-hole camera
    val cameraHolePaddingPx = (8f * d).roundToInt()
    val cameraHoleWidthPx = (rawCutoutW + cameraHolePaddingPx * 2)
        .coerceIn((28f * d).roundToInt(), (56f * d).roundToInt())
    val cameraHoleHeightPx = (rawCutoutH + (4f * d).roundToInt())
        .coerceIn((24f * d).roundToInt(), (44f * d).roundToInt())

    val isExpanded = expansionMode == SmartIslandExpansionMode.EXPANDED &&
        visualState != SmartIslandVisualState.CALL_ENDED &&
        visualState != SmartIslandVisualState.INCOMING_CALL

    val desiredCompactWidthPx = when (visualState) {
        SmartIslandVisualState.CALL_ENDED -> (192f * d).roundToInt()
        SmartIslandVisualState.INCOMING_CALL -> (218f * d).roundToInt()
        else -> (210f * d).roundToInt()
    }

    val islandWidthPx = if (isExpanded) {
        min((screenW * 0.92f).roundToInt(), (364f * d).roundToInt())
    } else {
        min(desiredCompactWidthPx, maxCompactWidthPx)
    }

    // Compact height wraps tightly around the real camera cutout vertically inside the status bar band
    val compactHeightPx = max(rawCutoutH + (8f * d).roundToInt(), (34f * d).roundToInt())
        .coerceAtMost(max(statusH - (2f * d).roundToInt(), (36f * d).roundToInt()))
    val islandHeightPx = if (isExpanded) {
        (114f * d).roundToInt()
    } else {
        compactHeightPx
    }

    // Align the compact pill's vertical center directly with the real punch-hole camera's vertical center
    val rawTopOffsetPx = cutoutCenterY - (compactHeightPx / 2)
    val minTopPx = if (metrics.hasPhysicalCutout && metrics.cutoutTopPx > 0) {
        max(0, metrics.cutoutTopPx - (5f * d).roundToInt())
    } else {
        (2f * d).roundToInt()
    }
    val islandTopOffsetPx = rawTopOffsetPx.coerceAtLeast(minTopPx)

    val islandLeftPx = (cutoutCenterX - islandWidthPx / 2).coerceIn(0, screenW - islandWidthPx)
    val islandRightPx = islandLeftPx + islandWidthPx
    val islandHorizontalOffsetFromCenterPx = (islandLeftPx + islandWidthPx / 2) - (screenW / 2)

    val cameraCenterXInIsland = (cutoutCenterX - islandLeftPx).toFloat()
        .coerceIn(cameraHoleWidthPx / 2f, islandWidthPx - cameraHoleWidthPx / 2f)
    val cameraCenterYInIsland = (cutoutCenterY - islandTopOffsetPx).toFloat()
        .coerceIn(rawCutoutH / 2f, islandHeightPx - rawCutoutH / 2f)

    val leftWingWidthPx = (cameraCenterXInIsland.roundToInt() - cameraHoleWidthPx / 2).coerceAtLeast((48f * d).roundToInt())
    val rightWingWidthPx = (islandWidthPx - (cameraCenterXInIsland.roundToInt() + cameraHoleWidthPx / 2)).coerceAtLeast((48f * d).roundToInt())

    val cameraHoleRadiusPx = (max(rawCutoutW, rawCutoutH) / 2f) + (2.5f * d)

    val overlapsIcons = if (isExpanded) {
        false
    } else {
        islandLeftPx < statusBarLeftSafeLimitPx || islandRightPx > statusBarRightSafeLimitPx
    }

    val cutoutTop = if (metrics.hasPhysicalCutout && metrics.cutoutHeightPx > 0) {
        metrics.cutoutTopPx
    } else {
        cutoutCenterY - rawCutoutH / 2
    }
    val cutoutBottom = if (metrics.hasPhysicalCutout && metrics.cutoutHeightPx > 0) {
        metrics.cutoutBottomPx
    } else {
        cutoutCenterY + rawCutoutH / 2
    }
    val cutoutLeft = if (metrics.hasPhysicalCutout && metrics.cutoutWidthPx > 0) {
        metrics.cutoutLeftPx
    } else {
        cutoutCenterX - rawCutoutW / 2
    }
    val cutoutRight = if (metrics.hasPhysicalCutout && metrics.cutoutWidthPx > 0) {
        metrics.cutoutRightPx
    } else {
        cutoutCenterX + rawCutoutW / 2
    }

    val surroundsCamera = islandLeftPx <= cutoutLeft &&
        islandRightPx >= cutoutRight &&
        islandTopOffsetPx <= cutoutTop &&
        (islandTopOffsetPx + islandHeightPx) >= cutoutBottom

    return SmartIslandCutoutPlacement(
        islandTopOffsetPx = islandTopOffsetPx,
        islandWidthPx = islandWidthPx,
        islandHeightPx = islandHeightPx,
        islandLeftPx = islandLeftPx,
        islandRightPx = islandRightPx,
        islandHorizontalOffsetFromCenterPx = islandHorizontalOffsetFromCenterPx,
        leftWingWidthPx = leftWingWidthPx,
        rightWingWidthPx = rightWingWidthPx,
        cameraHoleWidthPx = cameraHoleWidthPx,
        cameraHoleHeightPx = cameraHoleHeightPx,
        cameraHoleCenterXInIslandPx = cameraCenterXInIsland,
        cameraHoleCenterYInIslandPx = cameraCenterYInIsland,
        cameraHoleRadiusPx = cameraHoleRadiusPx,
        statusBarLeftSafeLimitPx = statusBarLeftSafeLimitPx,
        statusBarRightSafeLimitPx = statusBarRightSafeLimitPx,
        overlapsStatusBarIcons = overlapsIcons,
        surroundsPhysicalCamera = surroundsCamera
    )
}

/**
 * Extracts real [RealDisplayCutoutMetrics] from an Android [View] / [WindowManager] using
 * `rootWindowInsets.displayCutout` (`boundingRects` and `safeInsetTop`).
 */
fun inspectRealDisplayCutoutMetrics(context: Context, view: View? = null): RealDisplayCutoutMetrics {
    val resources = context.resources
    val dm = resources.displayMetrics
    val density = dm.density.coerceAtLeast(1f)
    var screenW = dm.widthPixels.coerceAtLeast((360f * density).roundToInt())
    var screenH = dm.heightPixels.coerceAtLeast((640f * density).roundToInt())

    val resStatusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
    val fallbackStatusBarPx = if (resStatusBarId > 0) {
        resources.getDimensionPixelSize(resStatusBarId)
    } else {
        (36f * density).roundToInt()
    }
    var statusBarPx = fallbackStatusBarPx

    var hasCutout = false
    var cutoutLeft = 0
    var cutoutTop = 0
    var cutoutRight = 0
    var cutoutBottom = 0

    try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val windowMetrics = wm.currentWindowMetrics
            val bounds = windowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                screenW = bounds.width()
                screenH = bounds.height()
            }
            val windowInsets = windowMetrics.windowInsets
            val statusInsets = windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.statusBars()
            )
            if (statusInsets.top > 0) {
                statusBarPx = max(statusBarPx, statusInsets.top)
            }
            val displayCutout = windowInsets.displayCutout
            if (displayCutout != null) {
                if (displayCutout.safeInsetTop > 0) {
                    statusBarPx = max(statusBarPx, displayCutout.safeInsetTop)
                }
                val topRect = displayCutout.boundingRectTop.takeIf { !it.isEmpty }
                    ?: displayCutout.boundingRects.minByOrNull { it.top }
                if (topRect != null && !topRect.isEmpty) {
                    hasCutout = true
                    cutoutLeft = topRect.left
                    cutoutTop = topRect.top
                    cutoutRight = topRect.right
                    cutoutBottom = topRect.bottom
                }
            }
        }
    } catch (_: Throwable) {
    }

    if (!hasCutout && view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
            val rootInsets = view.rootWindowInsets
            val displayCutout = rootInsets?.displayCutout
            if (displayCutout != null) {
                if (displayCutout.safeInsetTop > 0) {
                    statusBarPx = max(statusBarPx, displayCutout.safeInsetTop)
                }
                val topRect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    displayCutout.boundingRectTop.takeIf { !it.isEmpty }
                        ?: displayCutout.boundingRects.minByOrNull { it.top }
                } else {
                    displayCutout.boundingRects.minByOrNull { it.top }
                }
                if (topRect != null && !topRect.isEmpty) {
                    hasCutout = true
                    cutoutLeft = topRect.left
                    cutoutTop = topRect.top
                    cutoutRight = topRect.right
                    cutoutBottom = topRect.bottom
                }
            }
        } catch (_: Throwable) {
        }
    }

    if (!hasCutout) {
        // Fallback punch-hole geometry centered in the status-bar band when DisplayCutout boundingRects are not reported
        val holeSizePx = (26f * density).roundToInt().coerceAtMost(max(statusBarPx - (6f * density).roundToInt(), (20f * density).roundToInt()))
        val centerX = screenW / 2
        val centerY = (statusBarPx / 2).coerceAtLeast((16f * density).roundToInt())
        cutoutLeft = centerX - holeSizePx / 2
        cutoutRight = centerX + holeSizePx / 2
        cutoutTop = (centerY - holeSizePx / 2).coerceAtLeast((2f * density).roundToInt())
        cutoutBottom = cutoutTop + holeSizePx
    }

    return RealDisplayCutoutMetrics(
        screenWidthPx = screenW,
        screenHeightPx = screenH,
        density = density,
        statusBarHeightPx = statusBarPx,
        hasPhysicalCutout = hasCutout,
        cutoutLeftPx = cutoutLeft,
        cutoutTopPx = cutoutTop,
        cutoutRightPx = cutoutRight,
        cutoutBottomPx = cutoutBottom
    )
}

/**
 * Complete UI state for the HET Phone Call Smart Island across the app and cross-app overlay.
 * - When [isFullScreenCallUiVisible] is `true` (`InCallActivity` Full-Screen Active Call or Full-Screen Incoming Call is visible),
 *   [isIslandVisible] is strictly `false` so a duplicate Smart Island never appears over the Full-Screen Call UI.
 */
data class SmartIslandUiState(
    val visualState: SmartIslandVisualState = SmartIslandVisualState.IDLE,
    val expansionMode: SmartIslandExpansionMode = SmartIslandExpansionMode.COMPACT,
    val callerTitle: String = "",
    val subtitleText: String = "",
    val formattedCallTimer: String = "00:00:00",
    val formattedRecordingTimer: String = "00:00:00",
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val isRecordingConfirmedActive: Boolean = false,
    val isAiAssistantActive: Boolean = false,
    val aiStatusLabel: String = "",
    val longPressProgress: Float = 0f,
    val isLongPressActive: Boolean = false,
    val isClosingAnimationActive: Boolean = false,
    val isDismissedForCurrentIncomingRing: Boolean = false,
    val isFullScreenCallUiVisible: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isSystemOverlayShowing: Boolean = false,
    val overlayPermissionPromptDismissedInSession: Boolean = false
) {
    val isIslandVisible: Boolean
        get() = !isFullScreenCallUiVisible &&
            visualState != SmartIslandVisualState.IDLE &&
            !(visualState == SmartIslandVisualState.INCOMING_CALL && isDismissedForCurrentIncomingRing)
}

/**
 * Formats elapsed seconds as `HH:MM:SS` (e.g., `00:02:36` or `00:00:18`) matching the
 * HET Phone Call Smart Island visual reference.
 */
fun formatSmartIslandDurationHHMMSS(elapsedSeconds: Long): String {
    val safeSec = elapsedSeconds.coerceAtLeast(0L)
    val hours = safeSec / 3600L
    val minutes = (safeSec % 3600L) / 60L
    val seconds = safeSec % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

/**
 * Pure deterministic resolver for the Smart Island's [SmartIslandVisualState].
 */
fun resolveSmartIslandVisualState(
    callState: ActiveCallUiState?,
    isRecordingConfirmedActive: Boolean = false,
    aiStatus: AiAssistantStatus = AiAssistantStatus.IDLE,
    isAiSessionActive: Boolean = false,
    isClosingAnimationActive: Boolean = false
): SmartIslandVisualState {
    if (callState == null || callState.state == Call.STATE_DISCONNECTED) {
        return if (isClosingAnimationActive) {
            SmartIslandVisualState.CALL_ENDED
        } else {
            SmartIslandVisualState.IDLE
        }
    }

    if (callState.isRinging || callState.isWaitingCallRinging) {
        return SmartIslandVisualState.INCOMING_CALL
    }

    val isCallConnected = callState.state == Call.STATE_ACTIVE || callState.state == Call.STATE_HOLDING
    if (isCallConnected && (isRecordingConfirmedActive || callState.isRecordingActive)) {
        return SmartIslandVisualState.CALL_RECORDING
    }

    if (isCallConnected && isAiSessionActive && aiStatus != AiAssistantStatus.IDLE) {
        return SmartIslandVisualState.AI_CALL_ASSISTANT
    }

    return SmartIslandVisualState.ACTIVE_CALL
}

/**
 * Pure visibility resolver determining whether the Smart Island should be displayed on screen:
 * - Returns `false` whenever [isFullScreenCallUiVisible] is `true` (`InCallActivity` Full-Screen Active Call Screen).
 * - Returns `true` when a call is active/ringing (or closing animation is active) and the user is on the
 *   Home Screen, another app (YouTube, Chrome, ChatGPT), or Phone app screens other than Full-Screen Active Call Screen.
 */
fun shouldDisplaySmartIsland(
    visualState: SmartIslandVisualState,
    isFullScreenCallUiVisible: Boolean,
    isDismissedForCurrentIncomingRing: Boolean = false
): Boolean {
    if (isFullScreenCallUiVisible) return false
    if (visualState == SmartIslandVisualState.IDLE) return false
    if (visualState == SmartIslandVisualState.INCOMING_CALL && isDismissedForCurrentIncomingRing) return false
    return true
}

/**
 * Maps [AiAssistantStatus] to the concise status subtitle shown on the AI Call Assistant island
 * (e.g. "Listening...", "Speaking...", "Paused", "Processing...").
 */
fun resolveSmartIslandAiStatusSubtitle(status: AiAssistantStatus): String {
    return when (status) {
        AiAssistantStatus.LISTENING -> "Listening..."
        AiAssistantStatus.SPEAKING -> "Speaking..."
        AiAssistantStatus.PROCESSING,
        AiAssistantStatus.SPEECH_RECOGNIZED -> "Processing..."
        AiAssistantStatus.PAUSED -> "Paused"
        AiAssistantStatus.CONNECTING,
        AiAssistantStatus.WAITING_FOR_USER -> "Waiting for User..."
        AiAssistantStatus.RECOGNITION_FAILED,
        AiAssistantStatus.ERROR -> "Recognition Failed"
        AiAssistantStatus.IDLE -> "Inactive"
    }
}

/**
 * Pure evaluator for the 3-second (`3000ms`) long-press circular progress gesture on the Compact Island.
 */
fun evaluateSmartIslandLongPress(
    heldDurationMs: Long,
    isReleased: Boolean,
    requiredHoldMs: Long = 3000L
): SmartIslandLongPressEvaluation {
    val safeTarget = requiredHoldMs.coerceAtLeast(500L)
    val clampedHeld = heldDurationMs.coerceAtLeast(0L)
    if (clampedHeld >= safeTarget) {
        return SmartIslandLongPressEvaluation(
            progress = 1f,
            isCompleted = true,
            isCancelled = false,
            outcome = SmartIslandGestureOutcome.OPEN_FULL_SCREEN_ACTIVE_CALL
        )
    }
    if (isReleased) {
        return SmartIslandLongPressEvaluation(
            progress = 0f,
            isCompleted = false,
            isCancelled = true,
            outcome = SmartIslandGestureOutcome.CANCEL_LONG_PRESS_PROGRESS
        )
    }
    val rawProgress = (clampedHeld.toFloat() / safeTarget.toFloat()).coerceIn(0f, 0.999f)
    return SmartIslandLongPressEvaluation(
        progress = rawProgress,
        isCompleted = false,
        isCancelled = false,
        outcome = SmartIslandGestureOutcome.NONE
    )
}

/**
 * Pure resolver for tap, outside-tap, and vertical swipe gestures on the Call Smart Island:
 * - Guarantees a single gesture never triggers multiple actions.
 * - Prevents accidental call actions while swiping.
 */
fun resolveSmartIslandGestureOutcome(
    visualState: SmartIslandVisualState,
    expansionMode: SmartIslandExpansionMode,
    isSingleTap: Boolean = false,
    isOutsideTap: Boolean = false,
    dragDeltaY: Float = 0f,
    wasLongPressStarted: Boolean = false,
    longPressDurationMs: Long = 0L,
    swipeThresholdPx: Float = 32f
): SmartIslandGestureOutcome {
    if (visualState == SmartIslandVisualState.IDLE || visualState == SmartIslandVisualState.CALL_ENDED) {
        return SmartIslandGestureOutcome.NONE
    }

    if (wasLongPressStarted) {
        return if (longPressDurationMs >= 3000L) {
            SmartIslandGestureOutcome.OPEN_FULL_SCREEN_ACTIVE_CALL
        } else {
            SmartIslandGestureOutcome.CANCEL_LONG_PRESS_PROGRESS
        }
    }

    val effectiveThreshold = abs(swipeThresholdPx).coerceAtLeast(16f)

    if (abs(dragDeltaY) >= effectiveThreshold) {
        return if (dragDeltaY < 0f) {
            if (expansionMode == SmartIslandExpansionMode.EXPANDED) {
                SmartIslandGestureOutcome.COLLAPSE_ISLAND
            } else if (visualState == SmartIslandVisualState.INCOMING_CALL) {
                SmartIslandGestureOutcome.DISMISS_INCOMING_BANNER_KEEP_RINGING
            } else {
                SmartIslandGestureOutcome.NONE
            }
        } else {
            if (expansionMode == SmartIslandExpansionMode.COMPACT) {
                if (visualState == SmartIslandVisualState.INCOMING_CALL) {
                    SmartIslandGestureOutcome.OPEN_FULL_SCREEN_ACTIVE_CALL
                } else {
                    SmartIslandGestureOutcome.EXPAND_ISLAND
                }
            } else {
                SmartIslandGestureOutcome.OPEN_FULL_SCREEN_ACTIVE_CALL
            }
        }
    }

    if (isOutsideTap && expansionMode == SmartIslandExpansionMode.EXPANDED) {
        return SmartIslandGestureOutcome.COLLAPSE_ISLAND
    }

    if (isSingleTap) {
        return if (expansionMode == SmartIslandExpansionMode.COMPACT) {
            SmartIslandGestureOutcome.EXPAND_ISLAND
        } else {
            SmartIslandGestureOutcome.NONE
        }
    }

    return SmartIslandGestureOutcome.NONE
}

/**
 * Central state manager for the HET Phone Call Smart Island system.
 * Connects strictly to existing [CallStateManager], [CallRecordingManager], and [AiCallAssistantManager]
 * without modifying their internal implementations.
 */
object CallSmartIslandManager {

    const val LONG_PRESS_DURATION_MS = 3000L
    const val CALL_ENDED_ANIMATION_DURATION_MS = 850L

    private val mainHandler: Handler? by lazy {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) Handler(looper) else null
        } catch (_: Throwable) {
            null
        }
    }
    private var callEndedDismissRunnable: Runnable? = null
    private var hadOngoingCallPreviously: Boolean = false
    private var lastEndedCallerTitle: String = ""

    private val _uiState = MutableStateFlow(SmartIslandUiState())
    val uiState: StateFlow<SmartIslandUiState> = _uiState.asStateFlow()

    fun canDrawOverlays(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun createManageOverlayPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun refreshOverlayPermissionState(context: Context) {
        val granted = canDrawOverlays(context)
        _uiState.update { it.copy(hasOverlayPermission = granted) }
    }

    fun setOverlayPromptDismissedInSession(dismissed: Boolean) {
        _uiState.update { it.copy(overlayPermissionPromptDismissedInSession = dismissed) }
    }

    fun setSystemOverlayShowing(showing: Boolean) {
        _uiState.update { it.copy(isSystemOverlayShowing = showing) }
    }

    /**
     * Immediately updates whether the Full-Screen Active Call / Full-Screen Incoming Call UI
     * (`InCallActivity`) is visible so the Smart Island hides without delay or flicker when
     * entering full-screen call UI and reappears immediately when leaving it.
     */
    fun setFullScreenCallUiVisible(visible: Boolean) {
        _uiState.update { current ->
            if (current.isFullScreenCallUiVisible == visible) {
                current
            } else {
                current.copy(
                    isFullScreenCallUiVisible = visible,
                    expansionMode = if (visible) SmartIslandExpansionMode.COMPACT else current.expansionMode,
                    longPressProgress = if (visible) 0f else current.longPressProgress,
                    isLongPressActive = if (visible) false else current.isLongPressActive
                )
            }
        }
    }

    fun expandIsland() {
        _uiState.update { current ->
            if (current.visualState == SmartIslandVisualState.IDLE ||
                current.visualState == SmartIslandVisualState.CALL_ENDED ||
                current.isFullScreenCallUiVisible
            ) {
                current
            } else {
                current.copy(
                    expansionMode = SmartIslandExpansionMode.EXPANDED,
                    longPressProgress = 0f,
                    isLongPressActive = false
                )
            }
        }
    }

    fun collapseIsland() {
        _uiState.update { current ->
            current.copy(
                expansionMode = SmartIslandExpansionMode.COMPACT,
                longPressProgress = 0f,
                isLongPressActive = false
            )
        }
    }

    fun updateLongPressProgress(progress: Float, isActive: Boolean) {
        _uiState.update { current ->
            current.copy(
                longPressProgress = progress.coerceIn(0f, 1f),
                isLongPressActive = isActive
            )
        }
    }

    fun cancelLongPressProgress() {
        _uiState.update { current ->
            current.copy(
                longPressProgress = 0f,
                isLongPressActive = false
            )
        }
    }

    /**
     * Opens the existing Full-Screen Active Call Screen (`InCallActivity`) when the user completes
     * a 3-second long press on the Compact Smart Island or taps "Full Screen" in the Expanded Island.
     *
     * Note: Does NOT pre-set `isFullScreenCallUiVisible = true` before `InCallActivity` starts;
     * `InCallActivity.onStart()` / `onResume()` updates `isFullScreenCallUiVisible` once the
     * Full-Screen Active Call UI is genuinely visible in the foreground.
     */
    fun openFullScreenCallUi(context: Context) {
        collapseIsland()
        cancelLongPressProgress()
        val activeCall = CallStateManager.activeCallUiState.value
        if (activeCall?.isRinging == true && context !is android.app.Activity) {
            CallStateManager.expandIncomingCallToFullScreen()
        } else {
            CallStateManager.openFullScreenActiveCallUi(context)
        }
    }

    /**
     * Synchronizes the Smart Island state with the real live states from [CallStateManager],
     * [CallRecordingManager], and [AiCallAssistantManager].
     */
    fun onExternalStatesUpdated(
        callState: ActiveCallUiState?,
        recordingState: CallRecordingSessionState,
        aiState: AiAssistantUiState,
        isBannerDismissedBySwipe: Boolean,
        elapsedCallSeconds: Long,
        isFullScreenCallUiVisible: Boolean = CallStateManager.isInCallActivityVisible.value
    ) {
        val hasOngoingCallNow = callState != null && callState.state != Call.STATE_DISCONNECTED

        if (!hasOngoingCallNow) {
            if (hadOngoingCallPreviously) {
                hadOngoingCallPreviously = false
                triggerCallEndedClosingAnimation()
            } else if (!_uiState.value.isClosingAnimationActive) {
                _uiState.update {
                    it.copy(
                        visualState = SmartIslandVisualState.IDLE,
                        expansionMode = SmartIslandExpansionMode.COMPACT,
                        callerTitle = "",
                        subtitleText = "",
                        formattedCallTimer = "00:00:00",
                        formattedRecordingTimer = "00:00:00",
                        isRecordingConfirmedActive = false,
                        isAiAssistantActive = false,
                        aiStatusLabel = "",
                        longPressProgress = 0f,
                        isLongPressActive = false,
                        isDismissedForCurrentIncomingRing = false,
                        isFullScreenCallUiVisible = false
                    )
                }
            }
            return
        }

        callEndedDismissRunnable?.let { mainHandler?.removeCallbacks(it) }
        callEndedDismissRunnable = null
        hadOngoingCallPreviously = true

        val callerTitle = when {
            callState.isWaitingCallRinging -> {
                callState.waitingCallerName?.takeIf { it.isNotBlank() }
                    ?: callState.waitingCallNumber?.takeIf { it.isNotBlank() }
                    ?: "Incoming Call"
            }
            !callState.displayName.isNullOrBlank() -> callState.displayName
            callState.number.isNotBlank() -> callState.number
            else -> "Unknown"
        }
        lastEndedCallerTitle = callerTitle

        val isCallConnected = callState.state == Call.STATE_ACTIVE || callState.state == Call.STATE_HOLDING
        val isRecordingConfirmed = isCallConnected &&
            (recordingState.isRecordingActive || callState.isRecordingActive)
        val isAiSessionActive = isCallConnected &&
            aiState.activeSessionId > 0L &&
            aiState.status != AiAssistantStatus.IDLE &&
            aiState.activeTestMode == AiTestMode.NONE

        val nextVisualState = resolveSmartIslandVisualState(
            callState = callState,
            isRecordingConfirmedActive = isRecordingConfirmed,
            aiStatus = aiState.status,
            isAiSessionActive = isAiSessionActive,
            isClosingAnimationActive = false
        )

        val formattedCallTime = formatSmartIslandDurationHHMMSS(elapsedCallSeconds)
        val formattedRecTime = formatSmartIslandDurationHHMMSS(recordingState.elapsedSeconds)
        val aiSubtitle = resolveSmartIslandAiStatusSubtitle(aiState.status)

        val subtitle = when (nextVisualState) {
            SmartIslandVisualState.INCOMING_CALL -> "Incoming call..."
            SmartIslandVisualState.CALL_RECORDING -> formattedRecTime
            SmartIslandVisualState.AI_CALL_ASSISTANT -> aiSubtitle
            SmartIslandVisualState.ACTIVE_CALL -> {
                when {
                    callState.state == Call.STATE_DIALING || callState.state == Call.STATE_CONNECTING -> "Calling..."
                    callState.isOnHold -> "On Hold • $formattedCallTime"
                    else -> formattedCallTime
                }
            }
            SmartIslandVisualState.CALL_ENDED -> "Call Ended"
            SmartIslandVisualState.IDLE -> ""
        }

        _uiState.update { current ->
            val nextExpansion = if (isFullScreenCallUiVisible ||
                (current.visualState == SmartIslandVisualState.INCOMING_CALL &&
                    nextVisualState != SmartIslandVisualState.INCOMING_CALL)
            ) {
                SmartIslandExpansionMode.COMPACT
            } else {
                current.expansionMode
            }

            current.copy(
                visualState = nextVisualState,
                expansionMode = nextExpansion,
                callerTitle = callerTitle,
                subtitleText = subtitle,
                formattedCallTimer = formattedCallTime,
                formattedRecordingTimer = formattedRecTime,
                isMuted = callState.isMuted,
                isSpeakerOn = callState.isSpeakerOn,
                isOnHold = callState.isOnHold,
                isRecordingConfirmedActive = isRecordingConfirmed,
                isAiAssistantActive = isAiSessionActive,
                aiStatusLabel = aiSubtitle,
                isClosingAnimationActive = false,
                isFullScreenCallUiVisible = isFullScreenCallUiVisible,
                isDismissedForCurrentIncomingRing = if (nextVisualState == SmartIslandVisualState.INCOMING_CALL) {
                    isBannerDismissedBySwipe
                } else {
                    false
                }
            )
        }
    }

    /**
     * Triggers the short (`850ms`) CALL_ENDED closing animation with subtle particle dispersion
     * before returning to [SmartIslandVisualState.IDLE]. Never delays Telecom call disconnection.
     */
    fun triggerCallEndedClosingAnimation() {
        callEndedDismissRunnable?.let { mainHandler?.removeCallbacks(it) }
        _uiState.update { current ->
            current.copy(
                visualState = SmartIslandVisualState.CALL_ENDED,
                expansionMode = SmartIslandExpansionMode.COMPACT,
                callerTitle = lastEndedCallerTitle.ifBlank { "Call Ended" },
                subtitleText = "Call Ended",
                longPressProgress = 0f,
                isLongPressActive = false,
                isClosingAnimationActive = true,
                isDismissedForCurrentIncomingRing = false,
                isFullScreenCallUiVisible = false
            )
        }
        val runnable = Runnable {
            _uiState.update { current ->
                if (current.visualState == SmartIslandVisualState.CALL_ENDED) {
                    current.copy(
                        visualState = SmartIslandVisualState.IDLE,
                        expansionMode = SmartIslandExpansionMode.COMPACT,
                        callerTitle = "",
                        subtitleText = "",
                        isClosingAnimationActive = false
                    )
                } else {
                    current
                }
            }
        }
        callEndedDismissRunnable = runnable
        mainHandler?.postDelayed(runnable, CALL_ENDED_ANIMATION_DURATION_MS)
    }

    fun dismissCallEndedAnimationImmediate() {
        callEndedDismissRunnable?.let { mainHandler?.removeCallbacks(it) }
        callEndedDismissRunnable = null
        hadOngoingCallPreviously = false
        _uiState.update { current ->
            current.copy(
                visualState = SmartIslandVisualState.IDLE,
                expansionMode = SmartIslandExpansionMode.COMPACT,
                callerTitle = "",
                subtitleText = "",
                isClosingAnimationActive = false,
                isFullScreenCallUiVisible = false
            )
        }
    }
}


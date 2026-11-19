package com.example.telecom

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.components.CallSmartIslandHost
import com.example.ui.theme.MyApplicationTheme

/**
 * Manages the cross-app system overlay window (`TYPE_APPLICATION_OVERLAY`) for the
 * HET Phone Call Smart Island while `ColorInCallService` is active.
 *
 * - Requires `Settings.canDrawOverlays(context)` (`SYSTEM_ALERT_WINDOW`).
 * - Configures `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` (API 30+) / `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`
 *   (API 28+) with `FLAG_LAYOUT_IN_SCREEN` so the overlay window origin (`y = 0`) aligns with the real
 *   physical top edge and punch-hole camera cutout rather than being pushed below the status bar.
 * - Toggles `View.VISIBLE` / `View.GONE` while a call is active so hiding the island on the Full-Screen
 *   Active Call Screen (`InCallActivity`) and restoring it on Home / other apps happens instantaneously
 *   without window re-creation flicker.
 */
class SmartIslandOverlayWindowController(
    private val context: Context
) {

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val viewModelStore: ViewModelStore
            get() = store

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performRestore(Bundle())
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    private var windowManager: WindowManager? = null
    private var rootOverlayContainer: FrameLayout? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var isAttached: Boolean = false

    fun syncOverlayVisibility(shouldShowCrossAppOverlay: Boolean) {
        val hasPermission = CallSmartIslandManager.canDrawOverlays(context)
        CallSmartIslandManager.refreshOverlayPermissionState(context)

        if (!hasPermission) {
            detachOverlay()
            return
        }

        val currentIslandState = CallSmartIslandManager.uiState.value
        val isCallCompletelyIdle =
            currentIslandState.visualState == SmartIslandVisualState.IDLE &&
                CallStateManager.activeCallUiState.value == null

        if (!shouldShowCrossAppOverlay) {
            if (isCallCompletelyIdle) {
                detachOverlay()
            } else {
                hideAttachedOverlayWithoutFlicker()
            }
            return
        }

        if (!isAttached) {
            attachOverlay()
        } else {
            showAttachedOverlayWithoutFlicker()
        }
    }

    private fun hideAttachedOverlayWithoutFlicker() {
        val container = rootOverlayContainer
        val lp = overlayLayoutParams
        val wm = windowManager
        if (!isAttached || container == null || lp == null || wm == null) {
            CallSmartIslandManager.setSystemOverlayShowing(false)
            return
        }
        try {
            if (container.visibility != View.GONE) {
                container.visibility = View.GONE
            }
            val updatedFlags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (lp.flags != updatedFlags) {
                lp.flags = updatedFlags
                wm.updateViewLayout(container, lp)
            }
        } catch (_: Throwable) {
        } finally {
            CallSmartIslandManager.setSystemOverlayShowing(false)
        }
    }

    private fun showAttachedOverlayWithoutFlicker() {
        val container = rootOverlayContainer
        val lp = overlayLayoutParams
        val wm = windowManager
        if (!isAttached || container == null || lp == null || wm == null) {
            attachOverlay()
            return
        }
        try {
            val updatedFlags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            if (lp.flags != updatedFlags) {
                lp.flags = updatedFlags
                wm.updateViewLayout(container, lp)
            }
            if (container.visibility != View.VISIBLE) {
                container.visibility = View.VISIBLE
            }
            CallSmartIslandManager.setSystemOverlayShowing(true)
        } catch (_: Throwable) {
            CallSmartIslandManager.setSystemOverlayShowing(false)
        }
    }

    private fun attachOverlay() {
        if (isAttached) {
            showAttachedOverlayWithoutFlicker()
            return
        }
        if (!CallSmartIslandManager.canDrawOverlays(context)) {
            CallSmartIslandManager.setSystemOverlayShowing(false)
            return
        }

        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            windowManager = wm

            val lifecycleOwner = OverlayLifecycleOwner().also { it.onCreate() }
            overlayLifecycleOwner = lifecycleOwner

            val container = object : FrameLayout(context) {
                override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
                    if (ev?.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                        if (CallSmartIslandManager.uiState.value.expansionMode == SmartIslandExpansionMode.EXPANDED) {
                            CallSmartIslandManager.collapseIsland()
                        }
                    }
                    return super.onInterceptTouchEvent(ev)
                }

                override fun onTouchEvent(event: MotionEvent?): Boolean {
                    if (event?.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                        if (CallSmartIslandManager.uiState.value.expansionMode == SmartIslandExpansionMode.EXPANDED) {
                            CallSmartIslandManager.collapseIsland()
                        }
                        return false
                    }
                    return super.onTouchEvent(event)
                }
            }.apply {
                clipChildren = false
                clipToPadding = false
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }

            val composeView = ComposeView(context).apply {
                clipChildren = false
                clipToPadding = false
                setContent {
                    MyApplicationTheme {
                        val islandState by CallSmartIslandManager.uiState.collectAsStateWithLifecycle()
                        CallSmartIslandHost(
                            islandState = islandState,
                            onAnswerCall = { CallStateManager.answerRingingCall(context) },
                            onDeclineOrEndCall = { CallStateManager.rejectOrDisconnectCall(context) },
                            onToggleSpeaker = { CallStateManager.toggleSpeakerphone() },
                            onToggleMute = { CallStateManager.toggleMute() },
                            onExpandIsland = { CallSmartIslandManager.expandIsland() },
                            onCollapseIsland = { CallSmartIslandManager.collapseIsland() },
                            onDismissIncomingKeepRinging = {
                                CallStateManager.dismissIncomingBannerKeepRinging()
                            },
                            onOpenFullScreenCall = {
                                CallSmartIslandManager.openFullScreenCallUi(context)
                            },
                            onUpdateLongPressProgress = { progress, active ->
                                CallSmartIslandManager.updateLongPressProgress(progress, active)
                            },
                            onCancelLongPressProgress = {
                                CallSmartIslandManager.cancelLongPressProgress()
                            }
                        )
                    }
                }
            }

            container.addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                title = "HETPhoneCallSmartIsland"
            }

            wm.addView(container, layoutParams)
            rootOverlayContainer = container
            overlayLayoutParams = layoutParams
            isAttached = true
            CallSmartIslandManager.setSystemOverlayShowing(true)
        } catch (_: Throwable) {
            isAttached = false
            rootOverlayContainer = null
            overlayLayoutParams = null
            overlayLifecycleOwner?.onDestroy()
            overlayLifecycleOwner = null
            CallSmartIslandManager.setSystemOverlayShowing(false)
        }
    }

    fun detachOverlay() {
        if (!isAttached && rootOverlayContainer == null) {
            CallSmartIslandManager.setSystemOverlayShowing(false)
            return
        }
        try {
            rootOverlayContainer?.let { view ->
                windowManager?.removeViewImmediate(view)
            }
        } catch (_: Throwable) {
        } finally {
            rootOverlayContainer = null
            overlayLayoutParams = null
            overlayLifecycleOwner?.onDestroy()
            overlayLifecycleOwner = null
            isAttached = false
            CallSmartIslandManager.setSystemOverlayShowing(false)
        }
    }
}

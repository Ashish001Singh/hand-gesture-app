package com.handsfree.control.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.handsfree.control.data.model.DeviceAction

/**
 * GestureAccessibilityService is an Android Accessibility Service that performs
 * system-level gestures (scroll, swipe, tap) programmatically.
 *
 * ## How It Works
 *
 * Android's Accessibility Service API allows apps to perform gestures on behalf
 * of the user. This is the same API used by accessibility tools like TalkBack.
 *
 * Key methods used:
 * - [dispatchGesture]: Dispatches a [GestureDescription] that the system
 *   interprets as a real touch gesture. This works across ALL apps.
 *
 * ## Setup Required
 * The user must manually enable this service in:
 * Settings → Accessibility → HandsFree Control → Enable
 *
 * This is an Android security requirement — accessibility services cannot
 * be enabled programmatically.
 *
 * ## Gesture Construction
 * Each action is built as a [GestureDescription] with one or more [Path] strokes:
 * - **Scroll**: A vertical Path from point A to point B
 * - **Swipe**: A horizontal Path from point A to point B
 * - **Tap**: A very short Path (essentially a point) with short duration
 * - **Double Tap**: Two sequential taps
 */
class GestureAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GestureAccessService"

        /** Duration for scroll gestures in milliseconds. */
        private const val SCROLL_DURATION_MS = 300L

        /** Duration for swipe gestures in milliseconds. */
        private const val SWIPE_DURATION_MS = 200L

        /** Duration for a tap gesture in milliseconds. */
        private const val TAP_DURATION_MS = 50L

        /** Delay between taps in a double-tap (ms). */
        private const val DOUBLE_TAP_DELAY_MS = 100L

        /**
         * Singleton reference so other components can send actions to this service.
         * This is the standard pattern for communicating with Accessibility Services.
         */
        var instance: GestureAccessibilityService? = null
            private set
    }

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateScreenDimensions()
        Log.d(TAG, "Accessibility Service connected (${screenWidth}x${screenHeight})")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to process accessibility events — we only dispatch gestures.
        // This method is required by the AccessibilityService contract.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility Service destroyed")
    }

    /** Get current screen dimensions for gesture coordinate calculations. */
    private fun updateScreenDimensions() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    /**
     * Execute a device action by dispatching the appropriate system gesture.
     *
     * @param action The action to perform
     */
    fun executeAction(action: DeviceAction) {
        when (action) {
            is DeviceAction.ScrollUp -> performScroll(isUp = true, amount = action.amount)
            is DeviceAction.ScrollDown -> performScroll(isUp = false, amount = action.amount)
            is DeviceAction.SwipeLeft -> performSwipe(isLeftSwipe = true)
            is DeviceAction.SwipeRight -> performSwipe(isLeftSwipe = false)
            is DeviceAction.TapCenter -> performTap()
            is DeviceAction.DoubleTap -> performDoubleTap()
            is DeviceAction.LaunchApp -> launchApp(action.packageName)
            is DeviceAction.NoAction -> { /* Nothing to do */ }
        }
    }

    /**
     * Perform a vertical scroll gesture.
     *
     * A scroll is a vertical swipe on the screen:
     * - Scroll UP: finger moves from bottom to top (content moves up)
     * - Scroll DOWN: finger moves from top to bottom (content moves down)
     *
     * @param isUp   true for scroll up, false for scroll down
     * @param amount Scroll distance in pixels
     */
    private fun performScroll(isUp: Boolean, amount: Int) {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val scrollPixels = amount.coerceAtMost(screenHeight / 2)

        val startY: Float
        val endY: Float

        if (isUp) {
            // Scroll up = swipe upward (finger goes from lower to higher)
            startY = centerY + scrollPixels / 2f
            endY = centerY - scrollPixels / 2f
        } else {
            // Scroll down = swipe downward (finger goes from higher to lower)
            startY = centerY - scrollPixels / 2f
            endY = centerY + scrollPixels / 2f
        }

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        dispatchGestureFromPath(path, SCROLL_DURATION_MS)
        Log.d(TAG, "Scroll ${if (isUp) "UP" else "DOWN"} ($amount px)")
    }

    /**
     * Perform a horizontal swipe gesture.
     *
     * - Swipe LEFT: finger moves from right to left (next content)
     * - Swipe RIGHT: finger moves from left to right (previous content)
     */
    private fun performSwipe(isLeftSwipe: Boolean) {
        val centerY = screenHeight / 2f
        val margin = screenWidth * 0.1f  // 10% margin from edges

        val startX: Float
        val endX: Float

        if (isLeftSwipe) {
            startX = screenWidth - margin
            endX = margin
        } else {
            startX = margin
            endX = screenWidth - margin
        }

        val path = Path().apply {
            moveTo(startX, centerY)
            lineTo(endX, centerY)
        }

        dispatchGestureFromPath(path, SWIPE_DURATION_MS)
        Log.d(TAG, "Swipe ${if (isLeftSwipe) "LEFT" else "RIGHT"}")
    }

    /**
     * Perform a single tap at the center of the screen.
     * Used for play/pause in video apps.
     */
    private fun performTap() {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        val path = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX + 1f, centerY + 1f)  // Tiny movement for a tap
        }

        dispatchGestureFromPath(path, TAP_DURATION_MS)
        Log.d(TAG, "Tap at center ($centerX, $centerY)")
    }

    /**
     * Perform a double-tap at the center of the screen.
     * Used for liking content (Instagram, YouTube).
     *
     * Dispatches two sequential taps with a short delay between them.
     */
    private fun performDoubleTap() {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        // First tap
        val path1 = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX + 1f, centerY + 1f)
        }

        // Second tap (starts after first tap + delay)
        val path2 = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX + 1f, centerY + 1f)
        }

        val stroke1 = GestureDescription.StrokeDescription(
            path1, 0L, TAP_DURATION_MS
        )
        val stroke2 = GestureDescription.StrokeDescription(
            path2, TAP_DURATION_MS + DOUBLE_TAP_DELAY_MS, TAP_DURATION_MS
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Double-tap completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Double-tap cancelled")
            }
        }, null)
    }

    /**
     * Launch an application by its package name.
     */
    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.d(TAG, "Launched app: $packageName")
            } else {
                Log.w(TAG, "App not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app: $packageName", e)
        }
    }

    /**
     * Helper to dispatch a single-stroke gesture from a Path.
     */
    private fun dispatchGestureFromPath(path: Path, durationMs: Long) {
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.v(TAG, "Gesture dispatched successfully")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture dispatch cancelled")
            }
        }, null)
    }
}

package com.handsfree.control.data.model

/**
 * Represents system-level actions that can be performed
 * via the Accessibility Service.
 */
sealed class DeviceAction {
    /** Scroll the current view upward by [amount] pixels. */
    data class ScrollUp(val amount: Int = 500) : DeviceAction()

    /** Scroll the current view downward by [amount] pixels. */
    data class ScrollDown(val amount: Int = 500) : DeviceAction()

    /** Perform a swipe from right to left (next content). */
    data object SwipeLeft : DeviceAction()

    /** Perform a swipe from left to right (previous content). */
    data object SwipeRight : DeviceAction()

    /** Tap the center of the screen (play/pause). */
    data object TapCenter : DeviceAction()

    /** Double-tap to like content. */
    data object DoubleTap : DeviceAction()

    /** Launch an application by its package name. */
    data class LaunchApp(val packageName: String) : DeviceAction()

    /** No action to perform. */
    data object NoAction : DeviceAction()
}

package com.handsfree.control.mapping

import com.handsfree.control.data.model.DeviceAction
import com.handsfree.control.data.model.GestureSettings
import com.handsfree.control.data.model.HandGesture

/**
 * GestureActionMapper converts confirmed hand gestures into device actions.
 *
 * ## Mapping Table
 *
 * | Gesture            | Action                          |
 * |--------------------|---------------------------------|
 * | INDEX_FINGER_UP    | ScrollUp (configurable amount)  |
 * | INDEX_FINGER_DOWN  | ScrollDown (configurable amount)|
 * | SWIPE_RIGHT        | SwipeLeft (next content)        |
 * | SWIPE_LEFT         | SwipeRight (previous content)   |
 * | FIST               | TapCenter (play/pause)          |
 * | THUMBS_UP          | DoubleTap (like content)        |
 * | TWO_FINGERS        | LaunchApp (YouTube)             |
 * | NONE               | NoAction                        |
 *
 * Note: SWIPE_RIGHT gesture maps to SwipeLeft *action* because a right-hand
 * swipe (moving hand to the right) corresponds to swiping content to the left
 * (showing next content), which matches the UX of apps like Instagram Reels
 * and YouTube Shorts.
 */
class GestureActionMapper(
    private var settings: GestureSettings = GestureSettings()
) {
    companion object {
        /** YouTube package name for the LaunchApp action. */
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }

    /**
     * Map a confirmed gesture to its corresponding device action.
     *
     * @param gesture The confirmed gesture from GestureProcessor
     * @return The device action to execute
     */
    fun mapGestureToAction(gesture: HandGesture): DeviceAction {
        // Check if gesture is enabled
        if (settings.enabledGestures[gesture] == false) {
            return DeviceAction.NoAction
        }

        return when (gesture) {
            HandGesture.INDEX_FINGER_UP -> DeviceAction.ScrollUp(settings.scrollAmount)
            HandGesture.INDEX_FINGER_DOWN -> DeviceAction.ScrollDown(settings.scrollAmount)
            HandGesture.SWIPE_RIGHT -> DeviceAction.SwipeLeft   // Next content
            HandGesture.SWIPE_LEFT -> DeviceAction.SwipeRight   // Previous content
            HandGesture.FIST -> DeviceAction.TapCenter          // Play/Pause
            HandGesture.THUMBS_UP -> DeviceAction.DoubleTap     // Like
            HandGesture.TWO_FINGERS -> DeviceAction.LaunchApp(YOUTUBE_PACKAGE)
            HandGesture.NONE -> DeviceAction.NoAction
        }
    }

    /** Update settings (e.g., when user changes scroll amount). */
    fun updateSettings(newSettings: GestureSettings) {
        settings = newSettings
    }
}

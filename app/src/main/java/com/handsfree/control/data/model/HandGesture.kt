package com.handsfree.control.data.model

/**
 * Represents all recognized hand gestures.
 *
 * Each gesture maps to a specific device action. The gesture names
 * describe the hand pose or motion that triggers them.
 */
enum class HandGesture(val displayName: String, val description: String) {
    INDEX_FINGER_UP(
        displayName = "Index Finger Up",
        description = "Point index finger upward to scroll up"
    ),
    INDEX_FINGER_DOWN(
        displayName = "Index Finger Down",
        description = "Point index finger downward to scroll down"
    ),
    SWIPE_RIGHT(
        displayName = "Swipe Right",
        description = "Open hand swipe right for next content"
    ),
    SWIPE_LEFT(
        displayName = "Swipe Left",
        description = "Open hand swipe left for previous content"
    ),
    FIST(
        displayName = "Fist",
        description = "Make a fist to pause or play video"
    ),
    THUMBS_UP(
        displayName = "Thumbs Up",
        description = "Thumbs up to like content"
    ),
    TWO_FINGERS(
        displayName = "Two Fingers (Peace)",
        description = "Show two fingers to open YouTube"
    ),
    NONE(
        displayName = "No Gesture",
        description = "No gesture detected"
    );
}

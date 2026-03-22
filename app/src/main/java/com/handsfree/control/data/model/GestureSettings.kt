package com.handsfree.control.data.model

/**
 * User-configurable gesture detection settings.
 */
data class GestureSettings(
    /** Whether gesture detection is active. */
    val isEnabled: Boolean = true,

    /** Sensitivity from 0.0 (least sensitive) to 1.0 (most sensitive). */
    val sensitivity: Float = 0.7f,

    /** Minimum confidence threshold for accepting a gesture (0.0 to 1.0). */
    val confidenceThreshold: Float = 0.6f,

    /** Cooldown in milliseconds between consecutive gesture actions. */
    val gestureCooldownMs: Long = 800L,

    /** Whether to show the gesture overlay on camera preview. */
    val showOverlay: Boolean = true,

    /** Whether to show the camera preview floating window. */
    val showPreview: Boolean = true,

    /** Scroll amount in pixels for scroll gestures. */
    val scrollAmount: Int = 500,

    /** Per-gesture enable/disable map. */
    val enabledGestures: Map<HandGesture, Boolean> = HandGesture.entries
        .filter { it != HandGesture.NONE }
        .associateWith { true }
)

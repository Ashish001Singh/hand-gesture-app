package com.handsfree.control.gesture

import android.util.Log
import com.handsfree.control.data.model.GestureSettings
import com.handsfree.control.data.model.HandGesture
import com.handsfree.control.data.model.HandLandmarks

/**
 * GestureProcessor combines static gesture recognition and swipe detection,
 * applies debouncing/cooldown, and emits final gesture events.
 *
 * ## Processing Pipeline
 * 1. Receive hand landmarks from [HandDetector]
 * 2. Run static gesture recognition ([GestureRecognizer])
 * 3. Run swipe detection ([SwipeDetector])
 * 4. Apply cooldown timer to prevent rapid-fire gestures
 * 5. Apply stability filter (gesture must persist for N frames)
 * 6. Emit the final confirmed gesture via [GestureCallback]
 *
 * ## Debouncing Strategy
 * A gesture must be detected for [stabilityFrames] consecutive frames before
 * it is confirmed. This prevents flickering between gestures and filters
 * out momentary misclassifications. Combined with the cooldown timer, this
 * ensures smooth, intentional gesture control.
 */
class GestureProcessor(
    private val callback: GestureCallback,
    private var settings: GestureSettings = GestureSettings()
) {
    companion object {
        private const val TAG = "GestureProcessor"
        /** Number of consecutive frames a gesture must persist before confirming. */
        private const val STABILITY_FRAMES = 3
    }

    private val gestureRecognizer = GestureRecognizer(settings.sensitivity)
    private val swipeDetector = SwipeDetector()

    // Debouncing state
    private var lastConfirmedGesture: HandGesture = HandGesture.NONE
    private var candidateGesture: HandGesture = HandGesture.NONE
    private var candidateFrameCount: Int = 0
    private var lastGestureTimestamp: Long = 0L

    /**
     * Process detected hand landmarks and emit gesture events.
     *
     * @param hands List of detected hands (typically 0 or 1)
     */
    fun processHands(hands: List<HandLandmarks>) {
        if (!settings.isEnabled) return

        if (hands.isEmpty()) {
            // No hand detected — reset state
            resetState()
            callback.onGestureDetected(HandGesture.NONE, 0f)
            return
        }

        val hand = hands.first()

        // Step 1: Try static gesture recognition
        var detectedGesture = gestureRecognizer.recognize(hand)

        // Step 2: If no static gesture, check for swipe
        if (detectedGesture == HandGesture.NONE) {
            detectedGesture = swipeDetector.update(hand)
        }

        // Step 3: Check if gesture is enabled in settings
        if (detectedGesture != HandGesture.NONE &&
            settings.enabledGestures[detectedGesture] == false
        ) {
            detectedGesture = HandGesture.NONE
        }

        // Step 4: Apply stability filter (debouncing)
        if (detectedGesture == candidateGesture) {
            candidateFrameCount++
        } else {
            candidateGesture = detectedGesture
            candidateFrameCount = 1
        }

        // Step 5: Confirm gesture if stable for enough frames
        if (candidateFrameCount >= STABILITY_FRAMES && candidateGesture != HandGesture.NONE) {
            val now = System.currentTimeMillis()
            val cooldownElapsed = now - lastGestureTimestamp >= settings.gestureCooldownMs

            if (cooldownElapsed && candidateGesture != lastConfirmedGesture) {
                // New gesture confirmed!
                lastConfirmedGesture = candidateGesture
                lastGestureTimestamp = now

                val confidence = candidateFrameCount.toFloat() / (STABILITY_FRAMES + 2)
                val clampedConfidence = confidence.coerceAtMost(1.0f)

                Log.d(TAG, "Gesture confirmed: ${candidateGesture.displayName} " +
                        "(confidence: ${"%.2f".format(clampedConfidence)})")

                callback.onGestureConfirmed(candidateGesture, clampedConfidence)
            }
        }

        // Always report the current (possibly unconfirmed) gesture for UI feedback
        val currentConfidence = if (candidateGesture != HandGesture.NONE) {
            (candidateFrameCount.toFloat() / STABILITY_FRAMES).coerceAtMost(1.0f)
        } else {
            0f
        }
        callback.onGestureDetected(candidateGesture, currentConfidence)
    }

    /** Reset the debouncing state when no hand is visible. */
    private fun resetState() {
        candidateGesture = HandGesture.NONE
        candidateFrameCount = 0
        lastConfirmedGesture = HandGesture.NONE
        swipeDetector.reset()
    }

    /** Update settings (e.g., when user changes sensitivity). */
    fun updateSettings(newSettings: GestureSettings) {
        settings = newSettings
    }
}

/**
 * Callback interface for gesture events.
 */
interface GestureCallback {
    /**
     * Called every frame with the currently detected gesture (may not be confirmed yet).
     * Useful for UI feedback (showing what gesture is being recognized).
     */
    fun onGestureDetected(gesture: HandGesture, confidence: Float)

    /**
     * Called when a gesture has been confirmed (stable for enough frames and cooldown elapsed).
     * This triggers the actual device action.
     */
    fun onGestureConfirmed(gesture: HandGesture, confidence: Float)
}

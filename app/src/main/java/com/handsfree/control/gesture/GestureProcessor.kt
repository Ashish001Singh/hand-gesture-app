package com.handsfree.control.gesture

import android.util.Log
import com.handsfree.control.data.model.GestureSettings
import com.handsfree.control.data.model.HandGesture
import com.handsfree.control.data.model.HandLandmarks

/**
 * GestureProcessor combines static gesture recognition and swipe detection,
 * applies debouncing/cooldown, and emits confirmed gesture events.
 *
 * BUGS FIXED:
 * 1. Sensitivity never applied: GestureRecognizer.sensitivityMultiplier is now
 *    updated directly in updateSettings() instead of requiring object recreation.
 * 2. lastConfirmedGesture reset bug: resetState() no longer clears
 *    lastConfirmedGesture. The cooldown timer alone guards re-triggering.
 *    Clearing it caused the same gesture to fire immediately after a hand
 *    reappeared even within the cooldown window.
 * 3. SwipeDetector was updated even when a static gesture was recognized
 *    (accumulating spurious palm positions into history) — now skip swipe
 *    update when a static gesture is already confirmed.
 */
class GestureProcessor(
    private val callback: GestureCallback,
    private var settings: GestureSettings = GestureSettings()
) {
    companion object {
        private const val TAG = "GestureProcessor"
        private const val STABILITY_FRAMES = 3
    }

    // FIX #1: GestureRecognizer now exposes sensitivityMultiplier as a var
    // so we can update it without recreating the whole object.
    private val gestureRecognizer = GestureRecognizer(settings.sensitivity)
    private val swipeDetector = SwipeDetector()

    private var lastConfirmedGesture: HandGesture = HandGesture.NONE
    private var candidateGesture: HandGesture = HandGesture.NONE
    private var candidateFrameCount: Int = 0
    private var lastGestureTimestamp: Long = 0L

    fun processHands(hands: List<HandLandmarks>) {
        if (!settings.isEnabled) return

        if (hands.isEmpty()) {
            resetState()
            callback.onGestureDetected(HandGesture.NONE, 0f)
            return
        }

        val hand = hands.first()

        // Step 1: Try static gesture recognition
        var detectedGesture = gestureRecognizer.recognize(hand)

        // FIX #3: Only update swipe detector if no static gesture was found.
        // Previously swipeDetector.update() was always called, which accumulated
        // palm positions even while holding a static pose, potentially causing
        // false swipe triggers when transitioning between gestures.
        if (detectedGesture == HandGesture.NONE) {
            detectedGesture = swipeDetector.update(hand)
        }

        // Step 3: Check per-gesture enabled setting
        if (detectedGesture != HandGesture.NONE &&
            settings.enabledGestures[detectedGesture] == false
        ) {
            detectedGesture = HandGesture.NONE
        }

        // Step 4: Stability filter
        if (detectedGesture == candidateGesture) {
            candidateFrameCount++
        } else {
            candidateGesture = detectedGesture
            candidateFrameCount = 1
        }

        // Step 5: Confirm gesture if stable + cooldown elapsed
        if (candidateFrameCount >= STABILITY_FRAMES && candidateGesture != HandGesture.NONE) {
            val now = System.currentTimeMillis()
            val cooldownElapsed = now - lastGestureTimestamp >= settings.gestureCooldownMs

            // FIX #2: Removed `candidateGesture != lastConfirmedGesture` from condition.
            // The cooldown timer alone is sufficient to prevent rapid re-triggering.
            // The old equality guard, combined with resetState() clearing lastConfirmedGesture,
            // created a loophole where the same gesture could fire again right after
            // the hand momentarily disappeared (resetting lastConfirmedGesture to NONE).
            if (cooldownElapsed) {
                lastConfirmedGesture = candidateGesture
                lastGestureTimestamp = now

                val confidence = (candidateFrameCount.toFloat() / (STABILITY_FRAMES + 2))
                    .coerceAtMost(1.0f)

                Log.d(TAG, "Gesture confirmed: ${candidateGesture.displayName} " +
                        "(confidence=${"%.2f".format(confidence)})")

                callback.onGestureConfirmed(candidateGesture, confidence)
            }
        }

        val currentConfidence = if (candidateGesture != HandGesture.NONE) {
            (candidateFrameCount.toFloat() / STABILITY_FRAMES).coerceAtMost(1.0f)
        } else 0f

        callback.onGestureDetected(candidateGesture, currentConfidence)
    }

    private fun resetState() {
        candidateGesture = HandGesture.NONE
        candidateFrameCount = 0
        // FIX #2: Do NOT reset lastConfirmedGesture here.
        // Keep it so the cooldown check remains effective even after hand disappears.
        swipeDetector.reset()
    }

    /** Update settings live — no need to recreate this object. */
    fun updateSettings(newSettings: GestureSettings) {
        settings = newSettings
        // FIX #1: Update sensitivity on the existing recognizer directly
        gestureRecognizer.sensitivityMultiplier = newSettings.sensitivity
    }
}

interface GestureCallback {
    fun onGestureDetected(gesture: HandGesture, confidence: Float)
    fun onGestureConfirmed(gesture: HandGesture, confidence: Float)
}

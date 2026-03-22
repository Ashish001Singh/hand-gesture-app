package com.handsfree.control.detection

import com.handsfree.control.data.model.HandLandmarks

/**
 * Callback interface for receiving hand detection results.
 */
fun interface HandDetectionListener {
    /**
     * Called when hands are detected (or not) in a frame.
     *
     * @param hands List of detected hands. Empty list means no hands detected.
     *              Typically contains 0 or 1 entries (MAX_HANDS = 1).
     */
    fun onHandsDetected(hands: List<HandLandmarks>)
}

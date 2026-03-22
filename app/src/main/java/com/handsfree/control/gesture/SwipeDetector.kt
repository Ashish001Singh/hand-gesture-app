package com.handsfree.control.gesture

import com.handsfree.control.data.model.HandGesture
import com.handsfree.control.data.model.HandLandmarks

/**
 * SwipeDetector tracks palm position across consecutive frames to detect
 * horizontal swipe gestures.
 *
 * ## How Swipe Detection Works
 *
 * Unlike static poses (fist, thumbs up), swipes are dynamic gestures that
 * require tracking movement over time. The approach:
 *
 * 1. Track the palm center (average of wrist, index MCP, middle MCP, ring MCP, pinky MCP).
 * 2. When a new frame arrives, compare the current palm center X position
 *    against the previous frame's position.
 * 3. If the total horizontal displacement over a short window exceeds the
 *    [swipeThreshold], classify it as a swipe in that direction.
 * 4. A minimum number of frames ([minSwipeFrames]) must show consistent
 *    movement in the same direction to avoid false positives.
 *
 * ## Why Palm Center?
 * Using the palm center instead of a single landmark (like index tip) provides
 * more stable tracking — individual fingertips jitter more than the palm.
 */
class SwipeDetector(
    /** Minimum horizontal displacement (normalized) to trigger a swipe. */
    private val swipeThreshold: Float = 0.15f,
    /** Minimum consecutive frames moving in the same direction. */
    private val minSwipeFrames: Int = 3,
    /** Maximum time window (ms) for a swipe gesture. */
    private val swipeTimeWindowMs: Long = 500L
) {
    /** History of palm center X positions with timestamps. */
    private val positionHistory = mutableListOf<TimedPosition>()

    private data class TimedPosition(val x: Float, val y: Float, val timestampMs: Long)

    /**
     * Update the detector with a new frame and check for swipe gestures.
     *
     * @param landmarks Current hand landmarks
     * @return SWIPE_LEFT, SWIPE_RIGHT, or NONE
     */
    fun update(landmarks: HandLandmarks): HandGesture {
        val palmCenter = calculatePalmCenter(landmarks)
        val now = System.currentTimeMillis()

        // Add current position to history
        positionHistory.add(TimedPosition(palmCenter.first, palmCenter.second, now))

        // Remove entries older than the time window
        positionHistory.removeAll { now - it.timestampMs > swipeTimeWindowMs }

        // Need enough frames to detect a swipe
        if (positionHistory.size < minSwipeFrames) {
            return HandGesture.NONE
        }

        // Calculate total horizontal displacement
        val oldest = positionHistory.first()
        val newest = positionHistory.last()
        val deltaX = newest.x - oldest.x
        val timeDelta = newest.timestampMs - oldest.timestampMs

        // Check if the displacement exceeds threshold within the time window
        if (timeDelta in 1..swipeTimeWindowMs && kotlin.math.abs(deltaX) > swipeThreshold) {
            // Verify consistent direction: check that most frames moved the same way
            var consistentFrames = 0
            for (i in 1 until positionHistory.size) {
                val frameDelta = positionHistory[i].x - positionHistory[i - 1].x
                if ((deltaX > 0 && frameDelta > 0) || (deltaX < 0 && frameDelta < 0)) {
                    consistentFrames++
                }
            }

            val consistencyRatio = consistentFrames.toFloat() / (positionHistory.size - 1)
            if (consistencyRatio > 0.6f) {
                // Clear history after detecting a swipe to prevent repeated triggers
                positionHistory.clear()

                return if (deltaX > 0) {
                    HandGesture.SWIPE_RIGHT
                } else {
                    HandGesture.SWIPE_LEFT
                }
            }
        }

        return HandGesture.NONE
    }

    /**
     * Calculate the palm center as the average of key palm landmarks.
     *
     * Uses wrist, index MCP, middle MCP, ring MCP, and pinky MCP —
     * these five points form the base of the hand and provide stable tracking.
     *
     * @return Pair of (x, y) normalized coordinates
     */
    private fun calculatePalmCenter(hand: HandLandmarks): Pair<Float, Float> {
        val palmLandmarks = listOf(
            hand.wrist,
            hand.indexMcp,
            hand.middleMcp,
            hand.ringMcp,
            hand.pinkyMcp
        )
        val avgX = palmLandmarks.map { it.x }.average().toFloat()
        val avgY = palmLandmarks.map { it.y }.average().toFloat()
        return Pair(avgX, avgY)
    }

    /** Reset the swipe detector state. */
    fun reset() {
        positionHistory.clear()
    }
}

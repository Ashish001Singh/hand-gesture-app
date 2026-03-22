package com.handsfree.control.gesture

import com.handsfree.control.data.model.HandGesture
import com.handsfree.control.data.model.HandLandmarks
import kotlin.math.abs

/**
 * SwipeDetector tracks palm position to detect horizontal swipe gestures.
 *
 * BUGS FIXED:
 * 1. positionHistory was a mutableListOf<> — removeAll {} iterates the entire
 *    list O(n) every frame at 30fps. Replaced with ArrayDeque which allows
 *    O(1) removal from the front (removeFirst / removeWhile from the front).
 */
class SwipeDetector(
    private val swipeThreshold: Float = 0.15f,
    private val minSwipeFrames: Int = 3,
    private val swipeTimeWindowMs: Long = 500L
) {
    private data class TimedPosition(val x: Float, val y: Float, val timestampMs: Long)

    // FIX #10: ArrayDeque allows O(1) front removal vs O(n) for MutableList.removeAll
    private val positionHistory = ArrayDeque<TimedPosition>()

    fun update(landmarks: HandLandmarks): HandGesture {
        val palmCenter = calculatePalmCenter(landmarks)
        val now = System.currentTimeMillis()

        positionHistory.addLast(TimedPosition(palmCenter.first, palmCenter.second, now))

        // FIX: Remove from front (oldest entries) — O(1) per removal vs O(n) removeAll
        while (positionHistory.isNotEmpty() &&
            now - positionHistory.first().timestampMs > swipeTimeWindowMs
        ) {
            positionHistory.removeFirst()
        }

        if (positionHistory.size < minSwipeFrames) {
            return HandGesture.NONE
        }

        val oldest = positionHistory.first()
        val newest = positionHistory.last()
        val deltaX = newest.x - oldest.x
        val timeDelta = newest.timestampMs - oldest.timestampMs

        if (timeDelta in 1..swipeTimeWindowMs && abs(deltaX) > swipeThreshold) {
            var consistentFrames = 0
            for (i in 1 until positionHistory.size) {
                val frameDelta = positionHistory[i].x - positionHistory[i - 1].x
                if ((deltaX > 0 && frameDelta > 0) || (deltaX < 0 && frameDelta < 0)) {
                    consistentFrames++
                }
            }

            val consistencyRatio = consistentFrames.toFloat() / (positionHistory.size - 1)
            if (consistencyRatio > 0.6f) {
                positionHistory.clear()
                return if (deltaX > 0) HandGesture.SWIPE_RIGHT else HandGesture.SWIPE_LEFT
            }
        }

        return HandGesture.NONE
    }

    private fun calculatePalmCenter(hand: HandLandmarks): Pair<Float, Float> {
        val palmLandmarks = listOf(
            hand.wrist, hand.indexMcp, hand.middleMcp, hand.ringMcp, hand.pinkyMcp
        )
        val avgX = palmLandmarks.map { it.x }.average().toFloat()
        val avgY = palmLandmarks.map { it.y }.average().toFloat()
        return Pair(avgX, avgY)
    }

    fun reset() {
        positionHistory.clear()
    }
}

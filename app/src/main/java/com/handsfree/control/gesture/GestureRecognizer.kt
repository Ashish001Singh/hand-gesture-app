package com.handsfree.control.gesture

import com.handsfree.control.data.model.HandGesture
import com.handsfree.control.data.model.HandLandmarks
import kotlin.math.sqrt

/**
 * GestureRecognizer interprets MediaPipe hand landmarks to classify gestures.
 *
 * BUGS FIXED:
 * 1. INDEX_FINGER_DOWN: Previously used `fingers.indexExtended` (tip.y < pip.y)
 *    which is false when pointing down. Now uses a dedicated `isFingerPointingDown()`
 *    check (tip.y > pip.y + threshold) independent of the "extended upward" definition.
 * 2. Sensitivity not applied: GestureRecognizer was created once with initial sensitivity
 *    and never updated. `sensitivityMultiplier` is now a var that can be updated live.
 * 3. Unused imports removed (atan2, abs were imported but never used).
 * 4. isTwoFingers now also requires thumb NOT extended (thumb-up + peace sign confusion).
 */
class GestureRecognizer(
    // FIX #2 (Bug #6 in master list): var instead of val so sensitivity can be
    // updated live when the user changes it in Settings, without recreating the object.
    var sensitivityMultiplier: Float = 1.0f
) {

    fun recognize(landmarks: HandLandmarks): HandGesture {
        val fingers = getFingerStates(landmarks)

        return when {
            isThumbsUp(landmarks, fingers) -> HandGesture.THUMBS_UP
            isFist(fingers) -> HandGesture.FIST
            isTwoFingers(fingers) -> HandGesture.TWO_FINGERS
            isIndexUp(landmarks, fingers) -> HandGesture.INDEX_FINGER_UP
            isIndexDown(landmarks, fingers) -> HandGesture.INDEX_FINGER_DOWN  // FIX #1
            else -> HandGesture.NONE
        }
    }

    data class FingerStates(
        val thumbExtended: Boolean,
        /** True when finger tip is ABOVE its PIP joint (pointing upward). */
        val indexExtended: Boolean,
        /** True when finger tip is BELOW its PIP joint (pointing downward). */
        val indexPointingDown: Boolean,
        val middleExtended: Boolean,
        val ringExtended: Boolean,
        val pinkyExtended: Boolean
    ) {
        val allCurled: Boolean
            get() = !thumbExtended && !indexExtended && !indexPointingDown &&
                    !middleExtended && !ringExtended && !pinkyExtended
    }

    /**
     * Determine finger extension states.
     *
     * KEY FIX: We now track TWO states for the index finger:
     * - [FingerStates.indexExtended]:     tip.y < pip.y  (pointing UP, image coords)
     * - [FingerStates.indexPointingDown]: tip.y > pip.y  (pointing DOWN, hand inverted)
     *
     * Previously, [isIndexDown] required [indexExtended] = true which is impossible
     * when pointing down. This bug meant scroll-down NEVER worked.
     */
    private fun getFingerStates(hand: HandLandmarks): FingerStates {
        // Clamp to avoid negative thresholds at max sensitivity
        val threshold = (0.04f * (1.0f - sensitivityMultiplier * 0.3f)).coerceAtLeast(0.01f)

        // Thumb: extended if tip is farther from index MCP than IP joint
        val thumbExtended = run {
            val tipToMcp = distance2D(hand.thumbTip, hand.indexMcp)
            val ipToMcp = distance2D(hand.thumbIp, hand.indexMcp)
            tipToMcp > ipToMcp + threshold
        }

        // Index: detect both upward and downward extension separately
        val indexExtended = hand.indexTip.y < hand.indexPip.y - threshold       // tip above pip
        val indexPointingDown = hand.indexTip.y > hand.indexPip.y + threshold   // tip below pip (FIX)

        val middleExtended = hand.middleTip.y < hand.middlePip.y - threshold
        val ringExtended = hand.ringTip.y < hand.ringPip.y - threshold
        val pinkyExtended = hand.pinkyTip.y < hand.pinkyPip.y - threshold

        return FingerStates(
            thumbExtended = thumbExtended,
            indexExtended = indexExtended,
            indexPointingDown = indexPointingDown,
            middleExtended = middleExtended,
            ringExtended = ringExtended,
            pinkyExtended = pinkyExtended
        )
    }

    /**
     * INDEX_FINGER_UP: Only index finger extended upward, others curled.
     */
    private fun isIndexUp(hand: HandLandmarks, fingers: FingerStates): Boolean {
        return fingers.indexExtended &&
                !fingers.middleExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended &&
                hand.indexTip.y < hand.wrist.y
    }

    /**
     * INDEX_FINGER_DOWN: Only index finger pointing downward, others curled.
     *
     * FIX: Uses [FingerStates.indexPointingDown] (tip.y > pip.y + threshold),
     * NOT [FingerStates.indexExtended] (tip.y < pip.y − threshold).
     * The old code used indexExtended which is false when pointing down.
     */
    private fun isIndexDown(hand: HandLandmarks, fingers: FingerStates): Boolean {
        return fingers.indexPointingDown &&
                !fingers.middleExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended &&
                hand.indexTip.y > hand.wrist.y    // tip is below wrist = pointing down
    }

    /**
     * FIST: All fingers curled into the palm.
     */
    private fun isFist(fingers: FingerStates): Boolean {
        return !fingers.indexExtended &&
                !fingers.indexPointingDown &&
                !fingers.middleExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended &&
                !fingers.thumbExtended
    }

    /**
     * THUMBS_UP: Only thumb pointing upward, all other fingers curled.
     */
    private fun isThumbsUp(hand: HandLandmarks, fingers: FingerStates): Boolean {
        return fingers.thumbExtended &&
                !fingers.indexExtended &&
                !fingers.indexPointingDown &&
                !fingers.middleExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended &&
                hand.thumbTip.y < hand.thumbMcp.y
    }

    /**
     * TWO_FINGERS (Peace/Victory): Index and middle extended, others curled.
     *
     * FIX: Added !fingers.thumbExtended to prevent confusion with a "thumbs up
     * + two fingers" pose that would incorrectly match this gesture.
     */
    private fun isTwoFingers(fingers: FingerStates): Boolean {
        return fingers.indexExtended &&
                fingers.middleExtended &&
                !fingers.thumbExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended
    }

    private fun distance2D(
        a: com.handsfree.control.data.model.HandLandmarkPoint,
        b: com.handsfree.control.data.model.HandLandmarkPoint
    ): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

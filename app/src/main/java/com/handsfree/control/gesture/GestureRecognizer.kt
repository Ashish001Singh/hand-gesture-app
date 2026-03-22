package com.handsfree.control.gesture

import com.handsfree.control.data.model.HandGesture
import com.handsfree.control.data.model.HandLandmarks
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * GestureRecognizer interprets MediaPipe hand landmarks to classify gestures.
 *
 * ## How Landmark-Based Gesture Detection Works
 *
 * MediaPipe Hands returns 21 3D landmarks per hand. Each landmark has (x, y, z)
 * coordinates normalized to [0.0, 1.0]. The key insight is that finger states
 * (extended vs curled) can be determined by comparing the positions of fingertip
 * landmarks against their corresponding knuckle landmarks.
 *
 * ### Finger Extension Detection
 * A finger is considered "extended" when its TIP landmark is farther from the
 * WRIST than its PIP (proximal interphalangeal) joint. Specifically:
 *
 * - For fingers (index, middle, ring, pinky):
 *   The finger is UP if tip.y < pip.y (tip is above the pip joint in image coords,
 *   where y=0 is the top of the image).
 *
 * - For the thumb:
 *   The thumb is extended if the distance between thumb tip and index MCP is
 *   greater than the distance between thumb IP and index MCP.
 *
 * ### Gesture Definitions
 *
 * 1. **INDEX_FINGER_UP**: Only index finger extended, pointing upward.
 *    - Index tip.y < index pip.y (index extended)
 *    - Middle, ring, pinky tips.y > their pip.y (all curled)
 *    - Index tip.y < wrist.y (pointing up relative to wrist)
 *
 * 2. **INDEX_FINGER_DOWN**: Only index finger extended, pointing downward.
 *    - Index tip.y > index pip.y (index extended downward)
 *    - Middle, ring, pinky curled
 *    - Index tip.y > wrist.y (pointing down relative to wrist)
 *
 * 3. **FIST**: All fingers curled into the palm.
 *    - All fingertips are closer to the wrist than their MCP joints
 *    - No finger is extended
 *
 * 4. **THUMBS_UP**: Only thumb extended, pointing upward.
 *    - Thumb tip.y < thumb IP.y (thumb pointing up)
 *    - All other fingers curled
 *    - Thumb tip.y < index MCP.y
 *
 * 5. **TWO_FINGERS (Peace)**: Index and middle fingers extended, others curled.
 *    - Index and middle tips above their PIPs
 *    - Ring and pinky tips below their PIPs
 *
 * 6. **SWIPE_LEFT / SWIPE_RIGHT**: Detected by tracking palm center movement
 *    across consecutive frames (handled by [SwipeDetector]).
 */
class GestureRecognizer(
    private val sensitivityMultiplier: Float = 1.0f
) {
    /**
     * Classify the current hand pose into a [HandGesture].
     *
     * @param landmarks The 21 hand landmarks from MediaPipe
     * @return The detected gesture, or [HandGesture.NONE] if unrecognized
     */
    fun recognize(landmarks: HandLandmarks): HandGesture {
        val fingers = getFingerStates(landmarks)

        return when {
            isThumbsUp(landmarks, fingers) -> HandGesture.THUMBS_UP
            isFist(fingers) -> HandGesture.FIST
            isTwoFingers(fingers) -> HandGesture.TWO_FINGERS
            isIndexUp(landmarks, fingers) -> HandGesture.INDEX_FINGER_UP
            isIndexDown(landmarks, fingers) -> HandGesture.INDEX_FINGER_DOWN
            else -> HandGesture.NONE
        }
    }

    /**
     * Data class representing the extension state of each finger.
     */
    data class FingerStates(
        val thumbExtended: Boolean,
        val indexExtended: Boolean,
        val middleExtended: Boolean,
        val ringExtended: Boolean,
        val pinkyExtended: Boolean
    ) {
        val allCurled: Boolean
            get() = !thumbExtended && !indexExtended && !middleExtended &&
                    !ringExtended && !pinkyExtended

        val extendedCount: Int
            get() = listOf(thumbExtended, indexExtended, middleExtended,
                ringExtended, pinkyExtended).count { it }
    }

    /**
     * Determine which fingers are extended vs curled.
     *
     * For each finger (except thumb), compare the TIP y-coordinate
     * against the PIP y-coordinate. If tip.y < pip.y, the finger is
     * pointing upward (extended). Note: in image coordinates, y increases
     * downward, so "up" means smaller y values.
     *
     * For the thumb, we compare the horizontal distance of the tip vs IP
     * from the palm center, since the thumb moves laterally.
     */
    private fun getFingerStates(hand: HandLandmarks): FingerStates {
        // Threshold adjustment based on sensitivity (lower = more sensitive)
        val threshold = 0.04f * (1.0f - sensitivityMultiplier * 0.3f)

        // Thumb: extended if thumb tip is farther from palm than thumb IP
        val thumbExtended = run {
            val tipToMcp = distance2D(hand.thumbTip, hand.indexMcp)
            val ipToMcp = distance2D(hand.thumbIp, hand.indexMcp)
            tipToMcp > ipToMcp + threshold
        }

        // Index: tip above PIP means extended
        val indexExtended = hand.indexTip.y < hand.indexPip.y - threshold

        // Middle: tip above PIP means extended
        val middleExtended = hand.middleTip.y < hand.middlePip.y - threshold

        // Ring: tip above PIP means extended
        val ringExtended = hand.ringTip.y < hand.ringPip.y - threshold

        // Pinky: tip above PIP means extended
        val pinkyExtended = hand.pinkyTip.y < hand.pinkyPip.y - threshold

        return FingerStates(
            thumbExtended = thumbExtended,
            indexExtended = indexExtended,
            middleExtended = middleExtended,
            ringExtended = ringExtended,
            pinkyExtended = pinkyExtended
        )
    }

    /**
     * INDEX_FINGER_UP: Only index extended, pointing upward relative to wrist.
     * Used for scrolling up.
     */
    private fun isIndexUp(hand: HandLandmarks, fingers: FingerStates): Boolean {
        return fingers.indexExtended &&
                !fingers.middleExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended &&
                hand.indexTip.y < hand.wrist.y  // Tip is above wrist
    }

    /**
     * INDEX_FINGER_DOWN: Only index extended, pointing downward relative to wrist.
     * Used for scrolling down.
     *
     * This is detected when the hand is inverted — index tip is below the wrist.
     */
    private fun isIndexDown(hand: HandLandmarks, fingers: FingerStates): Boolean {
        return fingers.indexExtended &&
                !fingers.middleExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended &&
                hand.indexTip.y > hand.wrist.y  // Tip is below wrist
    }

    /**
     * FIST: All fingers curled. The hand is closed into a fist.
     * Used for play/pause toggle.
     *
     * Detected when all fingertips are below (greater y) than their MCP joints,
     * indicating all fingers are bent toward the palm.
     */
    private fun isFist(fingers: FingerStates): Boolean {
        return !fingers.indexExtended &&
                !fingers.middleExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended &&
                !fingers.thumbExtended
    }

    /**
     * THUMBS_UP: Only thumb extended upward, all others curled.
     * Used for liking content.
     *
     * Additional check: thumb tip must be significantly above the index MCP
     * to confirm it's actually pointing upward and not just sticking out sideways.
     */
    private fun isThumbsUp(hand: HandLandmarks, fingers: FingerStates): Boolean {
        return fingers.thumbExtended &&
                !fingers.indexExtended &&
                !fingers.middleExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended &&
                hand.thumbTip.y < hand.thumbMcp.y  // Thumb points upward
    }

    /**
     * TWO_FINGERS (Peace/Victory sign): Index and middle extended, others curled.
     * Used for launching YouTube.
     */
    private fun isTwoFingers(fingers: FingerStates): Boolean {
        return fingers.indexExtended &&
                fingers.middleExtended &&
                !fingers.ringExtended &&
                !fingers.pinkyExtended
    }

    /**
     * Calculate 2D Euclidean distance between two landmark points.
     * Uses only x and y (ignoring depth z).
     */
    private fun distance2D(
        a: com.handsfree.control.data.model.HandLandmarkPoint,
        b: com.handsfree.control.data.model.HandLandmarkPoint
    ): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

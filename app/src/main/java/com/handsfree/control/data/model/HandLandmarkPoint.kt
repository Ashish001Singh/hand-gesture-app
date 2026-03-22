package com.handsfree.control.data.model

/**
 * Represents a single hand landmark point in normalized coordinates (0.0 to 1.0).
 *
 * MediaPipe Hands returns 21 landmarks per detected hand.
 * Each landmark has x, y (normalized to image dimensions) and z (depth).
 *
 * Landmark indices (MediaPipe convention):
 *   0  = WRIST
 *   1  = THUMB_CMC
 *   2  = THUMB_MCP
 *   3  = THUMB_IP
 *   4  = THUMB_TIP
 *   5  = INDEX_FINGER_MCP
 *   6  = INDEX_FINGER_PIP
 *   7  = INDEX_FINGER_DIP
 *   8  = INDEX_FINGER_TIP
 *   9  = MIDDLE_FINGER_MCP
 *   10 = MIDDLE_FINGER_PIP
 *   11 = MIDDLE_FINGER_DIP
 *   12 = MIDDLE_FINGER_TIP
 *   13 = RING_FINGER_MCP
 *   14 = RING_FINGER_PIP
 *   15 = RING_FINGER_DIP
 *   16 = RING_FINGER_TIP
 *   17 = PINKY_MCP
 *   18 = PINKY_PIP
 *   19 = PINKY_DIP
 *   20 = PINKY_TIP
 */
data class HandLandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Contains all 21 landmarks for a single detected hand.
 */
data class HandLandmarks(
    val landmarks: List<HandLandmarkPoint>,
    val handedness: Handedness = Handedness.RIGHT,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Convenience accessors for key landmarks. */
    val wrist get() = landmarks[0]
    val thumbTip get() = landmarks[4]
    val thumbIp get() = landmarks[3]
    val thumbMcp get() = landmarks[2]
    val indexTip get() = landmarks[8]
    val indexDip get() = landmarks[7]
    val indexPip get() = landmarks[6]
    val indexMcp get() = landmarks[5]
    val middleTip get() = landmarks[12]
    val middleDip get() = landmarks[11]
    val middlePip get() = landmarks[10]
    val middleMcp get() = landmarks[9]
    val ringTip get() = landmarks[16]
    val ringDip get() = landmarks[15]
    val ringPip get() = landmarks[14]
    val ringMcp get() = landmarks[13]
    val pinkyTip get() = landmarks[20]
    val pinkyDip get() = landmarks[19]
    val pinkyPip get() = landmarks[18]
    val pinkyMcp get() = landmarks[17]
}

enum class Handedness {
    LEFT, RIGHT
}

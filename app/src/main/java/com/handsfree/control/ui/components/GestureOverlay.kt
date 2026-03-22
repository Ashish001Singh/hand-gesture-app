package com.handsfree.control.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.handsfree.control.data.model.HandLandmarks

/**
 * Overlay that draws hand landmarks and connections on top of the camera preview.
 *
 * This provides real-time visual feedback showing:
 * - Green circles at each of the 21 hand landmark positions
 * - White lines connecting landmarks to show hand skeleton
 *
 * The overlay helps users understand if their hand is being detected
 * correctly and position their hand optimally.
 */
@Composable
fun GestureOverlay(
    landmarks: List<HandLandmarks>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        for (hand in landmarks) {
            val points = hand.landmarks

            // Draw connections (bone lines) between landmarks
            HAND_CONNECTIONS.forEach { (startIdx, endIdx) ->
                if (startIdx < points.size && endIdx < points.size) {
                    val start = points[startIdx]
                    val end = points[endIdx]

                    drawLine(
                        color = Color.White.copy(alpha = 0.7f),
                        start = Offset(start.x * width, start.y * height),
                        end = Offset(end.x * width, end.y * height),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw landmark points
            points.forEachIndexed { index, point ->
                val color = when {
                    index == 0 -> Color.Red           // Wrist
                    index % 4 == 0 -> Color.Yellow    // Fingertips (4,8,12,16,20)
                    else -> Color.Green               // Other joints
                }

                drawCircle(
                    color = color,
                    radius = if (index % 4 == 0) 8f else 5f,
                    center = Offset(point.x * width, point.y * height)
                )
            }
        }
    }
}

/**
 * MediaPipe hand landmark connections.
 * Each pair (a, b) represents a bone connecting landmark a to landmark b.
 *
 * These form the hand skeleton:
 * - Thumb: 0→1→2→3→4
 * - Index: 0→5→6→7→8
 * - Middle: 0→9→10→11→12 (9 also connects to 0 via 5)
 * - Ring: 0→13→14→15→16
 * - Pinky: 0→17→18→19→20
 * - Palm: 0→5, 5→9, 9→13, 13→17, 0→17
 */
private val HAND_CONNECTIONS = listOf(
    // Thumb
    Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
    // Index finger
    Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
    // Middle finger
    Pair(9, 10), Pair(10, 11), Pair(11, 12),
    // Ring finger
    Pair(13, 14), Pair(14, 15), Pair(15, 16),
    // Pinky
    Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
    // Palm connections
    Pair(5, 9), Pair(9, 13), Pair(13, 17)
)

package com.handsfree.control.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handsfree.control.data.model.HandGesture
import com.handsfree.control.ui.theme.*

/**
 * Status bar showing the currently detected gesture and confidence level.
 * Displayed at the bottom of the camera preview.
 */
@Composable
fun GestureStatusBar(
    gesture: HandGesture,
    confidence: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            !isActive -> GestureInactiveColor
            gesture != HandGesture.NONE -> GestureDetectedColor
            else -> GestureActiveColor
        },
        label = "statusColor"
    )

    val confidenceColor by animateColorAsState(
        targetValue = when {
            confidence > 0.8f -> ConfidenceHighColor
            confidence > 0.5f -> ConfidenceMediumColor
            else -> ConfidenceLowColor
        },
        label = "confidenceColor"
    )

    val animatedConfidence by animateFloatAsState(
        targetValue = confidence,
        label = "confidence"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.75f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Gesture name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor)
                )
                Text(
                    text = if (isActive) {
                        gesture.displayName
                    } else {
                        "Detection Paused"
                    },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Confidence bar — use Float overload (compatible with all Material3 versions)
            if (isActive && gesture != HandGesture.NONE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Confidence",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    LinearProgressIndicator(
                        progress = animatedConfidence,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = confidenceColor,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${(animatedConfidence * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            // Action description
            if (isActive && gesture != HandGesture.NONE) {
                Text(
                    text = gesture.description,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

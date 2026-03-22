package com.handsfree.control.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handsfree.control.data.model.HandGesture
import com.handsfree.control.data.model.HandLandmarks
import com.handsfree.control.ui.components.CameraPreview
import com.handsfree.control.ui.components.GestureOverlay
import com.handsfree.control.ui.components.GestureStatusBar
import com.handsfree.control.ui.theme.GestureActiveColor
import com.handsfree.control.ui.theme.GestureInactiveColor

/**
 * Main screen of the HandsFree Control app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentGesture: HandGesture,
    gestureConfidence: Float,
    isDetectionActive: Boolean,
    isServiceConnected: Boolean,
    handLandmarks: List<HandLandmarks>,
    showOverlay: Boolean,
    onToggleDetection: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onPreviewCreated: (PreviewView) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HandsFree Control", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Accessibility Service Warning ──
            if (!isServiceConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accessibility Service Required",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Enable the HandsFree Control service in Accessibility settings to allow gesture control.",
                                fontSize = 12.sp)
                        }
                        TextButton(onClick = onOpenAccessibilitySettings) { Text("Enable") }
                    }
                }
            }

            // ── Camera Preview with Overlay ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                CameraPreview(onPreviewCreated = onPreviewCreated)

                if (showOverlay && handLandmarks.isNotEmpty()) {
                    GestureOverlay(landmarks = handLandmarks)
                }

                GestureStatusBar(
                    gesture = currentGesture,
                    confidence = gestureConfidence,
                    isActive = isDetectionActive,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // ── Control Buttons ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onToggleDetection,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDetectionActive) GestureInactiveColor else GestureActiveColor
                    )
                ) {
                    Icon(
                        imageVector = if (isDetectionActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDetectionActive) "Pause" else "Start",
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) {
                    Icon(
                        imageVector = if (isServiceConnected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isServiceConnected) GestureActiveColor else GestureInactiveColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isServiceConnected) "Connected" else "Disconnected",
                        fontSize = 13.sp
                    )
                }
            }

            GestureGuideCard()
        }
    }
}

@Composable
private fun GestureGuideCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Gesture Guide", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            val gestures = HandGesture.entries.filter { it != HandGesture.NONE }
            gestures.forEach { gesture ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = gesture.displayName, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.4f))
                    Text(text = gesture.description, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.6f))
                }
                if (gesture != gestures.last()) {
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

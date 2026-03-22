package com.handsfree.control.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handsfree.control.data.model.GestureSettings
import com.handsfree.control.data.model.HandGesture

/**
 * Settings screen for gesture customization.
 *
 * Allows users to:
 * - Adjust detection sensitivity
 * - Set confidence threshold
 * - Configure cooldown timing
 * - Toggle individual gestures on/off
 * - Toggle visual overlay
 * - Adjust scroll amount
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: GestureSettings,
    onSettingsChanged: (GestureSettings) -> Unit,
    onBack: () -> Unit
) {
    var localSettings by remember(settings) { mutableStateOf(settings) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Detection Settings ──
            Text("Detection", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            // Sensitivity slider
            SettingsSlider(
                label = "Sensitivity",
                value = localSettings.sensitivity,
                valueRange = 0.1f..1.0f,
                valueLabel = "${(localSettings.sensitivity * 100).toInt()}%",
                onValueChange = {
                    localSettings = localSettings.copy(sensitivity = it)
                    onSettingsChanged(localSettings)
                }
            )

            // Confidence threshold
            SettingsSlider(
                label = "Min Confidence",
                value = localSettings.confidenceThreshold,
                valueRange = 0.3f..0.9f,
                valueLabel = "${(localSettings.confidenceThreshold * 100).toInt()}%",
                onValueChange = {
                    localSettings = localSettings.copy(confidenceThreshold = it)
                    onSettingsChanged(localSettings)
                }
            )

            // Cooldown
            SettingsSlider(
                label = "Cooldown",
                value = localSettings.gestureCooldownMs.toFloat(),
                valueRange = 200f..2000f,
                valueLabel = "${localSettings.gestureCooldownMs}ms",
                onValueChange = {
                    localSettings = localSettings.copy(gestureCooldownMs = it.toLong())
                    onSettingsChanged(localSettings)
                }
            )

            // Scroll amount
            SettingsSlider(
                label = "Scroll Amount",
                value = localSettings.scrollAmount.toFloat(),
                valueRange = 100f..1500f,
                valueLabel = "${localSettings.scrollAmount}px",
                onValueChange = {
                    localSettings = localSettings.copy(scrollAmount = it.toInt())
                    onSettingsChanged(localSettings)
                }
            )

            HorizontalDivider()

            // ── Display Settings ──
            Text("Display", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            SettingsSwitch(
                label = "Show Hand Overlay",
                description = "Draw hand landmarks on camera preview",
                checked = localSettings.showOverlay,
                onCheckedChange = {
                    localSettings = localSettings.copy(showOverlay = it)
                    onSettingsChanged(localSettings)
                }
            )

            SettingsSwitch(
                label = "Show Camera Preview",
                description = "Display floating camera window",
                checked = localSettings.showPreview,
                onCheckedChange = {
                    localSettings = localSettings.copy(showPreview = it)
                    onSettingsChanged(localSettings)
                }
            )

            HorizontalDivider()

            // ── Individual Gesture Toggles ──
            Text("Gestures", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            HandGesture.entries.filter { it != HandGesture.NONE }.forEach { gesture ->
                SettingsSwitch(
                    label = gesture.displayName,
                    description = gesture.description,
                    checked = localSettings.enabledGestures[gesture] ?: true,
                    onCheckedChange = { enabled ->
                        val updatedMap = localSettings.enabledGestures.toMutableMap()
                        updatedMap[gesture] = enabled
                        localSettings = localSettings.copy(enabledGestures = updatedMap)
                        onSettingsChanged(localSettings)
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp)
            Text(valueLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

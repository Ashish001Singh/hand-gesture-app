package com.handsfree.control

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.handsfree.control.camera.CameraManager
import com.handsfree.control.detection.HandDetector
import com.handsfree.control.detection.HandDetectorErrorListener
import com.handsfree.control.ui.screens.MainScreen
import com.handsfree.control.ui.screens.SettingsScreen
import com.handsfree.control.ui.theme.HandsFreeControlTheme
import com.handsfree.control.ui.viewmodel.MainViewModel

/**
 * MainActivity — app entry point and camera/detection lifecycle owner.
 *
 * BUGS FIXED:
 * 1. Camera never restarted if permission was granted from system Settings:
 *    onResume() now checks permission and re-attempts camera start if needed.
 * 2. HandDetector errors (missing model, GPU+CPU failure) now surfaced to user
 *    via Toast instead of silently failing.
 * 3. No front camera feedback: CameraManager.noCameraListener now shows Toast.
 * 4. lifecycleScope import was unused — removed.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var cameraManager: CameraManager
    private var handDetector: HandDetector? = null
    private var previewView: PreviewView? = null

    // FIX #2: Error listener to surface detector failures to the user
    private val detectorErrorListener = HandDetectorErrorListener { message ->
        runOnUiThread {
            Toast.makeText(this, "Detection error: $message", Toast.LENGTH_LONG).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraAndDetection()
        } else {
            Toast.makeText(
                this,
                "Camera permission is required for gesture detection",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIX #3: Pass noCameraListener to surface front-camera absence
        cameraManager = CameraManager(this) {
            Toast.makeText(
                this,
                "No front camera detected on this device",
                Toast.LENGTH_LONG
            ).show()
        }

        setContent {
            HandsFreeControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }

        checkAndRequestCameraPermission()
    }

    @Composable
    private fun AppContent() {
        var showSettings by remember { mutableStateOf(false) }

        val currentGesture by viewModel.currentGesture.collectAsState()
        val gestureConfidence by viewModel.gestureConfidence.collectAsState()
        val isDetectionActive by viewModel.isDetectionActive.collectAsState()
        val isServiceConnected by viewModel.isServiceConnected.collectAsState()
        val handLandmarks by viewModel.handLandmarks.collectAsState()
        val settings by viewModel.settings.collectAsState()

        LaunchedEffect(Unit) {
            while (true) {
                viewModel.refreshServiceStatus()
                kotlinx.coroutines.delay(2000L)
            }
        }

        if (showSettings) {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = { viewModel.updateSettings(it) },
                onBack = { showSettings = false }
            )
        } else {
            MainScreen(
                currentGesture = currentGesture,
                gestureConfidence = gestureConfidence,
                isDetectionActive = isDetectionActive,
                isServiceConnected = isServiceConnected,
                handLandmarks = handLandmarks,
                showOverlay = settings.showOverlay,
                onToggleDetection = { viewModel.toggleDetection() },
                onOpenSettings = { showSettings = true },
                onOpenAccessibilitySettings = { openAccessibilitySettings() },
                onPreviewCreated = { preview ->
                    if (previewView != preview) {
                        previewView = preview
                        startCameraAndDetection()
                    }
                }
            )
        }
    }

    private fun startCameraAndDetection() {
        val preview = previewView ?: return

        if (!hasCameraPermission()) return

        if (handDetector == null) {
            handDetector = HandDetector(
                context = this,
                listener = viewModel.handDetectionListener,
                errorListener = detectorErrorListener  // FIX #2
            )
        }

        cameraManager.startCamera(
            lifecycleOwner = this,
            previewView = preview,
            frameAnalyzer = handDetector!!
        )
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun checkAndRequestCameraPermission() {
        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        // If already granted, camera starts once previewView is ready via onPreviewCreated
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshServiceStatus()

        // FIX #1: Re-attempt camera start when returning from system Settings
        // where the user may have just granted the camera permission.
        if (hasCameraPermission() && previewView != null && handDetector == null) {
            startCameraAndDetection()
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handDetector?.close()
        cameraManager.shutdown()
    }
}

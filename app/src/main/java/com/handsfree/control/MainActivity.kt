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
import androidx.lifecycle.lifecycleScope
import com.handsfree.control.camera.CameraManager
import com.handsfree.control.detection.HandDetector
import com.handsfree.control.ui.screens.MainScreen
import com.handsfree.control.ui.screens.SettingsScreen
import com.handsfree.control.ui.theme.HandsFreeControlTheme
import com.handsfree.control.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * MainActivity is the entry point of the HandsFree Control app.
 *
 * It manages:
 * - Camera permission requests
 * - Camera lifecycle (via CameraManager)
 * - Hand detection initialization (via HandDetector)
 * - Navigation between Main and Settings screens
 *
 * The Activity acts as the "glue" that connects the camera feed
 * to the gesture detection pipeline.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var cameraManager: CameraManager
    private var handDetector: HandDetector? = null
    private var previewView: PreviewView? = null

    // Camera permission launcher
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

        cameraManager = CameraManager(this)

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

        // Check camera permission
        checkAndRequestCameraPermission()
    }

    @Composable
    private fun AppContent() {
        // Navigation state
        var showSettings by remember { mutableStateOf(false) }

        // Collect ViewModel state
        val currentGesture by viewModel.currentGesture.collectAsState()
        val gestureConfidence by viewModel.gestureConfidence.collectAsState()
        val isDetectionActive by viewModel.isDetectionActive.collectAsState()
        val isServiceConnected by viewModel.isServiceConnected.collectAsState()
        val handLandmarks by viewModel.handLandmarks.collectAsState()
        val settings by viewModel.settings.collectAsState()

        // Refresh service status periodically
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

    /**
     * Start the camera and hand detection pipeline.
     *
     * This connects:
     * CameraManager → HandDetector (as FrameAnalyzer) → ViewModel (as HandDetectionListener)
     */
    private fun startCameraAndDetection() {
        val preview = previewView ?: return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Initialize hand detector (implements FrameAnalyzer)
        if (handDetector == null) {
            handDetector = HandDetector(
                context = this,
                listener = viewModel.handDetectionListener
            )
        }

        // Start camera with the hand detector as the frame analyzer
        cameraManager.startCamera(
            lifecycleOwner = this,
            previewView = preview,
            frameAnalyzer = handDetector!!
        )
    }

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted — camera will start when preview is ready
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Open the system Accessibility Settings page.
     * The user needs to manually enable the HandsFree Control service there.
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Refresh accessibility service status when returning from settings
        viewModel.refreshServiceStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        handDetector?.close()
        cameraManager.shutdown()
    }
}

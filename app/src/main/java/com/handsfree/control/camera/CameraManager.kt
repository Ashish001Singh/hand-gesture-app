package com.handsfree.control.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager handles CameraX setup, lifecycle binding, and frame delivery.
 *
 * It configures the front camera for preview and analysis, delivering each
 * frame to a provided [FrameAnalyzer] callback on a background thread.
 *
 * Performance notes:
 * - Uses ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST so the pipeline never
 *   backs up if gesture detection takes longer than one frame interval.
 * - Analysis runs on a dedicated single-thread executor to avoid blocking
 *   the UI thread.
 * - Target resolution of 640x480 balances detection accuracy with speed.
 */
class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
    }

    /** Single-thread executor dedicated to frame analysis. */
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null

    /**
     * Start the camera and bind it to the given lifecycle.
     *
     * @param lifecycleOwner Activity or Fragment lifecycle
     * @param previewView    The CameraX PreviewView to display the feed
     * @param frameAnalyzer  Callback invoked for each camera frame
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        frameAnalyzer: FrameAnalyzer
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView, frameAnalyzer)
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Bind Preview and ImageAnalysis use cases to the front camera.
     */
    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        frameAnalyzer: FrameAnalyzer
    ) {
        val provider = cameraProvider ?: return

        // Unbind any existing use cases before rebinding
        provider.unbindAll()

        // Preview use case — renders the camera feed to the PreviewView
        preview = Preview.Builder()
            .setTargetResolution(android.util.Size(TARGET_WIDTH, TARGET_HEIGHT))
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // ImageAnalysis use case — delivers frames for MediaPipe processing
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(TARGET_WIDTH, TARGET_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    frameAnalyzer.analyze(imageProxy)
                }
            }

        // Select the front camera
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            Log.d(TAG, "Camera bound successfully with front-facing lens")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    /** Release camera resources. */
    fun shutdown() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
        Log.d(TAG, "Camera shut down")
    }
}

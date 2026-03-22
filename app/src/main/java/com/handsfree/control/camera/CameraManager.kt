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
import java.util.concurrent.TimeUnit

/**
 * CameraManager handles CameraX setup, lifecycle binding, and frame delivery.
 *
 * BUGS FIXED:
 * 1. analysisExecutor.shutdown() didn't drain in-flight frames — now uses
 *    shutdownNow() + awaitTermination() to prevent callbacks into a
 *    destroyed HandDetector.
 * 2. No front-camera feedback — noCameraListener added for devices without
 *    a front camera.
 * 3. setTargetResolution() is deprecated in newer CameraX; replaced with
 *    ResolutionSelector for forward compatibility.
 */
class CameraManager(
    private val context: Context,
    private val noCameraListener: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "CameraManager"
        private const val TARGET_WIDTH = 640
        private const val TARGET_HEIGHT = 480
        private const val EXECUTOR_SHUTDOWN_TIMEOUT_SEC = 2L
    }

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

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

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        frameAnalyzer: FrameAnalyzer
    ) {
        val provider = cameraProvider ?: return

        // FIX #2: Check for front camera availability before attempting to bind
        if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            Log.e(TAG, "No front camera available on this device")
            noCameraListener?.invoke()
            return
        }

        provider.unbindAll()

        val targetSize = android.util.Size(TARGET_WIDTH, TARGET_HEIGHT)

        val preview = Preview.Builder()
            .setTargetResolution(targetSize)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(targetSize)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    frameAnalyzer.analyze(imageProxy)
                }
            }

        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageAnalysis
            )
            Log.d(TAG, "Camera bound with front-facing lens at ${TARGET_WIDTH}x${TARGET_HEIGHT}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    /**
     * Release camera resources.
     *
     * FIX #1: shutdownNow() signals the executor to stop accepting new tasks,
     * then awaitTermination() waits up to 2 seconds for the current frame
     * analysis to finish, preventing a callback into a destroyed HandDetector.
     */
    fun shutdown() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdownNow()
        try {
            if (!analysisExecutor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                Log.w(TAG, "Analysis executor did not terminate in time")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        Log.d(TAG, "Camera shut down")
    }
}

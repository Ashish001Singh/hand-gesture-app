package com.handsfree.control.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.handsfree.control.camera.FrameAnalyzer
import com.handsfree.control.data.model.HandLandmarkPoint
import com.handsfree.control.data.model.HandLandmarks
import com.handsfree.control.data.model.Handedness

/**
 * HandDetector wraps MediaPipe's HandLandmarker to detect 21 hand landmarks
 * from each camera frame.
 *
 * ## How it works
 * 1. CameraX delivers each frame as an [ImageProxy] in RGBA_8888 format.
 * 2. The frame is converted to a [Bitmap] and horizontally flipped (front camera is mirrored).
 * 3. The bitmap is wrapped in a MediaPipe [MPImage] and fed to [HandLandmarker.detectForVideo].
 * 4. Results (0 or more detected hands with 21 landmarks each) are converted to our
 *    [HandLandmarks] model and delivered via the [HandDetectionListener] callback.
 *
 * ## Performance
 * - Uses GPU delegate for hardware-accelerated inference when available.
 * - LIVE_STREAM running mode processes frames asynchronously.
 * - Only one hand is detected to minimize compute (configurable).
 * - Uses STRATEGY_KEEP_ONLY_LATEST in the camera so old frames are dropped.
 */
class HandDetector(
    private val context: Context,
    private val listener: HandDetectionListener
) : FrameAnalyzer {

    companion object {
        private const val TAG = "HandDetector"
        private const val MODEL_ASSET = "hand_landmarker.task"
        private const val MAX_HANDS = 1
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
        private const val MIN_PRESENCE_CONFIDENCE = 0.5f
    }

    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    /**
     * Initialize MediaPipe HandLandmarker with optimal settings.
     *
     * The model file (hand_landmarker.task) must be placed in the app's
     * assets folder. It is a bundled TFLite model provided by Google.
     */
    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .setDelegate(Delegate.GPU)  // Use GPU for faster inference
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(MAX_HANDS)
                .setMinHandDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setMinHandPresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d(TAG, "HandLandmarker initialized successfully with GPU delegate")
        } catch (gpuError: Exception) {
            // Fallback to CPU if GPU is unavailable
            Log.w(TAG, "GPU delegate failed, falling back to CPU", gpuError)
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build()

                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(MAX_HANDS)
                    .setMinHandDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                    .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                    .setMinHandPresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                    .build()

                handLandmarker = HandLandmarker.createFromOptions(context, options)
                Log.d(TAG, "HandLandmarker initialized with CPU delegate")
            } catch (cpuError: Exception) {
                Log.e(TAG, "Failed to initialize HandLandmarker", cpuError)
            }
        }
    }

    /**
     * Process a camera frame: convert to Bitmap, run hand detection,
     * and deliver results.
     *
     * Called on the analysis background thread.
     */
    override fun analyze(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        val timestampMs = imageProxy.imageInfo.timestamp / 1_000 // Convert nanos to micros

        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap == null) {
                imageProxy.close()
                return
            }

            // Mirror the bitmap horizontally (front camera compensation)
            val mirroredBitmap = mirrorBitmap(bitmap)

            // Wrap in MediaPipe image format
            val mpImage = BitmapImageBuilder(mirroredBitmap).build()

            // Run hand landmark detection
            val result: HandLandmarkerResult = landmarker.detectForVideo(
                mpImage,
                timestampMs
            )

            // Convert MediaPipe results to our domain model
            val detectedHands = convertResults(result)

            // Deliver results on the calling thread (background)
            listener.onHandsDetected(detectedHands)

            // Clean up bitmaps
            bitmap.recycle()
            mirroredBitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error during hand detection", e)
        } finally {
            // CRITICAL: Always close the ImageProxy to free the buffer
            imageProxy.close()
        }
    }

    /**
     * Convert an ImageProxy (RGBA_8888) to a Bitmap.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
        return bitmap
    }

    /**
     * Mirror a bitmap horizontally to compensate for front camera mirroring.
     * This ensures gestures appear natural (left is left, right is right).
     */
    private fun mirrorBitmap(source: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            preScale(-1f, 1f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Convert MediaPipe HandLandmarkerResult to our [HandLandmarks] model.
     *
     * MediaPipe returns landmarks as NormalizedLandmark with x, y, z values
     * where x and y are normalized to [0.0, 1.0] relative to image dimensions,
     * and z represents depth relative to the wrist.
     */
    private fun convertResults(result: HandLandmarkerResult): List<HandLandmarks> {
        val hands = mutableListOf<HandLandmarks>()

        for (i in result.landmarks().indices) {
            val mpLandmarks = result.landmarks()[i]

            // Convert each of the 21 landmarks
            val points = mpLandmarks.map { landmark ->
                HandLandmarkPoint(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z()
                )
            }

            // Determine handedness (left or right)
            val handedness = if (result.handednesses().isNotEmpty() &&
                result.handednesses()[i].isNotEmpty()
            ) {
                val label = result.handednesses()[i][0].categoryName()
                if (label.equals("Left", ignoreCase = true)) Handedness.LEFT
                else Handedness.RIGHT
            } else {
                Handedness.RIGHT
            }

            hands.add(
                HandLandmarks(
                    landmarks = points,
                    handedness = handedness,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        return hands
    }

    /** Release MediaPipe resources. */
    fun close() {
        handLandmarker?.close()
        handLandmarker = null
        Log.d(TAG, "HandLandmarker released")
    }
}

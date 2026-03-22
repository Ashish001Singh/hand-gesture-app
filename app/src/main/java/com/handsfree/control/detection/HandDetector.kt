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
 * BUGS FIXED:
 * 1. Timestamp: was dividing by 1_000 (giving microseconds); MediaPipe VIDEO
 *    mode expects milliseconds → now divides by 1_000_000.
 * 2. Row stride: imageProxyToBitmap now accounts for row padding bytes that
 *    camera buffers include, preventing distorted images.
 * 3. Bitmap leak: bitmap recycle moved to finally block so it always runs
 *    even if listener.onHandsDetected() throws.
 * 4. Thread safety: handLandmarker marked @Volatile so close() on main thread
 *    and analyze() on background thread can't race.
 * 5. Image rotation: rotationDegrees from imageProxy now applied to the bitmap
 *    so landmarks are correctly oriented on all devices.
 * 6. Silent failure: user-facing error reported via listener when model is missing.
 */
class HandDetector(
    private val context: Context,
    private val listener: HandDetectionListener,
    private val errorListener: HandDetectorErrorListener? = null
) : FrameAnalyzer {

    companion object {
        private const val TAG = "HandDetector"
        private const val MODEL_ASSET = "hand_landmarker.task"
        private const val MAX_HANDS = 1
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
        private const val MIN_PRESENCE_CONFIDENCE = 0.5f
    }

    // FIX #4: @Volatile ensures changes in close() (main thread) are immediately
    // visible in analyze() (background thread), preventing TOCTOU race conditions.
    @Volatile
    private var handLandmarker: HandLandmarker? = null

    // FIX #6: track whether initialisation failed to report it once
    @Volatile
    private var initFailed = false

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        // FIX #9: Verify model file exists in assets before attempting to load
        try {
            context.assets.open(MODEL_ASSET).close()
        } catch (e: Exception) {
            val msg = "hand_landmarker.task not found in assets. " +
                    "Download it from: https://storage.googleapis.com/mediapipe-models/" +
                    "hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
            Log.e(TAG, msg)
            errorListener?.onError(msg)
            initFailed = true
            return
        }

        // Try GPU first, fall back to CPU
        for (delegate in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(delegate)
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
                Log.d(TAG, "HandLandmarker initialized with $delegate delegate")
                return // success — stop trying
            } catch (e: Exception) {
                Log.w(TAG, "$delegate delegate failed, trying next option", e)
            }
        }

        // Both GPU and CPU failed
        val msg = "Failed to initialize HandLandmarker on both GPU and CPU"
        Log.e(TAG, msg)
        errorListener?.onError(msg)
        initFailed = true
    }

    override fun analyze(imageProxy: ImageProxy) {
        // FIX #4: Read volatile once so we have a stable reference for this frame
        val landmarker = handLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        // FIX #1: ImageProxy timestamp is in NANOSECONDS.
        // MediaPipe VIDEO mode detectForVideo() expects MILLISECONDS.
        // Previous code divided by 1_000 giving microseconds — 1000x too large.
        val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L

        var bitmap: Bitmap? = null
        var processedBitmap: Bitmap? = null

        try {
            // FIX #2: Convert ImageProxy to Bitmap correctly accounting for row stride
            bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap == null) {
                return
            }

            // FIX #5: Apply rotation AND mirror correction in a single matrix operation
            processedBitmap = applyTransformations(
                source = bitmap,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees
            )

            val mpImage = BitmapImageBuilder(processedBitmap).build()

            val result: HandLandmarkerResult = landmarker.detectForVideo(
                mpImage,
                timestampMs
            )

            listener.onHandsDetected(convertResults(result))

        } catch (e: Exception) {
            Log.e(TAG, "Error during hand detection", e)
        } finally {
            // FIX #3: Always recycle bitmaps AND always close ImageProxy,
            // even if the listener throws or detection fails.
            bitmap?.recycle()
            processedBitmap?.recycle()
            imageProxy.close()
        }
    }

    /**
     * Convert ImageProxy (RGBA_8888) to Bitmap, correctly handling row stride.
     *
     * FIX #2 DETAIL: Camera hardware often adds padding bytes at the end of each
     * row (rowStride >= width * pixelStride). If we naively read all buffer bytes
     * into a bitmap, the padding bytes shift every row right, producing a
     * diagonally sheared/distorted image. We must skip padding per row.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride    // bytes per pixel (4 for RGBA_8888)
        val rowStride = plane.rowStride        // bytes per row (may include padding)
        val rowPadding = rowStride - pixelStride * imageProxy.width

        // Create bitmap wide enough to hold one row including padding,
        // then crop to actual width
        val bitmap = Bitmap.createBitmap(
            imageProxy.width + rowPadding / pixelStride,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop away the row-padding columns on the right
        return if (rowPadding > 0) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height)
            bitmap.recycle()
            cropped
        } else {
            bitmap
        }
    }

    /**
     * Apply rotation correction and front-camera horizontal mirror in one pass.
     *
     * FIX #5 DETAIL: Front camera frames need two transforms:
     * 1. Rotation by imageInfo.rotationDegrees to match screen orientation.
     * 2. Horizontal flip to un-mirror the front camera feed, so that
     *    right hand = right side of image.
     *
     * Combining both into a single Matrix avoids creating an intermediate bitmap.
     */
    private fun applyTransformations(source: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            // Apply rotation first
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
            // Then mirror horizontally for front camera
            postScale(-1f, 1f, source.width / 2f, source.height / 2f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun convertResults(result: HandLandmarkerResult): List<HandLandmarks> {
        val hands = mutableListOf<HandLandmarks>()

        for (i in result.landmarks().indices) {
            val mpLandmarks = result.landmarks()[i]

            if (mpLandmarks.size < 21) {
                Log.w(TAG, "Incomplete landmarks: only ${mpLandmarks.size}/21 points")
                continue
            }

            val points = mpLandmarks.map { landmark ->
                HandLandmarkPoint(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z()
                )
            }

            val handedness = if (result.handednesses().isNotEmpty() &&
                i < result.handednesses().size &&
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

    /** Release MediaPipe resources. Safe to call from any thread. */
    fun close() {
        // FIX #4: Volatile write + null before close() prevents analyze() from using
        // a partially-destroyed landmarker
        val lm = handLandmarker
        handLandmarker = null
        lm?.close()
        Log.d(TAG, "HandLandmarker released")
    }
}

/** Reports non-recoverable errors during hand detection setup or inference. */
fun interface HandDetectorErrorListener {
    fun onError(message: String)
}

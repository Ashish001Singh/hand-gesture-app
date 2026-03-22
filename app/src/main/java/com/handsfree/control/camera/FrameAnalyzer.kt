package com.handsfree.control.camera

import androidx.camera.core.ImageProxy

/**
 * Interface for processing camera frames.
 *
 * Implementations must call [ImageProxy.close] when done to release the
 * frame buffer and allow the next frame to be delivered.
 */
fun interface FrameAnalyzer {
    /**
     * Process a single camera frame.
     *
     * This is called on a background thread (the analysis executor),
     * so heavy computation is safe here without blocking the UI.
     *
     * @param imageProxy The camera frame; MUST be closed after processing.
     */
    fun analyze(imageProxy: ImageProxy)
}

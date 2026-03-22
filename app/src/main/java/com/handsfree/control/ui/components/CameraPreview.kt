package com.handsfree.control.ui.components

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Composable wrapper around CameraX PreviewView.
 *
 * CameraX's PreviewView is a traditional Android View, so we use
 * AndroidView to embed it in Compose. The [onPreviewCreated] callback
 * provides the PreviewView instance so the Activity can bind it to
 * the camera.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onPreviewCreated: (PreviewView) -> Unit
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { previewView ->
            onPreviewCreated(previewView)
        }
    )
}

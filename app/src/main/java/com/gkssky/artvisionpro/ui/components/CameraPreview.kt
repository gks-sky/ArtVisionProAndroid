package com.gkssky.artvisionpro.ui.components

import android.graphics.Color
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.viewinterop.AndroidView
import com.gkssky.artvisionpro.camera.CameraController

@Composable
fun CameraPreview(controller: CameraController, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                setBackgroundColor(Color.BLACK)
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }
        },
        modifier = modifier.background(Black),
        update = controller::attachPreview,
        onRelease = controller::detachPreview,
    )

    DisposableEffect(controller) {
        onDispose { /* AndroidView.onRelease detaches the exact view instance. */ }
    }
}

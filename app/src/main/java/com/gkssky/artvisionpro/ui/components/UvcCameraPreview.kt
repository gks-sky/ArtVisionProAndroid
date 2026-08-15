package com.gkssky.artvisionpro.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.gkssky.artvisionpro.camera.CameraController
import com.serenegiant.widget.UVCCameraTextureView

@Composable
fun UvcCameraPreview(controller: CameraController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val previewView = remember(controller) {
        UVCCameraTextureView(context).apply {
            setAspectRatio(640, 480)
            Log.i(TAG, "UVC VIEW CREATED view=${identity(this)}")
        }
    }
    AndroidView(
        factory = { previewView },
        modifier = modifier.background(Black),
        update = {
            Log.i(TAG, "UVC VIEW REUSED view=${identity(it)}")
            controller.attachUvcPreview(it)
        },
        onRelease = controller::detachUvcPreview,
    )
}

private fun identity(value: Any): String =
    "${value.javaClass.simpleName}@${System.identityHashCode(value).toString(16)}"

private const val TAG = "UvcPreviewSurface"

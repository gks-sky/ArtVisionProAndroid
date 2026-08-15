package com.gkssky.artvisionpro.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CameraDisplayFlipArchitectureTest {
    private val sourceRoot = File(System.getProperty("user.dir"), "src/main/java/com/gkssky/artvisionpro")

    @Test fun `shared transform combines camera scale rotation and vertical flip`() {
        val screen = sourceRoot.resolve("ui/screens/CameraScreen.kt").readText()
        val transform = screen.substringBefore("referenceState.displayBitmap")
        assertTrue(transform.contains("scaleX = cameraTransform.scale"))
        assertTrue(transform.contains("scaleY = if (verticalFlipEnabled) -cameraTransform.scale else cameraTransform.scale"))
        assertTrue(transform.contains("rotationZ = cameraTransform.rotation"))
    }

    @Test fun `CameraX and UVC are children of the same display transform`() {
        val screen = sourceRoot.resolve("ui/screens/CameraScreen.kt").readText()
        val transformStart = screen.indexOf("scaleY = if (verticalFlipEnabled)")
        val cameraX = screen.indexOf("CameraPreview(controller = controller", transformStart)
        val uvc = screen.indexOf("UvcCameraPreview(controller = controller", transformStart)
        val reference = screen.indexOf("referenceState.displayBitmap", transformStart)
        assertTrue(transformStart >= 0 && cameraX > transformStart && uvc > transformStart)
        assertTrue(reference > cameraX && reference > uvc)
    }

    @Test fun `UVC uses transformable texture preview and flip does not reopen cameras`() {
        val preview = sourceRoot.resolve("ui/components/UvcCameraPreview.kt").readText()
        val screen = sourceRoot.resolve("ui/screens/CameraScreen.kt").readText()
        assertTrue(preview.contains("UVCCameraTextureView"))
        assertFalse(preview.contains("AspectRatioSurfaceView"))
        assertFalse(screen.contains("onToggleVerticalFlip = controller::"))
        assertTrue(screen.contains("onToggleVerticalFlip = referenceImageController::toggleVerticalFlip"))
    }

    @Test fun `flip state persists across backend switches and reference layer is separate`() {
        val state = sourceRoot.resolve("reference/ReferenceImageController.kt").readText()
        val screen = sourceRoot.resolve("ui/screens/CameraScreen.kt").readText()
        assertTrue(state.contains("preferences.getBoolean(KEY_VERTICAL_FLIP, false)"))
        assertTrue(state.contains("putBoolean(KEY_VERTICAL_FLIP, enabled)"))
        assertTrue(screen.indexOf("referenceState.displayBitmap") > screen.indexOf("scaleY = if (verticalFlipEnabled)"))
    }
}

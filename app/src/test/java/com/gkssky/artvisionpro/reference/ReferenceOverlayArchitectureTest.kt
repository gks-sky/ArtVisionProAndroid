package com.gkssky.artvisionpro.reference

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReferenceOverlayArchitectureTest {
    private val projectRoot = File(System.getProperty("user.dir"))

    @Test fun `reference state is owned outside composable and survives recomposition`() {
        val activity = projectRoot.resolve("src/main/java/com/gkssky/artvisionpro/MainActivity.kt").readText()
        assertTrue(activity.contains("private lateinit var referenceImageController"))
        assertTrue(activity.contains("ArtVisionProApp(referenceImageController)"))
        val controller = projectRoot.resolve("src/main/java/com/gkssky/artvisionpro/reference/ReferenceImageController.kt").readText()
        assertTrue(controller.contains("val transform: StateFlow<ReferenceTransformState>"))
    }

    @Test fun `camera preview remains composed behind reference overlay`() {
        val screen = projectRoot.resolve("src/main/java/com/gkssky/artvisionpro/ui/screens/CameraScreen.kt").readText()
        val camera = screen.indexOf("CameraPreview(controller")
        val overlay = screen.indexOf("referenceState.bitmap?.let")
        assertTrue(camera >= 0)
        assertTrue(overlay > camera)
    }

    @Test fun `camera transform remains unchanged and boundary needs a reference`() {
        val screen = projectRoot.resolve("src/main/java/com/gkssky/artvisionpro/ui/screens/CameraScreen.kt").readText()
        assertTrue(screen.contains("transformMode == TransformMode.REFERENCE"))
        assertTrue(screen.contains("CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())"))
        assertTrue(screen.contains("UvcCameraPreview(controller = controller, modifier = Modifier.fillMaxSize())"))
    }
}

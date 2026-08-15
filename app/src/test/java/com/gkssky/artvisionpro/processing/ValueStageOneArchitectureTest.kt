package com.gkssky.artvisionpro.processing

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueStageOneArchitectureTest {
    private val controller = File("src/main/java/com/gkssky/artvisionpro/reference/ReferenceImageController.kt").readText()
    private val screen = File("src/main/java/com/gkssky/artvisionpro/ui/screens/CameraScreen.kt").readText()

    @Test fun `auto reset and new image analysis are controller owned`() {
        assertTrue(controller.contains("fun autoValueThresholds()"))
        assertTrue(controller.contains("fun resetValueThresholds()"))
        assertTrue(controller.contains("HistogramAnalyzer.histogram(bitmap)"))
        assertTrue(controller.contains("automaticThresholds = thresholds"))
    }

    @Test fun `live updates are debounced and background processed`() {
        assertTrue(controller.contains("postDelayed(action, VALUE_PREVIEW_DEBOUNCE_MS)"))
        assertTrue(controller.contains("executor.execute"))
    }

    @Test fun `processing changes only reference bitmap state and preserves transforms`() {
        assertTrue(controller.contains("ValueProcessor.applyValueMode(source, mode, settings, artisticSettings)"))
        assertTrue(controller.contains("copy(processedBitmap = processed)"))
        assertTrue(controller.contains("mutableCameraTransform"))
        assertTrue(controller.contains("mutableTraceTransform"))
        assertTrue(screen.contains("referenceTransform.offsetX"))
        assertTrue(screen.contains("traceTransform.offsetX"))
    }
}


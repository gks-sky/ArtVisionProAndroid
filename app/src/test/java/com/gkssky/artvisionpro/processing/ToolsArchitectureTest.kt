package com.gkssky.artvisionpro.processing

import com.gkssky.artvisionpro.camera.CameraTransformState
import com.gkssky.artvisionpro.reference.ReferenceImageState
import com.gkssky.artvisionpro.reference.ReferenceTransformState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolsArchitectureTest {
    private val projectRoot = File(System.getProperty("user.dir"))

    @Test fun `tools and breakdown rows have independent visibility state`() {
        val controller = projectRoot.resolve("src/main/java/com/gkssky/artvisionpro/reference/ReferenceImageController.kt").readText()
        assertTrue(controller.contains("mutableToolsExpanded"))
        assertTrue(controller.contains("mutableBreakdownExpanded"))
    }

    @Test fun `selection remains stored when rows hide`() {
        val controller = projectRoot.resolve("src/main/java/com/gkssky/artvisionpro/reference/ReferenceImageController.kt").readText()
        assertTrue(controller.contains("mutableValueMode"))
        assertTrue(controller.contains("fun toggleBreakdown()"))
    }

    @Test fun `no reference has no display bitmap`() {
        assertNull(ReferenceImageState().displayBitmap)
    }

    @Test fun `processing selection does not share transform state`() {
        assertEquals(ReferenceTransformState(), ReferenceTransformState())
        assertEquals(CameraTransformState(), CameraTransformState())
    }
}

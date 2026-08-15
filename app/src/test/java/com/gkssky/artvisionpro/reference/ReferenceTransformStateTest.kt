package com.gkssky.artvisionpro.reference

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceTransformStateTest {
    private fun transformed(
        state: ReferenceTransformState = ReferenceTransformState(),
        panX: Float = 0f,
        panY: Float = 0f,
        zoom: Float = 1f,
        rotation: Float = 0f,
    ) = state.transformed(500f, 400f, panX, panY, zoom, rotation, 1000f, 800f)

    @Test fun `initial transform is fitted center transform`() {
        assertEquals(ReferenceTransformState(), ReferenceTransformState())
        assertEquals(1000 to 500, fitInside(4000, 2000, 1000, 800))
    }

    @Test fun `drag updates offset`() {
        val result = transformed(panX = 32f, panY = -18f)
        assertEquals(32f, result.offsetX)
        assertEquals(-18f, result.offsetY)
    }

    @Test fun `pinch updates scale`() {
        assertEquals(2f, transformed(zoom = 2f).scale)
    }

    @Test fun `rotation updates and normalizes angle`() {
        assertEquals(-170f, transformed(rotation = 190f).rotation)
    }

    @Test fun `scale is safely clamped`() {
        assertEquals(MAX_REFERENCE_SCALE, transformed(zoom = 100f).scale)
        assertEquals(MIN_REFERENCE_SCALE, transformed(zoom = 0.001f).scale)
    }

    @Test fun `reset restores fit transform`() {
        val changed = ReferenceTransformState(20f, 30f, 3f, 45f)
        assertEquals(ReferenceTransformState(), changed.reset())
    }
}

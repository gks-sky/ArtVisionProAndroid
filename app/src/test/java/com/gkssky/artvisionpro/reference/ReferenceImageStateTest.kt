package com.gkssky.artvisionpro.reference

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceImageStateTest {
    @Test fun `image selected enters loading state`() {
        val state = ReferenceImageState().loading(Uri.EMPTY)
        assertEquals(Uri.EMPTY, state.uri)
        assertTrue(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test fun `image removed resets reference state`() {
        val state = ReferenceImageState(isLoading = true, opacity = 0.8f).removed()
        assertNull(state.uri)
        assertNull(state.bitmap)
        assertFalse(state.isLoading)
        assertEquals(DEFAULT_REFERENCE_OPACITY, state.opacity)
    }

    @Test fun `loading failure exposes error and clears image`() {
        val state = ReferenceImageState(isLoading = true).loadFailed("Broken image")
        assertEquals("Broken image", state.errorMessage)
        assertFalse(state.isLoading)
        assertNull(state.uri)
    }

    @Test fun `opacity changes are clamped`() {
        assertEquals(1f, ReferenceImageState().withOpacity(2f).opacity)
        assertEquals(0f, ReferenceImageState().withOpacity(-1f).opacity)
        assertEquals(0.65f, ReferenceImageState().withOpacity(0.65f).opacity)
    }

    @Test fun `aspect ratio fitting preserves landscape image`() {
        assertEquals(1000 to 500, fitInside(4000, 2000, 1000, 1000))
    }

    @Test fun `aspect ratio fitting preserves portrait image`() {
        assertEquals(500 to 1000, fitInside(2000, 4000, 1000, 1000))
    }
}

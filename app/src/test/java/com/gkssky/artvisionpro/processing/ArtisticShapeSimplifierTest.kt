package com.gkssky.artvisionpro.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtisticShapeSimplifierTest {
    @Test fun `small island merges into neighbor with largest shared boundary`() {
        val labels = ByteArray(30 * 30) { 1 }
        labels[15 * 30 + 15] = 0
        val result = ArtisticShapeSimplifier.process(labels, 30, 30, ValueSettingsState(100, 0, 0))
        assertEquals(1, result[15 * 30 + 15].toInt())
    }

    @Test fun `large value mass remains intact`() {
        val labels = ByteArray(40 * 40) { 2 }
        for (y in 8..31) for (x in 8..31) labels[y * 40 + x] = 0
        val result = ArtisticShapeSimplifier.process(labels, 40, 40, ValueSettingsState(100, 100, 100))
        assertEquals(0, result[20 * 40 + 20].toInt())
        assertTrue(result.count { it.toInt() == 0 } > 400)
    }

    @Test fun `noise removal clears isolated dark and light speckles`() {
        val labels = ByteArray(25 * 25) { 1 }
        labels[12 * 25 + 12] = 0
        val darkRemoved = ArtisticShapeSimplifier.process(labels, 25, 25, ValueSettingsState(0, 25, 0))
        assertEquals(1, darkRemoved[12 * 25 + 12].toInt())
        val dark = ByteArray(25 * 25) { 0 }
        dark[12 * 25 + 12] = 2
        val lightRemoved = ArtisticShapeSimplifier.process(dark, 25, 25, ValueSettingsState(0, 25, 0))
        assertEquals(0, lightRemoved[12 * 25 + 12].toInt())
    }

    @Test fun `boundary smoothing removes a one pixel jagged protrusion without blur`() {
        val labels = ByteArray(25 * 25) { index -> if (index % 25 < 12) 0 else 2 }
        labels[12 * 25 + 12] = 0
        val result = ArtisticShapeSimplifier.process(labels, 25, 25, ValueSettingsState(20, 0, 100))
        assertEquals(2, result[12 * 25 + 12].toInt())
        assertTrue(result.all { it.toInt() == 0 || it.toInt() == 2 })
    }

    @Test fun `display resolution processing benchmark`() {
        val width = 1280
        val height = 720
        val labels = ByteArray(width * height) { index -> ((index / 37 + index / width / 29) % 5).toByte() }
        val settings = ValueSettingsState(50, 25, 30)
        repeat(2) { ArtisticShapeSimplifier.process(labels, width, height, settings) }
        val start = System.nanoTime()
        repeat(5) { ArtisticShapeSimplifier.process(labels, width, height, settings) }
        val averageMs = (System.nanoTime() - start) / 5.0 / 1_000_000.0
        println("ARTISTIC_SHAPE_AVERAGE_MS=$averageMs")
        assertTrue(averageMs < 5000.0)
    }
}


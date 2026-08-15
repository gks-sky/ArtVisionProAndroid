package com.gkssky.artvisionpro.processing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueProcessorTest {
    @Test fun `perceptual grayscale weights green most strongly`() {
        assertEquals(54, HistogramAnalyzer.luminance(255, 0, 0))
        assertEquals(182, HistogramAnalyzer.luminance(0, 255, 0))
        assertEquals(18, HistogramAnalyzer.luminance(0, 0, 255))
    }

    @Test fun `two values obey boundary equality`() {
        assertEquals(0, ValueProcessor.valueForLuminance(99, ValueMode.TWO_VALUES, intArrayOf(100)))
        assertEquals(255, ValueProcessor.valueForLuminance(100, ValueMode.TWO_VALUES, intArrayOf(100)))
    }

    @Test fun `three values produce dark mid and light`() {
        val thresholds = intArrayOf(80, 180)
        assertEquals(0, ValueProcessor.valueForLuminance(79, ValueMode.THREE_VALUES, thresholds))
        assertEquals(128, ValueProcessor.valueForLuminance(80, ValueMode.THREE_VALUES, thresholds))
        assertEquals(255, ValueProcessor.valueForLuminance(180, ValueMode.THREE_VALUES, thresholds))
    }

    @Test fun `five values produce clean output levels`() {
        val thresholds = intArrayOf(20, 70, 140, 210)
        assertArrayEquals(
            intArrayOf(0, 64, 128, 192, 255),
            intArrayOf(0, 20, 70, 140, 210).map {
                ValueProcessor.valueForLuminance(it, ValueMode.FIVE_VALUES, thresholds)
            }.toIntArray(),
        )
    }

    @Test fun `crossed thresholds are strictly ordered`() {
        assertArrayEquals(intArrayOf(200, 201, 202, 203), enforceOrdered(intArrayOf(200, 10, 10, 10)))
    }

    @Test fun `automatic thresholds follow image histogram`() {
        val histogram = IntArray(256).apply {
            this[20] = 40
            this[80] = 20
            this[180] = 20
            this[240] = 40
        }
        val automatic = HistogramAnalyzer.automaticThresholds(histogram)
        assertTrue(automatic.two in 20..239)
        assertTrue(automatic.threeDark < automatic.threeLight)
        assertTrue(automatic.fiveT1 < automatic.fiveT2)
        assertTrue(automatic.fiveT2 < automatic.fiveT3)
        assertTrue(automatic.fiveT3 < automatic.fiveT4)
    }
}

package com.gkssky.artvisionpro.processing

import android.graphics.Bitmap
import android.graphics.Color

object ValueProcessor {
    fun applyValueMode(sourceBitmap: Bitmap, mode: ValueMode, thresholds: ValueThresholds = ValueThresholds(), artisticSettings: ValueSettingsState = ValueSettingsState()): Bitmap {
        if (mode == ValueMode.ORIGINAL) return sourceBitmap
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val pixels = IntArray(width * height)
        sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val modeThresholds = thresholds.ordered().forMode(mode)
        val valueLevels = when (mode) {
            ValueMode.TWO_VALUES -> intArrayOf(0, 255)
            ValueMode.THREE_VALUES -> intArrayOf(0, 128, 255)
            ValueMode.FIVE_VALUES -> intArrayOf(0, 64, 128, 192, 255)
            else -> null
        }
        val labels = valueLevels?.let { ByteArray(pixels.size) }
        for (index in pixels.indices) {
            val color = pixels[index]
            val luminance = HistogramAnalyzer.luminance(Color.red(color), Color.green(color), Color.blue(color))
            val value = valueForLuminance(luminance, mode, modeThresholds)
            labels?.set(index, valueLevels!!.indexOf(value).toByte())
            pixels[index] = Color.argb(Color.alpha(color), value, value, value)
        }
        if (labels != null) {
            val cleaned = ArtisticShapeSimplifier.process(labels, width, height, artisticSettings)
            for (index in pixels.indices) {
                val value = valueLevels!![cleaned[index].toInt()]
                pixels[index] = Color.argb(Color.alpha(pixels[index]), value, value, value)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    internal fun valueForLuminance(luminance: Int, mode: ValueMode, thresholds: IntArray): Int {
        val value = luminance.coerceIn(0, 255)
        if (mode == ValueMode.GRAYSCALE) return value
        val levels = when (mode) {
            ValueMode.TWO_VALUES -> intArrayOf(0, 255)
            ValueMode.THREE_VALUES -> intArrayOf(0, 128, 255)
            ValueMode.FIVE_VALUES -> intArrayOf(0, 64, 128, 192, 255)
            else -> return value
        }
        val group = thresholds.indexOfFirst { value < it }.let { if (it < 0) thresholds.size else it }
        return levels[group]
    }
}

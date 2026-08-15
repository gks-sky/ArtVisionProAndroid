package com.gkssky.artvisionpro.export

import android.graphics.Bitmap
import android.graphics.Color
import com.gkssky.artvisionpro.processing.ValueMode
import com.gkssky.artvisionpro.processing.ValueProcessor
import com.gkssky.artvisionpro.processing.ValueSettingsState
import com.gkssky.artvisionpro.processing.ValueThresholds

data class ValueExportConfiguration(
    val mode: ValueMode,
    val thresholds: ValueThresholds,
    val settings: ValueSettingsState,
)

enum class ReferenceExportOption {
    CURRENT_VIEW,
    VALUE_STUDY,
    SHAPE_0,
    SHAPE_1,
    SHAPE_2,
    SHAPE_3,
    SHAPE_4,
    ALL_VALUE_SHAPES,
}

/** Builds export bitmaps from the same quantized value map used by the live preview. */
object ValueShapeExporter {
    fun currentView(source: Bitmap, configuration: ValueExportConfiguration): Bitmap =
        ValueProcessor.applyValueMode(
            source,
            configuration.mode,
            configuration.thresholds,
            configuration.settings,
        )

    fun shape(valueMap: Bitmap, selectedLevel: Int): Bitmap {
        val width = valueMap.width
        val height = valueMap.height
        val pixels = IntArray(width * height)
        valueMap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            pixels[index] = if (Color.red(pixels[index]) == selectedLevel) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun levelsFor(mode: ValueMode): IntArray = when (mode) {
        ValueMode.THREE_VALUES -> intArrayOf(0, 128, 255)
        ValueMode.FIVE_VALUES -> intArrayOf(0, 64, 128, 192, 255)
        else -> intArrayOf()
    }
}

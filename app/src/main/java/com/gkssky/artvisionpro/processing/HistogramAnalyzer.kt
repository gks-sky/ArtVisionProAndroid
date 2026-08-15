package com.gkssky.artvisionpro.processing

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

data class ValueThresholds(
    val two: Int = 128,
    val threeDark: Int = 85,
    val threeLight: Int = 170,
    val fiveT1: Int = 51,
    val fiveT2: Int = 102,
    val fiveT3: Int = 153,
    val fiveT4: Int = 204,
) {
    fun ordered(): ValueThresholds {
        val three = enforceOrdered(intArrayOf(threeDark, threeLight))
        val five = enforceOrdered(intArrayOf(fiveT1, fiveT2, fiveT3, fiveT4))
        return copy(
            two = two.coerceIn(0, 255),
            threeDark = three[0], threeLight = three[1],
            fiveT1 = five[0], fiveT2 = five[1], fiveT3 = five[2], fiveT4 = five[3],
        )
    }

    fun forMode(mode: ValueMode): IntArray = when (mode) {
        ValueMode.TWO_VALUES -> intArrayOf(two)
        ValueMode.THREE_VALUES -> intArrayOf(threeDark, threeLight)
        ValueMode.FIVE_VALUES -> intArrayOf(fiveT1, fiveT2, fiveT3, fiveT4)
        else -> intArrayOf()
    }
}

fun enforceOrdered(values: IntArray): IntArray {
    if (values.isEmpty()) return values
    val result = values.copyOf()
    for (i in result.indices) {
        val min = if (i == 0) 0 else result[i - 1] + 1
        val max = 255 - (result.lastIndex - i)
        result[i] = result[i].coerceIn(min, max)
    }
    return result
}

object HistogramAnalyzer {
    fun luminance(red: Int, green: Int, blue: Int): Int =
        (red * 0.2126 + green * 0.7152 + blue * 0.0722).roundToInt().coerceIn(0, 255)

    fun histogram(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return histogram(pixels)
    }

    internal fun histogram(pixels: IntArray): IntArray = IntArray(256).also { histogram ->
        pixels.forEach { color -> histogram[luminance(Color.red(color), Color.green(color), Color.blue(color))]++ }
    }

    fun automaticThresholds(histogram: IntArray): ValueThresholds {
        require(histogram.size == 256)
        if (histogram.sum() == 0) return ValueThresholds()
        return ValueThresholds(
            two = otsu(histogram),
            threeDark = percentile(histogram, 1.0 / 3.0),
            threeLight = percentile(histogram, 2.0 / 3.0),
            fiveT1 = percentile(histogram, 0.2),
            fiveT2 = percentile(histogram, 0.4),
            fiveT3 = percentile(histogram, 0.6),
            fiveT4 = percentile(histogram, 0.8),
        ).ordered()
    }

    private fun percentile(histogram: IntArray, fraction: Double): Int {
        val target = (histogram.sum() * fraction).roundToInt().coerceAtLeast(1)
        var count = 0
        for (value in histogram.indices) {
            count += histogram[value]
            if (count >= target) return value
        }
        return 255
    }

    private fun otsu(histogram: IntArray): Int {
        val total = histogram.sum()
        var sum = 0.0
        histogram.indices.forEach { sum += it * histogram[it].toDouble() }
        var backgroundWeight = 0
        var backgroundSum = 0.0
        var bestVariance = -1.0
        var best = 128
        for (threshold in 0..254) {
            backgroundWeight += histogram[threshold]
            if (backgroundWeight == 0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0) break
            backgroundSum += threshold * histogram[threshold].toDouble()
            val backgroundMean = backgroundSum / backgroundWeight
            val foregroundMean = (sum - backgroundSum) / foregroundWeight
            val difference = backgroundMean - foregroundMean
            val variance = backgroundWeight.toDouble() * foregroundWeight * difference * difference
            if (variance > bestVariance) {
                bestVariance = variance
                // The processor treats values equal to the boundary as light.
                best = (threshold + 1).coerceAtMost(255)
            }
        }
        return best
    }
}

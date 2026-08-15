package com.gkssky.artvisionpro.processing

/** Operates on quantized value labels; no stage interpolates or blurs image values. */
internal object ArtisticShapeSimplifier {
    fun process(labels: ByteArray, width: Int, height: Int, settings: ValueSettingsState): ByteArray {
        require(labels.size == width * height)
        val bounded = settings.bounded()
        var current = labels
        if (bounded.noiseRemovalAmount > 0) current = removeNoise(current, width, height, bounded.noiseRemovalAmount)
        if (bounded.simplifyAmount > 0) current = mergeSmallRegions(current, width, height, bounded.simplifyAmount)
        if (bounded.edgeSmoothnessAmount > 0) current = smoothBoundaries(current, width, height, bounded.edgeSmoothnessAmount, bounded.simplifyAmount)
        return current
    }

    private fun removeNoise(input: ByteArray, width: Int, height: Int, amount: Int): ByteArray {
        var source = input
        repeat(1 + amount / 40) {
            val target = source.copyOf()
            val counts = IntArray(5)
            for (y in 1 until height - 1) for (x in 1 until width - 1) {
                val index = y * width + x
                val center = source[index].toInt()
                java.util.Arrays.fill(counts, 0)
                for (dy in -1..1) for (dx in -1..1) counts[source[index + dy * width + dx].toInt()]++
                var winner = center
                var winnerCount = counts[center]
                for (label in counts.indices) if (counts[label] > winnerCount) { winner = label; winnerCount = counts[label] }
                if (winner != center && winnerCount >= if (amount < 50) 7 else 6) target[index] = winner.toByte()
            }
            source = target
        }
        return source
    }

    internal fun mergeSmallRegions(input: ByteArray, width: Int, height: Int, amount: Int): ByteArray {
        val labels = input.copyOf()
        val fraction = amount / 100f
        val minimumArea = (labels.size * (0.00002f + 0.006f * fraction * fraction)).toInt().coerceAtLeast(2)
        val visited = BooleanArray(labels.size)
        val queue = IntArray(labels.size)
        val component = IntArray(labels.size)
        repeat(2 + amount / 35) {
            java.util.Arrays.fill(visited, false)
            for (start in labels.indices) {
                if (visited[start]) continue
                val regionLabel = labels[start]
                var head = 0; var tail = 0; var componentSize = 0
                val boundaryCounts = IntArray(5)
                queue[tail++] = start; visited[start] = true
                while (head < tail) {
                    val index = queue[head++]; component[componentSize++] = index
                    val x = index % width; val y = index / width
                    fun inspect(neighbor: Int) {
                        if (labels[neighbor] == regionLabel) {
                            if (!visited[neighbor]) { visited[neighbor] = true; queue[tail++] = neighbor }
                        } else boundaryCounts[labels[neighbor].toInt()]++
                    }
                    if (x > 0) inspect(index - 1)
                    if (x + 1 < width) inspect(index + 1)
                    if (y > 0) inspect(index - width)
                    if (y + 1 < height) inspect(index + width)
                }
                if (componentSize < minimumArea) {
                    var replacement = -1; var sharedBoundary = 0
                    for (candidate in boundaryCounts.indices) if (boundaryCounts[candidate] > sharedBoundary) {
                        replacement = candidate; sharedBoundary = boundaryCounts[candidate]
                    }
                    if (replacement >= 0) for (i in 0 until componentSize) labels[component[i]] = replacement.toByte()
                }
            }
        }
        return labels
    }

    private fun smoothBoundaries(input: ByteArray, width: Int, height: Int, amount: Int, simplify: Int): ByteArray {
        var source = input
        repeat(1 + amount / 34 + simplify / 50) {
            val target = source.copyOf()
            val counts = IntArray(5)
            for (y in 1 until height - 1) for (x in 1 until width - 1) {
                val index = y * width + x
                val center = source[index].toInt()
                val counts = IntArray(5)
                var boundary = false
                for (dy in -1..1) for (dx in -1..1) {
                    val label = source[index + dy * width + dx].toInt(); counts[label]++
                    if (label != center) boundary = true
                }
                if (!boundary) continue
                var winner = center; var winnerCount = counts[center]
                for (label in counts.indices) if (counts[label] > winnerCount) { winner = label; winnerCount = counts[label] }
                if (winner != center && winnerCount >= if (amount + simplify >= 120) 5 else 6) target[index] = winner.toByte()
            }
            source = target
        }
        return source
    }
}


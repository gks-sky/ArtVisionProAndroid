package com.gkssky.artvisionpro.camera.uvc

import com.serenegiant.usb.Size as UvcSize

data class UvcSizeOption(
    val width: Int,
    val height: Int,
    val fps: Int,
    internal val librarySize: UvcSize? = null,
)

data class UvcPreviewConfig(val size: UvcSizeOption) {
    companion object {
        private val preferredOrder = listOf(640 to 480, 800 to 600, 1280 to 720, 1920 to 1080)

        fun orderedLibrarySizes(sizes: List<UvcSize>): List<UvcSize> = sizes.sortedWith(
            compareBy<UvcSize> { size ->
                preferredOrder.indexOf(size.width to size.height).let { if (it < 0) Int.MAX_VALUE else it }
            }.thenBy { it.width.toLong() * it.height }
                .thenBy { it.type }
                .thenBy { it.fps }
        )

        fun choose(sizes: List<UvcSizeOption>): UvcPreviewConfig? {
            if (sizes.isEmpty()) return null
            val stable = sizes.sortedWith(
                compareByDescending<UvcSizeOption> { it.fps >= 24 }
                    .thenByDescending { it.fps }
            )
            val chosen = stable.firstOrNull { it.width == 1920 && it.height == 1080 }
                ?: stable.firstOrNull { it.width == 1280 && it.height == 720 }
                ?: stable.maxByOrNull { it.width.toLong() * it.height }
            return chosen?.let(::UvcPreviewConfig)
        }

        fun fromLibrary(sizes: List<UvcSize>): Pair<UvcPreviewConfig?, List<UvcSizeOption>> {
            val options = sizes.map { size ->
                UvcSizeOption(
                    width = size.width,
                    height = size.height,
                    fps = size.fpsList.maxOrNull() ?: size.fps,
                    librarySize = size,
                )
            }
            return choose(options) to options
        }
    }
}

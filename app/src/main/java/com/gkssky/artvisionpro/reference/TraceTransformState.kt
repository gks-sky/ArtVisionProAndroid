package com.gkssky.artvisionpro.reference

data class TraceTransformState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
)

internal fun TraceTransformState.transformed(
    centroidX: Float,
    centroidY: Float,
    panX: Float,
    panY: Float,
    zoom: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): TraceTransformState {
    val newScale = (scale * zoom).coerceIn(0.25f, 8f)
    val effectiveZoom = newScale / scale
    val centerX = viewportWidth / 2f + offsetX
    val centerY = viewportHeight / 2f + offsetY
    return copy(
        offsetX = centroidX + (centerX - centroidX) * effectiveZoom + panX - viewportWidth / 2f,
        offsetY = centroidY + (centerY - centroidY) * effectiveZoom + panY - viewportHeight / 2f,
        scale = newScale,
    )
}

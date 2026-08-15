package com.gkssky.artvisionpro.camera

data class CameraTransformState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

enum class TransformMode { REFERENCE, CAMERA }

internal fun CameraTransformState.transformed(
    centroidX: Float,
    centroidY: Float,
    panX: Float,
    panY: Float,
    zoom: Float,
    rotationDelta: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): CameraTransformState {
    val newScale = (scale * zoom).coerceIn(1f, 8f)
    val effectiveZoom = newScale / scale
    val radians = Math.toRadians(rotationDelta.toDouble())
    val cos = kotlin.math.cos(radians).toFloat()
    val sin = kotlin.math.sin(radians).toFloat()
    val centerX = viewportWidth / 2f + offsetX
    val centerY = viewportHeight / 2f + offsetY
    val relativeX = (centerX - centroidX) * effectiveZoom
    val relativeY = (centerY - centroidY) * effectiveZoom
    return copy(
        offsetX = centroidX + relativeX * cos - relativeY * sin + panX - viewportWidth / 2f,
        offsetY = centroidY + relativeX * sin + relativeY * cos + panY - viewportHeight / 2f,
        scale = newScale,
        rotation = ((rotation + rotationDelta + 180f) % 360f + 360f) % 360f - 180f,
    )
}

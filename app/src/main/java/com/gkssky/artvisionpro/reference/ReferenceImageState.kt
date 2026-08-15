package com.gkssky.artvisionpro.reference

import android.graphics.Bitmap
import android.net.Uri

data class ReferenceImageState(
    val uri: Uri? = null,
    val bitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val opacity: Float = DEFAULT_REFERENCE_OPACITY,
) {
    val hasReference: Boolean get() = uri != null && bitmap != null
    val displayBitmap: Bitmap? get() = processedBitmap ?: bitmap
}

internal const val DEFAULT_REFERENCE_OPACITY = 0.5f

internal fun ReferenceImageState.withOpacity(value: Float) = copy(opacity = value.coerceIn(0f, 1f))

internal fun ReferenceImageState.removed() = ReferenceImageState()

internal fun ReferenceImageState.loading(uri: Uri) = copy(
    uri = uri,
    bitmap = null,
    processedBitmap = null,
    isLoading = true,
    errorMessage = null,
)

data class ReferenceTransformState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

internal const val MIN_REFERENCE_SCALE = 0.1f
internal const val MAX_REFERENCE_SCALE = 10f

internal fun ReferenceTransformState.dragged(deltaX: Float, deltaY: Float) = copy(
    offsetX = offsetX + deltaX,
    offsetY = offsetY + deltaY,
)

internal fun ReferenceTransformState.transformed(
    centroidX: Float,
    centroidY: Float,
    panX: Float,
    panY: Float,
    zoom: Float,
    rotationDelta: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ReferenceTransformState {
    val newScale = (scale * zoom).coerceIn(MIN_REFERENCE_SCALE, MAX_REFERENCE_SCALE)
    val effectiveZoom = newScale / scale
    val radians = Math.toRadians(rotationDelta.toDouble())
    val cos = kotlin.math.cos(radians).toFloat()
    val sin = kotlin.math.sin(radians).toFloat()
    val centerX = viewportWidth / 2f + offsetX
    val centerY = viewportHeight / 2f + offsetY
    val relativeX = (centerX - centroidX) * effectiveZoom
    val relativeY = (centerY - centroidY) * effectiveZoom
    val rotatedX = relativeX * cos - relativeY * sin
    val rotatedY = relativeX * sin + relativeY * cos
    return copy(
        offsetX = centroidX + rotatedX + panX - viewportWidth / 2f,
        offsetY = centroidY + rotatedY + panY - viewportHeight / 2f,
        scale = newScale,
        rotation = normalizeRotation(rotation + rotationDelta),
    )
}

internal fun normalizeRotation(value: Float): Float = ((value + 180f) % 360f + 360f) % 360f - 180f

internal fun ReferenceTransformState.reset() = ReferenceTransformState()

internal fun ReferenceImageState.loadFailed(message: String) = copy(
    uri = null,
    bitmap = null,
    processedBitmap = null,
    isLoading = false,
    errorMessage = message,
)

internal fun fitInside(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Pair<Int, Int> {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 0 to 0
    val scale = minOf(targetWidth.toFloat() / sourceWidth, targetHeight.toFloat() / sourceHeight)
    return (sourceWidth * scale).toInt() to (sourceHeight * scale).toInt()
}

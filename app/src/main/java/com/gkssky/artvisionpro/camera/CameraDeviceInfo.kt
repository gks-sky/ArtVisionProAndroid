package com.gkssky.artvisionpro.camera

import android.util.Size

enum class CameraLensFacing(val label: String) {
    REAR("Rear"),
    FRONT("Front"),
    EXTERNAL("External"),
    UNKNOWN("Unknown"),
}

data class CameraDeviceInfo(
    val id: String,
    val displayName: String,
    val sourceType: CameraSourceType = CameraSourceType.CAMERA_X,
    val lensFacing: CameraLensFacing,
    val isExternal: Boolean,
    val usbDeviceId: Int? = null,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val supportedPreviewSizes: List<Size>,
)

object CameraSelectionPolicy {
    fun preferred(cameras: List<CameraDeviceInfo>): CameraDeviceInfo? =
        cameras.firstOrNull { it.isExternal && it.sourceType == CameraSourceType.CAMERA_X }
            ?: cameras.firstOrNull { it.lensFacing == CameraLensFacing.REAR }
            ?: cameras.firstOrNull()
}

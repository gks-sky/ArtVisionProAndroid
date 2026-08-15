package com.gkssky.artvisionpro.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size

class CameraDiscovery(context: Context) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)

    fun discover(): List<CameraDeviceInfo> = cameraManager.cameraIdList.mapNotNull { id ->
        runCatching {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = classifyLensFacing(characteristics.get(CameraCharacteristics.LENS_FACING))
            val sizes = previewSizes(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
            CameraDeviceInfo(
                id = id,
                displayName = friendlyName(facing, id),
                lensFacing = facing,
                isExternal = facing == CameraLensFacing.EXTERNAL,
                supportedPreviewSizes = sizes,
            )
        }.getOrNull()
    }

    private fun previewSizes(map: StreamConfigurationMap?): List<Size> =
        map?.getOutputSizes(SurfaceTexture::class.java)
            ?.sortedByDescending { it.width.toLong() * it.height }
            .orEmpty()

    companion object {
        fun classifyLensFacing(value: Int?): CameraLensFacing = when (value) {
            CameraCharacteristics.LENS_FACING_BACK -> CameraLensFacing.REAR
            CameraCharacteristics.LENS_FACING_FRONT -> CameraLensFacing.FRONT
            CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraLensFacing.EXTERNAL
            else -> CameraLensFacing.UNKNOWN
        }

        fun friendlyName(facing: CameraLensFacing, id: String): String = when (facing) {
            CameraLensFacing.REAR -> "Rear Camera"
            CameraLensFacing.FRONT -> "Front Camera"
            CameraLensFacing.EXTERNAL -> "External Camera"
            CameraLensFacing.UNKNOWN -> "Camera $id"
        }
    }
}

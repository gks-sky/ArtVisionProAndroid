package com.gkssky.artvisionpro.camera.uvc

import android.util.Size

enum class UvcStatus {
    IDLE,
    PERMISSION_REQUIRED,
    OPENING,
    ACTIVE,
    DISCONNECTED,
    UNSUPPORTED_FORMAT,
    ERROR,
}

data class UvcCameraState(
    val status: UvcStatus = UvcStatus.IDLE,
    val deviceId: Int? = null,
    val supportedSizes: List<Size> = emptyList(),
    val activeResolution: Size? = null,
    val message: String? = null,
    val diagnostics: UvcDiagnostics = UvcDiagnostics(),
    val controls: UvcImageControls = UvcImageControls(),
)

data class UvcControlValue(
    val minimum: Int,
    val maximum: Int,
    val default: Int,
    val initial: Int,
    val current: Int,
)

data class UvcImageControls(
    val autoExposureSupported: Boolean = false,
    val autoExposure: Boolean = false,
    val exposure: UvcControlValue? = null,
    val brightness: UvcControlValue? = null,
    val gain: UvcControlValue? = null,
    val contrast: UvcControlValue? = null,
    val saturation: UvcControlValue? = null,
)

data class UvcDiagnostics(
    val supportedModeCount: Int = 0,
    val supportedModes: List<String> = emptyList(),
    val currentMode: String? = null,
    val currentFormat: Int? = null,
    val previewStarted: Boolean = false,
    val lastException: String? = null,
)

internal fun UvcCameraState.permissionRequired(deviceId: Int) = UvcCameraState(
    status = UvcStatus.PERMISSION_REQUIRED,
    deviceId = deviceId,
    message = "USB camera permission required.",
)

internal fun UvcCameraState.permissionDenied(deviceId: Int) = UvcCameraState(
    status = UvcStatus.PERMISSION_REQUIRED,
    deviceId = deviceId,
    message = "USB camera permission denied.",
)

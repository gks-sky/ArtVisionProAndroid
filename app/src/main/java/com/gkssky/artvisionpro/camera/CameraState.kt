package com.gkssky.artvisionpro.camera

import android.util.Size
import com.gkssky.artvisionpro.camera.uvc.UvcDiagnostics
import com.gkssky.artvisionpro.camera.uvc.UvcImageControls

enum class CameraStatus {
    IDLE,
    PERMISSION_REQUIRED,
    DISCOVERING,
    OPENING,
    ACTIVE,
    DISCONNECTED,
    UNSUPPORTED_FORMAT,
    ERROR,
}

data class CameraUiState(
    val status: CameraStatus = CameraStatus.IDLE,
    val cameras: List<CameraDeviceInfo> = emptyList(),
    val selectedCamera: CameraDeviceInfo? = null,
    val activeResolution: Size? = null,
    val errorMessage: String? = null,
    val usbDevices: List<UsbCameraDeviceInfo> = emptyList(),
    val uvcDiagnostics: UvcDiagnostics = UvcDiagnostics(),
    val uvcImageControls: UvcImageControls = UvcImageControls(),
)

internal fun CameraUiState.cameraRemoved(remaining: List<CameraDeviceInfo>): CameraUiState {
    val selectedStillExists = selectedCamera?.let { selected -> remaining.any { it.id == selected.id } } == true
    return if (selectedCamera != null && !selectedStillExists) {
        copy(
            status = CameraStatus.DISCONNECTED,
            cameras = remaining,
            selectedCamera = null,
            activeResolution = null,
            errorMessage = "The active camera was disconnected.",
        )
    } else {
        copy(cameras = remaining)
    }
}

internal fun CameraUiState.permissionDenied(): CameraUiState = copy(
    status = CameraStatus.PERMISSION_REQUIRED,
    activeResolution = null,
    errorMessage = "Camera permission was denied. Grant permission to start the preview.",
)

internal fun CameraUiState.usbDeviceRemoved(deviceId: Int): CameraUiState = copy(
    usbDevices = usbDevices.filterNot { it.deviceId == deviceId },
)

internal const val USB_CAMERA_UNAVAILABLE_MESSAGE =
    "USB camera detected, but this tablet does not expose it through Android Camera2. A dedicated UVC driver will be required."

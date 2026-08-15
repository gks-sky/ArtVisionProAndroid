package com.gkssky.artvisionpro.camera

import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraArchitectureTest {
    private fun camera(id: String, facing: CameraLensFacing) = CameraDeviceInfo(
        id = id,
        displayName = CameraDiscovery.friendlyName(facing, id),
        lensFacing = facing,
        isExternal = facing == CameraLensFacing.EXTERNAL,
        supportedPreviewSizes = emptyList(),
    )

    @Test
    fun `classifies all Camera2 lens facing values`() {
        assertEquals(CameraLensFacing.REAR, CameraDiscovery.classifyLensFacing(CameraCharacteristics.LENS_FACING_BACK))
        assertEquals(CameraLensFacing.FRONT, CameraDiscovery.classifyLensFacing(CameraCharacteristics.LENS_FACING_FRONT))
        assertEquals(CameraLensFacing.EXTERNAL, CameraDiscovery.classifyLensFacing(CameraCharacteristics.LENS_FACING_EXTERNAL))
        assertEquals(CameraLensFacing.UNKNOWN, CameraDiscovery.classifyLensFacing(null))
    }

    @Test
    fun `external camera is preferred`() {
        val rear = camera("0", CameraLensFacing.REAR)
        val external = camera("2", CameraLensFacing.EXTERNAL)
        assertEquals(external, CameraSelectionPolicy.preferred(listOf(rear, external)))
    }

    @Test
    fun `rear camera is fallback`() {
        val front = camera("1", CameraLensFacing.FRONT)
        val rear = camera("0", CameraLensFacing.REAR)
        assertEquals(rear, CameraSelectionPolicy.preferred(listOf(front, rear)))
    }

    @Test
    fun `no cameras returns no selection`() {
        assertNull(CameraSelectionPolicy.preferred(emptyList()))
    }

    @Test
    fun `permission denial is recoverable state`() {
        val result = CameraUiState(status = CameraStatus.OPENING).permissionDenied()
        assertEquals(CameraStatus.PERMISSION_REQUIRED, result.status)
        assertTrue(result.errorMessage!!.contains("denied"))
    }

    @Test
    fun `camera switching clears active resolution and opens selected camera`() {
        val front = camera("1", CameraLensFacing.FRONT)
        val result = CameraUiState(status = CameraStatus.ACTIVE).cameraSelected(front)
        assertEquals(CameraStatus.OPENING, result.status)
        assertEquals(front, result.selectedCamera)
        assertNull(result.activeResolution)
    }

    @Test
    fun `USB video class is detected at device or interface level`() {
        assertTrue(UsbCameraDetector.isVideoClass(14, emptyList()))
        assertTrue(UsbCameraDetector.isVideoClass(0, listOf(3, 14)))
        assertFalse(UsbCameraDetector.isVideoClass(0, listOf(3, 8)))
    }

    @Test
    fun `removed USB device leaves remaining devices`() {
        val first = UsbCameraDeviceInfo(1, "Webcam", "Artist Cam", 10, 20, true)
        val second = UsbCameraDeviceInfo(2, "Other", null, 30, 40, false)
        val result = CameraUiState(usbDevices = listOf(first, second)).usbDeviceRemoved(1)
        assertEquals(listOf(second), result.usbDevices)
    }

    @Test
    fun `active camera removal returns to selection`() {
        val external = camera("2", CameraLensFacing.EXTERNAL)
        val result = CameraUiState(
            status = CameraStatus.ACTIVE,
            cameras = listOf(external),
            selectedCamera = external,
        ).cameraRemoved(emptyList())
        assertEquals(CameraStatus.DISCONNECTED, result.status)
        assertNull(result.selectedCamera)
    }

    @Test
    fun `USB without external Camera2 warning is exact`() {
        assertEquals(
            "USB camera detected, but this tablet does not expose it through Android Camera2. A dedicated UVC driver will be required.",
            USB_CAMERA_UNAVAILABLE_MESSAGE,
        )
    }
}

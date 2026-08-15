package com.gkssky.artvisionpro.camera

import com.gkssky.artvisionpro.camera.uvc.UvcCameraState
import com.gkssky.artvisionpro.camera.uvc.UvcPreviewConfig
import com.gkssky.artvisionpro.camera.uvc.UvcSizeOption
import com.gkssky.artvisionpro.camera.uvc.UvcStatus
import com.gkssky.artvisionpro.camera.uvc.permissionDenied
import com.gkssky.artvisionpro.camera.uvc.permissionRequired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UvcArchitectureTest {
    private val usbInfo = UsbCameraDeviceInfo(7, "Lenovo FHD Webcam", "Lenovo", 5986, 2115, false)
    private val usbCamera = usbInfo.toCameraDeviceInfo()
    private val rearCamera = CameraDeviceInfo(
        id = "0",
        displayName = "Rear Camera",
        lensFacing = CameraLensFacing.REAR,
        isExternal = false,
        supportedPreviewSizes = emptyList(),
    )

    @Test
    fun `USB device appears without Camera2 exposure`() {
        val combined = listOf(rearCamera) + listOf(usbInfo).map(UsbCameraDeviceInfo::toCameraDeviceInfo)
        assertEquals(2, combined.size)
        assertEquals("USB Camera  Lenovo FHD Webcam", combined.last().displayName)
        assertEquals(CameraSourceType.USB_UVC, combined.last().sourceType)
    }

    @Test
    fun `USB permission required is explicit`() {
        val state = UvcCameraState().permissionRequired(7)
        assertEquals(UvcStatus.PERMISSION_REQUIRED, state.status)
        assertEquals("USB camera permission required.", state.message)
    }

    @Test
    fun `USB permission granted proceeds to opening`() {
        val state = UvcCameraState(UvcStatus.OPENING, 7, message = "Opening USB camera...")
        assertEquals(UvcStatus.OPENING, state.status)
    }

    @Test
    fun `USB permission denial remains recoverable`() {
        val state = UvcCameraState().permissionDenied(7)
        assertEquals(UvcStatus.PERMISSION_REQUIRED, state.status)
        assertEquals("USB camera permission denied.", state.message)
    }

    @Test
    fun `switching CameraX to UVC selects only UVC`() {
        val state = CameraUiState(selectedCamera = rearCamera).cameraSelected(usbCamera)
        assertEquals(CameraSourceType.USB_UVC, state.selectedCamera?.sourceType)
        assertEquals(CameraStatus.OPENING, state.status)
    }

    @Test
    fun `switching UVC to CameraX selects only CameraX`() {
        val state = CameraUiState(selectedCamera = usbCamera).cameraSelected(rearCamera)
        assertEquals(CameraSourceType.CAMERA_X, state.selectedCamera?.sourceType)
    }

    @Test
    fun `USB detach while active clears selection`() {
        val result = CameraUiState(
            status = CameraStatus.ACTIVE,
            cameras = listOf(rearCamera, usbCamera),
            selectedCamera = usbCamera,
        ).cameraRemoved(listOf(rearCamera))
        assertEquals(CameraStatus.DISCONNECTED, result.status)
        assertNull(result.selectedCamera)
    }

    @Test
    fun `reconnect discovery adds USB without selecting it`() {
        val cameras = listOf(rearCamera, usbInfo.toCameraDeviceInfo())
        assertEquals(rearCamera, CameraSelectionPolicy.preferred(cameras))
        assertTrue(cameras.any { it.sourceType == CameraSourceType.USB_UVC })
    }

    @Test
    fun `unsupported preview list returns no config`() {
        assertNull(UvcPreviewConfig.choose(emptyList()))
    }

    @Test
    fun `resolution preference chooses stable full HD then HD`() {
        val fullHd = UvcSizeOption(1920, 1080, 30)
        val hd = UvcSizeOption(1280, 720, 30)
        assertEquals(fullHd, UvcPreviewConfig.choose(listOf(hd, fullHd))?.size)
        assertEquals(hd, UvcPreviewConfig.choose(listOf(hd))?.size)
    }

    @Test
    fun `duplicate UVC open is rejected while opening or active`() {
        assertFalse(shouldStartCamera(CameraUiState(CameraStatus.OPENING, selectedCamera = usbCamera), null, usbCamera))
        assertFalse(shouldStartCamera(CameraUiState(CameraStatus.ACTIVE, selectedCamera = usbCamera), null, usbCamera))
        assertTrue(shouldStartCamera(CameraUiState(CameraStatus.ERROR, selectedCamera = usbCamera), null, usbCamera))
    }
}

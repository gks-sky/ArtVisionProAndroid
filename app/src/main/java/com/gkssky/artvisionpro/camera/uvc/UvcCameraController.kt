package com.gkssky.artvisionpro.camera.uvc

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size as UvcSize
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCControl
import com.serenegiant.widget.CameraViewInterface
import com.serenegiant.widget.UVCCameraTextureView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Size as AndroidSize

/** ArtVision adapter around the sequence proven by uvcdiagnostic. */
class UvcCameraController(
    context: Context,
    private val onStateChanged: (UvcCameraState) -> Unit = {},
) : AutoCloseable {
    private val mutableState = MutableStateFlow(UvcCameraState())
    val state: StateFlow<UvcCameraState> = mutableState.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val usbManager = context.applicationContext.getSystemService(UsbManager::class.java)

    private var helper: CameraHelper? = null
    private var selectedDevice: UsbDevice? = null
    private var previewView: UVCCameraTextureView? = null
    private var previewSurface: Surface? = null
    private var surfaceAttached = false
    private var closed = false
    private var supportedSizes: List<UvcSize> = emptyList()
    private var retrySizes: List<UvcSize> = emptyList()
    private var retryIndex = 0
    private var currentMode: UvcSize? = null
    private var firstFrameReceived = false
    private var cameraOpened = false
    private var previewStarted = false
    private var logitechDiagnostic = false
    private var attemptGeneration = 0
    private var uvcControl: UVCControl? = null
    private var initialControls = UvcImageControls()

    private val surfaceCallback = object : CameraViewInterface.Callback {
        override fun onSurfaceCreated(view: CameraViewInterface, surface: Surface) {
            Log.i(TAG, "SURFACE CREATED surface=${surfaceIdentity(surface)} valid=${surface.isValid}")
            acceptCurrentSurface(surface, "created")
        }

        override fun onSurfaceChanged(view: CameraViewInterface, surface: Surface, width: Int, height: Int) {
            Log.i(TAG, "SURFACE CHANGED surface=${surfaceIdentity(surface)} ${width}x$height valid=${surface.isValid}")
            if (previewSurface !== surface) acceptCurrentSurface(surface, "changed")
        }

        override fun onSurfaceDestroy(view: CameraViewInterface, surface: Surface) {
            Log.i(TAG, "SURFACE DESTROYED surface=${surfaceIdentity(surface)}")
            if (previewSurface === surface) {
                detachSurface(surface)
                previewSurface = null
            }
        }
    }

    private val cameraCallback = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) {
            Log.i(TAG, "Device attached: $device")
            if (isSelected(device)) {
                helper?.selectDevice(device)
                Log.i(TAG, "USB permission requested by selectDevice")
            }
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            if (!isSelected(device)) return
            Log.i(TAG, "Permission granted / device opened: isFirstOpen=$isFirstOpen")
            helper?.openCamera()
        }

        override fun onCameraOpen(device: UsbDevice) {
            if (!isSelected(device)) return
            val cameraHelper = helper ?: return
            supportedSizes = cameraHelper.supportedSizeList.orEmpty()
            currentMode = cameraHelper.previewSize
            cameraOpened = true
            Log.i(TAG, "Camera open callback: device=${device.deviceName}")
            supportedSizes.forEachIndexed { index, size ->
                Log.i(
                    TAG,
                    "SIZE[$index]: exact=$size width=${size.width} height=${size.height} " +
                        "fps=${size.fps} fpsList=${size.fpsList} type=${size.type}",
                )
            }
            currentMode?.let { previewView?.setAspectRatio(it.width, it.height) }
            update(
                mutableState.value.copy(
                    status = UvcStatus.OPENING,
                    supportedSizes = supportedSizes.map { AndroidSize(it.width, it.height) },
                    activeResolution = currentMode?.let { AndroidSize(it.width, it.height) },
                    message = "USB camera opened.",
                    diagnostics = diagnostics(),
                )
            )
            installFrameCallback(cameraHelper)
            mainHandler.postDelayed(::initializeImageControls, CONTROL_DELAY_MS)
            if (logitechDiagnostic) {
                startLogitechProbeIfReady()
                return
            }
            try {
                Log.i(TAG, "startPreview call (official sample order)")
                cameraHelper.startPreview()
                previewStarted = true
                Log.i(TAG, "PREVIEW STARTED")
                attachSurfaceAfterPreview(cameraHelper)
                markPreviewActive()
            } catch (error: Throwable) {
                Log.e(TAG, "Default preview failed", error)
                attachSurfaceAfterPreview(cameraHelper)
                beginReportedSizeFallback(error)
            }
        }

        override fun onCameraClose(device: UsbDevice) {
            if (!isSelected(device)) return
            previewSurface?.let(::detachSurface)
            previewStarted = false
            uvcControl = null
        }

        override fun onDeviceClose(device: UsbDevice) = Unit

        override fun onDetach(device: UsbDevice) {
            if (!isSelected(device)) return
            releaseHelper()
            selectedDevice = null
            update(UvcCameraState(UvcStatus.DISCONNECTED, device.deviceId, message = "USB camera disconnected."))
        }

        override fun onCancel(device: UsbDevice) {
            if (isSelected(device)) markPermissionDenied(device.deviceId)
        }

        override fun onError(device: UsbDevice, error: CameraException) {
            if (!isSelected(device)) return
            Log.e(TAG, "CameraHelper nativeCode=${error.code}", error)
            updateError(error, error.code)
        }
    }

    fun open(device: UsbDevice) {
        if (closed) return
        if (selectedDevice?.deviceId == device.deviceId &&
            mutableState.value.status in setOf(UvcStatus.OPENING, UvcStatus.ACTIVE)
        ) return
        closeCamera()
        selectedDevice = device
        logitechDiagnostic = isLogitech(device)
        cameraOpened = false
        firstFrameReceived = false
        val permission = usbManager.hasPermission(device)
        if (logitechDiagnostic) {
            Log.i(
                TAG,
                "LOGITECH SELECTED: deviceName=${device.deviceName} vendorId=${device.vendorId} " +
                    "productId=${device.productId} permission=$permission",
            )
        }
        update(UvcCameraState(UvcStatus.OPENING, device.deviceId, message = "Opening USB camera..."))
        helper = CameraHelper().also {
            it.setStateCallback(cameraCallback)
            // Same path as the diagnostic Open Camera button. CameraHelper owns permission.
            it.selectDevice(device)
        }
    }

    fun markPermissionRequired(deviceId: Int) = update(mutableState.value.permissionRequired(deviceId))
    fun markPermissionDenied(deviceId: Int) = update(mutableState.value.permissionDenied(deviceId))

    fun attachPreview(view: UVCCameraTextureView) {
        if (previewView === view) return
        previewView?.setCallback(null)
        previewView = view
        view.setCallback(surfaceCallback)
        if (view.hasSurface()) {
            Log.i(TAG, "UVC VIEW REUSED surface=${surfaceIdentity(view.surface)} valid=${view.surface.isValid}")
            acceptCurrentSurface(view.surface, "view reused")
        }
    }

    fun detachPreview(view: UVCCameraTextureView) {
        if (previewView !== view) return
        previewSurface?.let(::detachSurface)
        previewSurface = null
        view.setCallback(null)
        previewView = null
    }

    fun setAutoExposure(enabled: Boolean) {
        val control = uvcControl ?: return
        if (!mutableState.value.controls.autoExposureSupported) return
        if (enabled) control.resetExposureTimeAbsolute()
        control.setExposureTimeAuto(enabled)
        publishControls(readControls(control))
    }

    fun setExposure(value: Int) = applyControl(UvcControlKind.EXPOSURE, value) {
        setExposureTimeAbsolute(value)
    }

    fun setBrightness(value: Int) = applyControl(UvcControlKind.BRIGHTNESS, value) {
        setBrightness(value)
    }

    fun resetImageControls() {
        val control = uvcControl ?: return
        val initial = initialControls
        if (initial.autoExposureSupported) control.setExposureTimeAuto(false)
        initial.exposure?.let { control.setExposureTimeAbsolute(it.initial) }
        initial.brightness?.let { control.setBrightness(it.initial) }
        initial.gain?.let { control.setGain(it.initial) }
        initial.contrast?.let { control.setContrast(it.initial) }
        initial.saturation?.let { control.setSaturation(it.initial) }
        if (initial.autoExposureSupported) control.setExposureTimeAuto(initial.autoExposure)
        publishControls(readControls(control))
    }

    fun onDetached(deviceId: Int) {
        if (selectedDevice?.deviceId != deviceId) return
        closeCamera()
        update(UvcCameraState(UvcStatus.DISCONNECTED, deviceId, message = "USB camera disconnected."))
    }

    fun closeCamera() {
        mainHandler.removeCallbacksAndMessages(null)
        previewSurface?.let(::detachSurface)
        runCatching { helper?.closeCamera() }
        releaseHelper()
        selectedDevice = null
        if (!closed) update(UvcCameraState())
    }

    private fun attachSurfaceAfterPreview(cameraHelper: CameraHelper) {
        val surface = previewSurface ?: previewView?.takeIf { it.hasSurface() }?.surface
        if (surface != null && surface.isValid) attachSurfaceOnce(cameraHelper, surface, rebound = false)
    }

    private fun installFrameCallback(cameraHelper: CameraHelper) {
        firstFrameReceived = false
        cameraHelper.setFrameCallback({ buffer ->
            if (!firstFrameReceived) {
                val byteCount = buffer.remaining()
                Log.i(TAG, "First frame callback: bytes=$byteCount exact=$currentMode")
                if (byteCount > 0) {
                    firstFrameReceived = true
                    if (logitechDiagnostic) {
                        attemptGeneration++
                        markPreviewActive()
                    }
                }
            }
        }, UVCCamera.PIXEL_FORMAT_NV21)
    }

    private fun markPreviewActive() {
        previewStarted = true
        update(
            mutableState.value.copy(
                status = UvcStatus.ACTIVE,
                message = "USB camera active.",
                diagnostics = diagnostics(previewStarted = true),
            )
        )
    }

    private fun beginReportedSizeFallback(firstError: Throwable) {
        retrySizes = supportedSizes.filter { it.width == 640 && it.height == 480 } +
            supportedSizes.filterNot { it.width == 640 && it.height == 480 }
        retryIndex = 0
        tryNextReportedSize(firstError)
    }

    private fun acceptCurrentSurface(surface: Surface, reason: String) {
        val oldSurface = previewSurface
        if (oldSurface !== null && oldSurface !== surface) detachSurface(oldSurface)
        previewSurface = surface
        if (!surface.isValid) return
        val cameraHelper = helper
        if (cameraOpened && previewStarted && cameraHelper != null) {
            attachSurfaceOnce(cameraHelper, surface, rebound = oldSurface !== null && oldSurface !== surface)
        } else if (logitechDiagnostic) {
            startLogitechProbeIfReady()
        }
        if (reason == "view reused" && oldSurface === surface && surfaceAttached) {
            Log.i(TAG, "PREVIEW ALREADY ACTIVE surface=${surfaceIdentity(surface)}")
        }
    }

    private fun attachSurfaceOnce(cameraHelper: CameraHelper, surface: Surface, rebound: Boolean) {
        if (previewSurface !== surface || !surface.isValid) return
        if (surfaceAttached) {
            Log.i(TAG, "PREVIEW ALREADY ACTIVE surface=${surfaceIdentity(surface)}")
            return
        }
        cameraHelper.addSurface(surface, false)
        surfaceAttached = true
        Log.i(TAG, "SURFACE ATTACHED surface=${surfaceIdentity(surface)} valid=${surface.isValid}")
        if (rebound) Log.i(TAG, "SURFACE REBOUND AFTER RECOMPOSITION surface=${surfaceIdentity(surface)}")
    }

    private fun detachSurface(surface: Surface) {
        if (previewSurface !== surface || !surfaceAttached) return
        runCatching { helper?.removeSurface(surface) }
            .onSuccess { Log.i(TAG, "SURFACE DETACHED surface=${surfaceIdentity(surface)}") }
            .onFailure { Log.e(TAG, "SURFACE DETACH FAILED surface=${surfaceIdentity(surface)}", it) }
        surfaceAttached = false
    }

    private fun startLogitechProbeIfReady() {
        val surface = previewSurface ?: previewView?.takeIf { it.hasSurface() }?.surface ?: return
        if (!cameraOpened || !surface.isValid || retrySizes.isNotEmpty() || firstFrameReceived) return
        retrySizes = supportedSizes.sortedWith(compareBy<UvcSize>(::preferredRank))
        retryIndex = 0
        Log.i(
            TAG,
            "Logitech probe ready: permission=${selectedDevice?.let(usbManager::hasPermission)} " +
                "surface=${surfaceIdentity(surface)} valid=${surface.isValid} modes=${retrySizes.size}",
        )
        tryNextLogitechMode(null)
    }

    private fun tryNextLogitechMode(previousError: Throwable?) {
        val cameraHelper = helper ?: return
        val surface = previewSurface
        val exactSize = retrySizes.getOrNull(retryIndex++)
        if (exactSize == null) {
            val error = previousError ?: IllegalStateException("No reported Logitech mode produced a frame")
            Log.e(TAG, "Every reported Logitech mode was attempted; no frame received", error)
            updateError(error, (error as? CameraException)?.code, unsupported = true)
            return
        }
        if (surface == null || !surface.isValid) {
            val error = IllegalStateException("Surface invalid before mode attempt: $exactSize")
            Log.e(TAG, "Logitech failure stage=surface", error)
            updateError(error, null)
            return
        }

        currentMode = exactSize
        firstFrameReceived = false
        val attempt = ++attemptGeneration
        Log.i(
            TAG,
            "ATTEMPT ${retryIndex}/${retrySizes.size}: exact=$exactSize width=${exactSize.width} " +
                "height=${exactSize.height} fps=${exactSize.fps} fpsList=${exactSize.fpsList} type=${exactSize.type}",
        )
        var stage = "stopPreview"
        try {
            runCatching { cameraHelper.stopPreview() }
            previewStarted = false
            if (surfaceAttached) {
                stage = "removeSurface"
                detachSurface(surface)
            }
            stage = "setPreviewSize"
            cameraHelper.setPreviewSize(exactSize)
            Log.i(TAG, "setPreviewSize result=success exact=$exactSize")
            previewView?.setAspectRatio(exactSize.width, exactSize.height)
            stage = "startPreview"
            cameraHelper.startPreview()
            previewStarted = true
            Log.i(TAG, "startPreview result=success exact=$exactSize")
            stage = "addSurface"
            attachSurfaceOnce(cameraHelper, surface, rebound = false)
            mainHandler.postDelayed({
                if (attempt == attemptGeneration && !firstFrameReceived) {
                    val timeout = IllegalStateException(
                        "First-frame timeout after ${FIRST_FRAME_TIMEOUT_MS}ms for exact Size $exactSize"
                    )
                    Log.e(TAG, "Logitech failure stage=firstFrame bytes=0", timeout)
                    tryNextLogitechMode(timeout)
                }
            }, FIRST_FRAME_TIMEOUT_MS)
        } catch (error: Throwable) {
            Log.e(TAG, "Logitech failure stage=$stage exact=$exactSize", error)
            mainHandler.post { tryNextLogitechMode(error) }
        }
    }

    private fun preferredRank(size: UvcSize): Int = when (size.width to size.height) {
        1280 to 720 -> 0
        640 to 480 -> 1
        800 to 600 -> 2
        320 to 240 -> 3
        else -> 4
    }

    private fun tryNextReportedSize(lastError: Throwable) {
        val cameraHelper = helper ?: return
        val exactSize = retrySizes.getOrNull(retryIndex++)
        if (exactSize == null) {
            updateError(lastError, (lastError as? CameraException)?.code, unsupported = true)
            return
        }
        currentMode = exactSize
        Log.i(TAG, "Exact Size object selected: $exactSize type=${exactSize.type}")
        try {
            cameraHelper.stopPreview()
            previewStarted = false
            cameraHelper.setPreviewSize(exactSize)
            previewView?.setAspectRatio(exactSize.width, exactSize.height)
            cameraHelper.startPreview()
            previewStarted = true
            attachSurfaceAfterPreview(cameraHelper)
            markPreviewActive()
        } catch (error: Throwable) {
            Log.e(TAG, "Exact reported Size failed: $exactSize", error)
            tryNextReportedSize(error)
        }
    }

    private fun initializeImageControls() {
        val cameraHelper = helper ?: return
        if (!cameraHelper.isCameraOpened) return
        runCatching {
            val control = cameraHelper.uvcControl ?: return
            uvcControl = control
            val controls = readControls(control)
            initialControls = controls
            publishControls(controls)
        }.onFailure { Log.e(TAG, "Unable to read UVC controls", it) }
    }

    private fun readControls(control: UVCControl) = UvcImageControls(
        autoExposureSupported = control.isAutoExposureModeEnable,
        autoExposure = control.isAutoExposureModeEnable && control.isExposureTimeAuto,
        exposure = if (control.isExposureTimeAbsoluteEnable) controlValue(
            control.updateExposureTimeAbsoluteLimit(), control.exposureTimeAbsolute
        ) else null,
        brightness = if (control.isBrightnessEnable) controlValue(
            control.updateBrightnessLimit(), control.brightness
        ) else null,
        gain = if (control.isGainEnable) controlValue(control.updateGainLimit(), control.gain) else null,
        contrast = if (control.isContrastEnable) controlValue(control.updateContrastLimit(), control.contrast) else null,
        saturation = if (control.isSaturationEnable) controlValue(
            control.updateSaturationLimit(), control.saturation
        ) else null,
    )

    private fun controlValue(limits: IntArray?, current: Int): UvcControlValue? =
        limits?.takeIf { it.size >= 3 && it[1] >= it[0] }?.let {
            UvcControlValue(it[0], it[1], it[2], current, current)
        }

    private inline fun applyControl(
        kind: UvcControlKind,
        value: Int,
        setter: UVCControl.() -> Unit,
    ) {
        val control = uvcControl ?: return
        val range = when (kind) {
            UvcControlKind.EXPOSURE -> mutableState.value.controls.exposure
            UvcControlKind.BRIGHTNESS -> mutableState.value.controls.brightness
        } ?: return
        if (value !in range.minimum..range.maximum) return
        control.setter()
        publishControls(readControls(control))
    }

    private fun publishControls(controls: UvcImageControls) = update(mutableState.value.copy(controls = controls))

    private fun diagnostics(previewStarted: Boolean = mutableState.value.diagnostics.previewStarted) = UvcDiagnostics(
        supportedModeCount = supportedSizes.size,
        supportedModes = supportedSizes.map { it.toString() },
        currentMode = currentMode?.toString(),
        currentFormat = currentMode?.type,
        previewStarted = previewStarted,
        lastException = mutableState.value.diagnostics.lastException,
    )

    private fun releaseHelper() {
        runCatching { helper?.setStateCallback(null) }
        runCatching { helper?.release() }
        helper = null
        surfaceAttached = false
        cameraOpened = false
        previewStarted = false
        retrySizes = emptyList()
        uvcControl = null
    }

    private fun isSelected(device: UsbDevice) = selectedDevice?.deviceId == device.deviceId

    private fun isLogitech(device: UsbDevice): Boolean =
        device.vendorId == LOGITECH_VENDOR_ID ||
            device.manufacturerName?.contains("Logitech", ignoreCase = true) == true ||
            device.productName?.contains("C270", ignoreCase = true) == true

    private fun surfaceIdentity(surface: Surface): String =
        "Surface@${System.identityHashCode(surface).toString(16)}"

    private fun updateError(error: Throwable, nativeCode: Int?, unsupported: Boolean = false) = update(
        mutableState.value.copy(
            status = if (unsupported) UvcStatus.UNSUPPORTED_FORMAT else UvcStatus.ERROR,
            activeResolution = null,
            message = error.message ?: "USB camera preview failed.",
            diagnostics = diagnostics().copy(lastException = "$error (native code=${nativeCode ?: "unavailable"}, surfaceAttached=$surfaceAttached)"),
        )
    )

    private fun update(value: UvcCameraState) {
        mutableState.value = value
        onStateChanged(value)
    }

    override fun close() {
        if (closed) return
        closeCamera()
        closed = true
    }

    private enum class UvcControlKind { EXPOSURE, BRIGHTNESS }

    private companion object {
        const val TAG = "UvcPreviewPipeline"
        const val CONTROL_DELAY_MS = 500L
        const val FIRST_FRAME_TIMEOUT_MS = 2500L
        const val LOGITECH_VENDOR_ID = 0x046d
    }
}

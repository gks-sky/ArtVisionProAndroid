package com.gkssky.artvisionpro.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gkssky.artvisionpro.camera.uvc.UvcCameraController
import com.gkssky.artvisionpro.camera.uvc.UvcCameraState
import com.gkssky.artvisionpro.camera.uvc.UvcStatus
import com.serenegiant.widget.UVCCameraTextureView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CameraController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val discovery: CameraDiscovery = CameraDiscovery(context),
    private val usbDetector: UsbCameraDetector = UsbCameraDetector(context),
) : DefaultLifecycleObserver, AutoCloseable {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val mutableState = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = mutableState.asStateFlow()
    private val uvcController = UvcCameraController(appContext, ::onUvcStateChanged)

    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var boundCameraId: String? = null
    private var isStarted = false
    private var closed = false
    private var usbActivationJob: Job? = null
    private var usbActivationGeneration = 0

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        updatePermissionState()
    }

    override fun onStart(owner: LifecycleOwner) {
        isStarted = true
        usbDetector.start(::onUsbEvent)
        refreshCameras()
    }

    override fun onStop(owner: LifecycleOwner) {
        isStarted = false
        cancelUsbActivation()
        unbindCamera()
        uvcController.closeCamera()
        usbDetector.stop()
    }

    fun attachPreview(view: PreviewView) {
        if (previewView === view) return
        previewView = view
        bindSelectedCamera()
    }

    fun detachPreview(view: PreviewView) {
        if (previewView !== view) return
        previewView = null
        unbindCamera()
    }

    fun attachUvcPreview(view: UVCCameraTextureView) = uvcController.attachPreview(view)

    fun detachUvcPreview(view: UVCCameraTextureView) = uvcController.detachPreview(view)

    fun setUvcAutoExposure(enabled: Boolean) = uvcController.setAutoExposure(enabled)

    fun setUvcExposure(value: Int) = uvcController.setExposure(value)

    fun setUvcBrightness(value: Int) = uvcController.setBrightness(value)

    fun resetUvcImageControls() = uvcController.resetImageControls()

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            unbindCamera()
            mutableState.value = mutableState.value.permissionDenied()
            return
        }
        refreshCameras()
    }

    fun refreshCameras() {
        if (closed) return
        if (!hasCameraPermission()) {
            updatePermissionState()
            return
        }
        mutableState.value = mutableState.value.copy(status = CameraStatus.DISCOVERING, errorMessage = null)
        val usbDevices = usbDetector.detect()
        val cameras = discovery.discover() + usbDevices.map(UsbCameraDeviceInfo::toCameraDeviceInfo)
        val previous = mutableState.value.selectedCamera
        val removedActive = previous != null && cameras.none { it.id == previous.id }
        val selected = if (removedActive) null else {
            previous?.let { old -> cameras.firstOrNull { it.id == old.id } }
                ?: CameraSelectionPolicy.preferred(cameras)
        }
        mutableState.value = mutableState.value.copy(
            status = when {
                removedActive -> CameraStatus.DISCONNECTED
                cameras.isEmpty() -> CameraStatus.IDLE
                else -> CameraStatus.IDLE
            },
            cameras = cameras,
            selectedCamera = selected,
            activeResolution = null,
            usbDevices = usbDevices,
            errorMessage = when {
                removedActive -> "The active camera was disconnected."
                cameras.isEmpty() -> "No Camera2 cameras are available."
                else -> null
            },
        )
        if (selected?.sourceType == CameraSourceType.USB_UVC && usbActivationJob?.isActive != true) {
            openSelectedUvcCamera(selected)
        } else {
            bindSelectedCamera()
        }
    }

    fun selectCamera(cameraId: String) {
        val camera = mutableState.value.cameras.firstOrNull { it.id == cameraId } ?: return
        if (!shouldStartCamera(mutableState.value, boundCameraId, camera)) return
        unbindCamera()
        uvcController.closeCamera()
        cancelUsbActivation()
        mutableState.value = mutableState.value.cameraSelected(camera)
        if (camera.sourceType == CameraSourceType.USB_UVC) {
            beginFreshUsbSelection(camera)
        } else {
            bindSelectedCamera()
        }
    }

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun bindSelectedCamera() {
        val view = previewView ?: return
        val selected = mutableState.value.selectedCamera ?: return
        if (selected.sourceType != CameraSourceType.CAMERA_X) return
        if (!isStarted || !hasCameraPermission() || boundCameraId == selected.id || closed) return
        mutableState.value = mutableState.value.copy(status = CameraStatus.OPENING, errorMessage = null)
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            runCatching {
                val cameraProvider = future.get()
                if (!isStarted || previewView !== view || mutableState.value.selectedCamera?.id != selected.id) return@addListener
                provider = cameraProvider
                cameraProvider.unbindAll()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider { request ->
                    mutableState.value = mutableState.value.copy(
                        status = CameraStatus.ACTIVE,
                        activeResolution = request.resolution,
                        errorMessage = null,
                    )
                    view.surfaceProvider.onSurfaceRequested(request)
                }
                val selector = CameraSelector.Builder()
                    .addCameraFilter { cameraInfos ->
                        cameraInfos.filter { Camera2CameraInfo.from(it).cameraId == selected.id }
                    }
                    .build()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                boundCameraId = selected.id
            }.onFailure { error ->
                boundCameraId = null
                mutableState.value = mutableState.value.copy(
                    status = CameraStatus.ERROR,
                    activeResolution = null,
                    errorMessage = error.message ?: "Unable to open the selected camera.",
                )
            }
        }, mainExecutor)
    }

    private fun unbindCamera() {
        provider?.unbindAll()
        boundCameraId = null
        mutableState.value = mutableState.value.copy(activeResolution = null)
    }

    private fun onUsbEvent(event: UsbCameraEvent) {
        var shouldRefresh = true
        when (event) {
            is UsbCameraEvent.Connected -> Unit
            is UsbCameraEvent.Removed -> {
                uvcController.onDetached(event.deviceId)
                mutableState.value = mutableState.value.usbDeviceRemoved(event.deviceId)
            }
            is UsbCameraEvent.PermissionResult -> {
                shouldRefresh = false
                val selected = mutableState.value.selectedCamera
                if (selected?.sourceType == CameraSourceType.USB_UVC && selected.usbDeviceId == event.device.deviceId) {
                    if (event.granted) {
                        usbDetector.findDevice(event.device.deviceId)?.let(uvcController::open)
                    } else {
                        uvcController.markPermissionDenied(event.device.deviceId)
                    }
                }
                mutableState.value = mutableState.value.copy(usbDevices = usbDetector.detect())
            }
        }
        if (shouldRefresh) refreshCameras()
    }

    private fun openSelectedUvcCamera(camera: CameraDeviceInfo) {
        val deviceId = camera.usbDeviceId ?: return
        val device = usbDetector.findDevice(deviceId)
        if (device == null) {
            uvcController.onDetached(deviceId)
        } else {
            // CameraHelper.selectDevice owns the proven diagnostic permission flow.
            uvcController.open(device)
        }
    }

    private fun beginFreshUsbSelection(staleSelection: CameraDeviceInfo) {
        val generation = ++usbActivationGeneration
        usbActivationJob?.cancel()
        usbActivationJob = lifecycleOwner.lifecycleScope.launch {
            Log.i(
                TAG,
                "USB selection refresh begin generation=$generation deviceId=${staleSelection.usbDeviceId} " +
                    "vid=${staleSelection.vendorId} pid=${staleSelection.productId}",
            )
            repeat(USB_DISCOVERY_ATTEMPTS) { attempt ->
                if (!isStarted || closed || generation != usbActivationGeneration) return@launch
                val refreshedUsb = usbDetector.detect()
                val refreshedCameras = discovery.discover() + refreshedUsb.map(UsbCameraDeviceInfo::toCameraDeviceInfo)
                val device = usbDetector.resolveDevice(
                    staleSelection.usbDeviceId,
                    staleSelection.vendorId,
                    staleSelection.productId,
                )
                val resolvedCamera = device?.let { resolved ->
                    refreshedCameras.firstOrNull {
                        it.sourceType == CameraSourceType.USB_UVC && it.usbDeviceId == resolved.deviceId
                    }
                }
                mutableState.value = mutableState.value.copy(
                    cameras = refreshedCameras,
                    usbDevices = refreshedUsb,
                    selectedCamera = resolvedCamera ?: staleSelection,
                    status = CameraStatus.OPENING,
                    errorMessage = null,
                )
                if (device != null && resolvedCamera != null) {
                    val selectedNow = mutableState.value.selectedCamera
                    if (selectedNow?.sourceType != CameraSourceType.USB_UVC ||
                        selectedNow.vendorId != staleSelection.vendorId ||
                        selectedNow.productId != staleSelection.productId
                    ) return@launch
                    Log.i(
                        TAG,
                        "USB selection resolved generation=$generation attempt=${attempt + 1} " +
                            "deviceId=${device.deviceId} name=${device.deviceName} " +
                            "permission=${usbDetector.hasPermission(device.deviceId)}",
                    )
                    uvcController.open(device)
                    return@launch
                }
                Log.i(TAG, "USB discovery not ready generation=$generation attempt=${attempt + 1}")
                delay(USB_DISCOVERY_RETRY_MS)
            }
            if (generation == usbActivationGeneration) {
                mutableState.value = mutableState.value.copy(
                    status = CameraStatus.DISCONNECTED,
                    activeResolution = null,
                    errorMessage = "Selected USB camera is no longer available.",
                )
                Log.e(TAG, "USB selection refresh exhausted generation=$generation")
            }
        }
    }

    private fun cancelUsbActivation() {
        usbActivationGeneration++
        usbActivationJob?.cancel()
        usbActivationJob = null
    }

    private fun onUvcStateChanged(uvcState: UvcCameraState) {
        val selected = mutableState.value.selectedCamera
        if (selected?.sourceType != CameraSourceType.USB_UVC || selected.usbDeviceId != uvcState.deviceId) return
        val supported = uvcState.supportedSizes
        mutableState.value = mutableState.value.copy(
            status = when (uvcState.status) {
                UvcStatus.IDLE -> CameraStatus.IDLE
                UvcStatus.PERMISSION_REQUIRED -> CameraStatus.PERMISSION_REQUIRED
                UvcStatus.OPENING -> CameraStatus.OPENING
                UvcStatus.ACTIVE -> CameraStatus.ACTIVE
                UvcStatus.DISCONNECTED -> CameraStatus.DISCONNECTED
                UvcStatus.UNSUPPORTED_FORMAT -> CameraStatus.UNSUPPORTED_FORMAT
                UvcStatus.ERROR -> CameraStatus.ERROR
            },
            selectedCamera = if (supported.isEmpty()) selected else selected.copy(supportedPreviewSizes = supported),
            activeResolution = uvcState.activeResolution,
            errorMessage = uvcState.message,
            uvcDiagnostics = uvcState.diagnostics,
            uvcImageControls = uvcState.controls,
        )
    }

    private fun updatePermissionState() {
        mutableState.value = mutableState.value.copy(
            status = if (hasCameraPermission()) CameraStatus.IDLE else CameraStatus.PERMISSION_REQUIRED,
            errorMessage = null,
        )
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    override fun close() {
        if (closed) return
        closed = true
        cancelUsbActivation()
        unbindCamera()
        uvcController.close()
        usbDetector.stop()
        lifecycleOwner.lifecycle.removeObserver(this)
    }

    private companion object {
        const val TAG = "UsbCameraSelection"
        const val USB_DISCOVERY_ATTEMPTS = 5
        const val USB_DISCOVERY_RETRY_MS = 100L
    }
}

internal fun CameraUiState.cameraSelected(camera: CameraDeviceInfo): CameraUiState = copy(
    status = CameraStatus.OPENING,
    selectedCamera = camera,
    activeResolution = null,
    errorMessage = null,
)

internal fun shouldStartCamera(
    state: CameraUiState,
    boundCameraId: String?,
    requested: CameraDeviceInfo,
): Boolean {
    if (state.selectedCamera?.id != requested.id) return true
    return when (requested.sourceType) {
        CameraSourceType.CAMERA_X -> boundCameraId != requested.id
        CameraSourceType.USB_UVC -> state.status !in setOf(CameraStatus.OPENING, CameraStatus.ACTIVE)
    }
}

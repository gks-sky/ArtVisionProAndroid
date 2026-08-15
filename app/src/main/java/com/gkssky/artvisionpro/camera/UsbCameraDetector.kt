package com.gkssky.artvisionpro.camera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat

data class UsbCameraDeviceInfo(
    val deviceId: Int,
    val productName: String?,
    val manufacturer: String?,
    val vendorId: Int,
    val productId: Int,
    val hasPermission: Boolean,
)

sealed interface UsbCameraEvent {
    data class Connected(val device: UsbCameraDeviceInfo) : UsbCameraEvent
    data class Removed(val deviceId: Int) : UsbCameraEvent
    data class PermissionResult(val device: UsbCameraDeviceInfo, val granted: Boolean) : UsbCameraEvent
}

class UsbCameraDetector(context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val permissionAction = "${appContext.packageName}.USB_PERMISSION"
    private var listener: ((UsbCameraEvent) -> Unit)? = null
    private var isRegistered = false
    private val permissionRequests = mutableSetOf<Int>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.usbDevice() ?: return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (device.isVideoDevice()) {
                    listener?.invoke(UsbCameraEvent.Connected(device.toInfo()))
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> if (device.isVideoDevice()) {
                    permissionRequests.remove(device.deviceId)
                    listener?.invoke(UsbCameraEvent.Removed(device.deviceId))
                }
                permissionAction -> if (device.isVideoDevice()) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    permissionRequests.remove(device.deviceId)
                    listener?.invoke(UsbCameraEvent.PermissionResult(device.toInfo(), granted))
                }
            }
        }
    }

    fun detect(): List<UsbCameraDeviceInfo> =
        usbManager.deviceList.values.filter { it.isVideoDevice() }.map { it.toInfo() }

    fun findDevice(deviceId: Int): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId && it.isVideoDevice() }

    fun resolveDevice(deviceId: Int?, vendorId: Int?, productId: Int?): UsbDevice? {
        val current = usbManager.deviceList.values.filter { it.isVideoDevice() }
        return current.firstOrNull {
            it.deviceId == deviceId &&
                (vendorId == null || it.vendorId == vendorId) &&
                (productId == null || it.productId == productId)
        } ?: current.firstOrNull {
            vendorId != null && productId != null &&
                it.vendorId == vendorId && it.productId == productId
        }
    }

    fun hasPermission(deviceId: Int): Boolean = findDevice(deviceId)?.let(usbManager::hasPermission) == true

    fun start(onEvent: (UsbCameraEvent) -> Unit) {
        listener = onEvent
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(permissionAction)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isRegistered = true
    }

    fun stop() {
        if (isRegistered) appContext.unregisterReceiver(receiver)
        isRegistered = false
        listener = null
    }

    fun requestPermission(deviceId: Int) {
        val device = usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId } ?: return
        if (usbManager.hasPermission(device) || !permissionRequests.add(deviceId)) return
        val intent = Intent(permissionAction).setPackage(appContext.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            deviceId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        usbManager.requestPermission(device, pendingIntent)
    }

    private fun UsbDevice.toInfo() = UsbCameraDeviceInfo(
        deviceId = deviceId,
        productName = productName,
        manufacturer = manufacturerName,
        vendorId = vendorId,
        productId = productId,
        hasPermission = usbManager.hasPermission(this),
    )

    companion object {
        internal fun isVideoClass(deviceClass: Int, interfaceClasses: List<Int>): Boolean =
            deviceClass == UsbConstants.USB_CLASS_VIDEO || UsbConstants.USB_CLASS_VIDEO in interfaceClasses

        private fun UsbDevice.isVideoDevice(): Boolean = isVideoClass(
            deviceClass,
            (0 until interfaceCount).map { getInterface(it).interfaceClass },
        )

        @Suppress("DEPRECATION")
        private fun Intent.usbDevice(): UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }
}

internal fun UsbCameraDeviceInfo.toCameraDeviceInfo() = CameraDeviceInfo(
    id = "uvc:$deviceId",
    displayName = "USB Camera  ${productName ?: "Camera $deviceId"}",
    sourceType = CameraSourceType.USB_UVC,
    lensFacing = CameraLensFacing.EXTERNAL,
    isExternal = true,
    usbDeviceId = deviceId,
    vendorId = vendorId,
    productId = productId,
    supportedPreviewSizes = emptyList(),
)

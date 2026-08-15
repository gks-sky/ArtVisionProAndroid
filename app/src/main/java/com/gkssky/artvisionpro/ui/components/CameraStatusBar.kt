package com.gkssky.artvisionpro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gkssky.artvisionpro.camera.CameraUiState

@Composable
fun CameraStatusBar(state: CameraUiState, modifier: Modifier = Modifier) {
    val camera = state.selectedCamera
    val resolution = state.activeResolution?.let { "${it.width} × ${it.height}" } ?: "—"
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                StatusItem("Camera", camera?.displayName ?: "None")
                StatusItem("ID", camera?.id ?: "—")
                StatusItem("Facing", camera?.lensFacing?.label ?: "—")
                StatusItem("Resolution", resolution)
                StatusItem("State", state.status.name.lowercase().replaceFirstChar(Char::uppercase))
            }
            state.usbDevices.forEach { usb ->
                Text(
                    text = "USB: ${usb.productName ?: "Camera"} • ${usb.manufacturer ?: "Unknown manufacturer"} • VID ${usb.vendorId} • PID ${usb.productId} • Permission ${if (usb.hasPermission) "granted" else "not granted"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

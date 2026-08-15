package com.gkssky.artvisionpro.ui

import androidx.compose.runtime.Composable
import com.gkssky.artvisionpro.ui.screens.CameraScreen
import com.gkssky.artvisionpro.ui.theme.ArtVisionProAndroidTheme
import com.gkssky.artvisionpro.reference.ReferenceImageController

/** Root composable for application-wide theming and top-level UI structure. */
@Composable
fun ArtVisionProApp(referenceImageController: ReferenceImageController) {
    ArtVisionProAndroidTheme(darkTheme = true, dynamicColor = false) {
        CameraScreen(referenceImageController)
    }
}

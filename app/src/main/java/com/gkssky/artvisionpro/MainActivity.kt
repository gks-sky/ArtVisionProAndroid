package com.gkssky.artvisionpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gkssky.artvisionpro.ui.ArtVisionProApp
import com.gkssky.artvisionpro.reference.ReferenceImageController

class MainActivity : ComponentActivity() {
    private lateinit var referenceImageController: ReferenceImageController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        referenceImageController = ReferenceImageController(applicationContext)
        enableEdgeToEdge()
        setContent { ArtVisionProApp(referenceImageController) }
    }

    override fun onDestroy() {
        referenceImageController.close()
        super.onDestroy()
    }
}

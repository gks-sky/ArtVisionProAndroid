package com.gkssky.artvisionpro.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gkssky.artvisionpro.ui.theme.ArtVisionProAndroidTheme

/** Initial screen. Feature UI will be composed here as the application grows. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Text(text = "Hello Android!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ArtVisionProAndroidTheme {
        HomeScreen()
    }
}

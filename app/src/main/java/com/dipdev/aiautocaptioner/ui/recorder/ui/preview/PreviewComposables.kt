package com.dipdev.aiautocaptioner.ui.recorder.ui.preview

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.dipdev.aiautocaptioner.ui.recorder.model.BackgroundState

@Composable
fun CameraPreview(
    textureView: TextureView,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color(0xFF101010))) {
        AndroidView(
            factory = { textureView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun FacelessPreview(
    selectedBackground: BackgroundState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (selectedBackground) {
            is BackgroundState.SolidColor -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(selectedBackground.color)
                )
            }
            is BackgroundState.Gradient -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = selectedBackground.colors
                            )
                        )
                )
            }
        }
    }
}

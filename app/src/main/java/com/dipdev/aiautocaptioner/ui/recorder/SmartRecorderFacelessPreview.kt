package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

@Composable
fun SmartRecorderFacelessPreview(
    selectedBackground: BackgroundState
) {
    val bgModifier = Modifier.fillMaxSize()
    when (selectedBackground) {
        is BackgroundState.SolidColor -> {
            Box(modifier = bgModifier.background(selectedBackground.color))
        }
        is BackgroundState.Gradient -> {
            Box(modifier = bgModifier.background(Brush.linearGradient(selectedBackground.colors)))
        }
    }
}

// Video Background preview removed



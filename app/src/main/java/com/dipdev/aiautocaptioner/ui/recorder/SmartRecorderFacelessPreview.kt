package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun SmartRecorderFacelessPreview(
    selectedBackground: BackgroundState,
    micGranted: Boolean,
    isRecording: Boolean,
    onRequestMic: () -> Unit,
    onOpenSettings: () -> Unit,
    onTransformUpdate: (scale: Float, offsetX: Float, offsetY: Float) -> Unit
) {
    val bgModifier = Modifier.fillMaxSize()
    when (val bg = selectedBackground) {
        is BackgroundState.SolidColor -> Box(modifier = bgModifier.background(bg.color))
        is BackgroundState.Gradient -> Box(modifier = bgModifier.background(Brush.linearGradient(bg.colors)))
        is BackgroundState.ImageBitmap -> {
            var scale by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(bg.scale) }
            var offsetX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(bg.offsetX) }
            var offsetY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(bg.offsetY) }

            androidx.compose.runtime.LaunchedEffect(bg.bitmap) {
                scale = bg.scale
                offsetX = bg.offsetX
                offsetY = bg.offsetY
            }

            Image(
                bitmap = bg.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = bgModifier
                    .pointerInput(isRecording, bg.bitmap) {
                        if (isRecording) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale *= zoom
                            offsetX += pan.x
                            offsetY += pan.y
                            onTransformUpdate(scale, offsetX, offsetY)
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }

    if (!micGranted) {
        PermissionOverlay(
            message = "Microphone access is required for Faceless Mode.",
            onRequest = onRequestMic,
            onOpenSettings = onOpenSettings
        )
    }
}

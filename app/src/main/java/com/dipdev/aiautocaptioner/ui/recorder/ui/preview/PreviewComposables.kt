package com.dipdev.aiautocaptioner.ui.recorder.ui.preview

import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberCoroutineScope
import com.dipdev.aiautocaptioner.ui.recorder.model.BackgroundState
import kotlinx.coroutines.launch

@Composable
fun CameraPreview(
    textureView: TextureView,
    maxZoomRatio: Float,
    onZoomChanged: (Float) -> Unit,
    onTapToFocus: (x: Float, y: Float, viewWidth: Int, viewHeight: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusAlpha = remember { Animatable(1f) }
    val focusScale = remember { Animatable(0.5f) }
    val focusCoroutineScope = rememberCoroutineScope()

    var currentZoom by remember { mutableStateOf(1f) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { textureView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val tv = textureView
                        onTapToFocus(offset.x, offset.y, tv.width, tv.height)
                        focusPoint = offset
                        focusCoroutineScope.launch {
                            focusAlpha.snapTo(1f)
                            focusScale.snapTo(0.5f)
                        }
                    }
                }
                .pointerInput(maxZoomRatio) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newZoom = (currentZoom * zoom).coerceIn(1f, maxZoomRatio)
                        currentZoom = newZoom
                        onZoomChanged(newZoom)
                    }
                }
        )

        focusPoint?.let { point ->
            LaunchedEffect(point) {
                launch {
                    focusScale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    focusAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 800, easing = LinearEasing)
                    )
                    focusPoint = null
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = 40.dp.toPx() * focusScale.value
                drawCircle(
                    color = Color.White.copy(alpha = focusAlpha.value),
                    radius = radius,
                    center = point,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = focusAlpha.value * 0.3f),
                    radius = 4.dp.toPx(),
                    center = point
                )
            }
        }
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

package com.dipdev.aiautocaptioner.ui.recorder

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun SmartRecorderFacelessPreview(
    selectedBackground: BackgroundState,
    isRecording: Boolean,
    onTransformUpdate: (scale: Float, offsetX: Float, offsetY: Float) -> Unit
) {
    val bgModifier = Modifier.fillMaxSize()
    when (val bg = selectedBackground) {
        is BackgroundState.SolidColor -> {
            Box(modifier = bgModifier.background(bg.color)) {
                FacelessIdleHint(isRecording = isRecording)
            }
        }
        is BackgroundState.Gradient -> {
            Box(modifier = bgModifier.background(Brush.linearGradient(bg.colors))) {
                FacelessIdleHint(isRecording = isRecording)
            }
        }
        is BackgroundState.ImageBitmap -> {
            // BoxWithConstraints gives us the viewport size in pixels for clamp math
            BoxWithConstraints(modifier = bgModifier) {
                val viewportWidthPx = constraints.maxWidth.toFloat()
                val viewportHeightPx = constraints.maxHeight.toFloat()

                var scale by remember { mutableStateOf(bg.scale) }
                var offsetX by remember { mutableStateOf(bg.offsetX) }
                var offsetY by remember { mutableStateOf(bg.offsetY) }

                LaunchedEffect(bg.bitmap) {
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
                                // Clamp scale: never allow zooming out past 1x (which would expose
                                // the black surface behind the image) or in past 8x
                                val newScale = (scale * zoom).coerceIn(1f, 8f)
                                // Max pan in each axis = how much the scaled image overflows
                                // the viewport on that side
                                val maxPanX = (newScale - 1f) * viewportWidthPx / 2f
                                val maxPanY = (newScale - 1f) * viewportHeightPx / 2f
                                val newOffsetX = (offsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                                val newOffsetY = (offsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                                scale = newScale
                                offsetX = newOffsetX
                                offsetY = newOffsetY
                                onTransformUpdate(scale, offsetX, offsetY)
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    // Crop fills the entire frame — matches the recorded output.
                    // The old ContentScale.Fit caused black letterbox bars in preview
                    // that were absent from the actual recorded video file.
                    contentScale = ContentScale.Crop
                )
            }
        }
        is BackgroundState.VideoUri -> {
            VideoBackgroundPreview(
                uri = bg.uri,
                modifier = bgModifier
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ExoPlayer looping video background preview
// ---------------------------------------------------------------------------

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoBackgroundPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f // muted — audio is captured via mic, not the video file
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(uri) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------
// Subtle idle hint overlay — only shown when not recording, fades on start
// ---------------------------------------------------------------------------

@Composable
private fun FacelessIdleHint(isRecording: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (isRecording) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "idleHintAlpha"
    )

    if (alpha == 0f) return

    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.15f)
                        .background(Color.White, CircleShape)
                )
                Text(text = "🎙", fontSize = 34.sp)
            }
            Text(
                text = "Faceless Recording",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Your voice is the video",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

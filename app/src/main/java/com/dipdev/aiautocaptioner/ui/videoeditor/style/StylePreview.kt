package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.engine.CaptionRenderer
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun StylePreview(
    style: CaptionStyleEntity,
    videoPath: String?,
    segments: List<CaptionSegmentEntity>,
    wordsMap: Map<String, List<CaptionWordEntity>>,
    durationMs: Long,
    exoPlayer: ExoPlayer?,
    onPositionXChange: (Float) -> Unit,
    onPositionYChange: (Float) -> Unit,
    onSeek: (Long) -> Unit
) {
    if (videoPath == null || exoPlayer == null) return

    var currentPositionMs by remember { mutableLongStateOf(0L) }

    // Poll playback position on every frame when playing; slow-poll when paused
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                // Sync position every display frame while playing
                withFrameMillis {
                    currentPositionMs = exoPlayer.currentPosition
                }
            } else {
                // Paused — one update then sleep to avoid constant recomposition
                currentPositionMs = exoPlayer.currentPosition
                delay(250.milliseconds)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Video + caption overlay ──────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        // Use default resizeMode which is FIT. For 9:16 video on 9:16 screen, it fills perfectly.
                    }
                },
                update = { view -> 
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer 
                    }
                },
                modifier = Modifier.matchParentSize()
            )

            val currentPosY by rememberUpdatedState(style.positionY)
            val currentPosX by rememberUpdatedState(style.positionX)
            val context = LocalContext.current

            Canvas(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val heightPixels = size.height.toFloat()
                        val newRatio = (currentPosY + (dragAmount / heightPixels)).coerceIn(0.05f, 0.95f)
                        onPositionYChange(newRatio)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val widthPixels = size.width.toFloat()
                        val newRatio = (currentPosX + (dragAmount / widthPixels)).coerceIn(0.05f, 0.95f)
                        onPositionXChange(newRatio)
                    }
                }
            ) {
                drawIntoCanvas { canvas ->
                    CaptionRenderer.draw(
                        context = context,
                        canvas = canvas.nativeCanvas,
                        currentPositionMs = currentPositionMs,
                        videoWidth = size.width.toInt(),
                        videoHeight = size.height.toInt(),
                        style = style,
                        segments = segments,
                        wordsMap = wordsMap
                    )
                }
            }
        }

        // ── Seek bar ────────────────────────────────────────────────────
        if (durationMs > 0) {
            SeekBar(
                positionProvider = { currentPositionMs },
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * A minimal custom seek bar — a thin rounded track with a draggable thumb.
 * No Material Slider used so we have full visual control (no ticks, no label pop-ups).
 */
@Composable
private fun SeekBar(
    positionProvider: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track the drag fraction locally so the thumb feels instant
    var dragFraction by remember { mutableFloatStateOf(-1f) }

    BoxWithConstraints(
        modifier = modifier
            .height(20.dp)
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        if (dragFraction >= 0f) {
                            onSeek((dragFraction * durationMs).toLong())
                            dragFraction = -1f
                        }
                    },
                    onDragCancel = { dragFraction = -1f },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentPos = positionProvider()
            val displayFraction = if (dragFraction >= 0f) dragFraction
                                  else if (durationMs > 0) currentPos.toFloat() / durationMs.toFloat()
                                  else 0f
            val thumbX = (displayFraction * trackWidthPx).coerceIn(0f, trackWidthPx)

            val trackHeight = 4.dp.toPx()
            val cy = size.height / 2f

            // Background track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(0f, cy - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f)
            )
            // Filled progress
            if (thumbX > 0f) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(0f, cy - trackHeight / 2f),
                    size = Size(thumbX, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f)
                )
            }
            // Thumb dot
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(thumbX, cy)
            )
        }
    }
}

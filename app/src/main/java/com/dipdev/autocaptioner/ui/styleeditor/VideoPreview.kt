package com.dipdev.autocaptioner.ui.styleeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dipdev.autocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.autocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.autocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.autocaptioner.engine.CaptionRenderer
import kotlinx.coroutines.delay

@Composable
fun VideoPreview(
    style: CaptionStyleEntity,
    videoPath: String?,
    videoWidth: Int,
    videoHeight: Int,
    segments: List<CaptionSegmentEntity>,
    wordsMap: Map<String, List<CaptionWordEntity>>,
    currentPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    onPositionYChange: (Float) -> Unit
) {
    val context = LocalContext.current
    if (videoPath == null) return

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(android.net.Uri.parse(videoPath))
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            androidx.compose.runtime.withFrameMillis {
                onPositionChanged(exoPlayer.currentPosition)
            }
        }
    }
    val videoAspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else {
        9f / 16f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                exoPlayer.playWhenReady = !exoPlayer.playWhenReady
            },
        contentAlignment = Alignment.Center
    ) {
        // Aspect-ratio bound container precisely mirroring the video frame
        BoxWithConstraints(
            modifier = Modifier
                .aspectRatio(videoAspectRatio)
                .fillMaxHeight()
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.matchParentSize()
            )
        
        val currentPosY by rememberUpdatedState(style.positionY)
        
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val heightPixels = size.height.toFloat()
                    val newRatio = (currentPosY + (dragAmount / heightPixels)).coerceIn(0f, 1f)
                    onPositionYChange(newRatio)
                }
            }
        ) {
            drawIntoCanvas { canvas ->
                CaptionRenderer.draw(
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
    }
}

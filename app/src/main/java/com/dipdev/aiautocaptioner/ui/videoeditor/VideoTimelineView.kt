package com.dipdev.aiautocaptioner.ui.videoeditor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.model.Clip
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun VideoTimelineView(
    clips: List<Clip>,
    mergedClips: List<Clip>,
    clipThumbnails: Map<String, List<Bitmap>>,
    selectedClipId: String?,
    onClipSelected: (String) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    zoomLevel: Float,
    player: Player,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var boxWidthPx by remember { mutableStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    
    val density = LocalDensity.current
    // 1 second = 50dp at 1x zoom
    val pixelsPerMs = with(density) { (50.dp.toPx() / 1000f) * zoomLevel }
    
    var isDragging by remember { mutableStateOf(false) }
    var draggingClipIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val totalEditedMs = clips.sumOf { it.endTrimMs - it.startTrimMs }

    // Sync playhead with video playback
    LaunchedEffect(player, isDragging, pixelsPerMs, mergedClips) {
        while (isActive) {
            if (!isDragging && player.isPlaying) {
                val windowIndex = player.currentMediaItemIndex
                val posInWindow = player.currentPosition
                var accumulated = 0L
                for (i in 0 until windowIndex.coerceAtMost(mergedClips.size)) {
                    accumulated += (mergedClips[i].endTrimMs - mergedClips[i].startTrimMs)
                }
                val currentPosMs = accumulated + posInWindow
                val scrollOffset = (currentPosMs * pixelsPerMs).toInt()
                scrollState.scrollTo(scrollOffset)
            }
            delay(16) // ~60fps
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                boxWidthPx = coordinates.size.width
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        onDragStateChange(true)
                        player.pause()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val newScroll = (scrollState.value - dragAmount.x).toInt()
                            scrollState.scrollTo(newScroll.coerceIn(0, scrollState.maxValue))
                            // Seek video based on absolute timeline ms
                            val seekTimeMs = (scrollState.value / pixelsPerMs).toLong()
                            
                            var accumulated = 0L
                            var targetWindowIndex = 0
                            var targetPosInWindow = 0L
                            
                            for (i in clips.indices) {
                                val clipDuration = clips[i].endTrimMs - clips[i].startTrimMs
                                if (seekTimeMs >= accumulated && seekTimeMs < accumulated + clipDuration) {
                                    targetWindowIndex = i
                                    targetPosInWindow = seekTimeMs - accumulated
                                    break
                                }
                                accumulated += clipDuration
                            }
                            
                            // If scrubbed past the very end, seek to the end of the last clip
                            if (seekTimeMs >= totalEditedMs && clips.isNotEmpty()) {
                                targetWindowIndex = clips.size - 1
                                targetPosInWindow = clips.last().endTrimMs - clips.last().startTrimMs
                            }
                            
                            player.seekTo(targetWindowIndex, targetPosInWindow)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragStateChange(false)
                    },
                    onDragCancel = {
                        isDragging = false
                        onDragStateChange(false)
                    }
                )
            }
    ) {
        val halfWidthDp = with(density) { (boxWidthPx / 2).toDp() }

        // Scrollable Timeline Track
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState, enabled = false) // manual scrolling handled by pointerInput
        ) {
            // Start Padding
            Spacer(modifier = Modifier.width(halfWidthDp))

            Column(modifier = Modifier.fillMaxHeight()) {
                // Ruler
                val totalWidthDp = with(density) { (totalEditedMs * pixelsPerMs).toDp() }
                Canvas(modifier = Modifier.width(totalWidthDp).height(30.dp)) {
                    val durationSec = totalEditedMs / 1000
                    for (i in 0..durationSec) {
                        val x = i * 1000 * pixelsPerMs
                        // Main tick mark
                        drawLine(
                            color = Color.Gray,
                            start = Offset(x, size.height - 15f),
                            end = Offset(x, size.height),
                            strokeWidth = 2f
                        )
                        // Intermediate tick marks every 200ms
                        for (j in 1..4) {
                            val subX = x + (j * 200 * pixelsPerMs)
                            if (subX <= size.width) {
                                drawLine(
                                    color = Color.DarkGray,
                                    start = Offset(subX, size.height - 8f),
                                    end = Offset(subX, size.height),
                                    strokeWidth = 1f
                                )
                            }
                        }
                        // Draw timestamp every second if zoomed in, or every 5 seconds if zoomed out
                        if (zoomLevel >= 1f || i % 5 == 0L || durationSec < 10) {
                            val timeText = String.format("%02d:%02d", i / 60, i % 60)
                            drawText(
                                textMeasurer = textMeasurer,
                                text = timeText,
                                style = TextStyle(color = Color.LightGray, fontSize = 10.sp),
                                topLeft = Offset(x + 4f, size.height - 30f)
                            )
                        }
                    }
                }
                
                // Clips Row
                Row(modifier = Modifier.fillMaxHeight()) {
                    clips.forEachIndexed { index, clip ->
                        androidx.compose.runtime.key(clip.id) {
                            val durationMs = clip.endTrimMs - clip.startTrimMs
                            val clipWidthPx = durationMs * pixelsPerMs
                            val clipWidthDp = with(density) { clipWidthPx.toDp() }
                            
                            val isSelected = clip.id == selectedClipId
                            val currentClipIndex by rememberUpdatedState(index)
                            val isBeingDragged = draggingClipIndex == currentClipIndex

                        Box(
                            modifier = Modifier
                                .width(clipWidthDp)
                                .fillMaxHeight()
                                .zIndex(if (isBeingDragged) 1f else 0f)
                                .offset { IntOffset(if (isBeingDragged) dragOffset.toInt() else 0, 0) }
                                .padding(horizontal = 1.dp) // slight gap between clips
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.DarkGray)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .pointerInput(clip.id, clipWidthPx) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { 
                                            draggingClipIndex = currentClipIndex 
                                            onDragStateChange(true)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount.x
                                            val threshold = clipWidthPx / 2
                                            if (dragOffset > threshold && currentClipIndex < clips.size - 1) {
                                                onMoveClip(currentClipIndex, currentClipIndex + 1)
                                                draggingClipIndex = currentClipIndex + 1
                                                dragOffset -= clipWidthPx
                                            } else if (dragOffset < -threshold && currentClipIndex > 0) {
                                                onMoveClip(currentClipIndex, currentClipIndex - 1)
                                                draggingClipIndex = currentClipIndex - 1
                                                dragOffset += clipWidthPx
                                            }
                                        },
                                        onDragEnd = {
                                            draggingClipIndex = null
                                            dragOffset = 0f
                                            onDragStateChange(false)
                                        },
                                        onDragCancel = {
                                            draggingClipIndex = null
                                            dragOffset = 0f
                                            onDragStateChange(false)
                                        }
                                    )
                                }
                                .clickable { onClipSelected(clip.id) }
                        ) {
                            val thumbnails = clipThumbnails[clip.id]
                            if (!thumbnails.isNullOrEmpty()) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    thumbnails.forEach { bitmap ->
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Thumbnail",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        } // end clip Box
                    } // end key(clip.id)
                } // end clips.forEachIndexed
            } // end Clips Row
    } // end Column

    // End Padding
    Spacer(modifier = Modifier.width(halfWidthDp))
} // end Scrollable Timeline Track Row

        // Fixed Playhead
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.Red)
        )
    } // end Box
} // end function

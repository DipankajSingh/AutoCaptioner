package com.dipdev.aiautocaptioner.ui.videoeditor

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("DefaultLocale")
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
    var boxWidthPx by remember { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    
    val density = LocalDensity.current
    // 1 second = 50dp at 1x zoom
    val pixelsPerMs = with(density) { (50.dp.toPx() / 1000f) * zoomLevel }
    
    var isDragging by remember { mutableStateOf(false) }
    var draggingClipIndex by remember { mutableStateOf<Int?>(null) }
    var dragPointerScreenX by remember { mutableFloatStateOf(0f) }
    val totalEditedMs = clips.sumOf { it.endTrimMs - it.startTrimMs }

    val halfWidthPx = boxWidthPx / 2f
    val clipLayoutCenters = remember(clips, pixelsPerMs, halfWidthPx) {
        val centers = FloatArray(clips.size)
        var accX = 0f
        for (i in clips.indices) {
            val width = (clips[i].endTrimMs - clips[i].startTrimMs) * pixelsPerMs
            centers[i] = halfWidthPx + accX + width / 2
            accX += width
        }
        centers
    }

    val checkSwaps: () -> Unit = {
        val draggedIdx = draggingClipIndex
        if (draggedIdx != null && draggedIdx in clips.indices) {
            val centerInRow = dragPointerScreenX + scrollState.value
            if (draggedIdx < clips.size - 1) {
                val nextCenter = clipLayoutCenters[draggedIdx + 1]
                if (centerInRow > nextCenter) {
                    onMoveClip(draggedIdx, draggedIdx + 1)
                    draggingClipIndex = draggedIdx + 1
                }
            } else if (draggedIdx > 0) {
                val prevCenter = clipLayoutCenters[draggedIdx - 1]
                if (centerInRow < prevCenter) {
                    onMoveClip(draggedIdx, draggedIdx - 1)
                    draggingClipIndex = draggedIdx - 1
                }
            }
        }
    }

    LaunchedEffect(draggingClipIndex, dragPointerScreenX) {
        if (draggingClipIndex != null) {
            val edgeThreshold = with(density) { 60.dp.toPx() }
            val speed = 15f
            while (isActive) {
                var scrolled = false
                if (dragPointerScreenX < edgeThreshold && scrollState.value > 0) {
                    scrollState.scrollTo((scrollState.value - speed.toInt()).coerceAtLeast(0))
                    scrolled = true
                } else if (dragPointerScreenX > boxWidthPx - edgeThreshold && scrollState.value < scrollState.maxValue) {
                    scrollState.scrollTo((scrollState.value + speed.toInt()).coerceAtMost(scrollState.maxValue))
                    scrolled = true
                }
                if (scrolled) {
                    checkSwaps()
                    delay(16)
                } else {
                    break
                }
            }
        }
    }

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
            delay(16.milliseconds) // ~60fps
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .background(surfaceColor)
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
                val safeTotalWidthPx = maxOf(1f, totalEditedMs * pixelsPerMs)
                val totalWidthDp = with(density) { safeTotalWidthPx.toDp() }
                Canvas(modifier = Modifier.width(totalWidthDp).height(40.dp)) {
                    val durationSec = totalEditedMs / 1000
                    for (i in 0..durationSec) {
                        val x = i * 1000 * pixelsPerMs
                        drawLine(
                            color = onSurfaceColor.copy(alpha = 0.7f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2f
                        )
                        for (j in 1..4) {
                            val subX = x + (j * 200 * pixelsPerMs)
                            if (subX <= size.width) {
                                drawLine(
                                    color = onSurfaceColor.copy(alpha = 0.25f),
                                    start = Offset(subX, size.height - 10f),
                                    end = Offset(subX, size.height),
                                    strokeWidth = 1f
                                )
                            }
                        }
                        if (zoomLevel >= 1f || i % 5 == 0L || durationSec < 10) {
                            val timeText = String.format("%02d:%02d", i / 60, i % 60)
                            val layoutResult = textMeasurer.measure(
                                text = timeText,
                                style = TextStyle(color = AccentAmber, fontSize = 10.sp)
                            )
                            drawText(textLayoutResult = layoutResult, topLeft = Offset(x + 4f, 4f))
                        }
                    }
                }
                
                // Clips Row
                Row(modifier = Modifier.fillMaxHeight()) {
                    clips.forEachIndexed { index, clip ->
                        androidx.compose.runtime.key(clip.id) {
                            val durationMs = clip.endTrimMs - clip.startTrimMs
                            val clipWidthPx = maxOf(1f, durationMs * pixelsPerMs)
                            val clipWidthDp = with(density) { clipWidthPx.toDp() }
                            
                            val isSelected = clip.id == selectedClipId
                            val currentClipIndex by rememberUpdatedState(index)
                            val isBeingDragged = draggingClipIndex == currentClipIndex
                            val layoutCenter = clipLayoutCenters[index]
                            val currentDragOffset = if (isBeingDragged) {
                                (dragPointerScreenX + scrollState.value) - layoutCenter
                            } else {
                                0f
                            }

                        Box(
                            modifier = Modifier
                                .width(clipWidthDp)
                                .fillMaxHeight()
                                .zIndex(if (isBeingDragged) 1f else 0f)
                                .offset { IntOffset(currentDragOffset.toInt(), 0) }
                                .padding(horizontal = 1.dp) // slight gap between clips
                                .clip(RoundedCornerShape(4.dp))
                                .background(surfaceVariantColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AccentAmber else outlineColor.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .pointerInput(clip.id, clipWidthPx) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { 
                                            draggingClipIndex = currentClipIndex 
                                            onDragStateChange(true)
                                            dragPointerScreenX = clipLayoutCenters[currentClipIndex] - scrollState.value
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragPointerScreenX += dragAmount.x
                                            checkSwaps()
                                        },
                                        onDragEnd = {
                                            draggingClipIndex = null
                                            onDragStateChange(false)
                                        },
                                        onDragCancel = {
                                            draggingClipIndex = null
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

        // Fixed Playhead — amber triangle needle
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
        ) {
            // Triangle handle at top
            Canvas(
                modifier = Modifier
                    .width(14.dp)
                    .height(10.dp)
                    .align(Alignment.TopCenter)
            ) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width / 2f, size.height)
                    lineTo(0f, 0f)
                    lineTo(size.width, 0f)
                    close()
                }
                drawPath(path, color = AccentAmber)
            }
            // The needle line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(AccentAmber)
                    .align(Alignment.Center)
            )
        }
    } // end Box
} // end function

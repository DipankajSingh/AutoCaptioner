package com.dipdev.aiautocaptioner.ui.videoeditor.timeline

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("DefaultLocale")
@Composable
fun TimelineView(
    modifier: Modifier = Modifier,
    clips: List<Clip>,
    clipThumbnails: Map<String, List<Bitmap>>,
    selectedClipId: String?,
    onClipSelected: (String) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    overlays: List<ImageOverlayEntity> = emptyList(),
    selectedOverlayId: String? = null,
    onOverlaySelected: (String) -> Unit = {},
    onOverlayTimingChanged: (id: String, startTimeMs: Long, endTimeMs: Long) -> Unit = {_,_,_ ->},
    onCaptionTap: () -> Unit = {},
    onDragStateChange: (Boolean) -> Unit,
    zoomLevel: Float,
    player: Player,
    currentTimelineMs: () -> Long,
    onTrimClip: (String, Long, Long) -> Unit = {_,_,_ ->}
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var boxWidthPx by remember { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    
    val density = LocalDensity.current
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
                    kotlinx.coroutines.delay(16.milliseconds)
                } else {
                    break
                }
            }
        }
    }

    LaunchedEffect(isDragging, pixelsPerMs) {
        if (!isDragging) {
            snapshotFlow { currentTimelineMs() }.collect { timeMs ->
                if (player.isPlaying) {
                    val scrollOffset = (timeMs * pixelsPerMs).toInt()
                    scrollState.scrollTo(scrollOffset)
                }
            }
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
            .onGloballyPositioned { coordinates -> boxWidthPx = coordinates.size.width }
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
                            
                            if (seekTimeMs >= totalEditedMs && clips.isNotEmpty()) {
                                targetWindowIndex = clips.size - 1
                                targetPosInWindow = clips.last().endTrimMs - clips.last().startTrimMs
                            }
                            
                            player.seekTo(targetWindowIndex, targetPosInWindow)
                        }
                    },
                    onDragEnd = { isDragging = false; onDragStateChange(false) },
                    onDragCancel = { isDragging = false; onDragStateChange(false) }
                )
            }
    ) {
        val halfWidthDp = with(density) { (boxWidthPx / 2).toDp() }
        val safeTotalWidthPx = maxOf(1f, totalEditedMs * pixelsPerMs)
        val totalWidthDp = with(density) { safeTotalWidthPx.toDp() }

        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState, enabled = false)
        ) {
            Spacer(modifier = Modifier.width(halfWidthDp))

            Column(modifier = Modifier.fillMaxHeight()) {
                TimelineRuler(totalEditedMs, pixelsPerMs, totalWidthDp, zoomLevel, textMeasurer, onSurfaceColor)
                
                if (clips.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tap the video to trim and split",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Row(modifier = Modifier.height(56.dp).fillMaxWidth()) {
                        clips.forEachIndexed { index, clip ->
                            key(clip.id) {
                                val durationMs = clip.endTrimMs - clip.startTrimMs
                                val clipWidthPx = maxOf(1f, durationMs * pixelsPerMs)
                                val clipWidthDp = with(density) { clipWidthPx.toDp() }
                                
                                val isSelected = clip.id == selectedClipId
                                val currentClipIndex by rememberUpdatedState(index)
                                val isBeingDragged = draggingClipIndex == currentClipIndex
                                val layoutCenter = clipLayoutCenters[index]
                                val currentDragOffset = if (isBeingDragged) {
                                    (dragPointerScreenX + scrollState.value) - layoutCenter
                                } else { 0f }
                                
                                val hasGapBefore = index > 0 && clips[index].startTrimMs > clips[index - 1].endTrimMs + 100

                                VideoClipItem(
                                    clip = clip,
                                    index = index,
                                    isSelected = isSelected,
                                    clipWidthPx = clipWidthPx,
                                    clipWidthDp = clipWidthDp,
                                    isBeingDragged = isBeingDragged,
                                    currentDragOffset = currentDragOffset,
                                    clipLayoutCenters = clipLayoutCenters,
                                    scrollStateValue = scrollState.value,
                                    surfaceVariantColor = surfaceVariantColor,
                                    outlineColor = outlineColor,
                                    thumbnails = clipThumbnails[clip.id],
                                    onDragStateChange = onDragStateChange,
                                    onDragPointerStart = { dragPointerScreenX = it },
                                    onDragPointerChange = { dragPointerScreenX += it },
                                    onCheckSwaps = checkSwaps,
                                    onDraggingIndexChange = { draggingClipIndex = it },
                                    onClipSelected = onClipSelected,
                                    hasGapBefore = hasGapBefore,
                                    onTrimClip = onTrimClip,
                                    pixelsPerMs = pixelsPerMs,
                                    totalEditedMs = totalEditedMs
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(totalWidthDp).height(4.dp))

                Box(modifier = Modifier.height(28.dp).width(totalWidthDp).background(Color.DarkGray.copy(alpha = 0.1f), RoundedCornerShape(4.dp))) {
                    overlays.forEach { overlay ->
                        val endTimeMs = if (overlay.endTimeMs == Long.MAX_VALUE) totalEditedMs else overlay.endTimeMs.coerceAtMost(totalEditedMs)
                        val startTimeMs = overlay.startTimeMs.coerceAtMost(totalEditedMs)
                        val durationMs = maxOf(0L, endTimeMs - startTimeMs)
                        
                        if (durationMs > 0) {
                            CaptionOverlayItem(
                                overlay = overlay,
                                isSelectedOverlay = overlay.id == selectedOverlayId,
                                overlayOffsetXDp = with(density) { (startTimeMs * pixelsPerMs).toDp() },
                                overlayWidthDp = with(density) { (durationMs * pixelsPerMs).toDp() },
                                pixelsPerMs = pixelsPerMs,
                                currentEndTimeMs = endTimeMs,
                                totalEditedMs = totalEditedMs,
                                primaryColor = primaryColor,
                                onOverlaySelected = onOverlaySelected,
                                onDragStateChange = onDragStateChange,
                                onOverlayTimingChanged = onOverlayTimingChanged,
                                onCaptionTap = onCaptionTap
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(totalWidthDp).height(4.dp))

                Box(modifier = Modifier.height(28.dp).width(totalWidthDp).background(Color.DarkGray.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.width(halfWidthDp))
        }

        Box(modifier = Modifier.align(Alignment.Center).fillMaxHeight()) {
            PlayheadMarker()
        }
    }
}

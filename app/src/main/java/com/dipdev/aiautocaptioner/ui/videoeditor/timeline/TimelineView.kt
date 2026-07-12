package com.dipdev.aiautocaptioner.ui.videoeditor.timeline
import androidx.compose.foundation.gestures.scrollBy

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.compose.foundation.Canvas
import coil3.compose.AsyncImage
import compose.icons.FeatherIcons
import compose.icons.feathericons.Layers
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.data.model.mergeContiguousClips
import com.dipdev.aiautocaptioner.ui.videoeditor.image.components.ImageOverlayTrackItem
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("DefaultLocale")
@Composable
fun TimelineView(
    modifier: Modifier = Modifier,
    clips: List<Clip>,
    thumbnails: Map<Long, Bitmap>,
    onRequestThumbnails: (List<Long>) -> Unit,
    originalDurationMs: Long,
    selectedClipId: String?,
    onClipSelected: (String) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    overlays: List<ImageOverlayEntity> = emptyList(),
    selectedOverlayId: String? = null,
    onOverlaySelected: (String) -> Unit = {},
    onOverlayTimingChanged: (id: String, startTimeMs: Long, endTimeMs: Long) -> Unit = {_,_,_ ->},
    onMoveOverlayZ: (String, Boolean) -> Unit = {_,_ ->},
    onCaptionTap: () -> Unit = {},
    onDragStateChange: (Boolean) -> Unit,
    zoomLevel: Float,
    player: Player,
    currentTimelineMs: () -> Long,
    onTrimClip: (String, Long, Long) -> Unit = {_,_,_ ->}
) {
    val scrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var boxWidthPx by remember { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    
    val density = LocalDensity.current
    val pixelsPerMs = with(density) { (50.dp.toPx() / 1000f) * zoomLevel }
    
    var draggingClipIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOverlayId by remember { mutableStateOf<String?>(null) }
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
            var swapped = false
            if (draggedIdx < clips.size - 1) {
                val nextCenter = clipLayoutCenters[draggedIdx + 1]
                if (centerInRow > nextCenter) {
                    onMoveClip(draggedIdx, draggedIdx + 1)
                    draggingClipIndex = draggedIdx + 1
                    swapped = true
                }
            }
            if (!swapped && draggedIdx > 0) {
                val prevCenter = clipLayoutCenters[draggedIdx - 1]
                if (centerInRow < prevCenter) {
                    onMoveClip(draggedIdx, draggedIdx - 1)
                    draggingClipIndex = draggedIdx - 1
                }
            }
        }
    }

    LaunchedEffect(draggingClipIndex, draggingOverlayId, dragPointerScreenX) {
        if (draggingClipIndex != null || draggingOverlayId != null) {
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

    val targetChunkMs = (1000f / zoomLevel).toLong()
    val thumbnailIntervalMs = remember(targetChunkMs) {
        when {
            targetChunkMs <= 100 -> 100L
            targetChunkMs <= 250 -> 250L
            targetChunkMs <= 500 -> 500L
            targetChunkMs <= 1000 -> 1000L
            targetChunkMs <= 2000 -> 2000L
            else -> 5000L
        }
    }

    LaunchedEffect(scrollState.value, boxWidthPx, pixelsPerMs, clips, thumbnailIntervalMs) {
        if (boxWidthPx == 0 || pixelsPerMs == 0f) return@LaunchedEffect
        kotlinx.coroutines.delay(80L.milliseconds) // Debounce rapid scroll events
        
        val visibleStartMs = (scrollState.value / pixelsPerMs).toLong()
        val visibleEndMs = ((scrollState.value + boxWidthPx) / pixelsPerMs).toLong()
        
        val requested = mutableSetOf<Long>()
        var currentTimelineMs = 0L
        
        for (clip in clips) {
            val clipDurationMs = clip.endTrimMs - clip.startTrimMs
            val clipStartTimelineMs = currentTimelineMs
            val clipEndTimelineMs = currentTimelineMs + clipDurationMs
            
            // If the clip intersects with the visible edited timeline
            if (clipEndTimelineMs > visibleStartMs && clipStartTimelineMs < visibleEndMs) {
                val visibleClipStartMs = maxOf(clipStartTimelineMs, visibleStartMs)
                val visibleClipEndMs = minOf(clipEndTimelineMs, visibleEndMs)
                
                // Map the visible edited times to original video timestamps
                val offsetIntoClipStartMs = visibleClipStartMs - clipStartTimelineMs
                val offsetIntoClipEndMs = visibleClipEndMs - clipStartTimelineMs
                
                val originalStartMs = clip.startTrimMs + offsetIntoClipStartMs
                val originalEndMs = clip.startTrimMs + offsetIntoClipEndMs
                
                val startChunk = (originalStartMs / thumbnailIntervalMs) * thumbnailIntervalMs
                val endChunk = (originalEndMs / thumbnailIntervalMs) * thumbnailIntervalMs
                
                for (time in startChunk..endChunk step thumbnailIntervalMs) {
                    requested.add(time)
                }
            }
            currentTimelineMs += clipDurationMs
        }
        
        onRequestThumbnails(requested.toList())
    }

    val mergedClips = remember(clips) { mergeContiguousClips(clips) }

    LaunchedEffect(scrollState.isScrollInProgress, pixelsPerMs) {
        if (scrollState.isScrollInProgress) {
            onDragStateChange(true)
            player.pause()
            var lastSeekTime = -1L
            snapshotFlow { scrollState.value }.collect { scrollValue ->
                val seekTimeMs = (scrollValue / pixelsPerMs).toLong()
                if (kotlin.math.abs(seekTimeMs - lastSeekTime) > 20L) {
                    lastSeekTime = seekTimeMs
                    var accumulated = 0L
                    var targetWindowIndex = 0
                    var targetPosInWindow = 0L
                    
                    for (i in mergedClips.indices) {
                        val clipDuration = mergedClips[i].endTrimMs - mergedClips[i].startTrimMs
                        if (seekTimeMs >= accumulated && seekTimeMs < accumulated + clipDuration) {
                            targetWindowIndex = i
                            targetPosInWindow = seekTimeMs - accumulated
                            break
                        }
                        accumulated += clipDuration
                    }
                    
                    if (seekTimeMs >= totalEditedMs && mergedClips.isNotEmpty()) {
                        targetWindowIndex = mergedClips.size - 1
                        targetPosInWindow = mergedClips.last().endTrimMs - mergedClips.last().startTrimMs
                    }
                    
                    player.seekTo(targetWindowIndex, targetPosInWindow)
                }
            }
        } else {
            onDragStateChange(false)
            snapshotFlow { currentTimelineMs() }.collect { timeMs ->
                if (player.isPlaying) {
                    val scrollOffset = (timeMs * pixelsPerMs).toInt()
                    scrollState.scrollTo(scrollOffset)
                }
            }
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.background
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .background(surfaceColor)
            .onGloballyPositioned { coordinates -> boxWidthPx = coordinates.size.width }
    ) {
        val halfWidthDp = with(density) { (boxWidthPx / 2).toDp() }
        val safeTotalWidthPx = maxOf(1f, totalEditedMs * pixelsPerMs)
        val totalWidthDp = with(density) { safeTotalWidthPx.toDp() }

        // Main Timeline Area
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState, enabled = true)
        ) {
            Spacer(modifier = Modifier.width(halfWidthDp - 40.dp))

            Column(modifier = Modifier.fillMaxHeight()) {
                Row {
                    Spacer(modifier = Modifier.width(40.dp))
                    TimelineRuler(totalEditedMs, pixelsPerMs, totalWidthDp, zoomLevel, textMeasurer, onSurfaceColor)
                }
                
                Column(modifier = Modifier.weight(1f).verticalScroll(verticalScrollState)) {
                    if (clips.isEmpty()) {
                        Row(modifier = Modifier.height(100.dp).fillMaxWidth()) {
                            Spacer(modifier = Modifier.width(40.dp))
                            Box(modifier = Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Tap the video to trim and split",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        Row(modifier = Modifier.height(56.dp).fillMaxWidth()) {
                            Spacer(modifier = Modifier.width(40.dp))
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
                                    
                                    val hasGapBefore = index > 0 && clips[index].startTrimMs >= clips[index - 1].endTrimMs

                                    VideoClipItem(
                                        clip = clip,
                                        index = index,
                                        thumbnails = thumbnails,
                                        originalDurationMs = originalDurationMs,
                                        isSelected = isSelected,
                                        clipWidthPx = clipWidthPx,
                                        clipWidthDp = clipWidthDp,
                                        isBeingDragged = isBeingDragged,
                                        currentDragOffset = currentDragOffset,
                                        clipLayoutCenters = clipLayoutCenters,
                                        scrollStateValue = scrollState.value,
                                        surfaceVariantColor = surfaceVariantColor,
                                        outlineColor = outlineColor,
                                        onDragStateChange = onDragStateChange,
                                        onDragPointerStart = { dragPointerScreenX = it },
                                        onDragPointerChange = { dragPointerScreenX += it },
                                        onCheckSwaps = checkSwaps,
                                        onDraggingIndexChange = { draggingClipIndex = it },
                                        onClipSelected = onClipSelected,
                                        hasGapBefore = hasGapBefore,
                                        onTrimClip = onTrimClip,
                                        pixelsPerMs = pixelsPerMs,
                                        thumbnailIntervalMs = thumbnailIntervalMs
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(totalWidthDp).height(4.dp))

                    overlays.forEach { overlay ->
                        key(overlay.id) {
                            var isDragging by remember { mutableStateOf(false) }
                            var dragOffsetY by remember { mutableFloatStateOf(0f) }
                            
                            Row(modifier = Modifier.height(48.dp).fillMaxWidth().padding(bottom = 4.dp)) {
                                // Track Header (Drag Handle)
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .fillMaxHeight()
                                        .zIndex(2f)
                                        .graphicsLayer {
                                            translationY = dragOffsetY
                                            scaleX = if (isDragging) 1.1f else 1f
                                            scaleY = if (isDragging) 1.1f else 1f
                                            shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                                        }
                                        .background(Color(0xFF333333), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                        .pointerInput(overlay.id) {
                                            var accumulatedY = 0f
                                            detectVerticalDragGestures(
                                                onDragStart = { 
                                                    isDragging = true 
                                                    accumulatedY = 0f
                                                    dragOffsetY = 0f
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    accumulatedY += dragAmount
                                                    dragOffsetY += dragAmount
                                                    
                                                    if (accumulatedY > 52f) { // 48dp height + 4dp gap
                                                        onMoveOverlayZ(overlay.id, false)
                                                        accumulatedY -= 52f
                                                        dragOffsetY -= 52f
                                                    } else if (accumulatedY < -52f) {
                                                        onMoveOverlayZ(overlay.id, true)
                                                        accumulatedY += 52f
                                                        dragOffsetY += 52f
                                                    }
                                                },
                                                onDragEnd = {
                                                    isDragging = false
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    isDragging = false
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        }
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val dotRadius = 1.5.dp.toPx()
                                        val spacingX = 6.dp.toPx()
                                        val spacingY = 6.dp.toPx()
                                        val startX = size.width / 2 - spacingX / 2
                                        val startY = size.height / 2 - spacingY
                                        for (row in 0..2) {
                                            for (col in 0..1) {
                                                drawCircle(
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    radius = dotRadius,
                                                    center = androidx.compose.ui.geometry.Offset(startX + col * spacingX, startY + row * spacingY)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Box(modifier = Modifier.width(totalWidthDp).fillMaxHeight().background(Color.DarkGray.copy(alpha = 0.1f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))) {
                                    val endTimeMs = if (overlay.endTimeMs == Long.MAX_VALUE) totalEditedMs else overlay.endTimeMs.coerceAtMost(totalEditedMs)
                                    val startTimeMs = overlay.startTimeMs.coerceAtMost(totalEditedMs)
                                    val durationMs = maxOf(0L, endTimeMs - startTimeMs)
                                    
                                    if (durationMs > 0) {
                                        ImageOverlayTrackItem(
                                            overlay = overlay,
                                            isSelectedOverlay = overlay.id == selectedOverlayId,
                                            pixelsPerMs = pixelsPerMs,
                                            currentEndTimeMs = endTimeMs,
                                            totalEditedMs = totalEditedMs,
                                            primaryColor = primaryColor,
                                            scrollStateValue = scrollState.value,
                                            timelineWidthPx = boxWidthPx,
                                            onOverlaySelected = onOverlaySelected,
                                            onDragStateChange = { 
                                                onDragStateChange(it)
                                                if (!it) draggingOverlayId = null
                                            },
                                            onOverlayTimingChanged = onOverlayTimingChanged,
                                            onDragPointerStart = { 
                                                dragPointerScreenX = it 
                                                draggingOverlayId = overlay.id
                                            },
                                            onDragPointerChange = { dragPointerScreenX += it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(totalWidthDp).height(40.dp))
                }
            }

            Spacer(modifier = Modifier.width(halfWidthDp))
        }
        
        Box(modifier = Modifier.align(Alignment.Center).fillMaxHeight()) {
            PlayheadMarker()
        }
    }
}

package com.dipdev.aiautocaptioner.ui.videoeditor.image.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import coil3.compose.AsyncImage
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity

@Composable
fun ImageOverlayTrackItem(
    overlay: ImageOverlayEntity,
    isSelectedOverlay: Boolean,
    pixelsPerMs: Float,
    currentEndTimeMs: Long,
    totalEditedMs: Long,
    primaryColor: Color,
    scrollStateValue: Int,
    timelineWidthPx: Int,
    onOverlaySelected: (String) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onOverlayTimingChanged: (String, Long, Long) -> Unit,
    onScrollBy: (Float) -> Unit
) {
    val updatedOverlay by rememberUpdatedState(overlay)
    val updatedEndTimeMs by rememberUpdatedState(currentEndTimeMs)

    var dragStateStartMs by remember { mutableStateOf<Long?>(null) }
    var dragStateEndMs by remember { mutableStateOf<Long?>(null) }

    val effectiveStartMs = dragStateStartMs ?: overlay.startTimeMs
    val effectiveEndMs = dragStateEndMs ?: updatedEndTimeMs

    val dragOffsetXDp = with(androidx.compose.ui.platform.LocalDensity.current) { (effectiveStartMs * pixelsPerMs).toDp() }
    val dragWidthDp = with(androidx.compose.ui.platform.LocalDensity.current) { (maxOf(0L, effectiveEndMs - effectiveStartMs) * pixelsPerMs).toDp() }

    Box(
        modifier = Modifier
            .offset(x = dragOffsetXDp)
            .width(dragWidthDp)
            .fillMaxHeight()
            .zIndex(if (dragStateStartMs != null || dragStateEndMs != null) 1f else 0f)
            .graphicsLayer {
                if (dragStateStartMs != null && dragStateEndMs != null) { // Type 3 drag (move)
                    scaleX = 1.05f
                    scaleY = 1.05f
                    shadowElevation = 8.dp.toPx()
                    shape = RoundedCornerShape(4.dp)
                    this.clip = true
                }
            }
            .padding(horizontal = 1.dp) // slight gap
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelectedOverlay) Color.White else primaryColor.copy(alpha = 0.3f))
            .border(
                width = if (isSelectedOverlay) 2.dp else 1.dp,
                color = if (isSelectedOverlay) Color.White else primaryColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(overlay.id) {
                var accumulatedDeltaX = 0f
                var currentStartMs = 0L
                var currentEndMs = 0L
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onOverlaySelected(updatedOverlay.id)
                        accumulatedDeltaX = 0f
                        currentStartMs = updatedOverlay.startTimeMs
                        currentEndMs = updatedEndTimeMs
                        onDragStateChange(true)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDeltaX += dragAmount.x
                        val deltaMs = (accumulatedDeltaX / pixelsPerMs).toLong()
                        if (deltaMs != 0L) {
                            accumulatedDeltaX -= (deltaMs * pixelsPerMs)
                            val dur = currentEndMs - currentStartMs
                            val newStart = (currentStartMs + deltaMs).coerceIn(0L, totalEditedMs - dur)
                            if (newStart != currentStartMs) {
                                val newEnd = newStart + dur
                                currentStartMs = newStart
                                currentEndMs = newEnd
                                dragStateStartMs = newStart
                                dragStateEndMs = newEnd
                                onScrollBy(-dragAmount.x)
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragStateStartMs != null && dragStateEndMs != null) {
                            val finalStart = dragStateStartMs ?: updatedOverlay.startTimeMs
                            val finalEnd = dragStateEndMs ?: updatedEndTimeMs
                            onOverlayTimingChanged(
                                updatedOverlay.id, 
                                finalStart, 
                                if (updatedOverlay.endTimeMs == Long.MAX_VALUE) Long.MAX_VALUE else finalEnd
                            )
                        }
                        dragStateStartMs = null
                        dragStateEndMs = null
                        onDragStateChange(false)
                    },
                    onDragCancel = {
                        dragStateStartMs = null
                        dragStateEndMs = null
                        onDragStateChange(false)
                    }
                )
            }
            .clickable { onOverlaySelected(overlay.id) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isSelectedOverlay) 16.dp else 14.dp,
                    vertical = if (isSelectedOverlay) 2.dp else 2.dp
                )
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            // Tiled Thumbnail
            Row(modifier = Modifier.fillMaxSize()) {
                val numImages = (dragWidthDp.value / 32f).toInt() + 1
                
                for (i in 0 until numImages) {
                    AsyncImage(
                        model = overlay.imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                    )
                }
            }
            
            // Dark overlay for readability
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            
            // Selected Overlay
            if (isSelectedOverlay) {
                Box(modifier = Modifier.fillMaxSize().background(AccentAmber.copy(alpha = 0.1f)))
            }
        }

        // Selected Handles (Matching Video Timeline)
        if (isSelectedOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(16.dp)
                    .fillMaxHeight()
                    .pointerInput(overlay.id + "_left") {
                        var accumulatedDeltaX = 0f
                        var currentStartMs = 0L
                        detectDragGestures(
                            onDragStart = { 
                                onDragStateChange(true)
                                accumulatedDeltaX = 0f
                                currentStartMs = updatedOverlay.startTimeMs
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDeltaX += dragAmount.x
                                val deltaMs = (accumulatedDeltaX / pixelsPerMs).toLong()
                                if (deltaMs != 0L) {
                                    accumulatedDeltaX -= (deltaMs * pixelsPerMs)
                                    val newStart = (currentStartMs + deltaMs).coerceIn(0L, updatedEndTimeMs - 100L)
                                    if (newStart != currentStartMs) {
                                        currentStartMs = newStart
                                        dragStateStartMs = newStart
                                        dragStateEndMs = updatedEndTimeMs
                                        onScrollBy(-dragAmount.x)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (dragStateStartMs != null) {
                                    onOverlayTimingChanged(overlay.id, dragStateStartMs!!, updatedEndTimeMs)
                                }
                                dragStateStartMs = null
                                dragStateEndMs = null
                                onDragStateChange(false)
                            },
                            onDragCancel = {
                                dragStateStartMs = null
                                dragStateEndMs = null
                                onDragStateChange(false)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(2.dp).height(14.dp).background(AccentAmber, RoundedCornerShape(50)))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(16.dp)
                    .fillMaxHeight()
                    .pointerInput(overlay.id + "_right") {
                        var accumulatedDeltaX = 0f
                        var currentEndMs = 0L
                        detectDragGestures(
                            onDragStart = { 
                                onDragStateChange(true)
                                accumulatedDeltaX = 0f
                                currentEndMs = updatedEndTimeMs
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDeltaX += dragAmount.x
                                val deltaMs = (accumulatedDeltaX / pixelsPerMs).toLong()
                                if (deltaMs != 0L) {
                                    accumulatedDeltaX -= (deltaMs * pixelsPerMs)
                                    val newEnd = (currentEndMs + deltaMs).coerceIn(updatedOverlay.startTimeMs + 100L, totalEditedMs)
                                    if (newEnd != currentEndMs) {
                                        currentEndMs = newEnd
                                        dragStateStartMs = updatedOverlay.startTimeMs
                                        dragStateEndMs = newEnd
                                        onScrollBy(dragAmount.x)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (dragStateEndMs != null) {
                                    val finalEnd = if (overlay.endTimeMs == Long.MAX_VALUE && dragStateEndMs == totalEditedMs) Long.MAX_VALUE else dragStateEndMs!!
                                    onOverlayTimingChanged(overlay.id, updatedOverlay.startTimeMs, finalEnd)
                                }
                                dragStateStartMs = null
                                dragStateEndMs = null
                                onDragStateChange(false)
                            },
                            onDragCancel = {
                                dragStateStartMs = null
                                dragStateEndMs = null
                                onDragStateChange(false)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(2.dp).height(14.dp).background(AccentAmber, RoundedCornerShape(50)))
            }
        }
    }
}

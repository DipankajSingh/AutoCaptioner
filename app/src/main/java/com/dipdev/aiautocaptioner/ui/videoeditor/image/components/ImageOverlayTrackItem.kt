package com.dipdev.aiautocaptioner.ui.videoeditor.image.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.videoeditor.timeline.timelineMoveGesture
import com.dipdev.aiautocaptioner.ui.videoeditor.timeline.timelineTrimGesture
import kotlin.math.roundToInt

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
    trackContentOffsetPx: Float,
    onOverlaySelected: (String) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onOverlayTimingChanged: (id: String, startTimeMs: Long, endTimeMs: Long) -> Unit,
    onDragPointerStart: (screenX: Float) -> Unit,
    onDragPointerMove: (screenX: Float) -> Unit,
) {
    val density = LocalDensity.current
    val updatedOverlay by rememberUpdatedState(overlay)
    val updatedEndTimeMs by rememberUpdatedState(currentEndTimeMs)
    val updatedPixelsPerMs by rememberUpdatedState(pixelsPerMs)
    val updatedTotalEditedMs by rememberUpdatedState(totalEditedMs)
    val updatedScrollStateValue by rememberUpdatedState(scrollStateValue)
    val updatedTrackContentOffsetPx by rememberUpdatedState(trackContentOffsetPx)

    // Local transient states for drawing
    var dragStateStartMs by remember { mutableStateOf<Long?>(null) }
    var dragStateEndMs by remember { mutableStateOf<Long?>(null) }
    var dragStartScrollPx by remember { mutableIntStateOf(0) }
    var isRepositioning by remember { mutableStateOf(false) }

    val activeStartMs = dragStateStartMs ?: overlay.startTimeMs
    val activeEndMs = dragStateEndMs ?: currentEndTimeMs
    val isMoving = dragStateStartMs != null

    val dragOffsetPx = activeStartMs * pixelsPerMs
    val scrollCompensationPx = if (isRepositioning) (updatedScrollStateValue - dragStartScrollPx).toFloat() else 0f
    val dragWidthPx = (activeEndMs - activeStartMs) * pixelsPerMs
    
    val dragWidthDp = maxOf(20.dp, with(density) { dragWidthPx.toDp() })

    Box(
        modifier = Modifier
            .offset { IntOffset((dragOffsetPx + scrollCompensationPx).roundToInt(), 0) }
            .width(dragWidthDp)
            .fillMaxHeight()
            .zIndex(if (isMoving) 1f else 0f)
            .graphicsLayer {
                if (isMoving) {
                    scaleX = 1.05f
                    scaleY = 1.05f
                    shadowElevation = 8.dp.toPx()
                    shape = RoundedCornerShape(4.dp)
                    this.clip = true
                }
            }
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelectedOverlay) Color.White else primaryColor.copy(alpha = 0.3f))
            .border(
                width = if (isSelectedOverlay) 2.dp else 1.dp,
                color = if (isSelectedOverlay) Color.White else primaryColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .timelineMoveGesture(
                key1 = overlay.id,
                pixelsPerMs = { updatedPixelsPerMs },
                startTimeMs = { updatedOverlay.startTimeMs },
                endTimeMs   = { updatedEndTimeMs },
                totalDurationMs = { updatedTotalEditedMs },
                scrollStateValue = { updatedScrollStateValue },
                contentOffsetPx = { updatedTrackContentOffsetPx },
                onDragStart = {
                    dragStartScrollPx = updatedScrollStateValue
                    isRepositioning = true
                    onOverlaySelected(updatedOverlay.id)
                    onDragStateChange(true)
                },
                onDragPointerStart  = onDragPointerStart,
                onDragPointerMove = onDragPointerMove,
                onPositionChange = { newStart, newEnd ->
                    dragStateStartMs = newStart
                    dragStateEndMs   = newEnd
                },
                onDragEnd = { finalStart, finalEnd ->
                    if (dragStateStartMs != null) {
                        val scrollDeltaMs = ((updatedScrollStateValue - dragStartScrollPx) / updatedPixelsPerMs).toLong()
                        val correctedStart = (finalStart + scrollDeltaMs).coerceIn(0L, updatedTotalEditedMs - (finalEnd - finalStart))
                        val correctedEnd = correctedStart + (finalEnd - finalStart)
                        val persistedEnd = if (updatedOverlay.endTimeMs == Long.MAX_VALUE)
                            Long.MAX_VALUE else correctedEnd
                        onOverlayTimingChanged(updatedOverlay.id, correctedStart, persistedEnd)
                    }
                    dragStateStartMs = null
                    dragStateEndMs   = null
                    isRepositioning = false
                    onDragStateChange(false)
                },
            )
            .clickable { onOverlaySelected(overlay.id) },
    ) {
        // ── Thumbnail tiles ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isSelectedOverlay) 16.dp else 14.dp,
                    vertical   = 2.dp,
                )
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val numImages = (dragWidthDp.value / 32f).toInt() + 1
                repeat(numImages) {
                    AsyncImage(
                        model = overlay.imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            if (isSelectedOverlay) {
                Box(modifier = Modifier.fillMaxSize().background(AccentAmber.copy(alpha = 0.1f)))
            }
        }

        // ── Trim handles (only when selected) ─────────────────────────────
        if (isSelectedOverlay) {
            // Left trim handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(16.dp)
                    .fillMaxHeight()
                    .timelineTrimGesture(
                        key1 = overlay.id,
                        key2 = "left",
                        pixelsPerMs = { updatedPixelsPerMs },
                        currentEdgeMs = { updatedOverlay.startTimeMs },
                        minEdgeMs    = { 0L },
                        maxEdgeMs    = { updatedEndTimeMs - 100L },
                        scrollStateValue = { updatedScrollStateValue },
                        onDragStart  = { onDragStateChange(true) },
                        onDragPointerStart  = {},
                        onDragPointerMove = {},
                        onEdgeChange = { newStart ->
                            dragStateStartMs = newStart
                            dragStateEndMs   = updatedEndTimeMs
                        },
                        onDragEnd = { finalStart ->
                            if (dragStateStartMs != null) {
                                onOverlayTimingChanged(updatedOverlay.id, finalStart, updatedEndTimeMs)
                            }
                            dragStateStartMs = null
                            dragStateEndMs   = null
                            onDragStateChange(false)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.width(2.dp).height(14.dp).background(AccentAmber, RoundedCornerShape(50)))
            }

            // Right trim handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(16.dp)
                    .fillMaxHeight()
                    .timelineTrimGesture(
                        key1 = overlay.id,
                        key2 = "right",
                        pixelsPerMs = { updatedPixelsPerMs },
                        currentEdgeMs = { updatedEndTimeMs },
                        minEdgeMs    = { updatedOverlay.startTimeMs + 100L },
                        maxEdgeMs    = { updatedTotalEditedMs },
                        scrollStateValue = { updatedScrollStateValue },
                        onDragStart  = { onDragStateChange(true) },
                        onDragPointerStart  = {},
                        onDragPointerMove = {},
                        onEdgeChange = { newEnd ->
                            dragStateStartMs = updatedOverlay.startTimeMs
                            dragStateEndMs   = newEnd
                        },
                        onDragEnd = { finalEnd ->
                            if (dragStateEndMs != null) {
                                val persistedEnd = if (updatedOverlay.endTimeMs == Long.MAX_VALUE
                                    && finalEnd >= updatedTotalEditedMs - 100L) Long.MAX_VALUE else finalEnd
                                onOverlayTimingChanged(updatedOverlay.id, updatedOverlay.startTimeMs, persistedEnd)
                            }
                            dragStateStartMs = null
                            dragStateEndMs   = null
                            onDragStateChange(false)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.width(2.dp).height(14.dp).background(AccentAmber, RoundedCornerShape(50)))
            }
        }
    }
}

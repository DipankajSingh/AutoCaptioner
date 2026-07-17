package com.dipdev.aiautocaptioner.ui.videoeditor.image.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.videoeditor.timeline.timelineMoveGesture
import com.dipdev.aiautocaptioner.ui.videoeditor.timeline.timelineTrimGesture

/**
 * Renders a single image overlay pill on the timeline.
 *
 * Gesture behaviour:
 *  • Tap            → select
 *  • Long-press drag → move (uses [timelineMoveGesture])
 *  • Left/right edge drag → trim (uses [timelineTrimGesture])
 *
 * The parent [TimelineView] owns [dragPointerScreenX] and the edge-scroll
 * LaunchedEffect; this composable just reports pointer-X changes up via
 * [onDragPointerStart] / [onDragPointerChange].
 */
    enum class DragState { None, Moving, TrimmingLeft, TrimmingRight }
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
    onDragPointerStart: (Float) -> Unit,
    onDragPointerChange: (Float) -> Unit,
) {
    // rememberUpdatedState lets the gesture lambdas always see the latest
    // values without restarting the pointerInput block on every recomposition.
    val updatedOverlay by rememberUpdatedState(overlay)
    val updatedEndTimeMs by rememberUpdatedState(currentEndTimeMs)

    // Nullable → null means "not dragging / showing DB-committed position".
    var dragState by remember { mutableStateOf(DragState.None) }
    var dragAccumulatedPx by remember { mutableStateOf(0f) }
    var dragStartScrollValue by remember { mutableStateOf(0) }
    var initialEdgeMs by remember { mutableStateOf(0L) }

    val visualStartPx = (overlay.startTimeMs * pixelsPerMs)
    val visualEndPx = (updatedEndTimeMs * pixelsPerMs)

    var currentStartPx = visualStartPx
    var currentEndPx = visualEndPx

    if (dragState == DragState.Moving) {
        currentStartPx += dragAccumulatedPx
        currentEndPx += dragAccumulatedPx
    } else if (dragState == DragState.TrimmingLeft) {
        currentStartPx = (initialEdgeMs * pixelsPerMs) + dragAccumulatedPx
        currentStartPx = currentStartPx.coerceIn(0f, currentEndPx - (100L * pixelsPerMs))
    } else if (dragState == DragState.TrimmingRight) {
        currentEndPx = (initialEdgeMs * pixelsPerMs) + dragAccumulatedPx
        currentEndPx = currentEndPx.coerceIn(currentStartPx + (100L * pixelsPerMs), totalEditedMs * pixelsPerMs)
    }

    val density = LocalDensity.current
    val dragOffsetXDp = with(density) { currentStartPx.toDp() }
    val dragWidthDp   = with(density) { maxOf(0f, currentEndPx - currentStartPx).toDp() }

    val isMoving = dragState == DragState.Moving

    Box(
        modifier = Modifier
            .offset { IntOffset(dragOffsetXDp.roundToPx(), 0) }
            .width(dragWidthDp)
            .fillMaxHeight()
            .zIndex(if (dragState != DragState.None) 1f else 0f)
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
            .pointerInput(overlay.id, "move") {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onOverlaySelected(updatedOverlay.id)
                        dragState = DragState.Moving
                        dragAccumulatedPx = 0f
                        dragStartScrollValue = scrollStateValue
                        
                        val screenX = offset.x + (updatedOverlay.startTimeMs * pixelsPerMs) - scrollStateValue
                        onDragPointerStart(screenX)
                        onDragStateChange(true)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatedPx += dragAmount.x
                        onDragPointerChange(dragAmount.x)
                    },
                    onDragEnd = {
                        val finalStartPx = (updatedOverlay.startTimeMs * pixelsPerMs) + dragAccumulatedPx
                        val finalStartMs = (finalStartPx / pixelsPerMs).toLong().coerceIn(0L, totalEditedMs)
                        val durationMs = updatedOverlay.endTimeMs - updatedOverlay.startTimeMs
                        val finalEndMs = if (updatedOverlay.endTimeMs == Long.MAX_VALUE) Long.MAX_VALUE else finalStartMs + durationMs
                        
                        onOverlayTimingChanged(overlay.id, finalStartMs, finalEndMs)
                        dragState = DragState.None
                        onDragStateChange(false)
                    },
                    onDragCancel = {
                        dragState = DragState.None
                        onDragStateChange(false)
                    }
                )
            }
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
                    .pointerInput(overlay.id, "left") {
                        detectDragGestures(
                            onDragStart = {
                                dragState = DragState.TrimmingLeft
                                dragAccumulatedPx = 0f
                                dragStartScrollValue = scrollStateValue
                                initialEdgeMs = updatedOverlay.startTimeMs
                                onDragStateChange(true)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulatedPx += dragAmount.x
                            },
                            onDragEnd = {
                                val finalStartPx = (initialEdgeMs * pixelsPerMs) + dragAccumulatedPx
                                val finalStartMs = (finalStartPx / pixelsPerMs).toLong()
                                onOverlayTimingChanged(overlay.id, finalStartMs, updatedEndTimeMs)
                                dragState = DragState.None
                                onDragStateChange(false)
                            },
                            onDragCancel = {
                                dragState = DragState.None
                                onDragStateChange(false)
                            }
                        )
                    },
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
                    .pointerInput(overlay.id, "right") {
                        detectDragGestures(
                            onDragStart = {
                                dragState = DragState.TrimmingRight
                                dragAccumulatedPx = 0f
                                dragStartScrollValue = scrollStateValue
                                initialEdgeMs = updatedEndTimeMs
                                onDragStateChange(true)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulatedPx += dragAmount.x
                            },
                            onDragEnd = {
                                val finalEndPx = (initialEdgeMs * pixelsPerMs) + dragAccumulatedPx
                                val finalEndMs = (finalEndPx / pixelsPerMs).toLong()
                                val persistedEnd = if (overlay.endTimeMs == Long.MAX_VALUE && finalEndMs >= totalEditedMs) Long.MAX_VALUE else finalEndMs
                                onOverlayTimingChanged(overlay.id, updatedOverlay.startTimeMs, persistedEnd)
                                dragState = DragState.None
                                onDragStateChange(false)
                            },
                            onDragCancel = {
                                dragState = DragState.None
                                onDragStateChange(false)
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.width(2.dp).height(14.dp).background(AccentAmber, RoundedCornerShape(50)))
            }
        }
    }
}

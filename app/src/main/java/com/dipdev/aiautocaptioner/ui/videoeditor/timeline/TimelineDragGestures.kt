package com.dipdev.aiautocaptioner.ui.videoeditor.timeline

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Attaches a **long-press → drag** gesture that lets the user reposition a
 * free-floating timeline clip (e.g. image overlay, future text/audio track).
 */
fun Modifier.timelineMoveGesture(
    key1: Any,
    key2: Any = Unit,
    pixelsPerMs: () -> Float,
    startTimeMs: () -> Long,
    endTimeMs: () -> Long,
    totalDurationMs: () -> Long,
    scrollStateValue: () -> Int,
    onDragStart: () -> Unit = {},
    onDragPointerStart: (screenX: Float) -> Unit,
    onDragPointerMove: (screenX: Float) -> Unit,
    onPositionChange: (newStartMs: Long, newEndMs: Long) -> Unit,
    onDragEnd: (finalStartMs: Long, finalEndMs: Long) -> Unit,
): Modifier = this.pointerInput(key1, key2) {
    var initialStartMs = 0L
    var initialEndMs = 0L
    var gripOffsetPx = 0f

    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            initialStartMs = startTimeMs()
            initialEndMs = endTimeMs()
            
            val clipTimelinePx = initialStartMs * pixelsPerMs()
            val viewportX = offset.x + clipTimelinePx - scrollStateValue()
            gripOffsetPx = offset.x
            
            onDragPointerStart(viewportX)
            onDragStart()
        },
        onDrag = { change, _ ->
            change.consume()
            
            val clipTimelinePx = startTimeMs() * pixelsPerMs()
            val viewportX = change.position.x + clipTimelinePx - scrollStateValue()
            onDragPointerMove(viewportX)
            
            val absoluteTimelinePx = viewportX + scrollStateValue()
            val targetStartPx = absoluteTimelinePx - gripOffsetPx
            
            val duration = initialEndMs - initialStartMs
            val newStart = (targetStartPx / pixelsPerMs()).toLong().coerceIn(0L, totalDurationMs() - duration)
            
            if (newStart != startTimeMs()) {
                onPositionChange(newStart, newStart + duration)
            }
        },
        onDragEnd = { onDragEnd(startTimeMs(), endTimeMs()) },
        onDragCancel = { onDragEnd(startTimeMs(), endTimeMs()) },
    )
}

/**
 * Attaches an **immediate drag** gesture for a trim handle (left or right edge).
 */
fun Modifier.timelineTrimGesture(
    key1: Any,
    key2: Any = Unit,
    pixelsPerMs: () -> Float,
    currentEdgeMs: () -> Long,
    minEdgeMs: () -> Long,
    maxEdgeMs: () -> Long,
    scrollStateValue: () -> Int,
    onDragStart: () -> Unit = {},
    onDragPointerStart: (screenX: Float) -> Unit,
    onDragPointerMove: (screenX: Float) -> Unit,
    onEdgeChange: (newEdgeMs: Long) -> Unit,
    onDragEnd: (finalEdgeMs: Long) -> Unit,
): Modifier = this.pointerInput(key1, key2) {
    var gripOffsetPx = 0f

    detectDragGestures(
        onDragStart = { offset ->
            val edgeTimelinePx = currentEdgeMs() * pixelsPerMs()
            val viewportX = offset.x + edgeTimelinePx - scrollStateValue()
            gripOffsetPx = offset.x
            
            onDragPointerStart(viewportX)
            onDragStart()
        },
        onDrag = { change, _ ->
            change.consume()
            
            val edgeTimelinePx = currentEdgeMs() * pixelsPerMs()
            val viewportX = change.position.x + edgeTimelinePx - scrollStateValue()
            onDragPointerMove(viewportX)
            
            val absoluteTimelinePx = viewportX + scrollStateValue()
            val targetEdgePx = absoluteTimelinePx - gripOffsetPx
            
            val newEdge = (targetEdgePx / pixelsPerMs()).toLong().coerceIn(minEdgeMs(), maxEdgeMs())
            if (newEdge != currentEdgeMs()) {
                onEdgeChange(newEdge)
            }
        },
        onDragEnd = { onDragEnd(currentEdgeMs()) },
        onDragCancel = { onDragEnd(currentEdgeMs()) },
    )
}

/**
 * Attaches a **long-press → drag** gesture for swapping sequential clips 
 * (like video clips) without changing their individual timestamps directly.
 */
fun Modifier.timelineClipSwapGesture(
    key1: Any,
    key2: Any = Unit,
    clipCenterPx: Float,
    scrollStateValue: Int,
    onDragStart: () -> Unit,
    onDragPointerStart: (screenX: Float) -> Unit,
    onDragPointerChange: (deltaX: Float) -> Unit,
    onCheckSwaps: () -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(key1, key2) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            onDragStart()
            onDragPointerStart(clipCenterPx - scrollStateValue)
        },
        onDrag = { change, dragAmount ->
            change.consume()
            onDragPointerChange(dragAmount.x)
            onCheckSwaps()
        },
        onDragEnd = { onDragEnd() },
        onDragCancel = { onDragEnd() }
    )
}

/**
 * Attaches an **immediate vertical drag** gesture for reordering layers (e.g. tracks).
 */
fun Modifier.timelineLayerReorderGesture(
    key1: Any,
    key2: Any = Unit,
    rowHeightPx: Float,
    onDragStart: () -> Unit,
    onDragOffsetChange: (deltaY: Float) -> Unit,
    onMoveLayer: (moveUp: Boolean) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(key1, key2) {
    var accumulatedY = 0f

    detectVerticalDragGestures(
        onDragStart = {
            accumulatedY = 0f
            onDragStart()
        },
        onVerticalDrag = { change, dragAmount ->
            change.consume()
            accumulatedY += dragAmount
            onDragOffsetChange(dragAmount)
            
            if (accumulatedY > rowHeightPx) {
                onMoveLayer(false) // moved down
                accumulatedY -= rowHeightPx
                onDragOffsetChange(-rowHeightPx) // correction
            } else if (accumulatedY < -rowHeightPx) {
                onMoveLayer(true) // moved up
                accumulatedY += rowHeightPx
                onDragOffsetChange(rowHeightPx) // correction
            }
        },
        onDragEnd = { onDragEnd() },
        onDragCancel = { onDragEnd() }
    )
}

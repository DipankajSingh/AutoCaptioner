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
    var accumulatedPx = 0f
    var viewportX = 0f

    fun currentEdge(): Long {
        val duration = initialEndMs - initialStartMs
        val deltaMs = (accumulatedPx / pixelsPerMs()).toLong()
        return (initialStartMs + deltaMs).coerceIn(0L, totalDurationMs() - duration)
    }

    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            initialStartMs = startTimeMs()
            initialEndMs = endTimeMs()
            accumulatedPx = 0f

            val clipTimelinePx = initialStartMs * pixelsPerMs()
            viewportX = offset.x + clipTimelinePx - scrollStateValue()

            onDragPointerStart(viewportX)
            onDragStart()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            accumulatedPx += dragAmount.x
            viewportX += dragAmount.x
            onDragPointerMove(viewportX)

            val newStart = currentEdge()
            onPositionChange(newStart, newStart + (initialEndMs - initialStartMs))
        },
        onDragEnd = {
            val newStart = currentEdge()
            onDragEnd(newStart, newStart + (initialEndMs - initialStartMs))
        },
        onDragCancel = {
            val newStart = currentEdge()
            onDragEnd(newStart, newStart + (initialEndMs - initialStartMs))
        },
    )
}


/**
 * Immediate-drag gesture for a trim handle (left or right edge).
 * Same fix as [timelineMoveGesture] — see its doc comment.
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
    var initialEdgeMs = 0L
    var accumulatedPx = 0f
    var viewportX = 0f

    fun currentEdge(): Long {
        val deltaMs = (accumulatedPx / pixelsPerMs()).toLong()
        return (initialEdgeMs + deltaMs).coerceIn(minEdgeMs(), maxEdgeMs())
    }

    detectDragGestures(
        onDragStart = { offset ->
            initialEdgeMs = currentEdgeMs()
            accumulatedPx = 0f

            val edgeTimelinePx = initialEdgeMs * pixelsPerMs()
            viewportX = offset.x + edgeTimelinePx - scrollStateValue()

            onDragPointerStart(viewportX)
            onDragStart()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            accumulatedPx += dragAmount.x
            viewportX += dragAmount.x
            onDragPointerMove(viewportX)

            onEdgeChange(currentEdge())
        },
        onDragEnd = { onDragEnd(currentEdge()) },
        onDragCancel = { onDragEnd(currentEdge()) },
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

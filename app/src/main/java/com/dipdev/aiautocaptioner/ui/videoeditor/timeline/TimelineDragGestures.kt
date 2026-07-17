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
    onDragPointerChange: (deltaX: Float) -> Unit,
    onPositionChange: (newStartMs: Long, newEndMs: Long) -> Unit,
    onDragEnd: (finalStartMs: Long, finalEndMs: Long) -> Unit,
): Modifier = this.pointerInput(key1, key2) {
    var accumulatedPx = 0f
    var currentStartMs = 0L
    var currentEndMs = 0L

    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            currentStartMs = startTimeMs()
            currentEndMs = endTimeMs()
            accumulatedPx = 0f

            // Calculate exact touch point based on current physical position
            val absoluteScreenX = offset.x + (currentStartMs * pixelsPerMs()) - scrollStateValue()
            onDragPointerStart(absoluteScreenX)
            onDragStart()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            onDragPointerChange(dragAmount.x)

            accumulatedPx += dragAmount.x
            val ppms = pixelsPerMs()
            val deltaMs = (accumulatedPx / ppms).toLong()
            if (deltaMs != 0L) {
                accumulatedPx -= deltaMs * ppms

                val duration = currentEndMs - currentStartMs
                val newStart = (currentStartMs + deltaMs).coerceIn(0L, totalDurationMs() - duration)
                if (newStart != currentStartMs) {
                    currentStartMs = newStart
                    currentEndMs = newStart + duration
                    onPositionChange(currentStartMs, currentEndMs)
                }
            }
        },
        onDragEnd = { onDragEnd(currentStartMs, currentEndMs) },
        onDragCancel = { onDragEnd(currentStartMs, currentEndMs) },
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
    onDragPointerChange: (deltaX: Float) -> Unit,
    onEdgeChange: (newEdgeMs: Long) -> Unit,
    onDragEnd: (finalEdgeMs: Long) -> Unit,
): Modifier = this.pointerInput(key1, key2) {
    var accumulatedPx = 0f
    var edgeMs = 0L

    detectDragGestures(
        onDragStart = { offset ->
            edgeMs = currentEdgeMs()
            accumulatedPx = 0f

            val edgeScreenX = offset.x + (edgeMs * pixelsPerMs()) - scrollStateValue()
            onDragPointerStart(edgeScreenX)
            onDragStart()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            onDragPointerChange(dragAmount.x)

            accumulatedPx += dragAmount.x
            val ppms = pixelsPerMs()
            val deltaMs = (accumulatedPx / ppms).toLong()
            if (deltaMs != 0L) {
                accumulatedPx -= deltaMs * ppms

                val newEdge = (edgeMs + deltaMs).coerceIn(minEdgeMs(), maxEdgeMs())
                if (newEdge != edgeMs) {
                    edgeMs = newEdge
                    onEdgeChange(edgeMs)
                }
            }
        },
        onDragEnd = { onDragEnd(edgeMs) },
        onDragCancel = { onDragEnd(edgeMs) },
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

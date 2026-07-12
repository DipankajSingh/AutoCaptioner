package com.dipdev.aiautocaptioner.ui.videoeditor.timeline

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

// ---------------------------------------------------------------------------
// TimelineDragGestures.kt
//
// Reusable Modifier extensions that encode the two gesture models used by
// every timeline track item:
//
//  1. timelineMoveGesture  – long-press then drag to reposition a clip in
//                            free-floating tracks (image overlay, text, audio).
//                            Reports accumulated-pixel→ms deltas so the caller
//                            never has to touch raw pixels.
//
//  2. timelineTrimGesture  – immediate drag to extend or shrink one edge of a
//                            clip (left/right trim handles on every track).
//
// Both modifiers report a screen-space pointer X coordinate to the parent
// TimelineView via [onDragPointerStart] / [onDragPointerChange] so the single
// edge-scroll LaunchedEffect there can auto-scroll regardless of which track
// type is being dragged.
//
// Performance notes:
//  • All mutable state lives in plain local vars inside the gesture coroutine
//    (no State objects → no extra recompositions while dragging).
//  • The accumulated-fractional-pixel technique avoids Int-truncation jitter.
//  • Keys are forwarded to pointerInput so Compose can skip re-subscribing when
//    unrelated state changes trigger recomposition.
// ---------------------------------------------------------------------------

/**
 * Attaches a **long-press → drag** gesture that lets the user reposition a
 * free-floating timeline clip (e.g. image overlay, future text/audio track).
 *
 * @param key1 / key2   Stable identity keys forwarded to [pointerInput].
 *                      Pass the clip's id plus any value that should reset
 *                      gesture state when it changes (e.g. pixelsPerMs).
 * @param pixelsPerMs   Current zoom-derived scale factor.
 * @param startTimeMs   The clip's current start time in milliseconds.
 * @param endTimeMs     The clip's current end time in milliseconds.
 * @param totalDurationMs  Maximum allowed end time (the edited video length).
 * @param scrollStateValue Horizontal scroll offset in pixels (read-only snapshot).
 * @param onDragStart   Called once when the long-press drag begins.
 * @param onDragPointerStart  Reports the initial screen-X of the pointer so
 *                            the parent's edge-scroll loop knows where we are.
 *                            Pass the clip centre in screen-X: (centreMs * pxPerMs) - scroll.
 * @param onDragPointerChange Reports the delta screen-X on each drag event so
 *                            the parent can keep its [dragPointerScreenX] updated.
 * @param onPositionChange    Called whenever the clip should move.  Receives the
 *                            new (startMs, endMs) — already clamped and debounced.
 * @param onDragEnd     Called when the gesture ends (finger up or cancelled).
 *                      Receives the final (startMs, endMs) to persist to the DB.
 */
fun Modifier.timelineMoveGesture(
    key1: Any,
    key2: Any = Unit,
    pixelsPerMs: Float,
    startTimeMs: Long,
    endTimeMs: Long,
    totalDurationMs: Long,
    scrollStateValue: Int,
    onDragStart: () -> Unit = {},
    onDragPointerStart: (screenX: Float) -> Unit,
    onDragPointerChange: (deltaX: Float) -> Unit,
    onPositionChange: (newStartMs: Long, newEndMs: Long) -> Unit,
    onDragEnd: (finalStartMs: Long, finalEndMs: Long) -> Unit,
): Modifier = this.pointerInput(key1, key2) {
    // All state is local to the gesture coroutine — zero Compose State allocations.
    var accumulatedPx = 0f
    var currentStartMs = 0L
    var currentEndMs = 0L

    detectDragGesturesAfterLongPress(
        onDragStart = {
            currentStartMs = startTimeMs
            currentEndMs = endTimeMs
            accumulatedPx = 0f

            val centerScreenX = ((currentStartMs + currentEndMs) / 2f * pixelsPerMs) - scrollStateValue
            onDragPointerStart(centerScreenX)
            onDragStart()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            onDragPointerChange(dragAmount.x)

            accumulatedPx += dragAmount.x
            val deltaMs = (accumulatedPx / pixelsPerMs).toLong()
            if (deltaMs != 0L) {
                // Consume only the whole-millisecond portion to avoid jitter.
                accumulatedPx -= deltaMs * pixelsPerMs

                val duration = currentEndMs - currentStartMs
                val newStart = (currentStartMs + deltaMs).coerceIn(0L, totalDurationMs - duration)
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
 *
 * Unlike [timelineMoveGesture], this uses [detectDragGestures] (no long-press
 * required) because the touch target is a dedicated narrow strip that cannot be
 * confused with a scroll.
 *
 * @param key1 / key2   Stable identity keys forwarded to [pointerInput].
 * @param pixelsPerMs   Current zoom-derived scale factor.
 * @param currentEdgeMs The edge being trimmed (startTimeMs for left handle,
 *                      endTimeMs for right handle).
 * @param minEdgeMs     Minimum allowed value for [currentEdgeMs] after trimming.
 * @param maxEdgeMs     Maximum allowed value for [currentEdgeMs] after trimming.
 * @param scrollStateValue  Horizontal scroll offset in pixels (read-only snapshot).
 * @param onDragPointerStart  Reports the initial screen-X of this handle edge.
 * @param onDragPointerChange Reports the delta screen-X on each drag event.
 * @param onEdgeChange  Called whenever the edge should update, with the new
 *                      clamped edge time in milliseconds.
 * @param onDragEnd     Called when the gesture ends. Receives the final edge time.
 */
fun Modifier.timelineTrimGesture(
    key1: Any,
    key2: Any = Unit,
    pixelsPerMs: Float,
    currentEdgeMs: Long,
    minEdgeMs: Long,
    maxEdgeMs: Long,
    scrollStateValue: Int,
    onDragStart: () -> Unit = {},
    onDragPointerStart: (screenX: Float) -> Unit,
    onDragPointerChange: (deltaX: Float) -> Unit,
    onEdgeChange: (newEdgeMs: Long) -> Unit,
    onDragEnd: (finalEdgeMs: Long) -> Unit,
): Modifier = this.pointerInput(key1, key2) {
    var accumulatedPx = 0f
    var edgeMs = 0L

    detectDragGestures(
        onDragStart = {
            edgeMs = currentEdgeMs
            accumulatedPx = 0f

            val edgeScreenX = edgeMs * pixelsPerMs - scrollStateValue
            onDragPointerStart(edgeScreenX)
            onDragStart()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            onDragPointerChange(dragAmount.x)

            accumulatedPx += dragAmount.x
            val deltaMs = (accumulatedPx / pixelsPerMs).toLong()
            if (deltaMs != 0L) {
                accumulatedPx -= deltaMs * pixelsPerMs

                val newEdge = (edgeMs + deltaMs).coerceIn(minEdgeMs, maxEdgeMs)
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

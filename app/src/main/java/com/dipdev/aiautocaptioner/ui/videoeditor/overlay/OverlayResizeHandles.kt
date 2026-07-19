package com.dipdev.aiautocaptioner.ui.videoeditor.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import kotlin.math.roundToInt

private enum class HandleRole { CORNER, EDGE_H, EDGE_V }

@Composable
fun OverlayResizeHandles(
    boxWidthPx: Float,
    boxHeightPx: Float,
    scaleX: Float,
    scaleY: Float,
    onScaleChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleSizeDp = 12.dp

    val renderedW = boxWidthPx * scaleX
    val renderedH = boxHeightPx * scaleY

    data class Handle(val xDp: Int, val yDp: Int, val role: HandleRole)

    val halfWdp = with(density) { (renderedW / 2f).toDp().value.roundToInt() }
    val halfHdp = with(density) { (renderedH / 2f).toDp().value.roundToInt() }
    val handleHalfDp = handleSizeDp.value.roundToInt() / 2 + 2

    val handles = listOf(
        Handle(-halfWdp - handleHalfDp, -halfHdp - handleHalfDp, HandleRole.CORNER),
        Handle(halfWdp + handleHalfDp, -halfHdp - handleHalfDp, HandleRole.CORNER),
        Handle(-halfWdp - handleHalfDp, halfHdp + handleHalfDp, HandleRole.CORNER),
        Handle(halfWdp + handleHalfDp, halfHdp + handleHalfDp, HandleRole.CORNER),
        Handle(0, -halfHdp - handleHalfDp, HandleRole.EDGE_V),
        Handle(0, halfHdp + handleHalfDp, HandleRole.EDGE_V),
        Handle(-halfWdp - handleHalfDp, 0, HandleRole.EDGE_H),
        Handle(halfWdp + handleHalfDp, 0, HandleRole.EDGE_H),
    )

    handles.forEach { handle ->
        var dragAccumX by remember(handle) { mutableFloatStateOf(0f) }
        var dragAccumY by remember(handle) { mutableFloatStateOf(0f) }
        var isDragging by remember(handle) { mutableStateOf(false) }

        Box(
            modifier = modifier
                .offset { IntOffset(handle.xDp, handle.yDp) }
                .size(handleSizeDp)
                .background(
                    color = if (isDragging) AccentCyan else Color.White.copy(alpha = 0.9f),
                    shape = CircleShape
                )
                .border(width = 1.5.dp, color = AccentCyan, shape = CircleShape)
                .pointerInput(handle.role) {
                    detectDragGestures(
                        onDragStart = {
                            dragAccumX = 0f
                            dragAccumY = 0f
                            isDragging = true
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumX += dragAmount.x
                            dragAccumY += dragAmount.y

                            when (handle.role) {
                                HandleRole.EDGE_H -> {
                                    val delta = dragAccumX * scaleX / boxWidthPx
                                    onScaleChange(scaleX + delta, scaleY)
                                    dragAccumX = 0f
                                }
                                HandleRole.EDGE_V -> {
                                    val delta = dragAccumY * scaleY / boxHeightPx
                                    onScaleChange(scaleX, scaleY + delta)
                                    dragAccumY = 0f
                                }
                                HandleRole.CORNER -> {
                                    val dx = dragAccumX * scaleX / boxWidthPx
                                    val dy = dragAccumY * scaleY / boxHeightPx
                                    val avg = (dx + dy) / 2f
                                    onScaleChange(scaleX + avg, scaleY + avg)
                                    dragAccumX = 0f
                                    dragAccumY = 0f
                                }
                            }
                        }
                    )
                }
        )
    }
}

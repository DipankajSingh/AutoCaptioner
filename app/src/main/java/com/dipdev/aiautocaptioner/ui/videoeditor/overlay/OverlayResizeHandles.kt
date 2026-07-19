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
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MIN_SCALE = 0.05f
private const val MAX_SCALE = 5.0f
private const val HANDLE_VISUAL_DP = 16
private const val HANDLE_TOUCH_DP = 48
private const val HANDLE_MARGIN_PX = 10f

@Composable
fun OverlayResizeHandle(
    canvasWidth: Float,
    canvasHeight: Float,
    posX: Float,
    posY: Float,
    boxWidthPx: Float,
    boxHeightPx: Float,
    scaleX: Float,
    scaleY: Float,
    onScaleChange: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val imgCenterX = posX * canvasWidth
    val imgCenterY = posY * canvasHeight
    val renderedHalfW = boxWidthPx * scaleX / 2f
    val renderedHalfH = boxHeightPx * scaleY / 2f

    val handleCanvasX = imgCenterX + renderedHalfW + HANDLE_MARGIN_PX
    val handleCanvasY = imgCenterY + renderedHalfH + HANDLE_MARGIN_PX

    var isDragging by remember { mutableStateOf(false) }
    var cumDragX by remember { mutableFloatStateOf(0f) }
    var cumDragY by remember { mutableFloatStateOf(0f) }
    var initialScale by remember { mutableFloatStateOf(1f) }
    var initialDist by remember { mutableFloatStateOf(1f) }
    var initPointerX by remember { mutableFloatStateOf(0f) }
    var initPointerY by remember { mutableFloatStateOf(0f) }
    var initCenterX by remember { mutableFloatStateOf(0f) }
    var initCenterY by remember { mutableFloatStateOf(0f) }

    val halfTouchPx = with(density) { HANDLE_TOUCH_DP.dp.toPx() / 2f }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (handleCanvasX - halfTouchPx).roundToInt(),
                    (handleCanvasY - halfTouchPx).roundToInt()
                )
            }
            .size(HANDLE_TOUCH_DP.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { localOffset ->
                        isDragging = true
                        initialScale = scaleX
                        initCenterX = imgCenterX
                        initCenterY = imgCenterY
                        initPointerX = handleCanvasX + localOffset.x - halfTouchPx
                        initPointerY = handleCanvasY + localOffset.y - halfTouchPx
                        initialDist = sqrt(
                            (initPointerX - imgCenterX).pow(2) +
                                    (initPointerY - imgCenterY).pow(2)
                        ).coerceAtLeast(1f)
                        cumDragX = 0f
                        cumDragY = 0f
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        cumDragX += dragAmount.x
                        cumDragY += dragAmount.y

                        val curX = initPointerX + cumDragX
                        val curY = initPointerY + cumDragY
                        val curDist = sqrt(
                            (curX - initCenterX).pow(2) +
                                    (curY - initCenterY).pow(2)
                        )
                        val ratio = curDist / initialDist
                        val newScale = (initialScale * ratio).coerceIn(MIN_SCALE, MAX_SCALE)
                        onScaleChange(newScale, newScale)
                    }
                )
            }
    ) {
        val dotOffset = with(density) { ((HANDLE_TOUCH_DP - HANDLE_VISUAL_DP).dp.toPx() / 2f).roundToInt() }
        Box(
            modifier = Modifier
                .offset { IntOffset(dotOffset, dotOffset) }
                .size(HANDLE_VISUAL_DP.dp)
                .background(
                    color = if (isDragging) AccentCyan else Color.White,
                    shape = CircleShape
                )
                .border(width = 2.dp, color = AccentCyan, shape = CircleShape)
        )
    }
}

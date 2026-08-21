package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun PremiumSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    var internalRatio by remember { 
        mutableFloatStateOf(((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)) 
    }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDragging) {
            internalRatio = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        }
    }
    
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val fillColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    internalRatio = (internalRatio + dragAmount / size.width).coerceIn(0f, 1f)
                    val realValue = valueRange.start + internalRatio * (valueRange.endInclusive - valueRange.start)
                    onValueChange(realValue)
                }
            }
            .pointerInput(valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        val newRatio = (offset.x / size.width).coerceIn(0f, 1f)
                        val realValue = valueRange.start + newRatio * (valueRange.endInclusive - valueRange.start)
                        onValueChange(realValue)
                    }
                )
            }
    ) {
        val trackHeight = 2.dp.toPx()
        val cornerRadius = CornerRadius(trackHeight / 2f)
        val cy = size.height / 2f

        drawRoundRect(
            color = trackColor,
            size = Size(width = size.width, height = trackHeight),
            topLeft = Offset(0f, cy - trackHeight / 2f),
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = fillColor,
            size = Size(width = size.width * internalRatio, height = trackHeight),
            topLeft = Offset(0f, cy - trackHeight / 2f),
            cornerRadius = cornerRadius
        )
        drawCircle(
            color = thumbColor,
            radius = 10.dp.toPx(),
            center = Offset(size.width * internalRatio, cy)
        )
        drawCircle(
            color = androidx.compose.ui.graphics.Color.White,
            radius = 4.dp.toPx(),
            center = Offset(size.width * internalRatio, cy)
        )
    }
}

@Composable
fun VerticalPremiumSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    var internalRatio by remember {
        mutableFloatStateOf(((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f))
    }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isDragging) {
            internalRatio = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBackgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Canvas(
        modifier = modifier
            .width(36.dp)
            .height(220.dp)
            .pointerInput(valueRange) {
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    internalRatio = (internalRatio - dragAmount / size.height).coerceIn(0f, 1f)
                    val realValue = valueRange.start + internalRatio * (valueRange.endInclusive - valueRange.start)
                    onValueChange(realValue)
                }
            }
            .pointerInput(valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        val newRatio = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                        val realValue = valueRange.start + newRatio * (valueRange.endInclusive - valueRange.start)
                        onValueChange(realValue)
                    }
                )
            }
    ) {
        val trackWidth = 2.dp.toPx()
        val trackCornerRadius = CornerRadius(trackWidth / 2f)
        val cx = size.width / 2f
        val thumbY = size.height * (1f - internalRatio)
        val thumbRadius = 10.dp.toPx()

        // Draw background track
        drawRoundRect(
            color = trackBackgroundColor,
            size = Size(width = trackWidth, height = size.height),
            topLeft = Offset(cx - trackWidth / 2f, 0f),
            cornerRadius = trackCornerRadius
        )

        // Draw filled track
        drawRoundRect(
            color = primaryColor,
            size = Size(width = trackWidth, height = size.height * internalRatio),
            topLeft = Offset(cx - trackWidth / 2f, size.height * (1f - internalRatio)),
            cornerRadius = trackCornerRadius
        )

        // Draw thumb base
        drawCircle(
            color = primaryColor,
            radius = thumbRadius,
            center = Offset(cx, thumbY)
        )
        
        // Draw inner white dot
        drawCircle(
            color = androidx.compose.ui.graphics.Color.White,
            radius = 4.dp.toPx(),
            center = Offset(cx, thumbY)
        )

    }
}

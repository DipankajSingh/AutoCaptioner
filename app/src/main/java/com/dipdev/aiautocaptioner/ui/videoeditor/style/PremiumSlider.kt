package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LabeledPremiumSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        PremiumSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
    }
}

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
    
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
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
        val trackHeight = 4.dp.toPx()
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
            radius = 14.dp.toPx(),
            center = Offset(size.width * internalRatio, cy)
        )
    }
}

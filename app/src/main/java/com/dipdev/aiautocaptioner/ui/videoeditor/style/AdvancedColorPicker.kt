package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun AdvancedColorPicker(
    initialColor: Long,
    onColorChanged: (Long) -> Unit
) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(initialColor.toInt(), hsv)
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(horizontal = 24.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                    value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onColorChanged(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong())
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    saturation = (offset.x / size.width).coerceIn(0f, 1f)
                    value = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    onColorChanged(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong())
                }
            }
        ) {
            val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            drawRoundRect(Brush.horizontalGradient(listOf(Color.White, hueColor)), cornerRadius = CornerRadius(16f))
            drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), cornerRadius = CornerRadius(16f))
            
            drawCircle(
                color = Color.White,
                radius = 18.dp.toPx(),
                center = Offset(saturation * size.width, (1f - value) * size.height),
                style = Stroke(width = 4.dp.toPx())
            )
            drawCircle(
                color = Color.Black,
                radius = 20.dp.toPx(),
                center = Offset(saturation * size.width, (1f - value) * size.height),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        val rainbow = listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(28.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    hue = ((change.position.x / size.width) * 360f).coerceIn(0f, 360f)
                    onColorChanged(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong())
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    hue = ((offset.x / size.width) * 360f).coerceIn(0f, 360f)
                    onColorChanged(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong())
                }
            }
        ) {
            drawRoundRect(Brush.horizontalGradient(rainbow), cornerRadius = CornerRadius(size.height / 2f))
            
            drawCircle(
                color = Color.White,
                radius = size.height / 2f + 6.dp.toPx(),
                center = Offset((hue / 360f) * size.width, size.height / 2f),
                style = Stroke(width = 6.dp.toPx())
            )
        }
    }
}

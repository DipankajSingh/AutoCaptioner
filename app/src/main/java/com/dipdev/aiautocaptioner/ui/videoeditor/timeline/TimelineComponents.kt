package com.dipdev.aiautocaptioner.ui.videoeditor.timeline

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber

@SuppressLint("DefaultLocale")
@Composable
fun TimelineRuler(
    totalEditedMs: Long,
    pixelsPerMs: Float,
    totalWidthDp: Dp,
    zoomLevel: Float,
    textMeasurer: TextMeasurer,
    onSurfaceColor: Color
) {
    Canvas(modifier = Modifier.width(totalWidthDp).height(40.dp)) {
        val durationSec = totalEditedMs / 1000
        for (i in 0..durationSec) {
            val x = i * 1000 * pixelsPerMs
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.7f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2f
            )
            for (j in 1..4) {
                val subX = x + (j * 200 * pixelsPerMs)
                if (subX <= size.width) {
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.25f),
                        start = Offset(subX, size.height - 10f),
                        end = Offset(subX, size.height),
                        strokeWidth = 1f
                    )
                }
            }
            if (zoomLevel >= 1f || i % 5 == 0L || durationSec < 10) {
                val timeText = String.format("%02d:%02d", i / 60, i % 60)
                val layoutResult = textMeasurer.measure(
                    text = timeText,
                    style = TextStyle(color = AccentAmber, fontSize = 10.sp)
                )
                drawText(textLayoutResult = layoutResult, topLeft = Offset(x + 4f, 4f))
            }
        }
    }
}

@Composable
fun VideoClipItem(
    clip: Clip,
    index: Int,
    isSelected: Boolean,
    clipWidthPx: Float,
    clipWidthDp: Dp,
    isBeingDragged: Boolean,
    currentDragOffset: Float,
    clipLayoutCenters: FloatArray,
    scrollStateValue: Int,
    surfaceVariantColor: Color,
    outlineColor: Color,
    thumbnails: List<Bitmap>?,
    onDragStateChange: (Boolean) -> Unit,
    onDragPointerStart: (Float) -> Unit,
    onDragPointerChange: (Float) -> Unit,
    onCheckSwaps: () -> Unit,
    onDraggingIndexChange: (Int?) -> Unit,
    onClipSelected: (String) -> Unit,
    hasGapBefore: Boolean
) {
    Box(
        modifier = Modifier
            .width(clipWidthDp)
            .fillMaxHeight()
            .zIndex(if (isBeingDragged) 1f else 0f)
            .offset { IntOffset(currentDragOffset.toInt(), 0) }
            .graphicsLayer {
                if (isBeingDragged) {
                    scaleX = 1.05f
                    scaleY = 1.05f
                    shadowElevation = 8.dp.toPx()
                    shape = RoundedCornerShape(4.dp)
                    this.clip = true
                }
            }
            .padding(horizontal = 1.dp) // slight gap between clips
            .clip(RoundedCornerShape(4.dp))
            .background(surfaceVariantColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AccentAmber else outlineColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(clip.id, clipWidthPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onDraggingIndexChange(index)
                        onDragStateChange(true)
                        onDragPointerStart(clipLayoutCenters[index] - scrollStateValue)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragPointerChange(dragAmount.x)
                        onCheckSwaps()
                    },
                    onDragEnd = {
                        onDraggingIndexChange(null)
                        onDragStateChange(false)
                    },
                    onDragCancel = {
                        onDraggingIndexChange(null)
                        onDragStateChange(false)
                    }
                )
            }
            .clickable { onClipSelected(clip.id) }
    ) {
        if (!thumbnails.isNullOrEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                thumbnails.forEach { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
        
        // Dark overlay for readability
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
        
        // Selected Overlay
        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(AccentAmber.copy(alpha = 0.1f)))
        }

        // Drag Grip
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                )
            }
        }
        
        // Name Label
        Text(
            text = "Clip ${index + 1}",
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 4.dp)
        )
        
        // Dashed gap line indicator if there's a gap from previous clip
        if (hasGapBefore) {
            Canvas(modifier = Modifier.fillMaxHeight().width(2.dp).align(Alignment.CenterStart)) {
                drawLine(
                    color = AccentAmber,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }
        }
    }
}

@Composable
fun CaptionOverlayItem(
    overlay: ImageOverlayEntity,
    isSelectedOverlay: Boolean,
    overlayOffsetXDp: Dp,
    overlayWidthDp: Dp,
    pixelsPerMs: Float,
    currentEndTimeMs: Long,
    totalEditedMs: Long,
    primaryColor: Color,
    onOverlaySelected: (String) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    onOverlayTimingChanged: (String, Long, Long) -> Unit,
    onCaptionTap: () -> Unit
) {
    val updatedOverlay by rememberUpdatedState(overlay)
    val updatedEndTimeMs by rememberUpdatedState(currentEndTimeMs)

    Box(
        modifier = Modifier
            .offset(x = overlayOffsetXDp)
            .width(overlayWidthDp)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelectedOverlay) AccentAmber.copy(alpha = 0.5f) else primaryColor.copy(alpha = 0.3f))
            .border(
                width = if (isSelectedOverlay) 2.dp else 1.dp,
                color = if (isSelectedOverlay) AccentAmber else primaryColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(overlay.id) {
                var dragType = 0
                var accumulatedDeltaX = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        onOverlaySelected(updatedOverlay.id)
                        val edgeWidth = 20.dp.toPx()
                        dragType = when {
                            offset.x <= edgeWidth -> 1
                            offset.x >= size.width - edgeWidth -> 2
                            else -> 3
                        }
                        accumulatedDeltaX = 0f
                        onDragStateChange(true)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDeltaX += dragAmount.x
                        val deltaMs = (accumulatedDeltaX / pixelsPerMs).toLong()
                        if (deltaMs != 0L) {
                            accumulatedDeltaX -= (deltaMs * pixelsPerMs)
                            val oStart = updatedOverlay.startTimeMs
                            val oEnd = updatedEndTimeMs
                            when (dragType) {
                                1 -> {
                                    val newStart = (oStart + deltaMs).coerceIn(0L, oEnd - 100L)
                                    onOverlayTimingChanged(updatedOverlay.id, newStart, updatedOverlay.endTimeMs)
                                }
                                2 -> {
                                    val newEnd = (oEnd + deltaMs).coerceIn(oStart + 100L, totalEditedMs)
                                    onOverlayTimingChanged(updatedOverlay.id, oStart, if (updatedOverlay.endTimeMs == Long.MAX_VALUE) Long.MAX_VALUE else newEnd)
                                }
                                3 -> {
                                    val dur = oEnd - oStart
                                    val newStart = (oStart + deltaMs).coerceIn(0L, totalEditedMs - dur)
                                    val newEnd = newStart + dur
                                    val finalEnd = if (updatedOverlay.endTimeMs == Long.MAX_VALUE) Long.MAX_VALUE else newEnd
                                    onOverlayTimingChanged(updatedOverlay.id, newStart, finalEnd)
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        dragType = 0
                        onDragStateChange(false)
                    },
                    onDragCancel = {
                        dragType = 0
                        onDragStateChange(false)
                    }
                )
            }
            .clickable { 
                onOverlaySelected(overlay.id)
                onCaptionTap()
            }
    ) {
        if (isSelectedOverlay) {
            Box(modifier = Modifier.align(Alignment.CenterStart).width(10.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.3f)))
            Box(modifier = Modifier.align(Alignment.CenterEnd).width(10.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.3f)))
        }
    }
}

@Composable
fun PlayheadMarker() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
    ) {
        // Triangle handle at top
        Canvas(
            modifier = Modifier
                .width(14.dp)
                .height(10.dp)
                .align(Alignment.TopCenter)
        ) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width / 2f, size.height)
                lineTo(0f, 0f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(path, color = AccentAmber)
        }
        // The needle line
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(AccentAmber)
                .align(Alignment.Center)
        )
    }
}

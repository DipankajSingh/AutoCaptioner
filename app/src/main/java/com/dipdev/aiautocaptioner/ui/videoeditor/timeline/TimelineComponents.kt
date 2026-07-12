package com.dipdev.aiautocaptioner.ui.videoeditor.timeline

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    Canvas(modifier = Modifier.width(totalWidthDp).height(20.dp)) {
        val durationSec = totalEditedMs / 1000
        for (i in 0..durationSec) {
            val x = i * 1000 * pixelsPerMs
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.5f),
                start = Offset(x, size.height - 10f),
                end = Offset(x, size.height),
                strokeWidth = 2f
            )
            for (j in 1..4) {
                val subX = x + (j * 200 * pixelsPerMs)
                if (subX <= size.width) {
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.25f),
                        start = Offset(subX, size.height - 5f),
                        end = Offset(subX, size.height),
                        strokeWidth = 1f
                    )
                }
            }
            if (zoomLevel >= 1f || i % 5 == 0L || durationSec < 10) {
                val timeText = String.format("%02d:%02d", i / 60, i % 60)
                val layoutResult = textMeasurer.measure(
                    text = timeText,
                    style = TextStyle(color = onSurfaceColor.copy(alpha = 0.7f), fontSize = 8.sp)
                )
                drawText(textLayoutResult = layoutResult, topLeft = Offset(x + 2f, 0f))
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
    thumbnails: Map<Long, Bitmap>,
    originalDurationMs: Long,
    onDragStateChange: (Boolean) -> Unit,
    onDragPointerStart: (Float) -> Unit,
    onDragPointerChange: (Float) -> Unit,
    onCheckSwaps: () -> Unit,
    onDraggingIndexChange: (Int?) -> Unit,
    onClipSelected: (String) -> Unit,
    hasGapBefore: Boolean,
    onTrimClip: (String, Long, Long) -> Unit,
    pixelsPerMs: Float,
    thumbnailIntervalMs: Long
) {
    val density = LocalDensity.current
    val updatedClip by rememberUpdatedState(clip)
    
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color.White else surfaceVariantColor)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.White else outlineColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isSelected) 16.dp else 14.dp,
                    vertical = if (isSelected) 2.dp else 2.dp
                )
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            if (thumbnails.isNotEmpty() && originalDurationMs > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth() // Respect inner padded width
                ) {
                    val paddingPx = with(density) { (if (isSelected) 16.dp else 14.dp).toPx() }
                    
                    val relevantThumbnails = remember(thumbnails, thumbnailIntervalMs) {
                        thumbnails
                            .filterKeys { it % thumbnailIntervalMs == 0L }
                            .toSortedMap()
                    }
                        
                    relevantThumbnails.forEach { (timeMs, bitmap) ->
                        // Offset by paddingPx so the thumbnail coordinates align with the outer unpadded clip bounds
                        val localXPx = ((timeMs - clip.startTrimMs) * pixelsPerMs) - paddingPx
                        val thumbWidthPx = thumbnailIntervalMs * pixelsPerMs
                        
                        // Only render if thumbnail is visible within the clip's trimmed bounds
                        if (localXPx + thumbWidthPx > 0 && localXPx < clipWidthPx) {
                            androidx.compose.runtime.key(timeMs) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .absoluteOffset { IntOffset(localXPx.toInt(), 0) }
                                        .width(with(density) { thumbWidthPx.toDp() })
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
            
            // Dark overlay for readability
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            
            // Selected Overlay
            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(AccentAmber.copy(alpha = 0.1f)))
            }
        }

        // Trim Handles and Selected State Overlays
        if (isSelected) {
            // Left Trim Handle Touch Target & Grip
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(16.dp)
                    .timelineTrimGesture(
                        key1 = clip.id,
                        key2 = "left",
                        pixelsPerMs      = pixelsPerMs,
                        currentEdgeMs    = clip.startTrimMs,
                        minEdgeMs        = 0L,
                        maxEdgeMs        = updatedClip.endTrimMs - 100L,
                        scrollStateValue = scrollStateValue,
                        onDragStart  = { onDragStateChange(true) },
                        // Video trim handles report no pointer to the parent edge-scroll loop
                        // (that loop only runs during swap-drags, not trim).
                        onDragPointerStart  = {},
                        onDragPointerChange = {},
                        onEdgeChange = { newStart -> onTrimClip(clip.id, newStart, updatedClip.endTrimMs) },
                        onDragEnd    = { onDragStateChange(false) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.width(2.dp).height(14.dp).background(AccentAmber, RoundedCornerShape(50)))
            }
            
            // Right Trim Handle Touch Target & Grip
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(16.dp)
                    .timelineTrimGesture(
                        key1 = clip.id,
                        key2 = "right",
                        pixelsPerMs      = pixelsPerMs,
                        currentEdgeMs    = clip.endTrimMs,
                        minEdgeMs        = updatedClip.startTrimMs + 100L,
                        maxEdgeMs        = originalDurationMs,
                        scrollStateValue = scrollStateValue,
                        onDragStart  = { onDragStateChange(true) },
                        onDragPointerStart  = {},
                        onDragPointerChange = {},
                        onEdgeChange = { newEnd -> onTrimClip(clip.id, updatedClip.startTrimMs, newEnd) },
                        onDragEnd    = { onDragStateChange(false) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.width(2.dp).height(14.dp).background(AccentAmber, RoundedCornerShape(50)))
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

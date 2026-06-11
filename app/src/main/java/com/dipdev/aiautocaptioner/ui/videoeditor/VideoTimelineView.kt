package com.dipdev.aiautocaptioner.ui.videoeditor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import android.graphics.Bitmap
import com.dipdev.aiautocaptioner.data.model.Clip

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoTimelineView(
    clips: List<Clip>,
    clipThumbnails: Map<String, List<Bitmap>>,
    selectedClipId: String?,
    onClipSelected: (String) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }

    LazyRow(
        modifier = modifier.height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(clips, key = { _, clip -> clip.id }) { index, clip ->
            val isSelected = clip.id == selectedClipId
            val isBeingDragged = index == draggedIndex

            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isBeingDragged) {
                            translationX = dragOffsetX
                            scaleX = 1.05f
                            scaleY = 1.05f
                            alpha = 0.8f
                        }
                    }
                    // We use animateItem() from ExperimentalFoundationApi to handle smooth swaps
                    .animateItem()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onClipSelected(clip.id) }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { 
                                draggedIndex = index 
                                dragOffsetX = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                
                                // Simple swap logic
                                val itemWidthPx = 100.dp.toPx()
                                if (dragOffsetX > itemWidthPx && index < clips.size - 1) {
                                    onMoveClip(index, index + 1)
                                    draggedIndex = index + 1
                                    dragOffsetX -= itemWidthPx
                                } else if (dragOffsetX < -itemWidthPx && index > 0) {
                                    onMoveClip(index, index - 1)
                                    draggedIndex = index - 1
                                    dragOffsetX += itemWidthPx
                                }
                            },
                            onDragEnd = {
                                draggedIndex = null
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                draggedIndex = null
                                dragOffsetX = 0f
                            }
                        )
                    }
            ) {
                val thumbnails = clipThumbnails[clip.id]
                if (!thumbnails.isNullOrEmpty()) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        thumbnails.forEach { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Clip ${index + 1}",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import compose.icons.FeatherIcons
import compose.icons.feathericons.Camera
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import kotlinx.coroutines.launch

@Composable
fun IntegratedFilterShutter(
    activeFilter: CreatorFilter,
    onFilterSelected: (CreatorFilter) -> Unit,
    isRecording: Boolean,
    onRecordClick: () -> Unit,
    modifier: Modifier = Modifier,
    filters: List<CreatorFilter> = CreatorFilter.entries
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // The base size of the items
    val itemWidth = 64.dp 
    val horizontalPadding = (screenWidth / 2) - (itemWidth / 2)

    // Snap behavior
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Calculate center item continuously for auto-select
    val centerItemIndex by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val center = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.width / 2
            var closestItemIndex = 0
            var minDistance = Int.MAX_VALUE
            for (item in layoutInfo.visibleItemsInfo) {
                val itemCenter = item.offset + item.size / 2
                val distance = Math.abs(itemCenter - center)
                if (distance < minDistance) {
                    minDistance = distance
                    closestItemIndex = item.index
                }
            }
            closestItemIndex
        }
    }

    // Auto-select filter when scrolling stops
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val filter = filters.getOrNull(centerItemIndex)
            if (filter != null && filter != activeFilter) {
                onFilterSelected(filter)
            }
        }
    }

    // Programmatically scroll if external change
    var programmaticScroll by remember { mutableStateOf(false) }
    LaunchedEffect(activeFilter) {
        val index = filters.indexOf(activeFilter)
        if (index >= 0 && index != centerItemIndex) {
            programmaticScroll = true
            listState.animateScrollToItem(index)
            programmaticScroll = false
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxWidth()
    ) {
        // Layer 1: The sliding filter thumbnails
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items = filters, key = { _, filter -> filter.name }) { index, filter ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(itemWidth)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterPreviewCircle(
                            filter = filter,
                            modifier = Modifier.graphicsLayer {
                                val layoutInfo = listState.layoutInfo
                                val center = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.width / 2f
                                val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                                val fraction = if (itemInfo != null) {
                                    val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                    val distance = Math.abs(center - itemCenter)
                                    val maxDistance = itemInfo.size * 1.5f
                                    1f - (distance / maxDistance).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                
                                // Scale ranges from 0.75f (inactive) to 1.0f (active)
                                val scale = 0.75f + (0.25f * fraction)
                                scaleX = scale
                                scaleY = scale
                            }
                        )
                        
                        Text(
                            text = filter.displayName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                val layoutInfo = listState.layoutInfo
                                val center = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.width / 2f
                                val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                                val fraction = if (itemInfo != null) {
                                    val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                    val distance = Math.abs(center - itemCenter)
                                    val maxDistance = itemInfo.size * 1.0f
                                    1f - (distance / maxDistance).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                // Fade out text for non-active items
                                alpha = fraction
                            }
                        )
                    }
                }
            }
        }
        
        // Layer 2: The stationary shutter overlay (Hollow Ring)
        // Image top is at 24dp padding. Image height is 64dp.
        // Image center is at 24 + 32 = 56dp.
        // Ring height is 80dp. Ring center is at 40dp.
        // To align Ring center to Image center: Ring top = 56 - 40 = 16dp.
        HollowShutterRing(
            isRecording = isRecording,
            onClick = onRecordClick,
            modifier = Modifier.padding(top = 16.dp) 
        )
    }
}

@Composable
fun HollowShutterRing(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp) // Increased from 72 to 80 to leave a gap between image and ring
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw ONLY the pure white outline
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = (size.width / 2f) - 4.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
        }
    }
}

@Composable
private fun FilterPreviewCircle(
    filter: CreatorFilter,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp) // Base size, will be scaled down to 48dp by graphicsLayer when inactive
            .clip(CircleShape)
            .background(if (filter == CreatorFilter.NATURAL) Color.DarkGray.copy(alpha = 0.8f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (filter == CreatorFilter.NATURAL) {
            Icon(
                imageVector = compose.icons.FeatherIcons.Camera,
                contentDescription = filter.displayName,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Image(
                painter = painterResource(id = filter.drawableRes),
                contentDescription = filter.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import compose.icons.FeatherIcons
import compose.icons.feathericons.Aperture
import compose.icons.feathericons.Camera
import compose.icons.feathericons.Cloud
import compose.icons.feathericons.Film
import compose.icons.feathericons.Star
import compose.icons.feathericons.Sun
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalConfiguration

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
    // Padding to center the 64.dp items perfectly under the 72.dp shutter
    val itemWidth = 64.dp
    val horizontalPadding = (screenWidth / 2) - (itemWidth / 2)

    // Snap behavior
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Calculate center item continuously
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
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        // Layer 1: The sliding filter thumbnails
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
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
                    FilterPreviewCircle(filter = filter)
                }
            }
        }
        
        // Layer 2: The stationary shutter overlay (Hollow Ring)
        HollowShutterRing(
            isRecording = isRecording,
            onClick = onRecordClick
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
            .size(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = (size.width / 2f) * 0.86f
            
            // Draw ONLY the pure white outline
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = baseRadius - 6f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7f)
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
            .size(64.dp)
            .clip(CircleShape)
            .background(getFilterGradient(filter)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getFilterIcon(filter),
            contentDescription = filter.displayName,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun getFilterIcon(filter: CreatorFilter): ImageVector {
    return when (filter) {
        CreatorFilter.NATURAL -> FeatherIcons.Camera
        CreatorFilter.VIBRANT -> FeatherIcons.Star
        CreatorFilter.WARM_GLOW -> FeatherIcons.Sun
        CreatorFilter.STUDIO_BRIGHT -> FeatherIcons.Aperture
        CreatorFilter.CINEMATIC -> FeatherIcons.Film
        CreatorFilter.SOFT_PASTEL -> FeatherIcons.Cloud
        CreatorFilter.BLACK_AND_WHITE -> FeatherIcons.Camera // Or another icon like Moon if available
    }
}

private fun getFilterGradient(filter: CreatorFilter): Brush {
    return when (filter) {
        CreatorFilter.NATURAL -> Brush.linearGradient(listOf(Color(0xFF2B32B2), Color(0xFF1488CC)))
        CreatorFilter.VIBRANT -> Brush.linearGradient(listOf(Color(0xFFC850C0), Color(0xFF4158D0)))
        CreatorFilter.WARM_GLOW -> Brush.linearGradient(listOf(Color(0xFFFFCC70), Color(0xFFC850C0)))
        CreatorFilter.STUDIO_BRIGHT -> Brush.linearGradient(listOf(Color(0xFF56CCF2), Color(0xFF2F80ED)))
        CreatorFilter.CINEMATIC -> Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D)))
        CreatorFilter.SOFT_PASTEL -> Brush.linearGradient(listOf(Color(0xFFF64F59), Color(0xFFC471ED)))
        CreatorFilter.BLACK_AND_WHITE -> Brush.linearGradient(listOf(Color(0xFF434343), Color(0xFF000000)))
    }
}

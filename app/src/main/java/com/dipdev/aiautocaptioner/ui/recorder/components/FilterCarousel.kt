package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.delay

/**
 * Responsive, Instagram-style horizontal filter carousel designed for real-time 2026 studio recording.
 *
 * Implements aesthetic symbolic icons instead of ambiguous colors, smooth spring physics micro-animations,
 * dynamic border glow highlights, and a 12-second auto-dismiss timeout to ensure camera mode toggles
 * are never trapped underneath an abandoned filter tray.
 */
@Composable
fun FilterCarousel(
    activeFilter: CreatorFilter,
    onFilterSelected: (CreatorFilter) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    filters: List<CreatorFilter> = CreatorFilter.values().toList()
) {
    val listState = rememberLazyListState()
    var lastSelected by remember { mutableStateOf(activeFilter) }
    var interactCount by remember { mutableStateOf(0) }

    // Reset auto-dismiss timer whenever user interacts with filter items
    LaunchedEffect(activeFilter) {
        if (activeFilter != lastSelected) {
            lastSelected = activeFilter
            interactCount++
        }
    }

    // Automatically hide filter carousel after 12s of zero activity so mode switches remain accessible
    LaunchedEffect(interactCount) {
        delay(12000)
        onDismiss()
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        modifier = modifier.wrapContentHeight()
    ) {
        items(
            items = filters,
            key = { filter -> filter.name }
        ) { filter ->
            val isSelected = filter == activeFilter
            FilterCarouselItem(
                filter = filter,
                isSelected = isSelected,
                onClick = {
                    interactCount++
                    onFilterSelected(filter)
                }
            )
        }
    }
}

/**
 * Reusable individual filter chip featuring expressive icon symbolism, spring scaling, and glowing borders.
 */
@Composable
private fun FilterCarouselItem(
    filter: CreatorFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "filter_item_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFCC70) else Color.White.copy(alpha = 0.2f),
        animationSpec = tween(durationMillis = 250),
        label = "filter_border_color"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 250),
        label = "filter_border_width"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFCC70) else Color.White.copy(alpha = 0.85f),
        animationSpec = tween(durationMillis = 200),
        label = "filter_icon_tint"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.6f,
        animationSpec = tween(durationMillis = 200),
        label = "filter_text_alpha"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(getFilterGradient(filter))
                .border(width = borderWidth, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Inner dark glassmorphic badge layer to make icon crisp and prominent over gradient
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xD9121218)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFilterIcon(filter),
                    contentDescription = filter.displayName,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = filter.displayName,
            color = Color.White.copy(alpha = textAlpha),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Helper to match each filter type with an iconic photographic symbol for effortless visual recognition.
 */
private fun getFilterIcon(filter: CreatorFilter): ImageVector {
    return when (filter) {
        CreatorFilter.NATURAL -> FeatherIcons.Camera
        CreatorFilter.VIBRANT -> FeatherIcons.Star
        CreatorFilter.WARM_GLOW -> FeatherIcons.Sun
        CreatorFilter.STUDIO_BRIGHT -> FeatherIcons.Aperture
        CreatorFilter.CINEMATIC -> FeatherIcons.Film
        CreatorFilter.SOFT_PASTEL -> FeatherIcons.Cloud
    }
}

/**
 * Helper to produce vibrant gradient borders & halos corresponding to each filter tone.
 */
private fun getFilterGradient(filter: CreatorFilter): Brush {
    return when (filter) {
        CreatorFilter.NATURAL -> Brush.linearGradient(
            listOf(Color(0xFF2B32B2), Color(0xFF1488CC))
        )
        CreatorFilter.VIBRANT -> Brush.linearGradient(
            listOf(Color(0xFFC850C0), Color(0xFF4158D0))
        )
        CreatorFilter.WARM_GLOW -> Brush.linearGradient(
            listOf(Color(0xFFFFCC70), Color(0xFFC850C0))
        )
        CreatorFilter.STUDIO_BRIGHT -> Brush.linearGradient(
            listOf(Color(0xFF56CCF2), Color(0xFF2F80ED))
        )
        CreatorFilter.CINEMATIC -> Brush.linearGradient(
            listOf(Color(0xFF11998E), Color(0xFF38EF7D))
        )
        CreatorFilter.SOFT_PASTEL -> Brush.linearGradient(
            listOf(Color(0xFFF64F59), Color(0xFFC471ED))
        )
    }
}

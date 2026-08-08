package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Plus
import com.dipdev.aiautocaptioner.R

data class SpeedDialItem(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onColor: Color,
    val onClick: () -> Unit
)

@Composable
fun SpeedDialFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<SpeedDialItem>,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
    ) {
        // Items
        items.forEachIndexed { index, item ->
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(250, delayMillis = (items.size - index - 1) * 60)) +
                        slideInVertically(initialOffsetY = { 60 }, animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)) +
                        scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)),
                exit = fadeOut(animationSpec = tween(150)) +
                        scaleOut(targetScale = 0.8f) +
                        slideOutVertically(targetOffsetY = { 40 })
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 20.dp)
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                        label = "item_scale"
                    )

                    // Merged M3 Pill (Text + Icon)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .scale(scale)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    onExpandedChange(false)
                                    item.onClick()
                                }
                            )
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Icon Circle inside the pill
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(item.color)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = item.onColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Main FAB (M3 Themed Glow)
        val mainInteractionSource = remember { MutableInteractionSource() }
        val mainIsPressed by mainInteractionSource.collectIsPressedAsState()
        val mainScale by animateFloatAsState(
            targetValue = if (mainIsPressed) 0.85f else if (expanded) 0.95f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
            label = "main_fab_scale"
        )
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 135f else 0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
            label = "main_fab_rotation"
        )
        
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .scale(mainScale)
                .size(64.dp)
        ) {

            // Solid button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = mainInteractionSource,
                        indication = null,
                        onClick = { onExpandedChange(!expanded) }
                    )
            ) {
                Icon(
                    imageVector = FeatherIcons.Plus,
                    contentDescription = if (expanded) stringResource(R.string.fab_close) else stringResource(R.string.fab_create),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

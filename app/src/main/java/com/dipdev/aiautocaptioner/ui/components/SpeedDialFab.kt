package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.Plus
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                enter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = (items.size - index - 1) * 50)) + 
                        slideInVertically(initialOffsetY = { 50 }, animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + 
                        scaleIn(initialScale = 0.8f),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)) + 
                       scaleOut(targetScale = 0.8f) + 
                       slideOutVertically(targetOffsetY = { 50 })
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { 
                                onExpandedChange(false)
                                item.onClick() 
                            }
                        )
                ) {
                    // Label
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.alpha(0.95f)
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // Button
                    SmallFloatingActionButton(
                        onClick = { 
                            onExpandedChange(false)
                            item.onClick() 
                        },
                        containerColor = item.color,
                        contentColor = item.onColor,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                    ) {
                        Icon(item.icon, contentDescription = item.label)
                    }
                }
            }
        }
        
        // Main FAB
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 45f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
            label = "fab_rotation"
        )
        
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = if (expanded) 8.dp else 6.dp
            )
        ) {
            Icon(
                FeatherIcons.Plus,
                contentDescription = if (expanded) stringResource(R.string.fab_close) else stringResource(R.string.fab_create),
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

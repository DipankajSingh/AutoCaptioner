package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import compose.icons.FeatherIcons
import compose.icons.feathericons.Pause
import compose.icons.feathericons.Play
import compose.icons.feathericons.Square
import com.dipdev.aiautocaptioner.ui.theme.AccentRose

@Composable
fun PauseResumeControls(
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CircleIconButton(
            icon = if (isPaused) FeatherIcons.Play else FeatherIcons.Pause,
            size = 48.dp,
            onClick = if (isPaused) onResume else onPause
        )
        CircleIconButton(
            icon = FeatherIcons.Square,
            size = 52.dp,
            accentColor = AccentRose,
            onClick = onStop
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
    accentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = accentColor.copy(alpha = if (accentColor == AccentRose) 1f else 0.2f),
                radius = this.size.minDimension / 2
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.4f)
        )
    }
}

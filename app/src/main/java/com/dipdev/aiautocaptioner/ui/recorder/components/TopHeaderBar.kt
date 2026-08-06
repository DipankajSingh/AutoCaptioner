package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.recorder.model.AspectRatio
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingQuality
import compose.icons.FeatherIcons
import compose.icons.feathericons.Maximize
import compose.icons.feathericons.Monitor

/**
 * Unified top header tray designed for 2026 uncluttered studio recording.
 *
 * Houses low-frequency setup controls ([AspectRatio] and [RecordingQuality]) alongside real-time
 * storage metrics in a cohesive glassmorphic capsule row. Incorporates smooth 0.92x touch press
 * downscaling and fade micro-animations with zero haptics to maintain crisp visual feedback without
 * hardware vibration.
 */
@Composable
fun TopHeaderBar(
    aspectRatio: AspectRatio,
    recordingQuality: RecordingQuality,
    onAspectRatioClick: () -> Unit,
    onQualityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        // Aspect Ratio Pill (e.g. 9:16 or 16:9)
        HeaderActionPill(
            icon = FeatherIcons.Maximize,
            label = aspectRatio.displayLabel,
            onClick = onAspectRatioClick
        )

        // Recording Quality Pill (e.g. 720p / 1080p)
        HeaderActionPill(
            icon = FeatherIcons.Monitor,
            label = recordingQuality.label,
            onClick = onQualityClick
        )
    }
}

/**
 * Reusable interactive header capsule featuring 0.92x spring downscaling upon touch activation.
 */
@Composable
private fun HeaderActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "header_pill_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "header_pill_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Handled purely by custom scale & opacity micro-animation
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFFFCC70),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

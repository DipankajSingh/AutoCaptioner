package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Abstract animated orbs that pulse and orbit to convey "AI is processing."
 * Used on the transcription screen instead of a video player.
 */
@Composable
fun AiProcessingAnimation(
    modifier: Modifier = Modifier,
    progress: Float = 0f // 0..1
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_processing")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_phase"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val secondaryPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "secondary_pulse"
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = size.minDimension * 0.12f

        // Central glow — grows with progress
        val centerRadius = baseRadius * (1.2f + progress * 0.8f) * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.5f * pulse),
                    primary.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = centerRadius * 2.5f
            ),
            center = Offset(cx, cy),
            radius = centerRadius * 2.5f
        )

        // Core orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.9f),
                    primary.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = centerRadius
            ),
            center = Offset(cx, cy),
            radius = centerRadius
        )

        // Orbiting orbs
        val orbs = listOf(
            Triple(0.35f, tertiary, 1.0f),    // orbit radius factor, color, speed factor
            Triple(0.55f, secondary, 0.7f),
            Triple(0.45f, primary.copy(alpha = 0.6f), 1.3f),
        )

        for ((i, orb) in orbs.withIndex()) {
            val (radiusFactor, color, speed) = orb
            val orbitRadius = size.minDimension * radiusFactor
            val angle = phase * speed + (i * 2.094f) // 120° offset between orbs
            val orbX = cx + cos(angle) * orbitRadius
            val orbY = cy + sin(angle) * orbitRadius * 0.6f // Elliptical orbit
            val orbSize = baseRadius * (0.4f + 0.15f * if (i == 0) pulse else secondaryPulse)

            // Orb glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.6f),
                        color.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = Offset(orbX, orbY),
                    radius = orbSize * 3f
                ),
                center = Offset(orbX, orbY),
                radius = orbSize * 3f
            )

            // Orb core
            drawCircle(
                color = color.copy(alpha = 0.8f),
                center = Offset(orbX, orbY),
                radius = orbSize
            )
        }

        // Progress arc — subtle ring showing completion
        if (progress > 0.01f) {
            val arcRadius = size.minDimension * 0.42f
            drawArc(
                color = primary.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(cx - arcRadius, cy - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
            drawArc(
                color = primary.copy(alpha = 0.8f),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(cx - arcRadius, cy - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}

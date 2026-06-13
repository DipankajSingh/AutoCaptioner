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
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.sin

/**
 * Animated sine-wave waveform representing AI analyzing audio.
 */
@Composable
fun AudioWaveformAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val centerY = size.height / 2
        val barCount = 24
        val barWidth = size.width / (barCount * 2f)

        for (i in 0 until barCount) {
            val x = (size.width / barCount) * i + barWidth / 2
            val normalizedX = i.toFloat() / barCount

            // Two overlapping sine waves for organic feel
            val wave1 = sin(normalizedX * 4f + phase) * 0.4f
            val wave2 = sin(normalizedX * 6f + phase * 1.3f) * 0.25f
            val amplitude = (wave1 + wave2 + 0.35f).coerceIn(0.05f, 1f)

            val barHeight = amplitude * size.height * 0.4f
            val color = if (i % 2 == 0) primaryColor.copy(alpha = 0.8f) else secondaryColor.copy(alpha = 0.5f)

            drawLine(
                color = color,
                start = Offset(x, centerY - barHeight),
                end = Offset(x, centerY + barHeight),
                strokeWidth = barWidth * 0.7f,
                cap = StrokeCap.Round
            )
        }
    }
}

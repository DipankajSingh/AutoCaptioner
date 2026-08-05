package com.dipdev.aiautocaptioner.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import kotlin.math.cos
import kotlin.math.sin

/**
 * A bespoke, high-performance Canvas graphic designed specifically for the AutoCaptioner empty studio state.
 * Visually depicts the transformation of raw video footage into dynamic audio waveforms and illuminated subtitle tracks,
 * reinforcing the product's core value proposition without repeating the brand logo.
 */
@Composable
internal fun StudioEmptyGraphic(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "studio_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Box(
        modifier = modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(136.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            
            // 1. Ambient Studio Spotlight Glow (Radial Gradient)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentAmber.copy(alpha = pulseAlpha * 0.35f),
                        Color(0xFF2E1C06).copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.6f,
                center = center
            )

            // 2. Main Floating Video Viewfinder Frame (Styled Studio Canvas)
            val frameWidth = size.width * 0.66f
            val frameHeight = size.height * 0.50f
            val frameTopLeft = Offset(center.x - frameWidth / 2f, center.y - frameHeight / 2f - 6.dp.toPx())
            
            // Frame background drop shadow / back glow
            drawRoundRect(
                color = Color(0xFF1B160E),
                topLeft = frameTopLeft,
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(16.dp.toPx())
            )
            // Frame warm golden border
            drawRoundRect(
                color = AccentAmber.copy(alpha = 0.6f),
                topLeft = frameTopLeft,
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Play Triangle / Video Anchor inside viewfinder
            val trianglePath = Path().apply {
                val triCenterX = center.x
                val triCenterY = frameTopLeft.y + frameHeight * 0.42f
                val triRadius = 12.dp.toPx()
                moveTo(triCenterX - triRadius * 0.6f, triCenterY - triRadius * 0.8f)
                lineTo(triCenterX + triRadius * 0.9f, triCenterY)
                lineTo(triCenterX - triRadius * 0.6f, triCenterY + triRadius * 0.8f)
                close()
            }
            drawPath(
                path = trianglePath,
                color = Color.White.copy(alpha = 0.9f)
            )

            // 4. Equalizer / Soundwave Bars (Left & Right Flanks)
            val barWidth = 3.dp.toPx()
            val barGap = 5.dp.toPx()
            val waveY = frameTopLeft.y + frameHeight * 0.42f
            
            // Left waves
            listOf(14.dp.toPx(), 22.dp.toPx(), 12.dp.toPx()).forEachIndexed { i, h ->
                val x = center.x - 24.dp.toPx() - (i * (barWidth + barGap))
                drawRoundRect(
                    color = AccentAmber.copy(alpha = 0.7f - (i * 0.15f)),
                    topLeft = Offset(x, waveY - h / 2f),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            // Right waves
            listOf(14.dp.toPx(), 22.dp.toPx(), 12.dp.toPx()).forEachIndexed { i, h ->
                val x = center.x + 24.dp.toPx() + (i * (barWidth + barGap))
                drawRoundRect(
                    color = AccentAmber.copy(alpha = 0.7f - (i * 0.15f)),
                    topLeft = Offset(x, waveY - h / 2f),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }

            // 5. Generated Subtitle Caption Track (Overlay overlapping bottom of video frame)
            val subtitleWidth = frameWidth * 0.76f
            val subtitleHeight = 22.dp.toPx()
            val subTopLeft = Offset(center.x - subtitleWidth / 2f, frameTopLeft.y + frameHeight - subtitleHeight * 0.6f)
            
            drawRoundRect(
                color = Color(0xFF262117),
                topLeft = subTopLeft,
                size = Size(subtitleWidth, subtitleHeight),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawRoundRect(
                color = AccentAmber,
                topLeft = subTopLeft,
                size = Size(subtitleWidth, subtitleHeight),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = 1.5f.dp.toPx())
            )

            // Subtitle text imitation pills inside caption box
            val pillTop = subTopLeft.y + (subtitleHeight - 5.dp.toPx()) / 2f
            drawRoundRect(
                color = Color(0xFFFFD374),
                topLeft = Offset(subTopLeft.x + 10.dp.toPx(), pillTop),
                size = Size(subtitleWidth * 0.42f, 5.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(subTopLeft.x + 10.dp.toPx() + subtitleWidth * 0.46f, pillTop),
                size = Size(subtitleWidth * 0.28f, 5.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            
            // 6. Floating Magic Sparkle Particles (Top right & lower left accents)
            drawCircle(
                color = Color(0xFFFFD166),
                center = Offset(frameTopLeft.x + frameWidth + 10.dp.toPx(), frameTopLeft.y - 4.dp.toPx()),
                radius = 3.dp.toPx()
            )
            drawCircle(
                color = AccentAmber.copy(alpha = 0.6f),
                center = Offset(frameTopLeft.x - 8.dp.toPx(), frameTopLeft.y + frameHeight - 4.dp.toPx()),
                radius = 2.5f.dp.toPx()
            )
        }
    }
}

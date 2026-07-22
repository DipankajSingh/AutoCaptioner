package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R

/**
 * Lightweight pulsing animation with the app logo in the center.
 * Conveys "AI is processing" without overloading the GPU while Whisper runs.
 */
@Composable
fun AiProcessingAnimation(
    modifier: Modifier = Modifier,
    progress: Float = 0f // 0..1
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_processing")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring and progress arc
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxRadius = size.minDimension / 2f
            
            // Subtle pulse ring (much cheaper than radial gradients)
            drawCircle(
                color = primary.copy(alpha = 0.2f),
                radius = maxRadius * pulse
            )
            
            // Progress arc
            if (progress > 0.01f) {
                val arcRadius = maxRadius * 0.9f
                drawArc(
                    color = primary,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(cx - arcRadius, cy - arcRadius),
                    size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 8f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            } else {
                // Indeterminate spinning/loading indicator could go here if needed
                // But the pulse alone serves as a good indeterminate indicator
                val arcRadius = maxRadius * 0.9f
                drawArc(
                    color = primary.copy(alpha = 0.4f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(cx - arcRadius, cy - arcRadius),
                    size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 8f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        }

        // App logo inside
        Image(
            painter = painterResource(id = R.drawable.ic_logo_ui),
            contentDescription = stringResource(R.string.ai_animation_logo),
            modifier = Modifier.size(80.dp)
        )
    }
}

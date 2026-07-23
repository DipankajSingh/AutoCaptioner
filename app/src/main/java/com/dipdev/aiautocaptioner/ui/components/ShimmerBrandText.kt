package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber

@Composable
fun ShimmerBrandText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    durationMs: Int = 3000
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    val baseColor = MaterialTheme.colorScheme.onSurface
    Text(
        text = text,
        style = style.copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    baseColor,
                    baseColor,
                    AccentAmber,
                    Color.White,
                    AccentAmber,
                    baseColor,
                    baseColor
                ),
                start = Offset(offset * 200f, 0f),
                end = Offset(offset * 200f + 200f, 0f)
            )
        ),
        fontWeight = fontWeight,
        modifier = modifier
    )
}

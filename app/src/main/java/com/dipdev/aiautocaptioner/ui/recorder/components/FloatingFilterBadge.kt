package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Animated center-screen translucent pill badge in Instagram/Snapchat aesthetic.
 * 
 * Smoothly scales up with spring physics and glows with the active studio filter name when switching
 * filters, staying visible for ~800ms before dissolving outward. Designed strictly with visual feedback
 * (zero hardware haptics or vibration) for silent studio operation.
 */
@Composable
fun FloatingFilterBadge(
    activeFilter: CreatorFilter,
    modifier: Modifier = Modifier
) {
    var previousFilter by remember { mutableStateOf(activeFilter) }
    var isVisible by remember { mutableStateOf(false) }
    var displayedFilter by remember { mutableStateOf(activeFilter) }

    LaunchedEffect(activeFilter) {
        if (activeFilter != previousFilter) {
            previousFilter = activeFilter
            displayedFilter = activeFilter
            isVisible = true
            delay(800.milliseconds)
            isVisible = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialScale = 0.65f
        ) + fadeIn(tween(250)),
        exit = scaleOut(
            animationSpec = tween(250),
            targetScale = 1.15f
        ) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    spotColor = Color(0xFF9E7CFF).copy(alpha = 0.4f)
                )
                .clip(CircleShape)
                .background(Color(0xFF141218).copy(alpha = 0.75f))
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF9E7CFF).copy(alpha = 0.85f),
                            Color(0xFFE0C3FF).copy(alpha = 0.6f),
                            Color(0xFF9E7CFF).copy(alpha = 0.85f)
                        )
                    ),
                    shape = CircleShape
                )
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            Text(
                text = "✨ ${displayedFilter.displayName}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

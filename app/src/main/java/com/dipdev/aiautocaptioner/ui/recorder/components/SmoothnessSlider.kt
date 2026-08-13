package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Smile
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Custom social-media studio smoothness slider designed for instant touch-drag adjustment.
 * 
 * Replaces Material 3 sliders and manual confirm checkmarks with an intuitive interactive bar that
 * automatically applies and confirms face smoothing as the user scrolls, fading away automatically.
 */
@Composable
fun SmoothnessSlider(
    currentSmoothness: Float,
    onSmoothnessChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percentage = (currentSmoothness * 100f).toInt().coerceIn(0, 100)
    
    var lastPercentage by remember { mutableIntStateOf(percentage) }
    var isPulsing by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(percentage) {
        if (percentage != lastPercentage) {
            lastPercentage = percentage
            isPulsing = true
            isInteracting = true
        }
    }

    // Auto-confirm & dismiss after 2.5 seconds of zero touch activity
    LaunchedEffect(isInteracting, currentSmoothness) {
        if (!isInteracting) {
            delay(2500.milliseconds)
            onDismiss()
        }
    }

    val bubbleScale by animateFloatAsState(
        targetValue = if (isPulsing) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        finishedListener = { 
            isPulsing = false
            isInteracting = false
        },
        label = "percentage_bubble_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
    ) {
        // Floating percentage badge above the slider
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(bubbleScale)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF202028))
                .border(width = 1.dp, color = Color(0xFFFFCC70), shape = RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = "$percentage%",
                color = Color(0xFFFFCC70),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Custom touch interactive studio bar (no M3 slider, no confirm buttons)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xB3111116))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(22.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            isInteracting = true
                            val newVal = (offset.x / size.width).coerceIn(0f, 1f)
                            onSmoothnessChanged(newVal)
                            tryAwaitRelease()
                            isInteracting = false
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isInteracting = true
                            val newVal = (offset.x / size.width).coerceIn(0f, 1f)
                            onSmoothnessChanged(newVal)
                        },
                        onDragEnd = { isInteracting = false },
                        onDragCancel = { isInteracting = false },
                        onDrag = { change, _ ->
                            change.consume()
                            val newVal = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSmoothnessChanged(newVal)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Active Gold / Amber Progress Track
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val trackWidth = maxWidth
                val fillWidth = trackWidth * currentSmoothness.coerceIn(0f, 1f)
                
                Box(
                    modifier = Modifier
                        .width(fillWidth)
                        .fillMaxHeight()
                        .background(Color(0xFFFFCC70).copy(alpha = 0.35f))
                )
                
                // Glowing thumb indicator
                Box(
                    modifier = Modifier
                        .offset(x = (fillWidth - 12.dp).coerceAtLeast(0.dp))
                        .align(Alignment.CenterStart)
                        .padding(start = 2.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFCC70))
                        .border(2.dp, Color.White, CircleShape)
                )
            }

            // Retouch Icon Label inside track
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp)
            ) {
                Icon(
                    imageVector = FeatherIcons.Smile,
                    contentDescription = "Skin Retouch",
                    tint = if (currentSmoothness > 0.05f) Color(0xFFFFCC70) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

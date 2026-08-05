package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
import kotlin.random.Random
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.dipdev.aiautocaptioner.ui.recorder.model.AspectRatio
import kotlinx.coroutines.launch

@Composable
fun GridOverlay(aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val targetRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()
        val canvasWidth = size.width
        val canvasHeight = size.height
        val canvasRatio = canvasWidth / canvasHeight

        var activeWidth = canvasWidth
        var activeHeight = canvasHeight

        if (aspectRatio != AspectRatio.PORTRAIT_9_16) {
            if (targetRatio > canvasRatio) {
                activeWidth = canvasWidth
                activeHeight = canvasWidth / targetRatio
            } else {
                activeHeight = canvasHeight
                activeWidth = canvasHeight * targetRatio
            }
        }

        val topBottomPadding = ((canvasHeight - activeHeight) / 2f).coerceAtLeast(0f)
        val leftRightPadding = ((canvasWidth - activeWidth) / 2f).coerceAtLeast(0f)

        val startX = leftRightPadding
        val startY = topBottomPadding

        drawLine(
            Color.White.copy(alpha = 0.5f),
            Offset(startX + activeWidth / 3f, startY),
            Offset(startX + activeWidth / 3f, startY + activeHeight),
            strokeWidth = 2f
        )
        drawLine(
            Color.White.copy(alpha = 0.5f),
            Offset(startX + activeWidth * 2 / 3f, startY),
            Offset(startX + activeWidth * 2 / 3f, startY + activeHeight),
            strokeWidth = 2f
        )
        drawLine(
            Color.White.copy(alpha = 0.5f),
            Offset(startX, startY + activeHeight / 3f),
            Offset(startX + activeWidth, startY + activeHeight / 3f),
            strokeWidth = 2f
        )
        drawLine(
            Color.White.copy(alpha = 0.5f),
            Offset(startX, startY + activeHeight * 2 / 3f),
            Offset(startX + activeWidth, startY + activeHeight * 2 / 3f),
            strokeWidth = 2f
        )
    }
}

@Composable
fun AspectRatioMaskOverlay(aspectRatio: AspectRatio) {
    if (aspectRatio == AspectRatio.PORTRAIT_9_16) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val targetRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()
        val canvasWidth = size.width
        val canvasHeight = size.height
        val canvasRatio = canvasWidth / canvasHeight

        var activeWidth = canvasWidth
        var activeHeight = canvasHeight

        if (targetRatio > canvasRatio) {
            activeWidth = canvasWidth
            activeHeight = canvasWidth / targetRatio
        } else {
            activeHeight = canvasHeight
            activeWidth = canvasHeight * targetRatio
        }

        val topBottomPadding = ((canvasHeight - activeHeight) / 2f).coerceAtLeast(0f)
        val leftRightPadding = ((canvasWidth - activeWidth) / 2f).coerceAtLeast(0f)

        if (topBottomPadding > 0f) {
            drawRect(
                color = Color.Black,
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, topBottomPadding)
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(0f, canvasHeight - topBottomPadding),
                size = Size(canvasWidth, topBottomPadding)
            )
        }

        if (leftRightPadding > 0f) {
            drawRect(
                color = Color.Black,
                topLeft = Offset(0f, 0f),
                size = Size(leftRightPadding, canvasHeight)
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(canvasWidth - leftRightPadding, 0f),
                size = Size(leftRightPadding, canvasHeight)
            )
        }
    }
}

@Composable
fun AudioVisualizerOverlay(amplitude: Float) {
    val safeAmplitude = if (amplitude.isNaN()) 0f else (amplitude * 15f).coerceIn(0f, 1f)
    val baseColor = when {
        safeAmplitude > 0.85f -> Color.Red
        safeAmplitude < 0.15f -> Color.Yellow
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 11
        for (i in 0 until barCount) {
            val distanceToCenter = Math.abs(i - barCount / 2).toFloat()
            val scaleFactor = 1f - (distanceToCenter / (barCount / 2f))
            val barAmp = safeAmplitude * scaleFactor * (0.6f + (Math.sin((safeAmplitude * 20f + i).toDouble()).toFloat() * 0.4f))
            val targetHeight = 16f + (barAmp * 120f)
            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "barHeight"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(4.dp)
                    .height(animatedHeight.dp)
                    .clip(CircleShape)
                    .background(baseColor.copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
fun RecordingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseFraction"
    )
    val pulseAlpha = 0.25f + (1f - pulseFraction) * 0.75f

    Box(modifier = modifier.fillMaxSize()) {
        // Pulsing red border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
        )

        // REC badge — top right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
            )
            Text(
                text = stringResource(R.string.rec_rec),
                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun PausedIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pause_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pauseAlpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(3.dp, Color.White.copy(alpha = 0.4f))
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha))
            )
            Text(
                text = stringResource(R.string.rec_paused),
                color = Color.White.copy(alpha = alpha),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun AnimatedCountdown(
    value: Int,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(value) {
        scale.snapTo(0.5f)
        alpha.snapTo(1f)
        coroutineScope.launch {
            scale.animateTo(1.4f, tween(700, easing = FastOutSlowInEasing))
        }
        coroutineScope.launch {
            alpha.animateTo(0f, tween(700, easing = LinearEasing))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp),
            color = Color.White.copy(alpha = alpha.value),
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        )
    }
}

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
fun AnimatedCountdown(
    value: Int,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(value) {
        scale.snapTo(0.5f)
        alpha.snapTo(1f)
        launch {
            scale.animateTo(1.4f, tween(700, easing = FastOutSlowInEasing))
        }
        launch {
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

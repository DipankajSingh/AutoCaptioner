package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import kotlin.random.Random
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

@Composable
fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Vertical lines
        drawLine(Color.White.copy(alpha = 0.5f), Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 2f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), strokeWidth = 2f)
        // Horizontal lines
        drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = 2f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), strokeWidth = 2f)
    }
}

@Composable
fun AudioVisualizerOverlay(amplitude: Float) {
    // Elegant, thin Siri-style waveform with advanced diagnostics
    
    // Amplify the raw amplitude significantly since raw RMS on typical speech is very small
    val safeAmplitude = if (amplitude.isNaN()) 0f else (amplitude * 15f).coerceIn(0f, 1f)
    
    // Determine color based on overall amplitude
    val baseColor = when {
        safeAmplitude > 0.85f -> Color.Red         // Clipping/Too Loud
        safeAmplitude < 0.15f -> Color.Yellow      // Too Quiet
        else -> AccentCyan                         // Perfect
    }
    
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 11 // More, thinner bars
        for (i in 0 until barCount) {
            val distanceToCenter = Math.abs(i - barCount / 2).toFloat()
            val scaleFactor = 1f - (distanceToCenter / (barCount / 2f))
            
            // Generate some deterministic wave-like variations across the bars based on the amplitude
            val barAmp = safeAmplitude * scaleFactor * (0.6f + (Math.sin((safeAmplitude * 20f + i).toDouble()).toFloat() * 0.4f))
            
            val targetHeight = 16f + (barAmp * 120f) // Minimum 16dp height, up to 136dp
            
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
fun PermissionOverlay(
    message: String,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.nothing))
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(150.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequest) {
                Text("Grant Permission")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings", color = Color.White)
            }
        }
    }
}

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import kotlin.random.Random
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawLine(Color.White.copy(alpha = 0.5f), Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 2f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), strokeWidth = 2f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = 2f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), strokeWidth = 2f)
    }
}

@Composable
fun AudioVisualizerOverlay(amplitude: Float) {
    val safeAmplitude = if (amplitude.isNaN()) 0f else (amplitude * 15f).coerceIn(0f, 1f)
    val baseColor = when {
        safeAmplitude > 0.85f -> Color.Red
        safeAmplitude < 0.15f -> Color.Yellow
        else -> AccentCyan
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderOnboardingSheet(
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        contentColor = Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 32.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.FiberManualRecord,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = AccentRose
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.recorder_onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.recorder_onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            ModeInfoCard(
                icon = Icons.Rounded.Videocam,
                title = stringResource(R.string.recorder_onboarding_camera_title),
                description = stringResource(R.string.recorder_onboarding_camera_desc),
                accentColor = AccentCyan
            )
            Spacer(modifier = Modifier.height(12.dp))
            ModeInfoCard(
                icon = Icons.Rounded.Mic,
                title = stringResource(R.string.recorder_onboarding_faceless_title),
                description = stringResource(R.string.recorder_onboarding_faceless_desc),
                accentColor = AccentRose
            )
            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.recorder_onboarding_got_it),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ModeInfoCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = accentColor
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun PermissionRequestCard(
    icon: ImageVector,
    message: String,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.recorder_grant_permission),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = stringResource(R.string.recorder_open_settings),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

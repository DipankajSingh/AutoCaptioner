package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun SidebarButton(
    icon: ImageVector,
    text: String, // Kept for compatibility but ignored in UI
    isActive: Boolean = false,
    rotationY: Float = 0f,
    pulseRing: Boolean = false,
    breathingGlow: Boolean = false,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Interactive visual bounce physics: pressed = 0.85f -> release = 1.08f -> settle = 1.0f
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "buttonScale"
    )
    val alpha by animateFloatAsState(targetValue = if (isActive) 1f else 0.85f, label = "buttonAlpha")
    val activeColor = MaterialTheme.colorScheme.primary

    // Subtle ambient breathing aura when active (zero vibration / zero haptic feedback)
    val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulseRing || breathingGlow) 1.25f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "ringScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (pulseRing || breathingGlow) 0.6f else 0f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "ringAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .graphicsLayer {
                this.rotationY = rotationY
                cameraDistance = 8 * density
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        if (pulseRing || breathingGlow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = pulseAlpha))
            )
        }
        
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isActive) activeColor else Color.White.copy(alpha = alpha),
            modifier = Modifier
                .size(24.dp)
        )
        
        if (badgeText != null && badgeText != "0.0") {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color = activeColor)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun ModeToggle(currentMode: String, onModeSelected: (String) -> Unit) {
    val options = listOf("CAMERA", "FACELESS")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        options.forEach { mode ->
            val isSelected = mode == currentMode

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        if (!isSelected) onModeSelected(mode)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = mode,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val touchScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "record_touch_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.22f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isRecording) 0.85f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val buttonSize by animateDpAsState(
        targetValue = if (isRecording) 34.dp else 62.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "buttonSize"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 31.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cornerRadius"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(88.dp)
            .scale(touchScale)
            .testTag("RecordButton")
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer aura pulse ring during active recording / idle guide ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = (size.width / 2f) * 0.86f
            drawCircle(
                color = primaryColor.copy(alpha = if (isRecording) pulseAlpha * 0.4f else 0.25f),
                radius = if (isRecording) baseRadius * pulseScale else baseRadius,
                style = Stroke(width = if (isRecording) 10f else 6f)
            )
            // Inner crisp white guidance frame
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = baseRadius - 6f,
                style = Stroke(width = 7f)
            )
        }

        // Fluid morphing internal shape (Solid circle -> Rounded stop square)
        Box(
            modifier = Modifier
                .size(buttonSize)
                .shadow(
                    elevation = if (isRecording) 10.dp else 4.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    spotColor = primaryColor
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(color = primaryColor)
        )
    }
}

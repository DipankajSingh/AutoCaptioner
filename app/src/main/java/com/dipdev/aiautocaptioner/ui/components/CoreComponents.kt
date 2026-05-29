package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.ui.theme.LocalGlassmorphismEnabled

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isGlassEnabled = LocalGlassmorphismEnabled.current

    val bgColor = if (isGlassEnabled) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isGlassEnabled) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }

    var baseModifier = modifier
        .clip(shape)
        .background(bgColor)
        .border(1.dp, borderColor, shape)
        
    if (onClick != null) {
        baseModifier = baseModifier.clickable { onClick() }
    }

    Box(modifier = baseModifier) {
        content()
    }
}

@Composable
fun GradientPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    paddingValues: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
) {
    val primary = MaterialTheme.colorScheme.primary
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            primary.copy(alpha = 0.8f),
            primary
        )
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) gradient else Brush.linearGradient(listOf(Color.Gray, Color.Gray)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else Color.LightGray,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

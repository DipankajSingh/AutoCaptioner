package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.ui.theme.LocalGlassmorphismEnabled

@Composable
fun ModelPickerCard(
    model: WhisperModel,
    isRecommended: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isGlassEnabled = LocalGlassmorphismEnabled.current

    val containerColor = when {
        isSelected && isGlassEnabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isGlassEnabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = when {
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isRecommended -> BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary
                )
            )
        )
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = model.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (model.isDownloaded) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "✓ Ready",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (isRecommended) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "✨ Best for your device",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = model.description,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Metrics Row
                val speedText = when (model.speed) {
                    5 -> "Lightning Fast"
                    4 -> "Very Fast"
                    3 -> "Fast"
                    2 -> "Standard"
                    else -> "Slow"
                }
                
                val accText = when (model.accuracy) {
                    5 -> "Highest Acc"
                    4 -> "High Acc"
                    3 -> "Good Acc"
                    2 -> "Standard"
                    else -> "Basic"
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ $speedText",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "🎯 $accText",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "💾 ${model.sizeMb} MB",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            // Selection indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp).padding(top = 2.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(24.dp)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

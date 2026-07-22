package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.ui.theme.LocalGlassmorphismEnabled

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelPickerCard(
    model: WhisperModel,
    isRecommended: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    autoDetectMode: Boolean = false,
    isMultilingual: Boolean = false
) {
    val isGlassEnabled = LocalGlassmorphismEnabled.current

    val targetContainerColor = when {
        isSelected && isGlassEnabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        isGlassEnabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surface
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(300),
        label = "containerColorAnim"
    )

    val targetBorderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isRecommended -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(300),
        label = "borderColorAnim"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (isSelected || isRecommended) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Title, Badges, and Radio Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = model.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        if (isRecommended) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = FeatherIcons.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.model_badge_recommended),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        
                        if (model.isDownloaded) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = stringResource(R.string.model_badge_downloaded),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (autoDetectMode) {
                            val chipText = if (isMultilingual) {
                                stringResource(R.string.auto_detect_chip_any_language)
                            } else {
                                stringResource(R.string.auto_detect_chip_english)
                            }
                            val chipColor = if (isMultilingual) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                            val chipTextColor = if (isMultilingual) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = chipColor,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = chipText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = chipTextColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = model.description,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Selection Indicator
                if (isSelected) {
                    Icon(
                        imageVector = FeatherIcons.CheckCircle,
                        contentDescription = stringResource(R.string.model_selected_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MetricBars(label = stringResource(R.string.model_metric_speed), value = model.speed, activeColor = MaterialTheme.colorScheme.primary)
                }
                Box(modifier = Modifier.weight(1f)) {
                    MetricBars(label = stringResource(R.string.model_metric_accuracy), value = model.accuracy, activeColor = MaterialTheme.colorScheme.primary)
                }
                
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.model_metric_size),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.model_size_format, model.sizeMb),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBars(label: String, value: Int, maxValue: Int = 5, activeColor: Color) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(maxValue) { index ->
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (index < value) activeColor 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                )
            }
        }
    }
}


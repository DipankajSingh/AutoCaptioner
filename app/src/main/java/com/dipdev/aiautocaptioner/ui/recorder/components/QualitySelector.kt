package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.core.camera.RecordingQuality
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.GlassSurface
import com.dipdev.aiautocaptioner.ui.theme.TextPrimary
import com.dipdev.aiautocaptioner.ui.theme.TextSecondary

/**
 * Horizontal pill row showing available recording quality tiers.
 * Disabled during recording/paused to avoid mid-recording camera restarts.
 *
 * Example: [ 480p · 720p · 1080p ]
 */
@Composable
fun QualitySelector(
    qualities: List<RecordingQuality>,
    selected: RecordingQuality,
    enabled: Boolean,
    onSelect: (RecordingQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(GlassSurface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        qualities.forEach { quality ->
            val isSelected = quality == selected
            TextButton(
                onClick = { if (enabled) onSelect(quality) },
                enabled = enabled,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) AccentAmber.copy(alpha = 0.25f) else Color.Transparent
                    )
            ) {
                Text(
                    text = quality.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) AccentAmber
                            else if (enabled) TextPrimary
                            else TextSecondary
                )
            }
        }
    }
}

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
import com.dipdev.aiautocaptioner.core.camera.RecordingAspectRatio
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.GlassSurface
import com.dipdev.aiautocaptioner.ui.theme.TextPrimary
import com.dipdev.aiautocaptioner.ui.theme.TextSecondary

/**
 * Horizontal pill row showing available aspect ratio options.
 * Disabled during recording/paused to avoid mid-recording camera restarts.
 *
 * Example: [ 9:16 · 1:1 · 3:4 ]
 */
@Composable
fun RatioSelector(
    selected: RecordingAspectRatio,
    enabled: Boolean,
    onSelect: (RecordingAspectRatio) -> Unit,
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
        RecordingAspectRatio.entries.forEach { ratio ->
            val isSelected = ratio == selected
            TextButton(
                onClick = { if (enabled) onSelect(ratio) },
                enabled = enabled,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) AccentAmber.copy(alpha = 0.25f) else Color.Transparent
                    )
            ) {
                Text(
                    text = ratio.label,
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

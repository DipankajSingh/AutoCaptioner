package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus

/**
 * A small coloured chip showing the current [ProjectStatus].
 * Used in [HomeScreen]'s project cards.
 */
@Composable
fun ProjectStatusChip(status: ProjectStatus) {
    val (label, color) = when (status) {
        ProjectStatus.IMPORTED          -> "Imported"       to MaterialTheme.colorScheme.onSurfaceVariant
        ProjectStatus.EXTRACTING_AUDIO  -> "Extracting…"    to MaterialTheme.colorScheme.tertiary
        ProjectStatus.TRANSCRIBING      -> "Transcribing…"  to MaterialTheme.colorScheme.tertiary
        ProjectStatus.TRANSCRIBED       -> "Ready"          to MaterialTheme.colorScheme.primary
        ProjectStatus.EXPORTED          -> "Exported"       to MaterialTheme.colorScheme.secondary
    }
    Surface(
        color        = color.copy(alpha = 0.25f),
        shape        = RoundedCornerShape(12.dp), // More rounded corners
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Text(
            text       = label,
            color      = color,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

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
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber

/**
 * A small coloured chip showing the current [ProjectStatus].
 * Semantic colour coding:
 *   - Processing states → Cyan
 *   - Complete / ready states → Emerald (brand primary)
 *   - Error states → Rose
 *   - Neutral states → onSurfaceVariant
 */
@Composable
fun ProjectStatusChip(status: ProjectStatus) {
    val (label, color) = when (status) {
        ProjectStatus.IMPORTED               -> stringResource(R.string.status_imported)      to MaterialTheme.colorScheme.onSurfaceVariant
        ProjectStatus.READY_FOR_PROCESSING   -> stringResource(R.string.status_ready_for_ai)  to AccentAmber
        ProjectStatus.EXTRACTING_AUDIO       -> stringResource(R.string.status_extracting)    to AccentCyan
        ProjectStatus.TRANSCRIBING           -> stringResource(R.string.status_transcribing)  to AccentCyan
        ProjectStatus.TRANSCRIBED            -> stringResource(R.string.status_ready)          to AccentAmber
        ProjectStatus.EXPORTED               -> stringResource(R.string.status_exported)       to AccentAmber
    }
    Surface(
        color           = color.copy(alpha = 0.15f),
        shape           = RoundedCornerShape(12.dp),
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp
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

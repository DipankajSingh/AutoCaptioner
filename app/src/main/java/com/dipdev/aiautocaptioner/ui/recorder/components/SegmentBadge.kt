package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme

@Composable
fun SegmentBadge(
    segmentCount: Int,
    currentSegmentDurationMs: Long,
    modifier: Modifier = Modifier
) {
    if (segmentCount == 0) return

    val durationSec = (currentSegmentDurationMs / 1000).toInt()
    val minutes = durationSec / 60
    val seconds = durationSec % 60
    val durationText = String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = "Segment $segmentCount · $durationText",
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

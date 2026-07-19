package com.dipdev.aiautocaptioner.ui.videoeditor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.data.model.segmentToTimelineRange
import com.dipdev.aiautocaptioner.ui.theme.AccentViolet
import com.dipdev.aiautocaptioner.ui.theme.TextPrimary

/**
 * A single caption segment block rendered on the caption track.
 * Its position and width are derived by mapping the segment's source timestamps through
 * the current clip list so it always reflects the edited timeline correctly.
 */
@Composable
fun CaptionTrackItem(
    segment: CaptionSegmentEntity,
    clips: List<Clip>,
    pixelsPerMs: Float,
    isSelected: Boolean,
    onTap: (CaptionSegmentEntity) -> Unit
) {
    val density = LocalDensity.current
    val range = segmentToTimelineRange(segment.startTimeMs, segment.endTimeMs, clips) ?: return

    val startPx  = range.first  * pixelsPerMs
    val widthPx  = (range.second - range.first) * pixelsPerMs
    if (widthPx < 1f) return

    val startDp = with(density) { startPx.toDp() }
    val widthDp = with(density) { widthPx.toDp() }.coerceAtLeast(4.dp)

    val bgColor = if (isSelected) AccentViolet.copy(alpha = 0.65f) else AccentViolet

    Box(
        modifier = Modifier
            .offset(x = startDp)
            .width(widthDp)
            .fillMaxHeight()
            .padding(vertical = 3.dp, horizontal = 1.dp)
            .background(bgColor.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .clickable { onTap(segment) },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = segment.text,
            color = TextPrimary,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

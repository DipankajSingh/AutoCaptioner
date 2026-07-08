package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import compose.icons.FeatherIcons
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Minus
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Scissors
import compose.icons.feathericons.Trash2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.ui.videoeditor.overlay.OverlayActionMenu
import com.dipdev.aiautocaptioner.ui.videoeditor.timeline.TimelineView

@Composable
fun VideoTimelinePanel(
    timelineHeight: Dp,
    maxTimelineHeight: Dp,
    onTimelineHeightChanged: (Dp) -> Unit,
    clips: List<Clip>,
    thumbnails: Map<Long, Bitmap>,
    onRequestThumbnails: (List<Long>) -> Unit,
    originalDurationMs: Long,
    selectedClipId: String?,
    onClipSelected: (String?) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    overlays: List<ImageOverlayEntity>,
    selectedOverlayId: String?,
    onOverlaySelected: (String?) -> Unit,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    onCaptionTap: () -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    zoomLevel: Float,
    player: Player,
    currentTimelineMs: () -> Long,
    onTrimClip: (String, Long, Long) -> Unit,
    onMoveOverlayZ: (String, Boolean) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onSplit: () -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val currentTimelineHeight by androidx.compose.runtime.rememberUpdatedState(timelineHeight)

    Box(modifier = modifier.fillMaxWidth().height(timelineHeight)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Unified Drag handle at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val dragAmountDp = with(density) { dragAmount.toDp() }
                            onTimelineHeightChanged((currentTimelineHeight - dragAmountDp).coerceIn(200.dp, maxTimelineHeight))
                        }
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
            }

            // Timeline takes remaining space
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                TimelineView(
                    clips = clips,
                    thumbnails = thumbnails,
            onRequestThumbnails = onRequestThumbnails,
                    originalDurationMs = originalDurationMs,
                    selectedClipId = selectedClipId,
                    onClipSelected = onClipSelected,
                    onMoveClip = onMoveClip,
                    overlays = overlays,
                    selectedOverlayId = selectedOverlayId,
                    onOverlaySelected = onOverlaySelected,
                    onOverlayTimingChanged = { id, startMs, endMs ->
                        val overlay = overlays.find { it.id == id } ?: return@TimelineView
                        onUpdateOverlay(overlay.copy(startTimeMs = startMs, endTimeMs = endMs))
                    },
                    onCaptionTap = onCaptionTap,
                    onDragStateChange = onDragStateChange,
                    zoomLevel = zoomLevel,
                    player = player,
                    currentTimelineMs = currentTimelineMs,
                    onTrimClip = onTrimClip,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val selectedOverlay = overlays.find { it.id == selectedOverlayId }
            if (selectedOverlay != null) {
                OverlayActionMenu(
                    selectedOverlay = selectedOverlay,
                    onFullVideo = {
                        onUpdateOverlay(selectedOverlay.copy(startTimeMs = 0L, endTimeMs = Long.MAX_VALUE))
                    },
                    onSendToBack = { onMoveOverlayZ(selectedOverlay.id, false) },
                    onBringToFront = { onMoveOverlayZ(selectedOverlay.id, true) },
                    onDelete = { onDeleteOverlay(selectedOverlay.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Bottom Toolbar for Timeline Tools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Editing tools
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasSelection = selectedClipId != null
                    Icon(
                        FeatherIcons.Scissors, 
                        "Split", 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp).clickable { onSplit() }
                    )
                    Icon(
                        FeatherIcons.Copy, 
                        "Duplicate", 
                        tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(24.dp).clickable(enabled = hasSelection) { selectedClipId?.let { onDuplicate(it) } }
                    )
                    Icon(
                        FeatherIcons.Trash2, 
                        "Delete", 
                        tint = if (hasSelection) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(24.dp).clickable(enabled = hasSelection) { selectedClipId?.let { onDelete(it) } }
                    )
                }
                
                // Zoom controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        FeatherIcons.Minus, 
                        "Zoom Out", 
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp).clickable { onZoomOut() }
                    )
                    Text(
                        text = "${(zoomLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        FeatherIcons.Plus, 
                        "Zoom In", 
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp).clickable { onZoomIn() }
                    )
                }
            }
        }
    }
}

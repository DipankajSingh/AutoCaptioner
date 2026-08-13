package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.ui.videoeditor.timeline.TimelineView
import compose.icons.FeatherIcons
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Minus
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Scissors
import compose.icons.feathericons.Trash2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun VideoTimelinePanel(
    modifier: Modifier = Modifier,
    timelineHeight: Dp,
    maxTimelineHeight: Dp,
    onTimelineHeightChanged: (Dp) -> Unit,
    clips: ImmutableList<Clip>,
    thumbnails: Map<Long, Bitmap>,
    onRequestThumbnails: (List<Long>) -> Unit,
    originalDurationMs: Long,
    selectedClipId: String?,
    onClipSelected: (String?) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    overlays: ImmutableList<ImageOverlayEntity>,
    selectedOverlayId: String?,
    onOverlaySelected: (String?) -> Unit,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    textOverlays: ImmutableList<TextOverlayEntity> = persistentListOf(),
    onUpdateTextOverlay: (TextOverlayEntity) -> Unit = {},
    onCaptionTap: () -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    zoomLevel: Float,
    player: Player,
    currentTimelineMs: () -> Long,
    onTrimClip: (String, Long, Long) -> Unit,
    onMoveOverlayZ: (String, Boolean) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onDeleteTextOverlay: (String) -> Unit = {},
    onSplit: () -> Unit,
    onDuplicate: (String) -> Unit,
    onDuplicateOverlay: (String) -> Unit,
    onDuplicateTextOverlay: (String) -> Unit = {},
    onDelete: (String) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onPinchZoom: (scale: Float) -> Unit = {
},
    segments: List<com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity> = emptyList(),
    selectedCaptionSegmentId: String? = null,
    onCaptionSegmentTap: (com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity) -> Unit = {},
) {
    val density = LocalDensity.current
    val currentTimelineHeight by rememberUpdatedState(timelineHeight)
    val updatedOverlays by rememberUpdatedState(overlays)
    val updatedTextOverlays by rememberUpdatedState(textOverlays)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(timelineHeight)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    onPinchZoom(zoom)
                }
            }
    ) {
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
                        val overlay = updatedOverlays.find { it.id == id } ?: return@TimelineView
                        onUpdateOverlay(overlay.copy(startTimeMs = startMs, endTimeMs = endMs))
                    },
                    textOverlays = textOverlays,
                    onTextOverlayTimingChanged = { id, startMs, endMs ->
                        val textOverlay = updatedTextOverlays.find { it.id == id } ?: return@TimelineView
                        onUpdateTextOverlay(textOverlay.copy(startTimeMs = startMs, endTimeMs = endMs))
                    },
                    onDragStateChange = onDragStateChange,
                    zoomLevel = zoomLevel,
                    player = player,
                    currentTimelineMs = currentTimelineMs,
                    onTrimClip = onTrimClip,
                    onMoveOverlayZ = onMoveOverlayZ,
                    segments = segments,
                    selectedCaptionSegmentId = selectedCaptionSegmentId,
                    onCaptionSegmentTap = onCaptionSegmentTap,
                    modifier = Modifier.fillMaxSize()
                )
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
                    val hasClipSelection = selectedClipId != null
                    val hasOverlaySelection = selectedOverlayId != null
                    val hasAnySelection = hasClipSelection || hasOverlaySelection
                    val accentColor = MaterialTheme.colorScheme.primary
                    val grayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)


                    Icon(
                        FeatherIcons.Scissors, 
                        stringResource(R.string.timeline_split), 
                        tint = if (hasClipSelection) accentColor else grayColor,
                        modifier = Modifier.size(24.dp).clickable(enabled = hasClipSelection) { onSplit() }
                    )
                    Icon(
                        FeatherIcons.Copy, 
                        stringResource(R.string.timeline_duplicate), 
                        tint = if (hasAnySelection) accentColor else grayColor,
                        modifier = Modifier.size(24.dp).clickable(enabled = hasAnySelection) { 
                            if (hasOverlaySelection) selectedOverlayId.let { 
                                if (textOverlays.any { t -> t.id == it }) onDuplicateTextOverlay(it) else onDuplicateOverlay(it) 
                            }
                            else if (hasClipSelection) onDuplicate(selectedClipId)
                        }
                    )
                    Icon(
                        FeatherIcons.Trash2, 
                        stringResource(R.string.project_delete), 
                        tint = if (hasAnySelection) MaterialTheme.colorScheme.error else grayColor,
                        modifier = Modifier.size(24.dp).clickable(enabled = hasAnySelection) { 
                            if (hasOverlaySelection) selectedOverlayId.let { 
                                if (textOverlays.any { t -> t.id == it }) onDeleteTextOverlay(it) else onDeleteOverlay(it) 
                            }
                            else if (hasClipSelection) onDelete(selectedClipId)
                        }
                    )
                }
                
                // Zoom controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        FeatherIcons.Minus, 
                        stringResource(R.string.timeline_zoom_out), 
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
                        stringResource(R.string.timeline_zoom_in), 
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp).clickable { onZoomIn() }
                    )
                }
            }
        }
    }
}

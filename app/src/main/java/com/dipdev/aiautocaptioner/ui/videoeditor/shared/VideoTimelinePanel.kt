package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    clipThumbnails: Map<String, List<Bitmap>>,
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
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    player: Player,
    currentTimelineMs: () -> Long,
    onSplitClip: () -> Unit,
    onDuplicateClip: (String) -> Unit,
    onDeleteClip: (String) -> Unit,
    onMoveOverlayZ: (String, Boolean) -> Unit,
    onDeleteOverlay: (String) -> Unit,
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
                    clipThumbnails = clipThumbnails,
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

            VideoEditorToolbar(
                selectedClipId = selectedClipId,
                zoomLevel = zoomLevel,
                onSplit = onSplitClip,
                onDuplicate = { selectedClipId?.let { onDuplicateClip(it) } },
                onDelete = {
                    selectedClipId?.let {
                        onDeleteClip(it)
                        onClipSelected(null)
                    }
                },
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut
            )
        }
    }
}

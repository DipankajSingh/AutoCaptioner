package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import compose.icons.FeatherIcons
import compose.icons.feathericons.Film
import compose.icons.feathericons.Type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.ui.videoeditor.core.EditorMode
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StylePanel
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StyleViewModel

@Composable
fun EditorBottomDock(
    maxHeight: Dp,
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
    onDragStateChange: (Boolean) -> Unit,
    zoomLevel: Float,
    player: Player,
    currentTimelineMs: () -> Long,
    onTrimClip: (String, Long, Long) -> Unit,
    onMoveOverlayZ: (String, Boolean) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onDeleteTextOverlay: (String) -> Unit = {},
    styleViewModel: StyleViewModel,
    onSplit: () -> Unit,
    onDuplicate: (String) -> Unit,
    onDuplicateOverlay: (String) -> Unit,
    onDuplicateTextOverlay: (String) -> Unit = {},
    onDelete: (String) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    // Fix 6: pinch-to-zoom scale factor from VideoTimelinePanel
    onPinchZoom: (scale: Float) -> Unit = {},
    segments: List<com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity> = emptyList(),
    selectedCaptionSegmentId: String? = null,
    onCaptionSegmentTap: (com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity) -> Unit = {},
    onGenerateCaptions: () -> Unit = {},
    onAddImage: () -> Unit = {},
    selectedLanguage: String = "en",
    translateToEnglish: Boolean = false,
    onLanguageSelected: (String, Boolean) -> Unit = { _, _ -> },
    allowedLanguages: List<String> = listOf("multilingual"),
    currentMode: EditorMode = EditorMode.VIDEO,
    onModeChange: (EditorMode) -> Unit = {}
) {
    var timelineHeight by remember { mutableStateOf(220.dp) }
    val maxTimelineHeight = maxHeight * 0.5f
    val animatedTimelineHeight by animateDpAsState(targetValue = timelineHeight, label = "timelineHeight")

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        // Dynamic Tools Window
        Box(modifier = Modifier.fillMaxWidth().height(animatedTimelineHeight)) {
            val isVideoMode = currentMode == EditorMode.VIDEO
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isVideoMode) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (isVideoMode) 1f else 0f
                    }
                    .offset {
                        IntOffset(x = if (isVideoMode) 0 else 100000, y = 0)
                    }
            ) {
                VideoTimelinePanel(
                    timelineHeight = timelineHeight,
                    maxTimelineHeight = maxTimelineHeight,
                    onTimelineHeightChanged = { timelineHeight = it },
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
                    onUpdateOverlay = onUpdateOverlay,
                    textOverlays = textOverlays,
                    onUpdateTextOverlay = onUpdateTextOverlay,
                    onCaptionTap = { onModeChange(EditorMode.CAPTIONS) },
                    onAddImage = onAddImage,
                    onDragStateChange = onDragStateChange,
                    zoomLevel = zoomLevel,
                    player = player,
                    currentTimelineMs = currentTimelineMs,
                    onTrimClip = onTrimClip,
                    onMoveOverlayZ = onMoveOverlayZ,
                    onDeleteOverlay = onDeleteOverlay,
                    onDeleteTextOverlay = onDeleteTextOverlay,
                    onSplit = onSplit,
                    onDuplicate = onDuplicate,
                    onDuplicateOverlay = onDuplicateOverlay,
                    onDuplicateTextOverlay = onDuplicateTextOverlay,
                    onDelete = onDelete,
                    onZoomIn = onZoomIn,
                    onZoomOut = onZoomOut,
                    onPinchZoom = onPinchZoom,
                    segments = segments,
                    selectedCaptionSegmentId = selectedCaptionSegmentId,
                    onCaptionSegmentTap = onCaptionSegmentTap,
                    modifier = Modifier.fillMaxSize()
                )
            }

            val isCaptionsMode = currentMode == EditorMode.CAPTIONS
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isCaptionsMode) 1f else 0f)
                    .graphicsLayer {
                        alpha = if (isCaptionsMode) 1f else 0f
                    }
                    .offset {
                        IntOffset(x = if (isCaptionsMode) 0 else 100000, y = 0)
                    }
            ) {
                StylePanel(
                    viewModel = styleViewModel,
                    timelineHeight = timelineHeight,
                    maxTimelineHeight = maxTimelineHeight,
                    onTimelineHeightChanged = { timelineHeight = it },
                    onGenerateCaptions = onGenerateCaptions,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // Bottom Tab Bar with subtle separation and modern horizontal capsule styling
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
        ) {
            // Subtle top divider line for visual hierarchy
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorModeTab(
                    icon = FeatherIcons.Film,
                    label = stringResource(R.string.dock_video),
                    selected = currentMode == EditorMode.VIDEO,
                    onClick = { onModeChange(EditorMode.VIDEO) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                EditorModeTab(
                    icon = FeatherIcons.Type,
                    label = stringResource(R.string.dock_captions),
                    selected = currentMode == EditorMode.CAPTIONS,
                    onClick = { onModeChange(EditorMode.CAPTIONS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EditorModeTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

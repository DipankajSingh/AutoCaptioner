package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.ui.videoeditor.core.EditorMode
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StylePanel
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StyleViewModel

@Composable
fun EditorBottomDock(
    maxHeight: Dp,
    clips: List<Clip>,
    clipThumbnails: Map<String, List<Bitmap>>,
    selectedClipId: String?,
    onClipSelected: (String?) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    overlays: List<ImageOverlayEntity>,
    selectedOverlayId: String?,
    onOverlaySelected: (String?) -> Unit,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    zoomLevel: Float,
    player: Player,
    currentTimelineMs: () -> Long,
    onTrimClip: (String, Long, Long) -> Unit,
    onMoveOverlayZ: (String, Boolean) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    styleViewModel: StyleViewModel,
    modifier: Modifier = Modifier
) {
    var currentMode by remember { mutableStateOf(EditorMode.VIDEO) }
    var timelineHeight by remember { mutableStateOf(300.dp) }
    val maxTimelineHeight = maxHeight * 0.5f

    Column(modifier = modifier) {
        // Dynamic Tools Window
        Box(modifier = Modifier.fillMaxWidth().height(timelineHeight)) {
            when (currentMode) {
                EditorMode.VIDEO -> {
                    VideoTimelinePanel(
                        timelineHeight = timelineHeight,
                        maxTimelineHeight = maxTimelineHeight,
                        onTimelineHeightChanged = { timelineHeight = it },
                        clips = clips,
                        clipThumbnails = clipThumbnails,
                        selectedClipId = selectedClipId,
                        onClipSelected = onClipSelected,
                        onMoveClip = onMoveClip,
                        overlays = overlays,
                        selectedOverlayId = selectedOverlayId,
                        onOverlaySelected = onOverlaySelected,
                        onUpdateOverlay = onUpdateOverlay,
                        onCaptionTap = { currentMode = EditorMode.CAPTIONS },
                        onDragStateChange = onDragStateChange,
                        zoomLevel = zoomLevel,
                        player = player,
                        currentTimelineMs = currentTimelineMs,
                        onTrimClip = onTrimClip,
                        onMoveOverlayZ = onMoveOverlayZ,
                        onDeleteOverlay = onDeleteOverlay,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                EditorMode.CAPTIONS -> {
                    StylePanel(
                        viewModel = styleViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                EditorMode.AUDIO -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AudioToolbar()
                    }
                }
            }
        }
        
        // Bottom Tab Bar
        Surface(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactTabItem(
                    icon = Icons.Outlined.Movie,
                    label = "Video",
                    selected = currentMode == EditorMode.VIDEO,
                    onClick = { currentMode = EditorMode.VIDEO }
                )
                CompactTabItem(
                    icon = Icons.Outlined.Subtitles,
                    label = "Captions",
                    selected = currentMode == EditorMode.CAPTIONS,
                    onClick = { currentMode = EditorMode.CAPTIONS }
                )
                CompactTabItem(
                    icon = Icons.Outlined.MusicNote,
                    label = "Audio",
                    selected = currentMode == EditorMode.AUDIO,
                    onClick = { currentMode = EditorMode.AUDIO }
                )
            }
        }
    }
}

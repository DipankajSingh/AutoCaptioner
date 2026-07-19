package com.dipdev.aiautocaptioner.ui.videoeditor.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.engine.CaptionRenderer
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.videoeditor.overlay.OverlayActionMenu
import com.dipdev.aiautocaptioner.ui.videoeditor.overlay.OverlayRenderer

@Composable
fun PreviewSection(
    player: Player,
    overlays: List<ImageOverlayEntity>,
    currentTimelineMs: () -> Long,
    currentSourceMs: () -> Long,
    selectedOverlayId: String?,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    onSelectOverlay: (String?) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onBringToFront: (String) -> Unit,
    onSendToBack: (String) -> Unit,
    onFullVideo: (ImageOverlayEntity) -> Unit,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    activeStyle: CaptionStyleEntity? = null,
    segments: List<CaptionSegmentEntity> = emptyList(),
    wordsMap: Map<String, List<CaptionWordEntity>> = emptyMap(),
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        VideoPlayerCard(
            player = player,
            modifier = Modifier.fillMaxSize(),
            showControls = false,
            enableTapOverlay = false
        )

        PlayPauseTapOverlay(player = player)

        OverlayRenderer(
            overlays = overlays,
            currentTimelineMs = currentTimelineMs,
            selectedOverlayId = selectedOverlayId,
            onUpdateOverlay = onUpdateOverlay,
            onSelectOverlay = { id -> onSelectOverlay(id) },
            videoWidth = videoWidth,
            videoHeight = videoHeight
        )

        val selectedOverlay = selectedOverlayId?.let { id -> overlays.find { it.id == id } }
        if (selectedOverlay != null) {
            OverlayActionMenu(
                selectedOverlay = selectedOverlay,
                onFullVideo = { onFullVideo(selectedOverlay) },
                onSendToBack = { onSendToBack(selectedOverlay.id) },
                onBringToFront = { onBringToFront(selectedOverlay.id) },
                onDelete = { onDeleteOverlay(selectedOverlay.id) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (activeStyle != null && segments.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val srcMs = currentSourceMs()
                drawIntoCanvas { canvas ->
                    CaptionRenderer.draw(
                        canvas = canvas.nativeCanvas,
                        currentPositionMs = srcMs,
                        videoWidth = size.width.toInt(),
                        videoHeight = size.height.toInt(),
                        style = activeStyle,
                        segments = segments,
                        wordsMap = wordsMap
                    )
                }
            }
        }
    }
}

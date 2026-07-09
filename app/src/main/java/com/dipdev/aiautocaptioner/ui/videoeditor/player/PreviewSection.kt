package com.dipdev.aiautocaptioner.ui.videoeditor.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.videoeditor.overlay.OverlayRenderer

@Composable
fun PreviewSection(
    player: Player,
    overlays: List<ImageOverlayEntity>,
    currentTimelineMs: () -> Long,
    selectedOverlayId: String?,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    onSelectOverlay: (String?) -> Unit,
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
            onSelectOverlay = onSelectOverlay
        )
    }
}

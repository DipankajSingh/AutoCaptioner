package com.dipdev.aiautocaptioner.ui.videoeditor.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.engine.CaptionRenderer
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
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
            videoHeight = videoHeight,
            player = player
        )

        if (activeStyle != null && segments.isNotEmpty() && videoWidth > 0 && videoHeight > 0) {
            val context = LocalContext.current
            Canvas(modifier = Modifier.fillMaxSize()) {
                val srcMs = currentSourceMs()

                val containerW = size.width
                val containerH = size.height
                val scale = minOf(containerW / videoWidth.toFloat(), containerH / videoHeight.toFloat())
                val offsetX = (containerW - videoWidth * scale) / 2f
                val offsetY = (containerH - videoHeight * scale) / 2f

                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(offsetX, offsetY)
                    native.scale(scale, scale)
                    CaptionRenderer.draw(
                        context = context,
                        canvas = native,
                        currentPositionMs = srcMs,
                        videoWidth = videoWidth,
                        videoHeight = videoHeight,
                        style = activeStyle,
                        segments = segments,
                        wordsMap = wordsMap
                    )
                    native.restore()
                }
            }
        }
    }
}

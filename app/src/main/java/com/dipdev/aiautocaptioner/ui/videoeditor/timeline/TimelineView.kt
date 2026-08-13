package com.dipdev.aiautocaptioner.ui.videoeditor.timeline

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.ui.videoeditor.image.components.ImageOverlayTrackItem
import com.dipdev.aiautocaptioner.ui.videoeditor.text.components.TextOverlayTrackItem
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import compose.icons.FeatherIcons
import compose.icons.feathericons.Type

sealed interface TimelineTrackItem {
    val id: String
    val zOrder: Int
}

data class ImageTrackItem(val entity: ImageOverlayEntity) : TimelineTrackItem {
    override val id = entity.id
    override val zOrder = entity.zOrder
}

data class TextTrackItem(val entity: TextOverlayEntity) : TimelineTrackItem {
    override val id = entity.id
    override val zOrder = entity.zOrder
}
@SuppressLint("DefaultLocale")
@Composable
fun TimelineView(
    modifier: Modifier = Modifier,
    clips: ImmutableList<Clip>,
    thumbnails: Map<Long, Bitmap>,
    onRequestThumbnails: (List<Long>) -> Unit,
    originalDurationMs: Long,
    selectedClipId: String?,
    onClipSelected: (String) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    overlays: ImmutableList<ImageOverlayEntity> = persistentListOf(),
    selectedOverlayId: String? = null,
    onOverlaySelected: (String) -> Unit = {},
    onOverlayTimingChanged: (id: String, startTimeMs: Long, endTimeMs: Long) -> Unit = {_,_,_ ->},
    textOverlays: ImmutableList<TextOverlayEntity> = persistentListOf(),
    onTextOverlayTimingChanged: (id: String, startTimeMs: Long, endTimeMs: Long) -> Unit = {_,_,_ ->},
    onMoveOverlayZ: (String, Boolean) -> Unit = {_,_ ->},
    onDragStateChange: (Boolean) -> Unit,
    zoomLevel: Float,
    player: Player,
    currentTimelineMs: () -> Long,
    onTrimClip: (String, Long, Long) -> Unit = {_,_,_ ->},
    segments: List<com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity> = emptyList(),
    selectedCaptionSegmentId: String? = null,
    onCaptionSegmentTap: (com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity) -> Unit = {}
) {
    val state = rememberTimelineState(
        clips = clips,
        zoomLevel = zoomLevel,
        player = player,
        currentTimelineMs = currentTimelineMs,
        onMoveClip = onMoveClip,
        onRequestThumbnails = onRequestThumbnails,
        onDragStateChange = onDragStateChange
    )

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val surfaceColor = MaterialTheme.colorScheme.background
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .background(surfaceColor)
            .onGloballyPositioned { coordinates -> state.boxWidthPx = coordinates.size.width }
    ) {
        val halfWidthDp = with(density) { (state.boxWidthPx / 2).toDp() }
        val safeTotalWidthPx = maxOf(1f, state.totalEditedMs * state.pixelsPerMs)
        val totalWidthDp = with(density) { safeTotalWidthPx.toDp() }
        val halfWidthPx = state.boxWidthPx / 2f

        // Main Timeline Area
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(state.scrollState, enabled = true)
        ) {
            Spacer(modifier = Modifier.width(halfWidthDp - 40.dp))

            Column(modifier = Modifier.fillMaxHeight()) {
                Row {
                    Spacer(modifier = Modifier.width(40.dp))
                    TimelineRuler(state.totalEditedMs, state.pixelsPerMs, totalWidthDp, zoomLevel, textMeasurer, onSurfaceColor)
                }
                
                Column(modifier = Modifier.weight(1f).verticalScroll(state.verticalScrollState)) {
                    // Caption Track
                    if (segments.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(
                                            topStart = 6.dp, bottomStart = 6.dp
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    FeatherIcons.Type,
                                    contentDescription = stringResource(R.string.dock_captions),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(totalWidthDp)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                        RoundedCornerShape(
                                            topEnd = 6.dp, bottomEnd = 6.dp
                                        )
                                    )
                            ) {
                                segments.forEach { seg ->
                                    key(seg.id) {
                                        CaptionTrackItem(
                                            segment = seg,
                                            clips = clips,
                                            pixelsPerMs = state.pixelsPerMs,
                                            isSelected = seg.id == selectedCaptionSegmentId,
                                            onTap = onCaptionSegmentTap
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Video Clips Track
                    if (clips.isEmpty()) {
                        Row(modifier = Modifier.height(100.dp).fillMaxWidth()) {
                            Spacer(modifier = Modifier.width(40.dp))
                            Box(modifier = Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.timeline_tap_to_trim),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        Row(modifier = Modifier.height(56.dp).fillMaxWidth()) {
                            Spacer(modifier = Modifier.width(40.dp))
                            clips.forEachIndexed { index, clip ->
                                key(clip.id) {
                                    val durationMs = clip.endTrimMs - clip.startTrimMs
                                    val clipWidthPx = maxOf(1f, durationMs * state.pixelsPerMs)
                                    val clipWidthDp = with(density) { clipWidthPx.toDp() }
                                    
                                    val isSelected = clip.id == selectedClipId
                                    val currentClipIndex by rememberUpdatedState(index)
                                    val isBeingDragged = state.draggingClipIndex == currentClipIndex
                                    val layoutCenter = if(index < state.clipLayoutCenters.size) state.clipLayoutCenters[index] else 0f
                                    val currentDragOffset = if (isBeingDragged) {
                                        (state.dragPointerScreenX + state.scrollOffset) - layoutCenter
                                    } else { 0f }
                                    
                                    val hasGapBefore = index > 0 && clips[index].startTrimMs >= clips[index - 1].endTrimMs

                                    VideoClipItem(
                                        clip = clip,
                                        index = index,
                                        thumbnails = thumbnails,
                                        originalDurationMs = originalDurationMs,
                                        isSelected = isSelected,
                                        clipWidthPx = clipWidthPx,
                                        clipWidthDp = clipWidthDp,
                                        isBeingDragged = isBeingDragged,
                                        currentDragOffset = currentDragOffset,
                                        clipLayoutCenters = state.clipLayoutCenters,
                                        scrollStateValue = state.scrollOffset,
                                        surfaceVariantColor = surfaceVariantColor,
                                        outlineColor = outlineColor,
                                        onDragStateChange = onDragStateChange,
                                        onDragPointerStart = { state.dragPointerScreenX = it },
                                        onDragPointerChange = { state.dragPointerScreenX += it },
                                        onCheckSwaps = state.checkSwaps,
                                        onDraggingIndexChange = { state.draggingClipIndex = it },
                                        onClipSelected = onClipSelected,
                                        hasGapBefore = hasGapBefore,
                                        onTrimClip = onTrimClip,
                                        pixelsPerMs = state.pixelsPerMs,
                                        thumbnailIntervalMs = state.thumbnailIntervalMs
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(totalWidthDp).height(4.dp))

                    val allTracks = androidx.compose.runtime.remember(overlays, textOverlays) {
                        (overlays.map { ImageTrackItem(it) } + textOverlays.map { TextTrackItem(it) })
                            .sortedByDescending { it.zOrder }
                    }

                    allTracks.forEach { trackItem ->
                        when (trackItem) {
                            is ImageTrackItem -> {
                                val overlay = trackItem.entity
                                key(overlay.id) {
                                    DraggableTrackContainer(
                                        trackId = overlay.id,
                                        onMoveLayer = { moveUp -> onMoveOverlayZ(overlay.id, moveUp) }
                                    ) {
                                        Box(modifier = Modifier.width(totalWidthDp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))) {
                                            val endTimeMs = if (overlay.endTimeMs == Long.MAX_VALUE) state.totalEditedMs else overlay.endTimeMs.coerceAtMost(state.totalEditedMs)
                                            val startTimeMs = overlay.startTimeMs.coerceAtMost(state.totalEditedMs)
                                            val durationMs = maxOf(0L, endTimeMs - startTimeMs)
                                            
                                            if (durationMs > 0) {
                                                ImageOverlayTrackItem(
                                                    overlay = overlay,
                                                    isSelectedOverlay = overlay.id == selectedOverlayId,
                                                    pixelsPerMs = state.pixelsPerMs,
                                                    currentEndTimeMs = endTimeMs,
                                                    totalEditedMs = state.totalEditedMs,
                                                    primaryColor = primaryColor,
                                                    scrollStateValue = state.scrollOffset,
                                                    trackContentOffsetPx = halfWidthPx,
                                                    onOverlaySelected = onOverlaySelected,
                                                    onDragStateChange = { 
                                                        onDragStateChange(it)
                                                        if (!it) state.draggingOverlayId = null
                                                    },
                           /**
 * Converts an exact pixel font size into the TextUnit sp value that Compose
 * renders as that many px (accounting for density and system font scale).
 */                         onOverlayTimingChanged = onOverlayTimingChanged,
                                                    onDragPointerStart = {
                                                        state.dragPointerScreenX = it
                                                        state.draggingOverlayId = overlay.id
                                                    },
                                                    onDragPointerMove = { state.dragPointerScreenX = it }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            is TextTrackItem -> {
                                val overlay = trackItem.entity
                                key(overlay.id) {
                                    DraggableTrackContainer(
                                        trackId = overlay.id,
                                        onMoveLayer = { moveUp -> onMoveOverlayZ(overlay.id, moveUp) }
                                    ) {
                                        Box(modifier = Modifier.width(totalWidthDp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))) {
                                            val endTimeMs = if (overlay.endTimeMs == Long.MAX_VALUE) state.totalEditedMs else overlay.endTimeMs.coerceAtMost(state.totalEditedMs)
                                            val startTimeMs = overlay.startTimeMs.coerceAtMost(state.totalEditedMs)
                                            val durationMs = maxOf(0L, endTimeMs - startTimeMs)
                                            
                                            if (durationMs > 0) {
                                                TextOverlayTrackItem(
                                                    overlay = overlay,
                                                    isSelectedOverlay = overlay.id == selectedOverlayId,
                                                    pixelsPerMs = state.pixelsPerMs,
                                                    currentEndTimeMs = endTimeMs,
                                                    totalEditedMs = state.totalEditedMs,
                                                    primaryColor = primaryColor,
                                                    scrollStateValue = state.scrollOffset,
                                                    trackContentOffsetPx = halfWidthPx,
                                                    onOverlaySelected = { onOverlaySelected(overlay.id) },
                                                    onDragStateChange = { 
                                                        onDragStateChange(it)
                                                        if (!it) state.draggingOverlayId = null
                                                    },
                                                    onOverlayTimingChanged = onTextOverlayTimingChanged,
                                                    onDragPointerStart = {
                                                        state.dragPointerScreenX = it
                                                        state.draggingOverlayId = overlay.id
                                                    },
                                                    onDragPointerMove = { state.dragPointerScreenX = it }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(totalWidthDp).height(40.dp))
                }
            }

            Spacer(modifier = Modifier.width(halfWidthDp))
        }
        
        Box(modifier = Modifier.align(Alignment.Center).fillMaxHeight()) {
            PlayheadMarker()
        }
    }
}

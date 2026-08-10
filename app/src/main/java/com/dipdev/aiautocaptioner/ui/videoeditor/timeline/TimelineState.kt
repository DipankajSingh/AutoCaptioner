package com.dipdev.aiautocaptioner.ui.videoeditor.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.data.model.mergeContiguousClips
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

@Stable
class TimelineState(
    val scrollState: ScrollState,
    val verticalScrollState: ScrollState
) {
    var boxWidthPx by mutableIntStateOf(0)
    var draggingClipIndex by mutableStateOf<Int?>(null)
    var draggingOverlayId by mutableStateOf<String?>(null)
    var dragPointerScreenX by mutableFloatStateOf(0f)

    var pixelsPerMs by mutableFloatStateOf(0f)
    var totalEditedMs by mutableLongStateOf(0L)
    var thumbnailIntervalMs by mutableLongStateOf(0L)
    var clipLayoutCenters by mutableStateOf(FloatArray(0))

    var checkSwaps: () -> Unit = {}
    val scrollOffset: Int get() = scrollState.value
}

@Composable
fun rememberTimelineState(
    clips: ImmutableList<Clip>,
    zoomLevel: Float,
    player: Player,
    currentTimelineMs: () -> Long,
    onMoveClip: (Int, Int) -> Unit,
    onRequestThumbnails: (List<Long>) -> Unit,
    onDragStateChange: (Boolean) -> Unit
): TimelineState {
    val state = remember {
        TimelineState(
            scrollState = ScrollState(0),
            verticalScrollState = ScrollState(0)
        )
    }

    val density = LocalDensity.current
    state.pixelsPerMs = with(density) { (50.dp.toPx() / 1000f) * zoomLevel.coerceAtLeast(0.1f) }
    state.totalEditedMs = remember(clips) { clips.sumOf { it.endTrimMs - it.startTrimMs } }

    val halfWidthPx = state.boxWidthPx / 2f
    state.clipLayoutCenters = remember(clips, state.pixelsPerMs, halfWidthPx) {
        val centers = FloatArray(clips.size)
        var accX = 0f
        for (i in clips.indices) {
            val width = (clips[i].endTrimMs - clips[i].startTrimMs) * state.pixelsPerMs
            centers[i] = halfWidthPx + accX + width / 2
            accX += width
        }
        centers
    }

    state.checkSwaps = {
        val draggedIdx = state.draggingClipIndex
        if (draggedIdx != null && draggedIdx in clips.indices) {
            val centerInRow = state.dragPointerScreenX + state.scrollState.value
            var swapped = false
            if (draggedIdx < state.clipLayoutCenters.size - 1) {
                val nextCenter = state.clipLayoutCenters[draggedIdx + 1]
                if (centerInRow > nextCenter) {
                    onMoveClip(draggedIdx, draggedIdx + 1)
                    state.draggingClipIndex = draggedIdx + 1
                    swapped = true
                }
            }
            if (!swapped && draggedIdx > 0 && draggedIdx < state.clipLayoutCenters.size) {
                val prevCenter = state.clipLayoutCenters[draggedIdx - 1]
                if (centerInRow < prevCenter) {
                    onMoveClip(draggedIdx, draggedIdx - 1)
                    state.draggingClipIndex = draggedIdx - 1
                }
            }
        }
    }

    LaunchedEffect(state.draggingClipIndex, state.draggingOverlayId) {
        if (state.draggingClipIndex != null || state.draggingOverlayId != null) {
            val edgeThreshold = with(density) { 60.dp.toPx() }
            val speed = 15f
            while (isActive) {
                var scrolled = false
                if (state.dragPointerScreenX < edgeThreshold && state.scrollState.value > 0) {
                    state.scrollState.scrollTo((state.scrollState.value - speed.toInt()).coerceAtLeast(0))
                    scrolled = true
                } else if (state.dragPointerScreenX > state.boxWidthPx - edgeThreshold && state.scrollState.value < state.scrollState.maxValue) {
                    state.scrollState.scrollTo((state.scrollState.value + speed.toInt()).coerceAtMost(state.scrollState.maxValue))
                    scrolled = true
                }
                if (scrolled) state.checkSwaps()
                delay(16.milliseconds)
            }
        }
    }

    val targetChunkMs = (1000f / zoomLevel.coerceAtLeast(0.1f)).toLong()
    state.thumbnailIntervalMs = remember(targetChunkMs) {
        when {
            targetChunkMs <= 100 -> 100L
            targetChunkMs <= 250 -> 250L
            targetChunkMs <= 500 -> 500L
            targetChunkMs <= 1000 -> 1000L
            targetChunkMs <= 2000 -> 2000L
            else -> 5000L
        }
    }

    LaunchedEffect(state.boxWidthPx, state.pixelsPerMs, clips, state.thumbnailIntervalMs) {
        if (state.boxWidthPx == 0 || state.pixelsPerMs == 0f) return@LaunchedEffect
        snapshotFlow { state.scrollState.value }.collect {
            delay(80L.milliseconds)

            val visibleStartMs = (state.scrollState.value / state.pixelsPerMs).toLong()
            val visibleEndMs = ((state.scrollState.value + state.boxWidthPx) / state.pixelsPerMs).toLong()

            val requested = mutableSetOf<Long>()
            var currentTimelineMs = 0L

            for (clip in clips) {
                val clipDurationMs = clip.endTrimMs - clip.startTrimMs
                val clipStartTimelineMs = currentTimelineMs
                val clipEndTimelineMs = currentTimelineMs + clipDurationMs

                if (clipEndTimelineMs > visibleStartMs && clipStartTimelineMs < visibleEndMs) {
                    val visibleClipStartMs = maxOf(clipStartTimelineMs, visibleStartMs)
                    val visibleClipEndMs = minOf(clipEndTimelineMs, visibleEndMs)

                    val offsetIntoClipStartMs = visibleClipStartMs - clipStartTimelineMs
                    val offsetIntoClipEndMs = visibleClipEndMs - clipStartTimelineMs

                    val originalStartMs = clip.startTrimMs + offsetIntoClipStartMs
                    val originalEndMs = clip.startTrimMs + offsetIntoClipEndMs

                    val startChunk = (originalStartMs / state.thumbnailIntervalMs) * state.thumbnailIntervalMs
                    val endChunk = (originalEndMs / state.thumbnailIntervalMs) * state.thumbnailIntervalMs

                    for (time in startChunk..endChunk step state.thumbnailIntervalMs) {
                        requested.add(time)
                    }
                }
                currentTimelineMs += clipDurationMs
            }

            onRequestThumbnails(requested.toList())
        }
    }

    val mergedClips = remember(clips) { mergeContiguousClips(clips) }

    LaunchedEffect(state.scrollState.isScrollInProgress, state.pixelsPerMs) {
        if (state.scrollState.isScrollInProgress) {
            onDragStateChange(true)
            player.pause()
            var lastSeekTime = -1L
            snapshotFlow { state.scrollState.value }.collect { scrollValue ->
                val seekTimeMs = Math.round(scrollValue.toDouble() / state.pixelsPerMs.toDouble())
                if (kotlin.math.abs(seekTimeMs - lastSeekTime) > 20L) {
                    lastSeekTime = seekTimeMs
                    var accumulated = 0L
                    var targetWindowIndex = 0
                    var targetPosInWindow = 0L

                    for (i in mergedClips.indices) {
                        val clipDuration = mergedClips[i].endTrimMs - mergedClips[i].startTrimMs
                        if (seekTimeMs >= accumulated && seekTimeMs < accumulated + clipDuration) {
                            targetWindowIndex = i
                            targetPosInWindow = seekTimeMs - accumulated
                            break
                        }
                        accumulated += clipDuration
                    }

                    if (seekTimeMs >= state.totalEditedMs && mergedClips.isNotEmpty()) {
                        targetWindowIndex = mergedClips.size - 1
                        targetPosInWindow = mergedClips.last().endTrimMs - mergedClips.last().startTrimMs
                    }

                    player.seekTo(targetWindowIndex, targetPosInWindow)
                }
            }
        } else {
            if (state.draggingClipIndex == null && state.draggingOverlayId == null) {
                onDragStateChange(false)
            }
            snapshotFlow { currentTimelineMs() }.collect { timeMs ->
                if (player.isPlaying) {
                    val scrollOffset = Math.round(timeMs.toDouble() * state.pixelsPerMs.toDouble()).toInt()
                    state.scrollState.scrollTo(scrollOffset)
                }
            }
        }
    }

    return state
}

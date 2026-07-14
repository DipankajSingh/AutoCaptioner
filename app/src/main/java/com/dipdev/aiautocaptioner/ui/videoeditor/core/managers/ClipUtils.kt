package com.dipdev.aiautocaptioner.ui.videoeditor.core.managers

import com.dipdev.aiautocaptioner.data.model.Clip

/**
 * Maps an absolute timeline position (ms) to an ExoPlayer (windowIndex, positionInWindow) pair.
 *
 * This is the single source of truth for timeline → player position translation.
 * Previously duplicated in TimelineView, PlaybackControls, and EditorState.
 *
 * @param absoluteMs The absolute position in the edited timeline (0 = start of first clip).
 * @param clips      The merged/active clip list that ExoPlayer is loaded with.
 * @return A [Pair] of (windowIndex, positionInWindowMs). Clamped safely if absoluteMs exceeds total duration.
 */
fun resolveTimelinePosition(absoluteMs: Long, clips: List<Clip>): Pair<Int, Long> {
    if (clips.isEmpty()) return Pair(0, 0L)
    var accumulated = 0L
    for (i in clips.indices) {
        val duration = clips[i].endTrimMs - clips[i].startTrimMs
        if (absoluteMs < accumulated + duration) {
            return Pair(i, absoluteMs - accumulated)
        }
        accumulated += duration
    }
    // Beyond the end — clamp to last clip's end
    val lastIndex = clips.size - 1
    val lastDuration = clips[lastIndex].endTrimMs - clips[lastIndex].startTrimMs
    return Pair(lastIndex, lastDuration)
}

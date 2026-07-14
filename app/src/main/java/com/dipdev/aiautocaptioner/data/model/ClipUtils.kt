package com.dipdev.aiautocaptioner.data.model

/**
 * Utility functions for Clip operations.
 */
fun mergeContiguousClips(clips: List<Clip>): List<Clip> {
    val list = mutableListOf<Clip>()
    var currentMergedClip: Clip? = null
    
    for (clip in clips) {
        if (currentMergedClip == null) {
            currentMergedClip = clip
        } else {
            if (currentMergedClip.endTrimMs == clip.startTrimMs) {
                currentMergedClip = currentMergedClip.copy(endTrimMs = clip.endTrimMs)
            } else {
                list.add(currentMergedClip)
                currentMergedClip = clip
            }
        }
    }
    
    if (currentMergedClip != null) {
        list.add(currentMergedClip)
    }
    
    return list
}

/**
 * Maps a source-video absolute timestamp (ms) to an edited-timeline timestamp (ms).
 * Returns null if the source time falls in a trimmed-out region.
 *
 * Example: clips = [Clip(2000..5000), Clip(8000..10000)]
 *   sourceToTimelineMs(3000, clips) → 1000  (1 s into the first clip)
 *   sourceToTimelineMs(9000, clips) → 4000  (3s clip + 1s into second clip)
 *   sourceToTimelineMs(6000, clips) → null  (trimmed out)
 */
fun sourceToTimelineMs(sourceMs: Long, clips: List<Clip>): Long? {
    var timelineAccum = 0L
    for (clip in clips) {
        val clipDuration = clip.endTrimMs - clip.startTrimMs
        if (sourceMs in clip.startTrimMs until clip.endTrimMs) {
            return timelineAccum + (sourceMs - clip.startTrimMs)
        }
        timelineAccum += clipDuration
    }
    return null
}

/**
 * Returns the [start, end) range on the edited timeline that a caption segment occupies.
 * Handles segments that span clip gaps by clamping to the visible clip regions.
 * Returns null if the segment is entirely trimmed out.
 */
fun segmentToTimelineRange(startSourceMs: Long, endSourceMs: Long, clips: List<Clip>): Pair<Long, Long>? {
    var timelineAccum = 0L
    var rangeStart: Long? = null
    var rangeEnd: Long? = null

    for (clip in clips) {
        val clipDuration = clip.endTrimMs - clip.startTrimMs
        val clipTimelineEnd = timelineAccum + clipDuration

        // Overlap: source [startSourceMs, endSourceMs) ∩ clip [startTrimMs, endTrimMs)
        val overlapStart = maxOf(startSourceMs, clip.startTrimMs)
        val overlapEnd   = minOf(endSourceMs,   clip.endTrimMs)

        if (overlapStart < overlapEnd) {
            val tStart = timelineAccum + (overlapStart - clip.startTrimMs)
            val tEnd   = timelineAccum + (overlapEnd   - clip.startTrimMs)
            if (rangeStart == null) rangeStart = tStart
            rangeEnd = tEnd
        }
        timelineAccum = clipTimelineEnd
    }

    return if (rangeStart != null && rangeEnd != null) Pair(rangeStart, rangeEnd) else null
}


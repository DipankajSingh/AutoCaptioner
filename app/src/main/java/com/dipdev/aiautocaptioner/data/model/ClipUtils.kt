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
 * Internal index cache to provide O(log N) binary interval search over clip geometries
 * and O(1) cumulative duration lookup.
 */
private class ClipIndex(
    val clipsRef: List<Clip>,
    val prefixSums: LongArray,
    val sortedIndices: IntArray
)

@Volatile
private var cachedClipIndex: ClipIndex? = null

private fun getClipIndex(clips: List<Clip>): ClipIndex {
    val current = cachedClipIndex
    if (current != null && (current.clipsRef === clips || current.clipsRef == clips)) {
        return current
    }
    val n = clips.size
    val sums = LongArray(n)
    var accum = 0L
    for (i in 0 until n) {
        sums[i] = accum
        accum += (clips[i].endTrimMs - clips[i].startTrimMs)
    }
    val sortedIndices = (0 until n).sortedBy { clips[it].endTrimMs }.toIntArray()
    val newIndex = ClipIndex(clips, sums, sortedIndices)
    cachedClipIndex = newIndex
    return newIndex
}

/**
 * Maps a source-video absolute timestamp (ms) to an edited-timeline timestamp (ms).
 * Returns null if the source time falls in a trimmed-out region.
 *
 * Uses O(log N) binary interval search over precomputed clip geometries.
 */
fun sourceToTimelineMs(sourceMs: Long, clips: List<Clip>): Long? {
    if (clips.isEmpty()) return null
    val index = getClipIndex(clips)
    val sorted = index.sortedIndices

    var low = 0
    var high = sorted.size - 1
    var firstIdx = sorted.size
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (clips[sorted[mid]].endTrimMs > sourceMs) {
            firstIdx = mid
            high = mid - 1
        } else {
            low = mid + 1
        }
    }

    for (i in firstIdx until sorted.size) {
        val clipIdx = sorted[i]
        val clip = clips[clipIdx]
        if (clip.startTrimMs > sourceMs) {
            break
        }
        if (sourceMs in clip.startTrimMs until clip.endTrimMs) {
            return index.prefixSums[clipIdx] + (sourceMs - clip.startTrimMs)
        }
    }
    return null
}

/**
 * Returns the [start, end) range on the edited timeline that a caption segment occupies.
 * Handles segments that span clip gaps by clamping to the visible clip regions.
 * Returns null if the segment is entirely trimmed out.
 *
 * Uses O(log N) binary interval search to avoid O(S * N) linear iteration when rendering tracks.
 */
fun segmentToTimelineRange(startSourceMs: Long, endSourceMs: Long, clips: List<Clip>): Pair<Long, Long>? {
    if (clips.isEmpty()) return null
    val index = getClipIndex(clips)
    val sorted = index.sortedIndices

    var low = 0
    var high = sorted.size - 1
    var firstIdx = sorted.size
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (clips[sorted[mid]].endTrimMs > startSourceMs) {
            firstIdx = mid
            high = mid - 1
        } else {
            low = mid + 1
        }
    }

    var rangeStart: Long? = null
    var rangeEnd: Long? = null

    for (i in firstIdx until sorted.size) {
        val clipIdx = sorted[i]
        val clip = clips[clipIdx]
        if (clip.startTrimMs >= endSourceMs) {
            break
        }

        val overlapStart = maxOf(startSourceMs, clip.startTrimMs)
        val overlapEnd   = minOf(endSourceMs,   clip.endTrimMs)

        if (overlapStart < overlapEnd) {
            val tStart = index.prefixSums[clipIdx] + (overlapStart - clip.startTrimMs)
            val tEnd   = index.prefixSums[clipIdx] + (overlapEnd   - clip.startTrimMs)
            rangeStart = if (rangeStart == null) tStart else minOf(rangeStart, tStart)
            rangeEnd   = if (rangeEnd   == null) tEnd   else maxOf(rangeEnd,   tEnd)
        }
    }

    return if (rangeStart != null && rangeEnd != null) Pair(rangeStart, rangeEnd) else null
}


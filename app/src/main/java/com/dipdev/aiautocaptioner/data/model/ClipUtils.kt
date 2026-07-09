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

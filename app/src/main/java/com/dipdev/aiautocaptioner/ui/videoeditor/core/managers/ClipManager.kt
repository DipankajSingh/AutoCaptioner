package com.dipdev.aiautocaptioner.ui.videoeditor.core.managers

import com.dipdev.aiautocaptioner.data.model.Clip

class ClipManager(
    private val getOriginalDurationMs: () -> Long,
    private val getCurrentClips: () -> List<Clip>,
    private val onStateChanged: (clips: List<Clip>, hasEdits: Boolean, canUndo: Boolean, canRedo: Boolean) -> Unit,
    private val onUndoToOriginal: () -> Unit
) {
    private val history = mutableListOf<List<Clip>>()
    private var historyIndex = -1

    fun saveState(clipsToSave: List<Clip> = getCurrentClips()) {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(ArrayList(clipsToSave))
        historyIndex++
        
        if (history.size > 50) {
            history.removeAt(0)
            historyIndex--
        }
        
        updateState(clipsToSave, hasEdits = true)
    }

    private fun updateState(newClips: List<Clip>, hasEdits: Boolean) {
        onStateChanged(
            newClips,
            hasEdits,
            historyIndex >= 0,
            historyIndex < history.size - 1
        )
    }

    fun undo() {
        val canUndo = historyIndex >= 0
        if (canUndo) {
            val currentClips = getCurrentClips()
            if (historyIndex == history.size - 1 && history.lastOrNull() != currentClips) {
                saveState(currentClips)
                historyIndex--
            }
            historyIndex--
            val newClips = if (historyIndex >= 0) history[historyIndex] else emptyList()
            
            if (historyIndex < 0) {
                onUndoToOriginal()
            } else {
                updateState(newClips, hasEdits = true)
            }
        }
    }

    fun redo() {
        val canRedo = historyIndex < history.size - 1
        if (canRedo) {
            historyIndex++
            updateState(history[historyIndex], hasEdits = true)
        }
    }

    fun splitClipAtAbsoluteTime(absoluteTimelineMs: Long) {
        val currentClips = getCurrentClips().toMutableList()
        var accumulated = 0L
        var targetClipIndex = -1
        var relativeSplitMs = 0L

        for (i in currentClips.indices) {
            val clip = currentClips[i]
            val clipDuration = clip.endTrimMs - clip.startTrimMs
            if (absoluteTimelineMs >= accumulated && absoluteTimelineMs < accumulated + clipDuration) {
                targetClipIndex = i
                relativeSplitMs = absoluteTimelineMs - accumulated
                break
            }
            accumulated += clipDuration
        }

        if (targetClipIndex != -1) {
            val clip = currentClips[targetClipIndex]
            val absoluteSplitMs = clip.startTrimMs + relativeSplitMs
            if (absoluteSplitMs >= clip.startTrimMs + 100 && absoluteSplitMs <= clip.endTrimMs - 100) {
                saveState(currentClips)
                val clip1 = Clip(startTrimMs = clip.startTrimMs, endTrimMs = absoluteSplitMs)
                val clip2 = Clip(startTrimMs = absoluteSplitMs, endTrimMs = clip.endTrimMs)
                currentClips.removeAt(targetClipIndex)
                currentClips.add(targetClipIndex, clip2)
                currentClips.add(targetClipIndex, clip1)
                updateState(currentClips, hasEdits = true)
            }
        }
    }

    fun deleteClip(clipId: String) {
        val currentClips = getCurrentClips().toMutableList()
        if (currentClips.size > 1) {
            saveState(currentClips)
            currentClips.removeAll { it.id == clipId }
            updateState(currentClips, hasEdits = true)
        }
    }

    fun duplicateClip(clipId: String) {
        val currentClips = getCurrentClips().toMutableList()
        val index = currentClips.indexOfFirst { it.id == clipId }
        if (index != -1) {
            saveState(currentClips)
            val clipToDuplicate = currentClips[index]
            val newClip = Clip(startTrimMs = clipToDuplicate.startTrimMs, endTrimMs = clipToDuplicate.endTrimMs)
            currentClips.add(index + 1, newClip)
            updateState(currentClips, hasEdits = true)
        }
    }

    fun moveClip(fromIndex: Int, toIndex: Int) {
        val currentClips = getCurrentClips().toMutableList()
        if (fromIndex in currentClips.indices && toIndex in currentClips.indices) {
            saveState(currentClips)
            val clip = currentClips.removeAt(fromIndex)
            currentClips.add(toIndex, clip)
            updateState(currentClips, hasEdits = true)
        }
    }
    
    fun reset() {
        history.clear()
        historyIndex = -1
    }
}

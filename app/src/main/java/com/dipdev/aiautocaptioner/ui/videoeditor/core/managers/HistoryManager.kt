package com.dipdev.aiautocaptioner.ui.videoeditor.core.managers

import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip

data class EditorSnapshot(
    val clips: List<Clip>,
    val imageOverlays: List<ImageOverlayEntity>,
    val textOverlays: List<TextOverlayEntity>
)

private const val COALESCE_WINDOW_MS = 500L

class HistoryManager(
    private val getOriginalDurationMs: () -> Long,
    private val getCurrentClips: () -> List<Clip>,
    private val getCurrentImageOverlays: () -> List<ImageOverlayEntity>,
    private val getCurrentTextOverlays: () -> List<TextOverlayEntity>,
    private val onStateChanged: (clips: List<Clip>, imageOverlays: List<ImageOverlayEntity>, textOverlays: List<TextOverlayEntity>, hasEdits: Boolean, canUndo: Boolean, canRedo: Boolean) -> Unit,
    private val onRestoreSnapshot: (EditorSnapshot) -> Unit
) {
    private val history = mutableListOf<EditorSnapshot>()
    private var historyIndex = -1
    private var lastSaveTimeMs = 0L

    fun setBaseline(
        clips: List<Clip>,
        imageOverlays: List<ImageOverlayEntity>,
        textOverlays: List<TextOverlayEntity>
    ) {
        history.clear()
        history.add(
            EditorSnapshot(
                ArrayList(clips),
                ArrayList(imageOverlays),
                ArrayList(textOverlays)
            )
        )
        historyIndex = 0
        lastSaveTimeMs = 0L
    }

    fun saveState(
        clipsToSave: List<Clip> = getCurrentClips(),
        imageOverlaysToSave: List<ImageOverlayEntity> = getCurrentImageOverlays(),
        textOverlaysToSave: List<TextOverlayEntity> = getCurrentTextOverlays()
    ) {
        val snapshot = EditorSnapshot(
            ArrayList(clipsToSave),
            ArrayList(imageOverlaysToSave),
            ArrayList(textOverlaysToSave)
        )
        
        if (historyIndex >= 0 && history[historyIndex] == snapshot) return

        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }

        val now = System.currentTimeMillis()
        if (historyIndex >= 0 && history.isNotEmpty() && now - lastSaveTimeMs < COALESCE_WINDOW_MS) {
            // Coalesce rapid consecutive updates (e.g. per-commit overlay drags)
            // into a single history entry so a drag is one undo step.
            history[historyIndex] = snapshot
        } else {
            history.add(snapshot)
            historyIndex++
            if (history.size > 50) {
                history.removeAt(0)
                historyIndex--
            }
        }
        lastSaveTimeMs = now
        
        updateState(clipsToSave, imageOverlaysToSave, textOverlaysToSave)
    }

    private fun checkHasEdits(clips: List<Clip>): Boolean {
        if (clips.size != 1) return true
        val clip = clips.first()
        return clip.startTrimMs != 0L || clip.endTrimMs != getOriginalDurationMs()
    }

    private fun updateState(newClips: List<Clip>, newImg: List<ImageOverlayEntity>, newTxt: List<TextOverlayEntity>, hasEditsOverride: Boolean? = null) {
        val actualHasEdits = hasEditsOverride ?: checkHasEdits(newClips)
        onStateChanged(
            newClips,
            newImg,
            newTxt,
            actualHasEdits,
            historyIndex >= 0,
            historyIndex < history.size - 1
        )
    }

    fun undo() {
        if (historyIndex < 0) return

        val currentSnapshot = EditorSnapshot(
            getCurrentClips(),
            getCurrentImageOverlays(),
            getCurrentTextOverlays()
        )

        if (historyIndex == history.size - 1 && history.lastOrNull() != currentSnapshot) {
            // Unsaved changes exist on top of the latest history entry.
            // Persist them so they can be stepped back to, then decrement below.
            saveState(currentSnapshot.clips, currentSnapshot.imageOverlays, currentSnapshot.textOverlays)
        }

        historyIndex--
        if (historyIndex < 0) historyIndex = 0

        val newSnapshot = history[historyIndex]
        onRestoreSnapshot(newSnapshot)
        updateState(newSnapshot.clips, newSnapshot.imageOverlays, newSnapshot.textOverlays)
    }

    fun redo() {
        val canRedo = historyIndex < history.size - 1
        if (canRedo) {
            historyIndex++
            val nextSnapshot = history[historyIndex]
            onRestoreSnapshot(nextSnapshot)
            updateState(nextSnapshot.clips, nextSnapshot.imageOverlays, nextSnapshot.textOverlays)
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
                saveState(clipsToSave = currentClips)
                val clip1 = Clip(startTrimMs = clip.startTrimMs, endTrimMs = absoluteSplitMs)
                val clip2 = Clip(startTrimMs = absoluteSplitMs, endTrimMs = clip.endTrimMs)
                currentClips.removeAt(targetClipIndex)
                currentClips.add(targetClipIndex, clip2)
                currentClips.add(targetClipIndex, clip1)
                onRestoreSnapshot(EditorSnapshot(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays()))
                updateState(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays())
            }
        }
    }

    fun deleteClip(clipId: String) {
        val currentClips = getCurrentClips().toMutableList()
        if (currentClips.size > 1) {
            saveState(clipsToSave = currentClips)
            currentClips.removeAll { it.id == clipId }
            onRestoreSnapshot(EditorSnapshot(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays()))
            updateState(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays())
        }
    }

    fun duplicateClip(clipId: String) {
        val currentClips = getCurrentClips().toMutableList()
        val index = currentClips.indexOfFirst { it.id == clipId }
        if (index != -1) {
            saveState(clipsToSave = currentClips)
            val clipToDuplicate = currentClips[index]
            val newClip = Clip(startTrimMs = clipToDuplicate.startTrimMs, endTrimMs = clipToDuplicate.endTrimMs)
            currentClips.add(index + 1, newClip)
            onRestoreSnapshot(EditorSnapshot(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays()))
            updateState(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays())
        }
    }

    fun trimClip(clipId: String, newStartTrimMs: Long, newEndTrimMs: Long, saveToHistory: Boolean = true) {
        val currentClips = getCurrentClips().toMutableList()
        val index = currentClips.indexOfFirst { it.id == clipId }
        if (index != -1) {
            if (saveToHistory) {
                saveState(clipsToSave = currentClips)
            }
            val clipToTrim = currentClips[index]
            currentClips[index] = clipToTrim.copy(startTrimMs = newStartTrimMs, endTrimMs = newEndTrimMs)
            onRestoreSnapshot(EditorSnapshot(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays()))
            updateState(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays())
        }
    }

    fun moveClip(fromIndex: Int, toIndex: Int, saveToHistory: Boolean = true) {
        val currentClips = getCurrentClips().toMutableList()
        if (fromIndex in currentClips.indices && toIndex in currentClips.indices) {
            if (saveToHistory) {
                saveState(clipsToSave = currentClips)
            }
            val clip = currentClips.removeAt(fromIndex)
            currentClips.add(toIndex, clip)
            onRestoreSnapshot(EditorSnapshot(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays()))
            updateState(currentClips, getCurrentImageOverlays(), getCurrentTextOverlays())
        }
    }
    
    fun reset() {
        history.clear()
        historyIndex = -1
        lastSaveTimeMs = 0L
    }
}

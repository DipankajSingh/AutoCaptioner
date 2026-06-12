package com.dipdev.aiautocaptioner.ui.videoeditor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.EditedMediaItemSequence
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.dipdev.aiautocaptioner.core.video.ThumbnailExtractor
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed class VideoEditorUiState {
    data object Idle : VideoEditorUiState()
    data object Loading : VideoEditorUiState()
    data class Ready(val durationMs: Long, val originalPath: String) : VideoEditorUiState()
    data class Processing(val progress: Int) : VideoEditorUiState()
    data object Success : VideoEditorUiState()
    data class Error(val message: String) : VideoEditorUiState()
}

@UnstableApi
@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<VideoEditorUiState>(VideoEditorUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var currentProjectId: String? = null
    private var transformer: Transformer? = null

    private var originalDurationMs: Long = 0L
    private var originalVideoPath: String = ""

    // Timeline state
    private val _clips = MutableStateFlow<List<Clip>>(emptyList())
    val clips = _clips.asStateFlow()

    // Track if any edits were made to the original clip
    private val _hasEdits = MutableStateFlow(false)
    val hasEdits = _hasEdits.asStateFlow()

    // Undo / Redo History
    private val history = mutableListOf<List<Clip>>()
    private var historyIndex = -1

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    // Thumbnails for the timeline
    private val _clipThumbnails = MutableStateFlow<Map<String, List<Bitmap>>>(emptyMap())
    val clipThumbnails = _clipThumbnails.asStateFlow()

    val showTimelineThumbnails = settingsRepository.showTimelineThumbnailsFlow.stateInDefault(viewModelScope, false)

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(clips, showTimelineThumbnails) { currentClips, showThumbs ->
                Pair(currentClips, showThumbs)
            }.collect { (currentClips, showThumbs) ->
                if (showThumbs && originalVideoPath.isNotEmpty()) {
                    val newMap = _clipThumbnails.value.toMutableMap()
                    
                    // We generate a few thumbnails per clip (e.g. 5)
                    for (clip in currentClips) {
                        if (!newMap.containsKey(clip.id)) {
                            val bitmaps = ThumbnailExtractor.extractThumbnails(
                                videoPath = originalVideoPath,
                                startMs = clip.startTrimMs,
                                endMs = clip.endTrimMs,
                                count = 5
                            )
                            newMap[clip.id] = bitmaps
                        }
                    }
                    
                    // Cleanup old clips
                    val currentIds = currentClips.map { it.id }.toSet()
                    newMap.keys.retainAll(currentIds)
                    
                    _clipThumbnails.value = newMap
                } else {
                    _clipThumbnails.value = emptyMap()
                }
            }
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _uiState.value = VideoEditorUiState.Loading
            currentProjectId = projectId
            val project = projectRepository.getProjectById(projectId)
            if (project != null) {
                var durationMs = 0L
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(project.workingVideoPath)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = durationStr?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                originalDurationMs = durationMs
                originalVideoPath = project.workingVideoPath
                
                _clips.value = listOf(Clip(startTrimMs = 0L, endTrimMs = durationMs))
                _hasEdits.value = false

                _uiState.value = VideoEditorUiState.Ready(durationMs = durationMs, originalPath = project.workingVideoPath)
            } else {
                _uiState.value = VideoEditorUiState.Error("Project or video not found")
            }
        }
    }

    private fun saveState() {
        // Remove any future history if we are branching
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(ArrayList(_clips.value)) // deep copy not strictly needed since Clip is a data class, but ArrayList clone is safe
        historyIndex++
        
        // Cap history at 50 steps
        if (history.size > 50) {
            history.removeAt(0)
            historyIndex--
        }
        
        updateUndoRedoStates()
        _hasEdits.value = true
    }

    private fun updateUndoRedoStates() {
        _canUndo.value = historyIndex >= 0
        _canRedo.value = historyIndex < history.size - 1
    }

    fun undo() {
        if (_canUndo.value) {
            if (historyIndex == history.size - 1 && history.lastOrNull() != _clips.value) {
                // If the current state isn't in history yet (e.g., first undo), save it
                saveState()
                historyIndex-- // step back from the newly saved state
            }
            historyIndex--
            _clips.value = if (historyIndex >= 0) history[historyIndex] else emptyList() // or original state?
            
            // If we undo past the first edit, restore original clip
            if (historyIndex < 0) {
                _clips.value = listOf(Clip(startTrimMs = 0L, endTrimMs = originalDurationMs))
                _hasEdits.value = false
            }
            updateUndoRedoStates()
        }
    }

    fun redo() {
        if (_canRedo.value) {
            historyIndex++
            _clips.value = history[historyIndex]
            _hasEdits.value = true
            updateUndoRedoStates()
        }
    }

    fun updateDurationFromPlayer(actualDurationMs: Long) {
        if (actualDurationMs > 0 && actualDurationMs != originalDurationMs) {
            originalDurationMs = actualDurationMs
            // Only update clips if no edits have been made yet, or simply scale them?
            // Usually, if we just loaded, we should replace the single clip.
            if (!_hasEdits.value && _clips.value.size == 1) {
                _clips.value = listOf(Clip(startTrimMs = 0L, endTrimMs = actualDurationMs))
            }
        }
    }

    fun splitClipAtAbsoluteTime(absoluteTimelineMs: Long) {
        val currentClips = _clips.value.toMutableList()
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
            // Ensure we don't split exactly at the edges (allow a small margin like 100ms)
            if (absoluteSplitMs >= clip.startTrimMs + 100 && absoluteSplitMs <= clip.endTrimMs - 100) {
                saveState()
                val clip1 = Clip(startTrimMs = clip.startTrimMs, endTrimMs = absoluteSplitMs)
                val clip2 = Clip(startTrimMs = absoluteSplitMs, endTrimMs = clip.endTrimMs)
                currentClips.removeAt(targetClipIndex)
                currentClips.add(targetClipIndex, clip2)
                currentClips.add(targetClipIndex, clip1)
                _clips.value = currentClips
            }
        }
    }

    fun deleteClip(clipId: String) {
        val currentClips = _clips.value.toMutableList()
        if (currentClips.size > 1) { // Prevent deleting the last clip
            saveState()
            currentClips.removeAll { it.id == clipId }
            _clips.value = currentClips
        }
    }

    fun duplicateClip(clipId: String) {
        val currentClips = _clips.value.toMutableList()
        val index = currentClips.indexOfFirst { it.id == clipId }
        if (index != -1) {
            saveState()
            val clipToDuplicate = currentClips[index]
            val newClip = Clip(startTrimMs = clipToDuplicate.startTrimMs, endTrimMs = clipToDuplicate.endTrimMs)
            currentClips.add(index + 1, newClip)
            _clips.value = currentClips
        }
    }

    fun moveClip(fromIndex: Int, toIndex: Int) {
        val currentClips = _clips.value.toMutableList()
        if (fromIndex in currentClips.indices && toIndex in currentClips.indices) {
            saveState()
            val clip = currentClips.removeAt(fromIndex)
            currentClips.add(toIndex, clip)
            _clips.value = currentClips
        }
    }

    @OptIn(UnstableApi::class)
    fun applyEdits() {
        val projectId = currentProjectId ?: return
        val state = _uiState.value as? VideoEditorUiState.Ready ?: return
        
        viewModelScope.launch {
            _uiState.value = VideoEditorUiState.Processing(0)
            
            try {
                // Temporary output file
                val tempOutputFile = com.dipdev.aiautocaptioner.core.utils.FileUtils.createTempVideoFile(context)
                
                val editedMediaItems = _clips.value.map { clip ->
                    val mediaItem = MediaItem.Builder()
                        .setUri(state.originalPath)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(clip.startTrimMs)
                                .setEndPositionMs(clip.endTrimMs)
                                .build()
                        )
                        .build()
                    EditedMediaItem.Builder(mediaItem).build()
                }

                val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(editedMediaItems)
                val composition = Composition.Builder(listOf(sequence)).build()
                
                transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            // On success, replace the project's working video path
                            viewModelScope.launch {
                                projectRepository.updateWorkingVideoPath(projectId, tempOutputFile.absolutePath)
                                _uiState.value = VideoEditorUiState.Success
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            _uiState.value = VideoEditorUiState.Error(exportException.message ?: "Unknown error during trim")
                        }
                    })
                    .build()
                
                transformer?.start(composition, tempOutputFile.absolutePath)
                
                // Poll progress
                viewModelScope.launch {
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    while (transformer != null && _uiState.value is VideoEditorUiState.Processing) {
                        val progressState = transformer?.getProgress(progressHolder)
                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            _uiState.value = VideoEditorUiState.Processing(progressHolder.progress)
                        } else if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) {
                            _uiState.value = VideoEditorUiState.Processing(0)
                        }
                        kotlinx.coroutines.delay(500.milliseconds)
                    }
                }

            } catch (e: Exception) {
                _uiState.value = VideoEditorUiState.Error(e.message ?: "Failed to process video")
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun cancel() {
        transformer?.cancel()
        transformer = null
        if (originalVideoPath.isNotEmpty()) {
            _uiState.value = VideoEditorUiState.Ready(originalDurationMs, originalVideoPath)
        } else {
            _uiState.value = VideoEditorUiState.Idle
        }
    }

    fun deleteProject(onDeleted: () -> Unit) {
        viewModelScope.launch {
            currentProjectId?.let { id ->
                projectRepository.deleteProject(id)
            }
            onDeleted()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCleared() {
        super.onCleared()
        transformer?.cancel()
        transformer = null
    }
}

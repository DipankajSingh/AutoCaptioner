package com.dipdev.aiautocaptioner.ui.videoeditor

import android.content.Context
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
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed class VideoEditorUiStep {
    data object Idle : VideoEditorUiStep()
    data object Loading : VideoEditorUiStep()
    data class Ready(val durationMs: Long, val originalPath: String) : VideoEditorUiStep()
    data class Processing(val progress: Int) : VideoEditorUiStep()
    data object Success : VideoEditorUiStep()
    data class Error(val message: String) : VideoEditorUiStep()
}

data class VideoEditorUiState(
    val step: VideoEditorUiStep = VideoEditorUiStep.Idle,
    val clips: List<Clip> = emptyList(),
    val hasEdits: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val clipThumbnails: Map<String, List<Bitmap>> = emptyMap(),
    val showTimelineThumbnails: Boolean = false
) : UiState

sealed class VideoEditorUiEvent : UiEvent {
    data class LoadProject(val projectId: String) : VideoEditorUiEvent()
    data object Undo : VideoEditorUiEvent()
    data object Redo : VideoEditorUiEvent()
    data class UpdateDurationFromPlayer(val actualDurationMs: Long) : VideoEditorUiEvent()
    data class SplitClipAtAbsoluteTime(val absoluteTimelineMs: Long) : VideoEditorUiEvent()
    data class DeleteClip(val clipId: String) : VideoEditorUiEvent()
    data class DuplicateClip(val clipId: String) : VideoEditorUiEvent()
    data class MoveClip(val fromIndex: Int, val toIndex: Int) : VideoEditorUiEvent()
    data object ApplyEdits : VideoEditorUiEvent()
    data object Cancel : VideoEditorUiEvent()
    data object DeleteProject : VideoEditorUiEvent()
}

sealed class VideoEditorUiEffect : UiEffect {
    data object ProjectDeleted : VideoEditorUiEffect()
}

@UnstableApi
@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    settingsRepository: SettingsRepository
) : BaseViewModel<VideoEditorUiState, VideoEditorUiEvent, VideoEditorUiEffect>(VideoEditorUiState()) {

    private var currentProjectId: String? = null
    private var transformer: Transformer? = null

    private var originalDurationMs: Long = 0L
    private var originalVideoPath: String = ""

    // Undo / Redo History
    private val history = mutableListOf<List<Clip>>()
    private var historyIndex = -1

    init {
        viewModelScope.launch {
            settingsRepository.showTimelineThumbnailsFlow.collect { showThumbs ->
                setState { copy(showTimelineThumbnails = showThumbs) }
            }
        }

        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(uiState, uiState) { state, _ -> state }
                .collect { state ->
                    val showThumbs = state.showTimelineThumbnails
                    val currentClips = state.clips

                    if (showThumbs && originalVideoPath.isNotEmpty()) {
                        val newMap = state.clipThumbnails.toMutableMap()
                        
                        // We generate a few thumbnails per clip (e.g. 5)
                        for (clip in currentClips) {
                            if (!newMap.containsKey(clip.id)) {
                                val bitmaps = ThumbnailExtractor.extractThumbnails(
                                    context = context,
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
                        
                        setState { copy(clipThumbnails = newMap) }
                    } else if (state.clipThumbnails.isNotEmpty()) {
                        setState { copy(clipThumbnails = emptyMap()) }
                    }
                }
        }
    }

    override fun handleEvent(event: VideoEditorUiEvent) {
        when (event) {
            is VideoEditorUiEvent.LoadProject -> loadProject(event.projectId)
            is VideoEditorUiEvent.Undo -> undo()
            is VideoEditorUiEvent.Redo -> redo()
            is VideoEditorUiEvent.UpdateDurationFromPlayer -> updateDurationFromPlayer(event.actualDurationMs)
            is VideoEditorUiEvent.SplitClipAtAbsoluteTime -> splitClipAtAbsoluteTime(event.absoluteTimelineMs)
            is VideoEditorUiEvent.DeleteClip -> deleteClip(event.clipId)
            is VideoEditorUiEvent.DuplicateClip -> duplicateClip(event.clipId)
            is VideoEditorUiEvent.MoveClip -> moveClip(event.fromIndex, event.toIndex)
            is VideoEditorUiEvent.ApplyEdits -> applyEdits()
            is VideoEditorUiEvent.Cancel -> cancel()
            is VideoEditorUiEvent.DeleteProject -> deleteProject()
        }
    }

    private fun loadProject(projectId: String) {
        viewModelScope.launch {
            setState { copy(step = VideoEditorUiStep.Loading) }
            currentProjectId = projectId
            val project = projectRepository.getProjectById(projectId)
            if (project != null) {
                var durationMs = 0L
                try {
                    val retriever = MediaMetadataRetriever()
                    if (project.workingVideoPath.startsWith("content://") || project.workingVideoPath.startsWith("file://")) {
                        retriever.setDataSource(context, android.net.Uri.parse(project.workingVideoPath))
                    } else {
                        retriever.setDataSource(project.workingVideoPath)
                    }
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = durationStr?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                originalDurationMs = durationMs
                originalVideoPath = project.workingVideoPath
                
                setState { 
                    copy(
                        clips = listOf(Clip(startTrimMs = 0L, endTrimMs = durationMs)),
                        hasEdits = false,
                        step = VideoEditorUiStep.Ready(durationMs = durationMs, originalPath = project.workingVideoPath)
                    )
                }
            } else {
                setState { copy(step = VideoEditorUiStep.Error("Project or video not found")) }
            }
        }
    }

    private fun saveState() {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(ArrayList(currentState.clips))
        historyIndex++
        
        if (history.size > 50) {
            history.removeAt(0)
            historyIndex--
        }
        
        updateUndoRedoStates()
        setState { copy(hasEdits = true) }
    }

    private fun updateUndoRedoStates() {
        setState { 
            copy(
                canUndo = historyIndex >= 0,
                canRedo = historyIndex < history.size - 1
            )
        }
    }

    private fun undo() {
        if (currentState.canUndo) {
            if (historyIndex == history.size - 1 && history.lastOrNull() != currentState.clips) {
                saveState()
                historyIndex--
            }
            historyIndex--
            val newClips = if (historyIndex >= 0) history[historyIndex] else emptyList()
            
            if (historyIndex < 0) {
                setState { 
                    copy(
                        clips = listOf(Clip(startTrimMs = 0L, endTrimMs = originalDurationMs)),
                        hasEdits = false
                    )
                }
            } else {
                setState { copy(clips = newClips) }
            }
            updateUndoRedoStates()
        }
    }

    private fun redo() {
        if (currentState.canRedo) {
            historyIndex++
            setState { 
                copy(
                    clips = history[historyIndex],
                    hasEdits = true
                )
            }
            updateUndoRedoStates()
        }
    }

    private fun updateDurationFromPlayer(actualDurationMs: Long) {
        if (actualDurationMs > 0 && actualDurationMs != originalDurationMs) {
            originalDurationMs = actualDurationMs
            if (!currentState.hasEdits && currentState.clips.size == 1) {
                setState { copy(clips = listOf(Clip(startTrimMs = 0L, endTrimMs = actualDurationMs))) }
            }
        }
    }

    private fun splitClipAtAbsoluteTime(absoluteTimelineMs: Long) {
        val currentClips = currentState.clips.toMutableList()
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
                saveState()
                val clip1 = Clip(startTrimMs = clip.startTrimMs, endTrimMs = absoluteSplitMs)
                val clip2 = Clip(startTrimMs = absoluteSplitMs, endTrimMs = clip.endTrimMs)
                currentClips.removeAt(targetClipIndex)
                currentClips.add(targetClipIndex, clip2)
                currentClips.add(targetClipIndex, clip1)
                setState { copy(clips = currentClips) }
            }
        }
    }

    private fun deleteClip(clipId: String) {
        val currentClips = currentState.clips.toMutableList()
        if (currentClips.size > 1) {
            saveState()
            currentClips.removeAll { it.id == clipId }
            setState { copy(clips = currentClips) }
        }
    }

    private fun duplicateClip(clipId: String) {
        val currentClips = currentState.clips.toMutableList()
        val index = currentClips.indexOfFirst { it.id == clipId }
        if (index != -1) {
            saveState()
            val clipToDuplicate = currentClips[index]
            val newClip = Clip(startTrimMs = clipToDuplicate.startTrimMs, endTrimMs = clipToDuplicate.endTrimMs)
            currentClips.add(index + 1, newClip)
            setState { copy(clips = currentClips) }
        }
    }

    private fun moveClip(fromIndex: Int, toIndex: Int) {
        val currentClips = currentState.clips.toMutableList()
        if (fromIndex in currentClips.indices && toIndex in currentClips.indices) {
            saveState()
            val clip = currentClips.removeAt(fromIndex)
            currentClips.add(toIndex, clip)
            setState { copy(clips = currentClips) }
        }
    }

    @OptIn(UnstableApi::class)
    private fun applyEdits() {
        val projectId = currentProjectId ?: return
        val step = currentState.step as? VideoEditorUiStep.Ready ?: return
        
        viewModelScope.launch {
            setState { copy(step = VideoEditorUiStep.Processing(0)) }
            
            try {
                val tempOutputFile = com.dipdev.aiautocaptioner.core.utils.FileUtils.createTempVideoFile(context)
                
                val editedMediaItems = currentState.clips.map { clip ->
                    val mediaItem = MediaItem.Builder()
                        .setUri(step.originalPath)
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
                            viewModelScope.launch {
                                projectRepository.updateWorkingVideoPath(projectId, tempOutputFile.absolutePath)
                                projectRepository.updateStatus(projectId, com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus.READY_FOR_PROCESSING)
                                setState { copy(step = VideoEditorUiStep.Success) }
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            setState { copy(step = VideoEditorUiStep.Error(exportException.message ?: "Unknown error during trim")) }
                        }
                    })
                    .build()
                
                transformer?.start(composition, tempOutputFile.absolutePath)
                
                viewModelScope.launch {
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    while (transformer != null && currentState.step is VideoEditorUiStep.Processing) {
                        val progressState = transformer?.getProgress(progressHolder)
                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            setState { copy(step = VideoEditorUiStep.Processing(progressHolder.progress)) }
                        } else if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) {
                            setState { copy(step = VideoEditorUiStep.Processing(0)) }
                        }
                        kotlinx.coroutines.delay(500.milliseconds)
                    }
                }

            } catch (e: Exception) {
                setState { copy(step = VideoEditorUiStep.Error(e.message ?: "Failed to process video")) }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun cancel() {
        transformer?.cancel()
        transformer = null
        if (originalVideoPath.isNotEmpty()) {
            setState { copy(step = VideoEditorUiStep.Ready(originalDurationMs, originalVideoPath)) }
        } else {
            setState { copy(step = VideoEditorUiStep.Idle) }
        }
    }

    private fun deleteProject() {
        viewModelScope.launch {
            currentProjectId?.let { id ->
                projectRepository.deleteProject(id)
            }
            setEffect(VideoEditorUiEffect.ProjectDeleted)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCleared() {
        super.onCleared()
        transformer?.cancel()
        transformer = null
    }
}

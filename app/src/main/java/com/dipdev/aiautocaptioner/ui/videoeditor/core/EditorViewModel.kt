package com.dipdev.aiautocaptioner.ui.videoeditor.core

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.dipdev.aiautocaptioner.core.video.ThumbnailManager
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.data.repository.OverlayRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import com.dipdev.aiautocaptioner.ui.videoeditor.core.managers.ClipManager
import com.dipdev.aiautocaptioner.ui.videoeditor.core.managers.OverlayManager
import com.dipdev.aiautocaptioner.ui.videoeditor.export.ExportService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class VideoEditorUiStep {
    data object Idle : VideoEditorUiStep()
    data object Loading : VideoEditorUiStep()
    data class Ready(val durationMs: Long, val originalPath: String) : VideoEditorUiStep()
    data class Processing(val progress: Int) : VideoEditorUiStep()
    data class Error(val message: String) : VideoEditorUiStep()
}

data class VideoEditorUiState(
    val step: VideoEditorUiStep = VideoEditorUiStep.Idle,
    val clips: List<Clip> = emptyList(),
    val hasEdits: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val originalDurationMs: Long = 0L,
    val showTimelineThumbnails: Boolean = false,
    val selectedLanguage: String = "en",
    val translateToEnglish: Boolean = false
) : UiState
sealed class VideoEditorUiEvent : UiEvent {
    data class LoadProject(val projectId: String) : VideoEditorUiEvent()
    data object Undo : VideoEditorUiEvent()
    data object Redo : VideoEditorUiEvent()
    data object SaveState : VideoEditorUiEvent()
    data class UpdateDurationFromPlayer(val actualDurationMs: Long) : VideoEditorUiEvent()
    data class SplitClipAtAbsoluteTime(val absoluteTimelineMs: Long) : VideoEditorUiEvent()
    data class TrimClip(val clipId: String, val newStartTrimMs: Long, val newEndTrimMs: Long) : VideoEditorUiEvent()
    data class DeleteClip(val clipId: String) : VideoEditorUiEvent()
    data class DuplicateClip(val clipId: String) : VideoEditorUiEvent()
    data class MoveClip(val fromIndex: Int, val toIndex: Int) : VideoEditorUiEvent()
    data class ApplyEdits(val navigateToExport: Boolean = false) : VideoEditorUiEvent()
    data object Cancel : VideoEditorUiEvent()
    data object DeleteProject : VideoEditorUiEvent()
    data class SaveLanguage(val language: String, val translateToEnglish: Boolean) : VideoEditorUiEvent()
    data class AddOverlay(val uri: String) : VideoEditorUiEvent()
    data class UpdateOverlay(val overlay: ImageOverlayEntity) : VideoEditorUiEvent()
    data class DeleteOverlay(val overlayId: String) : VideoEditorUiEvent()
    data class SelectOverlay(val overlayId: String?) : VideoEditorUiEvent()
    data class DuplicateOverlay(val overlayId: String) : VideoEditorUiEvent()
    data class MoveOverlayZ(val overlayId: String, val bringToFront: Boolean) : VideoEditorUiEvent()
}

sealed class VideoEditorUiEffect : UiEffect {
    data object ProjectDeleted : VideoEditorUiEffect()
    data object NavigateToProcessing : VideoEditorUiEffect()
    data object NavigateToExport : VideoEditorUiEffect()
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val overlayRepository: OverlayRepository,
    private val videoExporter: ExportService
) : BaseViewModel<VideoEditorUiState, VideoEditorUiEvent, VideoEditorUiEffect>(VideoEditorUiState()) {

    private var currentProjectId: String? = null
    private val projectIdFlow = MutableStateFlow<String?>(null)
    
    private val _selectedOverlayId = MutableStateFlow<String?>(null)
    val selectedOverlayId = _selectedOverlayId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val overlays: StateFlow<List<ImageOverlayEntity>> = projectIdFlow
        .flatMapLatest { id ->
            if (id != null) overlayRepository.getOverlaysForProject(id)
            else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )


    private var originalDurationMs: Long = 0L
    private var originalVideoPath: String = ""

    private val clipManager = ClipManager(
        getOriginalDurationMs = { originalDurationMs },
        getCurrentClips = { currentState.clips },
        onStateChanged = { newClips, edits, undo, redo -> 
            setState { copy(clips = newClips, hasEdits = edits, canUndo = undo, canRedo = redo) } 
        },
        onUndoToOriginal = {
            setState { copy(clips = listOf(Clip(startTrimMs = 0L, endTrimMs = originalDurationMs)), hasEdits = false) }
        }
    )
    
    val overlayManager = OverlayManager(
        context = context,
        overlayRepository = overlayRepository,
        getOverlays = { overlays.value },
        getProjectId = { currentProjectId },
        onOverlaySelected = { _selectedOverlayId.value = it },
        isSelectedOverlay = { it == _selectedOverlayId.value }
    )
    
    val thumbnailManager = ThumbnailManager(context)


    override fun onCleared() {
        super.onCleared()
        videoExporter.cancel()
        thumbnailManager.release()
    }

    init {
        viewModelScope.launch {
            settingsRepository.showTimelineThumbnailsFlow.collect { showThumbs ->
                setState { copy(showTimelineThumbnails = showThumbs) }
            }
        }
        viewModelScope.launch {
            settingsRepository.lastLanguageFlow.collect { lang ->
                setState { copy(selectedLanguage = lang) }
            }
        }
        viewModelScope.launch {
            settingsRepository.lastTranslateFlow.collect { translate ->
                setState { copy(translateToEnglish = translate) }
            }
        }

        viewModelScope.launch {
            uiState.map { Pair(it.originalDurationMs, it.showTimelineThumbnails) }
                .distinctUntilChanged()
                .collect { (durationMs, showThumbs) ->
                    if (showThumbs && originalVideoPath.isNotEmpty() && durationMs > 0) {
                        thumbnailManager.setVideoPath(originalVideoPath)
                    } else {
                        thumbnailManager.clearMemoryCache()
                    }
                }
        }
    }

    override fun handleEvent(event: VideoEditorUiEvent) {
        when (event) {
            is VideoEditorUiEvent.LoadProject -> loadProject(event.projectId)
            is VideoEditorUiEvent.Undo -> clipManager.undo()
            is VideoEditorUiEvent.Redo -> clipManager.redo()
            is VideoEditorUiEvent.SaveState -> clipManager.saveState()
            is VideoEditorUiEvent.UpdateDurationFromPlayer -> updateDurationFromPlayer(event.actualDurationMs)
            is VideoEditorUiEvent.SplitClipAtAbsoluteTime -> clipManager.splitClipAtAbsoluteTime(event.absoluteTimelineMs)
            is VideoEditorUiEvent.TrimClip -> clipManager.trimClip(event.clipId, event.newStartTrimMs, event.newEndTrimMs)
            is VideoEditorUiEvent.DeleteClip -> clipManager.deleteClip(event.clipId)
            is VideoEditorUiEvent.DuplicateClip -> clipManager.duplicateClip(event.clipId)
            is VideoEditorUiEvent.MoveClip -> clipManager.moveClip(event.fromIndex, event.toIndex)
            is VideoEditorUiEvent.ApplyEdits -> applyEdits(event.navigateToExport)
            is VideoEditorUiEvent.Cancel -> cancel()
            is VideoEditorUiEvent.DeleteProject -> deleteProject()
            is VideoEditorUiEvent.SaveLanguage -> saveLanguage(event.language, event.translateToEnglish)
            is VideoEditorUiEvent.AddOverlay -> overlayManager.addOverlay(event.uri, viewModelScope)
            is VideoEditorUiEvent.UpdateOverlay -> overlayManager.updateOverlay(event.overlay, viewModelScope)
            is VideoEditorUiEvent.DeleteOverlay -> overlayManager.deleteOverlay(event.overlayId, viewModelScope)
            is VideoEditorUiEvent.SelectOverlay -> overlayManager.selectOverlay(event.overlayId)
            is VideoEditorUiEvent.DuplicateOverlay -> overlayManager.duplicateOverlay(event.overlayId, viewModelScope)
            is VideoEditorUiEvent.MoveOverlayZ -> overlayManager.moveOverlayZ(event.overlayId, event.bringToFront, viewModelScope)
        }
    }

    private fun loadProject(projectId: String) {
        if (currentProjectId == projectId && currentState.step !is VideoEditorUiStep.Error) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            setState { copy(step = VideoEditorUiStep.Loading) }
            currentProjectId = projectId
            projectIdFlow.value = projectId
            val project = projectRepository.getProjectById(projectId)
            if (project != null) {
                var workingPath = project.workingVideoPath
                if (!workingPath.startsWith("content://")) {
                    val videoFile = java.io.File(workingPath)
                    if (!videoFile.exists() && java.io.File(project.originalVideoUri).exists()) {
                        workingPath = project.originalVideoUri
                        projectRepository.updateWorkingVideoPath(projectId, workingPath)
                    }
                }
                var durationMs = 0L
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    if (workingPath.startsWith("content://") || workingPath.startsWith("file://")) {
                        retriever.setDataSource(context, workingPath.toUri())
                    } else {
                        retriever.setDataSource(workingPath)
                    }
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = durationStr?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    retriever?.release()
                }
                originalDurationMs = durationMs
                originalVideoPath = workingPath
                clipManager.reset()
                
                setState { 
                    copy(
                        clips = listOf(Clip(startTrimMs = 0L, endTrimMs = durationMs)),
                        hasEdits = false,
                        canUndo = false,
                        canRedo = false,
                        originalDurationMs = durationMs,
                        step = VideoEditorUiStep.Ready(durationMs = durationMs, originalPath = workingPath)
                    )
                }
            } else {
                setState { copy(step = VideoEditorUiStep.Error("Project or video not found")) }
            }
        }
    }

    private fun updateDurationFromPlayer(actualDurationMs: Long) {
        if (actualDurationMs > 0 && actualDurationMs != originalDurationMs) {
            originalDurationMs = actualDurationMs
            setState { copy(originalDurationMs = actualDurationMs) }
            if (!currentState.hasEdits && currentState.clips.size == 1) {
                setState { copy(clips = listOf(Clip(startTrimMs = 0L, endTrimMs = actualDurationMs))) }
            }
        }
    }

    private fun applyEdits(navigateToExport: Boolean) {
        val projectId = currentProjectId ?: return
        val step = currentState.step as? VideoEditorUiStep.Ready ?: return
        
        val targetEffect = if (navigateToExport) VideoEditorUiEffect.NavigateToExport else VideoEditorUiEffect.NavigateToProcessing

        if (!currentState.hasEdits) {
            // Bypass export since no edits were made
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                projectRepository.updateStatus(projectId, com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus.READY_FOR_PROCESSING)
                setEffect(targetEffect)
            }
            return
        }

        setState { copy(step = VideoEditorUiStep.Processing(0)) }
        
        videoExporter.startExport(
            scope = viewModelScope,
            originalPath = step.originalPath,
            clips = currentState.clips,
            onProgress = { progress ->
                setState { copy(step = VideoEditorUiStep.Processing(progress)) }
            },
            onSuccess = { tempFile ->
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val projectDir = java.io.File(context.filesDir, "projects/$projectId")
                    if (!projectDir.exists()) projectDir.mkdirs()
                    val permanentFile = java.io.File(projectDir, "edited_video_${System.currentTimeMillis()}.mp4")
                    tempFile.copyTo(permanentFile, overwrite = true)
                    tempFile.delete()
                    
                    projectRepository.updateWorkingVideoPath(projectId, permanentFile.absolutePath)
                    projectRepository.updateStatus(projectId, com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus.READY_FOR_PROCESSING)
                    setState { copy(step = step) }
                    setEffect(targetEffect)
                }
            },
            onError = { error ->
                setState { copy(step = VideoEditorUiStep.Error(error)) }
            }
        )
    }

    private fun cancel() {
        videoExporter.cancel()
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

    private fun saveLanguage(language: String, translateToEnglish: Boolean) {
        setState { copy(selectedLanguage = language, translateToEnglish = translateToEnglish) }
        viewModelScope.launch {
            settingsRepository.saveLastLanguageSettings(language, translateToEnglish)
        }
    }

    @OptIn(UnstableApi::class)
    fun cleanup() {
        videoExporter.cancel()
    }
}

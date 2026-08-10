package com.dipdev.aiautocaptioner.ui.videoeditor.core

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.dipdev.aiautocaptioner.core.video.ThumbnailManager
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.data.repository.OverlayRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.videoeditor.core.managers.EditorSnapshot
import com.dipdev.aiautocaptioner.ui.videoeditor.core.managers.HistoryManager
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

sealed class VideoEditorUiStep {
    data object Idle : VideoEditorUiStep()
    data object Loading : VideoEditorUiStep()
    data class Ready(val durationMs: Long, val originalPath: String) : VideoEditorUiStep()
    data class Processing(val progress: Int) : VideoEditorUiStep()
    data class Error(val message: String) : VideoEditorUiStep()
}

data class VideoEditorUiState(
    val step: VideoEditorUiStep = VideoEditorUiStep.Idle,
    val clips: ImmutableList<Clip> = persistentListOf(),
    val hasEdits: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val originalDurationMs: Long = 0L,
    val showTimelineThumbnails: Boolean = false,
    val selectedLanguage: String = "en",
    val translateToEnglish: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val isAddingText: Boolean = false,
    val editingTextOverlayId: String? = null,
    val imageOverlays: ImmutableList<ImageOverlayEntity> = persistentListOf(),
    val textOverlays: ImmutableList<TextOverlayEntity> = persistentListOf()
) : UiState
sealed class VideoEditorUiEvent : UiEvent {
    data class LoadProject(val projectId: String) : VideoEditorUiEvent()
    data object Undo : VideoEditorUiEvent()
    data object Redo : VideoEditorUiEvent()
    data object SaveState : VideoEditorUiEvent()
    data class UpdateDurationFromPlayer(val actualDurationMs: Long) : VideoEditorUiEvent()
    data class SplitClipAtAbsoluteTime(val absoluteTimelineMs: Long) : VideoEditorUiEvent()
    data class TrimClip(val clipId: String, val newStartTrimMs: Long, val newEndTrimMs: Long, val saveToHistory: Boolean = true) : VideoEditorUiEvent()
    data class DeleteClip(val clipId: String) : VideoEditorUiEvent()
    data class DuplicateClip(val clipId: String) : VideoEditorUiEvent()
    data class MoveClip(val fromIndex: Int, val toIndex: Int, val saveToHistory: Boolean = true) : VideoEditorUiEvent()
    data class ApplyEdits(val navigateToExport: Boolean = false) : VideoEditorUiEvent()
    data object Cancel : VideoEditorUiEvent()
    data object DeleteProject : VideoEditorUiEvent()
    data class SaveLanguage(val language: String, val translateToEnglish: Boolean) : VideoEditorUiEvent()
    data class AddOverlay(val uri: String, val currentPlayheadMs: Long) : VideoEditorUiEvent()
    data class UpdateOverlay(val overlay: ImageOverlayEntity) : VideoEditorUiEvent()
    data class DeleteOverlay(val overlayId: String) : VideoEditorUiEvent()
    data class SelectOverlay(val overlayId: String?) : VideoEditorUiEvent()
    data class DuplicateOverlay(val overlayId: String) : VideoEditorUiEvent()
    data class MoveOverlayZ(val overlayId: String, val bringToFront: Boolean) : VideoEditorUiEvent()
    
    // Text Overlay Events
    data class AddTextOverlay(val text: String, val currentPlayheadMs: Long) : VideoEditorUiEvent()
    data class UpdateTextOverlay(val overlay: TextOverlayEntity) : VideoEditorUiEvent()
    data class DeleteTextOverlay(val overlayId: String) : VideoEditorUiEvent()
    data class SelectTextOverlay(val overlayId: String?) : VideoEditorUiEvent()
    data class DuplicateTextOverlay(val overlayId: String) : VideoEditorUiEvent()

    data object StartAddingText : VideoEditorUiEvent()
    data object StartEditingText : VideoEditorUiEvent()
    data object CancelAddingText : VideoEditorUiEvent()
}

sealed class VideoEditorUiEffect : UiEffect {
    data object ProjectDeleted : VideoEditorUiEffect()
    data object NavigateToProcessing : VideoEditorUiEffect()
    data object NavigateToExport : VideoEditorUiEffect()
}

@HiltViewModel
@androidx.annotation.OptIn(UnstableApi::class)
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


    private var originalDurationMs: Long = 0L
    private var originalVideoPath: String = ""

    // Conflated channel that serializes overlay DB restores for undo/redo so rapid
    // undo/redo can't interleave delete+insert writes out of order.
    private val restoreChannel = Channel<EditorSnapshot>(Channel.CONFLATED)

    private val historyManager = HistoryManager(
        getOriginalDurationMs = { originalDurationMs },
        getCurrentClips = { currentState.clips },
        getCurrentImageOverlays = { currentState.imageOverlays },
        getCurrentTextOverlays = { currentState.textOverlays },
        onStateChanged = { newClips, newImg, newTxt, edits, undo, redo -> 
            setState { 
                copy(
                    clips = newClips.toPersistentList(), 
                    imageOverlays = newImg.toPersistentList(),
                    textOverlays = newTxt.toPersistentList(),
                    hasEdits = edits, 
                    canUndo = undo, 
                    canRedo = redo
                ) 
            } 
        },
        onRestoreSnapshot = { snapshot ->
            restoreChannel.trySend(snapshot)
        }
    )
    
    val overlayManager = OverlayManager(
        context = context,
        overlayRepository = overlayRepository,
        getOverlays = { currentState.imageOverlays },
        setOverlays = { list -> setState { copy(imageOverlays = list.toPersistentList()) } },
        getProjectId = { currentProjectId },
        onOverlaySelected = { _selectedOverlayId.value = it },
        isSelectedOverlay = { it == _selectedOverlayId.value },
        getTextOverlays = { currentState.textOverlays },
        setTextOverlays = { list -> setState { copy(textOverlays = list.toPersistentList()) } },
        onStateUpdated = { historyManager.saveState() }
    )
    
    val thumbnailManager = ThumbnailManager(context)


    override fun onCleared() {
        videoExporter.cancel()
        thumbnailManager.release()
    }

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (snapshot in restoreChannel) {
                currentProjectId?.let { id ->
                    overlayRepository.restoreOverlays(id, snapshot.imageOverlays, snapshot.textOverlays)
                }
            }
        }

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
            is VideoEditorUiEvent.Undo -> historyManager.undo()
            is VideoEditorUiEvent.Redo -> historyManager.redo()
            is VideoEditorUiEvent.SaveState -> historyManager.saveState()
            is VideoEditorUiEvent.UpdateDurationFromPlayer -> updateDurationFromPlayer(event.actualDurationMs)
            is VideoEditorUiEvent.SplitClipAtAbsoluteTime -> historyManager.splitClipAtAbsoluteTime(event.absoluteTimelineMs)
            is VideoEditorUiEvent.TrimClip -> historyManager.trimClip(event.clipId, event.newStartTrimMs, event.newEndTrimMs, event.saveToHistory)
            is VideoEditorUiEvent.DeleteClip -> historyManager.deleteClip(event.clipId)
            is VideoEditorUiEvent.DuplicateClip -> historyManager.duplicateClip(event.clipId)
            is VideoEditorUiEvent.MoveClip -> historyManager.moveClip(event.fromIndex, event.toIndex, event.saveToHistory)
            is VideoEditorUiEvent.ApplyEdits -> applyEdits(event.navigateToExport)
            is VideoEditorUiEvent.Cancel -> cancel()
            is VideoEditorUiEvent.DeleteProject -> deleteProject()
            is VideoEditorUiEvent.SaveLanguage -> saveLanguage(event.language, event.translateToEnglish)
            is VideoEditorUiEvent.AddOverlay -> {
                historyManager.saveState()
                overlayManager.addOverlay(event.uri, event.currentPlayheadMs, viewModelScope)
            }
            is VideoEditorUiEvent.UpdateOverlay -> {
                overlayManager.updateOverlay(event.overlay, viewModelScope)
            }
            is VideoEditorUiEvent.DeleteOverlay -> {
                overlayManager.deleteOverlay(event.overlayId, viewModelScope)
            }
            is VideoEditorUiEvent.SelectOverlay -> overlayManager.selectOverlay(event.overlayId)
            is VideoEditorUiEvent.DuplicateOverlay -> {
                overlayManager.duplicateOverlay(event.overlayId, viewModelScope)
            }
            is VideoEditorUiEvent.MoveOverlayZ -> {
                overlayManager.moveOverlayZ(event.overlayId, event.bringToFront, viewModelScope)
            }
            
            // Text Overlays
            is VideoEditorUiEvent.AddTextOverlay -> {
                overlayManager.addTextOverlay(
                    text = event.text,
                    fontAssetPath = "fonts/inter.ttf",
                    textColorArgb = android.graphics.Color.WHITE,
                    backgroundColorArgb = android.graphics.Color.TRANSPARENT,
                    backgroundOpacity = 0f,
                    textAlignment = "CENTER",
                    fontSize = 48f,
                    currentPlayheadMs = event.currentPlayheadMs,
                    scope = viewModelScope
                )
                setState { copy(isAddingText = false, editingTextOverlayId = null) }
            }
            is VideoEditorUiEvent.UpdateTextOverlay -> {
                overlayManager.updateTextOverlay(event.overlay, viewModelScope)
            }
            is VideoEditorUiEvent.DeleteTextOverlay -> {
                overlayManager.deleteTextOverlay(event.overlayId, viewModelScope)
            }
            is VideoEditorUiEvent.SelectTextOverlay -> overlayManager.selectTextOverlay(event.overlayId)
            is VideoEditorUiEvent.DuplicateTextOverlay -> {
                overlayManager.duplicateTextOverlay(event.overlayId, viewModelScope)
            }

            is VideoEditorUiEvent.StartAddingText -> setState { copy(isAddingText = true, editingTextOverlayId = null) }
            is VideoEditorUiEvent.StartEditingText -> setState { copy(isAddingText = false, editingTextOverlayId = _selectedOverlayId.value) }
            is VideoEditorUiEvent.CancelAddingText -> setState { copy(isAddingText = false, editingTextOverlayId = null) }
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
                    android.util.Log.e("EditorViewModel", "Error reading metadata", e)
                } finally {
                    retriever?.release()
                }
                originalDurationMs = durationMs
                originalVideoPath = workingPath
                
                val dbImageOverlays = overlayRepository.getOverlaysOnce(projectId)
                val dbTextOverlays = overlayRepository.getTextOverlaysForProjectSync(projectId)
                
                historyManager.setBaseline(
                    clips = listOf(Clip(startTrimMs = 0L, endTrimMs = durationMs)),
                    imageOverlays = dbImageOverlays,
                    textOverlays = dbTextOverlays
                )
                
                setState { 
                    copy(
                        clips = persistentListOf(Clip(startTrimMs = 0L, endTrimMs = durationMs)),
                        imageOverlays = dbImageOverlays.toPersistentList(),
                        textOverlays = dbTextOverlays.toPersistentList(),
                        hasEdits = false,
                        canUndo = false,
                        canRedo = false,
                        originalDurationMs = durationMs,
                        videoWidth = if (project.videoRotation == 90 || project.videoRotation == 270) project.videoHeight else project.videoWidth,
                        videoHeight = if (project.videoRotation == 90 || project.videoRotation == 270) project.videoWidth else project.videoHeight,
                        step = VideoEditorUiStep.Ready(durationMs = durationMs, originalPath = workingPath)
                    )
                }
            } else {
                setState { copy(step = VideoEditorUiStep.Error(context.getString(R.string.editor_not_found))) }
            }
        }
    }

    private fun updateDurationFromPlayer(actualDurationMs: Long) {
        if (actualDurationMs > 0 && actualDurationMs != originalDurationMs) {
            originalDurationMs = actualDurationMs
            setState { copy(originalDurationMs = actualDurationMs) }
            if (!currentState.hasEdits && currentState.clips.size == 1) {
                setState { copy(clips = persistentListOf(Clip(startTrimMs = 0L, endTrimMs = actualDurationMs))) }
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

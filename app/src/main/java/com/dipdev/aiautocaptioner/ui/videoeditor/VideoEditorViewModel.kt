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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.media3.common.Player
import java.util.UUID
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.repository.OverlayRepository
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
    val showTimelineThumbnails: Boolean = false,
    val selectedLanguage: String = "en",
    val translateToEnglish: Boolean = false
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
    data class SaveLanguage(val language: String, val translateToEnglish: Boolean) : VideoEditorUiEvent()
    data class AddOverlay(val uri: String) : VideoEditorUiEvent()
    data class UpdateOverlay(val overlay: ImageOverlayEntity) : VideoEditorUiEvent()
    data class DeleteOverlay(val overlayId: String) : VideoEditorUiEvent()
    data class SelectOverlay(val overlayId: String?) : VideoEditorUiEvent()
    data class MoveOverlayZ(val overlayId: String, val bringToFront: Boolean) : VideoEditorUiEvent()
}

sealed class VideoEditorUiEffect : UiEffect {
    data object ProjectDeleted : VideoEditorUiEffect()
}

@UnstableApi
@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val overlayRepository: OverlayRepository
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

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentTimelineMs = MutableStateFlow(0L)
    val currentTimelineMs = _currentTimelineMs.asStateFlow()

    private var progressJob: Job? = null
    private var boundPlayer: Player? = null

    fun bindPlayer(player: Player) {
        if (boundPlayer == player) return
        boundPlayer?.removeListener(playerListener)
        boundPlayer = player
        player.addListener(playerListener)
        _isPlaying.value = player.isPlaying
        if (player.isPlaying) {
            startProgressSync()
        } else {
            updateProgress()
        }
    }

    override fun onCleared() {
        super.onCleared()
        boundPlayer?.removeListener(playerListener)
        boundPlayer = null
        stopProgressSync()
        transformer?.cancel()
        transformer = null
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressSync()
            } else {
                stopProgressSync()
                updateProgress()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updateProgress()
        }
    }

    private fun startProgressSync() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                updateProgress()
                delay(16L) // TimeBar logic runs only when playing
            }
        }
    }

    private fun stopProgressSync() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateProgress() {
        val player = boundPlayer ?: return
        val windowIndex = player.currentMediaItemIndex
        val posInWindow = player.currentPosition
        
        var accumulated = 0L
        val clips = currentState.clips
        
        // Compute merged clips dynamically to calculate absolute timeline ms
        val mergedClips = mutableListOf<Clip>()
        var current: Clip? = null
        for (c in clips) {
            if (current == null) {
                current = c
            } else {
                if (current.endTrimMs == c.startTrimMs) {
                    current = current.copy(endTrimMs = c.endTrimMs)
                } else {
                    mergedClips.add(current)
                    current = c
                }
            }
        }
        if (current != null) mergedClips.add(current)
        
        for (i in 0 until windowIndex.coerceAtMost(mergedClips.size)) {
            accumulated += (mergedClips[i].endTrimMs - mergedClips[i].startTrimMs)
        }
        
        _currentTimelineMs.value = accumulated + posInWindow
    }

    private var transformer: Transformer? = null
    private var tempOutputFile: java.io.File? = null

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
            uiState.map { Pair(it.clips, it.showTimelineThumbnails) }
                .distinctUntilChanged()
                .collect { (currentClips, showThumbs) ->
                    if (showThumbs && originalVideoPath.isNotEmpty()) {
                        val newMap = currentState.clipThumbnails.toMutableMap()
                        
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
                        val removedKeys = newMap.keys - currentIds
                        removedKeys.forEach { key ->
                            newMap.remove(key)?.forEach { it.recycle() }
                        }
                        
                        setState { copy(clipThumbnails = newMap) }
                    } else if (currentState.clipThumbnails.isNotEmpty()) {
                        currentState.clipThumbnails.values.flatten().forEach { it.recycle() }
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
            is VideoEditorUiEvent.SaveLanguage -> saveLanguage(event.language, event.translateToEnglish)
            is VideoEditorUiEvent.AddOverlay -> addOverlay(event.uri)
            is VideoEditorUiEvent.UpdateOverlay -> updateOverlay(event.overlay)
            is VideoEditorUiEvent.DeleteOverlay -> deleteOverlay(event.overlayId)
            is VideoEditorUiEvent.SelectOverlay -> selectOverlay(event.overlayId)
            is VideoEditorUiEvent.MoveOverlayZ -> moveOverlayZ(event.overlayId, event.bringToFront)
        }
    }

    private fun loadProject(projectId: String) {
        viewModelScope.launch {
            setState { copy(step = VideoEditorUiStep.Loading) }
            currentProjectId = projectId
            projectIdFlow.value = projectId
            val project = projectRepository.getProjectById(projectId)
            if (project != null) {
                var durationMs = 0L
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    if (project.workingVideoPath.startsWith("content://") || project.workingVideoPath.startsWith("file://")) {
                        retriever.setDataSource(context, android.net.Uri.parse(project.workingVideoPath))
                    } else {
                        retriever.setDataSource(project.workingVideoPath)
                    }
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = durationStr?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    retriever?.release()
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
                tempOutputFile = com.dipdev.aiautocaptioner.core.utils.FileUtils.createTempVideoFile(context)
                val tempFile = tempOutputFile ?: return@launch
                
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
                                projectRepository.updateWorkingVideoPath(projectId, tempFile.absolutePath)
                                projectRepository.updateStatus(projectId, com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus.READY_FOR_PROCESSING)
                                setState { copy(step = VideoEditorUiStep.Success) }
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            tempFile.delete()
                            tempOutputFile = null
                            setState { copy(step = VideoEditorUiStep.Error(exportException.message ?: "Unknown error during trim")) }
                        }
                    })
                    .build()
                
                transformer?.start(composition, tempFile.absolutePath)
                
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
        tempOutputFile?.delete()
        tempOutputFile = null
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

    private fun addOverlay(uri: String) {
        val projectId = currentProjectId ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Copy to internal storage
                val overlayDir = java.io.File(context.filesDir, "projects/$projectId/overlays")
                if (!overlayDir.exists()) overlayDir.mkdirs()
                
                val destFile = java.io.File(overlayDir, "${UUID.randomUUID()}.jpg")
                val inputStream = context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                val outputStream = java.io.FileOutputStream(destFile)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                val maxZ = overlays.value.maxOfOrNull { it.zOrder } ?: -1
                val overlay = ImageOverlayEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    imageUri = destFile.absolutePath,
                    zOrder = maxZ + 1,
                    createdAt = System.currentTimeMillis()
                )
                overlayRepository.addOverlay(overlay)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateOverlay(overlay: ImageOverlayEntity) {
        viewModelScope.launch {
            overlayRepository.updateOverlay(overlay)
        }
    }

    private fun deleteOverlay(overlayId: String) {
        val overlay = overlays.value.find { it.id == overlayId }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            overlayRepository.deleteOverlay(overlayId)
            
            // Delete the file from storage
            if (overlay != null && !overlay.imageUri.startsWith("content://")) {
                try {
                    val file = java.io.File(overlay.imageUri)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (_selectedOverlayId.value == overlayId) {
                _selectedOverlayId.value = null
            }
        }
    }

    private fun selectOverlay(overlayId: String?) {
        _selectedOverlayId.value = overlayId
    }

    private fun moveOverlayZ(overlayId: String, bringToFront: Boolean) {
        viewModelScope.launch {
            val currentList = overlays.value.sortedBy { it.zOrder }
            val index = currentList.indexOfFirst { it.id == overlayId }
            if (index == -1) return@launch
            
            val overlay = currentList[index]
            if (bringToFront && index < currentList.size - 1) {
                // Swap with the next one
                val next = currentList[index + 1]
                val currentZ = overlay.zOrder
                overlayRepository.updateOverlay(overlay.copy(zOrder = next.zOrder))
                overlayRepository.updateOverlay(next.copy(zOrder = currentZ))
            } else if (!bringToFront && index > 0) {
                // Swap with the previous one
                val prev = currentList[index - 1]
                val currentZ = overlay.zOrder
                overlayRepository.updateOverlay(overlay.copy(zOrder = prev.zOrder))
                overlayRepository.updateOverlay(prev.copy(zOrder = currentZ))
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun cleanup() {
        transformer?.cancel()
        transformer = null
    }
}

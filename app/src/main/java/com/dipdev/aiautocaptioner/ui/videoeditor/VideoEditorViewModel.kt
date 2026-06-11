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
    private val settingsRepository: SettingsRepository
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

    fun splitClip(clipId: String, splitTimeMs: Long) {
        val currentClips = _clips.value.toMutableList()
        val index = currentClips.indexOfFirst { it.id == clipId }
        if (index != -1) {
            val clip = currentClips[index]
            // splitTimeMs is absolute to the video file
            if (splitTimeMs > clip.startTrimMs && splitTimeMs < clip.endTrimMs) {
                val clip1 = Clip(startTrimMs = clip.startTrimMs, endTrimMs = splitTimeMs)
                val clip2 = Clip(startTrimMs = splitTimeMs, endTrimMs = clip.endTrimMs)
                currentClips.removeAt(index)
                currentClips.add(index, clip2)
                currentClips.add(index, clip1)
                _clips.value = currentClips
                _hasEdits.value = true
            }
        }
    }

    fun deleteClip(clipId: String) {
        val currentClips = _clips.value.toMutableList()
        if (currentClips.size > 1) { // Prevent deleting the last clip
            currentClips.removeAll { it.id == clipId }
            _clips.value = currentClips
            _hasEdits.value = true
        }
    }

    fun duplicateClip(clipId: String) {
        val currentClips = _clips.value.toMutableList()
        val index = currentClips.indexOfFirst { it.id == clipId }
        if (index != -1) {
            val clipToDuplicate = currentClips[index]
            val newClip = Clip(startTrimMs = clipToDuplicate.startTrimMs, endTrimMs = clipToDuplicate.endTrimMs)
            currentClips.add(index + 1, newClip)
            _clips.value = currentClips
            _hasEdits.value = true
        }
    }

    fun moveClip(fromIndex: Int, toIndex: Int) {
        val currentClips = _clips.value.toMutableList()
        if (fromIndex in currentClips.indices && toIndex in currentClips.indices) {
            val clip = currentClips.removeAt(fromIndex)
            currentClips.add(toIndex, clip)
            _clips.value = currentClips
            _hasEdits.value = true
        }
    }

    fun updateTrim(clipId: String, startMs: Long, endMs: Long) {
        val currentClips = _clips.value.toMutableList()
        val index = currentClips.indexOfFirst { it.id == clipId }
        if (index != -1) {
            currentClips[index] = currentClips[index].copy(startTrimMs = startMs, endTrimMs = endMs)
            _clips.value = currentClips
            _hasEdits.value = true
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
                        kotlinx.coroutines.delay(500)
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

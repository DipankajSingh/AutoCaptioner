package com.dipdev.aiautocaptioner.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.core.utils.MediaManager
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ExportState {
    data object Idle             : ExportState()
    data object Ready            : ExportState()
    data object AlreadyExported  : ExportState()
    data object Running          : ExportState()
    data object Success          : ExportState()
    data object Cancelled        : ExportState()
    data object SavedToGallery   : ExportState()
    data class  Error(val message: String) : ExportState()
}

data class ExportUiState(
    val exportState: ExportState = ExportState.Idle,
    val progress: Float = 0f,
    val etaMs: Long? = null,
    val outputPath: String? = null,
    val workingVideoPath: String? = null,
    val hasCaptions: Boolean = true,
    val savedResolution: Int = -1,
    val savedFps: Int = -1,
    val savedQuality: Int = 1,
    val originalWidth: Int = 1080,
    val originalHeight: Int = 1920,
    val originalBitrate: Int = 5_000_000,
    val originalDurationMs: Long = 0L,
    val originalFps: Int = 30
) : UiState

sealed class ExportUiEvent : UiEvent {
    data class PrepareExport(val projectId: String) : ExportUiEvent()
    object CancelExport : ExportUiEvent()
    object ResetForReExport : ExportUiEvent()
    data class StartExport(
        val projectId: String,
        val targetBitrate: Int? = null,
        val targetFps: Int? = null,
        val targetHeight: Int? = null
    ) : ExportUiEvent()
    data class SaveToGallery(val filePath: String) : ExportUiEvent()
}

class ExportUiEffect : UiEffect

@UnstableApi
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository,
    private val crashReporter: CrashReporter,
    private val settingsRepository: SettingsRepository,
    private val mediaManager: MediaManager
) : BaseViewModel<ExportUiState, ExportUiEvent, ExportUiEffect>(ExportUiState()) {

    init {
        viewModelScope.launch {
            settingsRepository.exportResolutionFlow.collect { res ->
                setState { copy(savedResolution = res) }
            }
        }
        viewModelScope.launch {
            settingsRepository.exportFpsFlow.collect { fps ->
                setState { copy(savedFps = fps) }
            }
        }
        viewModelScope.launch {
            settingsRepository.exportQualityFlow.collect { qual ->
                setState { copy(savedQuality = qual) }
            }
        }
        
        // Observe the foreground service state
        viewModelScope.launch {
            ExportServiceManager.exportState.collect { state ->
                setState { copy(exportState = state) }
            }
        }
        viewModelScope.launch {
            ExportServiceManager.progress.collect { prog ->
                setState { copy(progress = prog) }
            }
        }
        viewModelScope.launch {
            ExportServiceManager.etaMs.collect { eta ->
                setState { copy(etaMs = eta) }
            }
        }
        viewModelScope.launch {
            ExportServiceManager.outputPath.collect { path ->
                if (path != null) {
                    setState { copy(outputPath = path) }
                }
            }
        }
    }

    override fun handleEvent(event: ExportUiEvent) {
        when (event) {
            is ExportUiEvent.PrepareExport -> prepareExport(event.projectId)
            is ExportUiEvent.CancelExport -> cancelExport()
            is ExportUiEvent.ResetForReExport -> resetForReExport()
            is ExportUiEvent.StartExport -> startExport(
                projectId = event.projectId, 
                targetBitrate = event.targetBitrate,
                targetFps = event.targetFps,
                targetHeight = event.targetHeight
            )
            is ExportUiEvent.SaveToGallery -> saveToGallery(event.filePath)
        }
    }

    private fun prepareExport(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val videoPath = project?.workingVideoPath
            
            setState { copy(workingVideoPath = videoPath) }
            if (videoPath != null) {
                extractMetadata(videoPath)
            }
            
            // Check if captions exist by looking at actual segments in the DB
            try {
                val segments = captionRepository.getSegmentsOnce(projectId)
                setState { copy(hasCaptions = segments.isNotEmpty()) }
            } catch (_: Exception) {
                setState { copy(hasCaptions = false) }
            }
            
            if (currentState.exportState != ExportState.Running) {
                ExportServiceManager.reset()
                setState { copy(exportState = ExportState.Ready) }
            }
        }
    }

    private fun cancelExport() {
        val intent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_CANCEL
        }
        context.startService(intent)
    }

    private fun resetForReExport() {
        ExportServiceManager.reset()
        setState { copy(exportState = ExportState.Ready) }
    }

    private fun startExport(
        projectId: String,
        targetBitrate: Int? = null,
        targetFps: Int? = null,
        targetHeight: Int? = null
    ) {
        if (currentState.exportState == ExportState.Running) return
        
        try {
            val intent = Intent(context, ExportForegroundService::class.java).apply {
                putExtra(ExportForegroundService.EXTRA_PROJECT_ID, projectId)
                if (targetBitrate != null) putExtra(ExportForegroundService.EXTRA_TARGET_BITRATE, targetBitrate)
                if (targetFps != null) putExtra(ExportForegroundService.EXTRA_TARGET_FPS, targetFps)
                if (targetHeight != null) putExtra(ExportForegroundService.EXTRA_TARGET_HEIGHT, targetHeight)
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            crashReporter.recordException(e)
            ExportServiceManager.exportState.value = ExportState.Error(e.message ?: context.getString(R.string.export_start_failed))
        }
    }

    private fun saveToGallery(filePath: String) {
        viewModelScope.launch {
            try {
                mediaManager.saveVideoToGallery(filePath)
                setState { copy(exportState = ExportState.SavedToGallery) }
            } catch (e: Exception) {
                crashReporter.recordException(e)
                setState { copy(exportState = ExportState.Error(context.getString(R.string.export_save_failed) + ": ${e.message}")) }
            }
        }
    }

    fun saveSettings(resolution: Int, fps: Int, quality: Int) {
        viewModelScope.launch {
            settingsRepository.saveExportSettings(resolution, fps, quality)
        }
    }

    fun shareVideo(path: String) {
        mediaManager.shareVideo(path)
    }

    private suspend fun extractMetadata(videoPath: String) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(videoPath))
                val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
                val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
                val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val (originalWidth, originalHeight) = if (rotation == 90 || rotation == 270) Pair(h, w) else Pair(w, h)
                
                val originalBitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 5_000_000
                val originalDurationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val originalFps = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toIntOrNull() ?: 30
                
                setState { 
                    copy(
                        originalWidth = originalWidth,
                        originalHeight = originalHeight,
                        originalBitrate = originalBitrate,
                        originalDurationMs = originalDurationMs,
                        originalFps = originalFps
                    )
                }
            } catch (e: Throwable) { 
                crashReporter.recordException(e)
            } finally { 
                try { retriever.release() } catch (_: Throwable) {} 
            }
        }
    }
}

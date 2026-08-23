package com.dipdev.aiautocaptioner.ui.export

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.core.utils.MediaManager
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// State / Event / Effect
// ─────────────────────────────────────────────────────────────────────────────

sealed class ExportState {
    data object Idle            : ExportState()
    data object Ready           : ExportState()
    data object AlreadyExported : ExportState()
    data object Running         : ExportState()
    data object Success         : ExportState()
    data object Cancelled       : ExportState()
    data object SavedToGallery  : ExportState()
    data class  Error(val message: String) : ExportState()
}

data class ExportUiState(
    val exportState: ExportState = ExportState.Idle,
    val progress: Float          = 0f,
    val etaMs: Long?             = null,
    val outputPath: String?      = null,
    val workingVideoPath: String? = null,
    val hasCaptions: Boolean     = true,
    // Persisted export settings
    val savedResolution: Int = -1,
    val savedFps: Int        = -1,
    val savedQuality: Int    = 1,
    // Source video metadata — used for size estimation & UI display
    val originalWidth: Int       = 1080,
    val originalHeight: Int      = 1920,
    val originalBitrate: Int     = 5_000_000,
    val originalDurationMs: Long = 0L,
    val originalFps: Int         = 30
) : UiState

sealed class ExportUiEvent : UiEvent {
    data class PrepareExport(val projectId: String) : ExportUiEvent()
    data object CancelExport : ExportUiEvent()
    data object ResetForReExport : ExportUiEvent()
    data class StartExport(
        val projectId: String,
        val targetBitrate: Int? = null,
        val targetFps: Int?     = null,
        val targetHeight: Int?  = null
    ) : ExportUiEvent()
    data class SaveToGallery(val filePath: String) : ExportUiEvent()
}

class ExportUiEffect : UiEffect

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

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
        observeServiceState()
        observeSettings()
    }

    // ── Service state → UI state ──────────────────────────────────────────────

    /**
     * Collects all four ExportServiceManager flows concurrently using [combine]
     * so a single [setState] call handles any update — avoids racing between
     * separate collectors all calling setState independently.
     */
    private fun observeServiceState() {
        viewModelScope.launch {
            combine(
                ExportServiceManager.exportState,
                ExportServiceManager.progress,
                ExportServiceManager.etaMs,
                ExportServiceManager.outputPath
            ) { state, prog, eta, path -> Quad(state, prog, eta, path) }
                .collect { (state, prog, eta, path) ->
                    setState {
                        copy(
                            exportState = state,
                            progress    = prog,
                            etaMs       = eta,
                            outputPath  = path ?: outputPath   // don't null-out a valid path
                        )
                    }
                }
        }
    }

    /**
     * Collects all three settings flows together — avoids three separate
     * collectors each triggering a full recomposition cycle.
     */
    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.exportResolutionFlow,
                settingsRepository.exportFpsFlow,
                settingsRepository.exportQualityFlow
            ) { res, fps, qual -> Triple(res, fps, qual) }
                .collect { (res, fps, qual) ->
                    setState { copy(savedResolution = res, savedFps = fps, savedQuality = qual) }
                }
        }
    }

    // ── Event dispatch ────────────────────────────────────────────────────────

    override fun handleEvent(event: ExportUiEvent) {
        when (event) {
            is ExportUiEvent.PrepareExport   -> prepareExport(event.projectId)
            is ExportUiEvent.CancelExport    -> cancelExport()
            is ExportUiEvent.ResetForReExport -> resetForReExport()
            is ExportUiEvent.StartExport     -> startExport(
                projectId     = event.projectId,
                targetBitrate = event.targetBitrate,
                targetFps     = event.targetFps,
                targetHeight  = event.targetHeight
            )
            is ExportUiEvent.SaveToGallery   -> saveToGallery(event.filePath)
        }
    }

    // ── Prepare ───────────────────────────────────────────────────────────────

    private fun prepareExport(projectId: String) {
        viewModelScope.launch {
            val project   = projectRepository.getProjectById(projectId)
            val videoPath = project?.workingVideoPath

            // Load caption presence and video metadata in parallel
            val hasCaptionsResult = runCatching {
                captionRepository.getSegmentsOnce(projectId).isNotEmpty()
            }.getOrDefault(false)

            setState { copy(workingVideoPath = videoPath, hasCaptions = hasCaptionsResult) }

            if (videoPath != null) {
                extractMetadata(videoPath)
            }

            // Only reset if no export is in progress — prevents clobbering a
            // service that was already running when the screen was re-entered.
            if (currentState.exportState !is ExportState.Running) {
                ExportServiceManager.reset()
                setState { copy(exportState = ExportState.Ready) }
            }
        }
    }

    // ── Export control ────────────────────────────────────────────────────────

    private fun startExport(
        projectId: String,
        targetBitrate: Int? = null,
        targetFps: Int?     = null,
        targetHeight: Int?  = null
    ) {
        // Guard against double-starts (e.g., rapid tapping)
        if (currentState.exportState is ExportState.Running) return

        try {
            val intent = Intent(context, ExportForegroundService::class.java).apply {
                putExtra(ExportForegroundService.EXTRA_PROJECT_ID, projectId)
                if (targetBitrate != null) putExtra(ExportForegroundService.EXTRA_TARGET_BITRATE, targetBitrate)
                if (targetFps     != null) putExtra(ExportForegroundService.EXTRA_TARGET_FPS,     targetFps)
                if (targetHeight  != null) putExtra(ExportForegroundService.EXTRA_TARGET_HEIGHT,  targetHeight)
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Throwable) {
            // Catches SecurityException on some OEMs + generic failures
            crashReporter.recordException(e)
            ExportServiceManager.exportState.value =
                ExportState.Error(context.getString(R.string.export_start_failed))
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

    // ── Post-export ───────────────────────────────────────────────────────────

    private fun saveToGallery(filePath: String) {
        viewModelScope.launch {
            try {
                mediaManager.saveVideoToGallery(filePath)
                setState { copy(exportState = ExportState.SavedToGallery) }
            } catch (e: Throwable) {
                crashReporter.recordException(e)
                // Use the friendly string only — don't append raw e.message
                setState {
                    copy(exportState = ExportState.Error(context.getString(R.string.export_save_failed)))
                }
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

    // ── Metadata extraction ───────────────────────────────────────────────────

    /**
     * Extracts video dimensions, bitrate, duration, and FPS from the source
     * file using [MediaMetadataRetriever]. Swaps width/height when the video
     * is rotated 90° or 270° so the UI always works with display-space values.
     *
     * Runs on [Dispatchers.IO]; failures are silently recorded (the UI already
     * has sensible defaults in [ExportUiState]).
     */
    private suspend fun extractMetadata(videoPath: String) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoPath.toUri())

                val rawW     = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()     ?: 1080
                val rawH     = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()    ?: 1920
                val rotation = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

                val (displayW, displayH) =
                    if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH

                val bitrate    = retriever.extract(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()          ?: 5_000_000
                val durationMs = retriever.extract(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()        ?: 0L
                val fps        = retriever.extract(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toIntOrNull() ?: 30

                setState {
                    copy(
                        originalWidth       = displayW,
                        originalHeight      = displayH,
                        originalBitrate     = bitrate,
                        originalDurationMs  = durationMs,
                        originalFps         = fps
                    )
                }
            } catch (e: Throwable) {
                crashReporter.recordException(e)
                // State already has safe defaults — no need to update on failure
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Convenience extension to reduce verbosity in [extractMetadata]. */
    private fun MediaMetadataRetriever.extract(key: Int): String? =
        extractMetadata(key)
}

/**
 * Minimal 4-tuple for combining four flows without an extra data class or
 * a nested pair of pairs.
 */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

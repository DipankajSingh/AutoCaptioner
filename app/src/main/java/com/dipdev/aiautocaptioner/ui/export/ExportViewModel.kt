package com.dipdev.aiautocaptioner.ui.export

import android.os.Environment
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.dipdev.aiautocaptioner.data.db.dao.ExportedFileDao
import com.dipdev.aiautocaptioner.data.db.entity.ExportedFileEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.OverlayRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.engine.CaptionOverlayEffect
import com.dipdev.aiautocaptioner.engine.ImageOverlayEffect
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.effect.TextureOverlay
import java.util.UUID
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import com.dipdev.aiautocaptioner.core.utils.MediaManager
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import kotlin.time.Duration.Companion.milliseconds

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
    val outputPath: String? = null,
    val workingVideoPath: String? = null,
    val savedResolution: Int = -1,
    val savedFps: Int = -1,
    val savedQuality: Int = 1
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

class ExportUiEffect : UiEffect // not used currently

@UnstableApi
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository,
    private val overlayRepository: OverlayRepository,
    private val exportedFileDao: ExportedFileDao,
    private val crashReporter: CrashReporter,
    private val settingsRepository: SettingsRepository,
    private val mediaManager: MediaManager
) : BaseViewModel<ExportUiState, ExportUiEvent, ExportUiEffect>(ExportUiState()) {

    private var activeTransformer: Transformer? = null

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
    }

    override fun handleEvent(event: ExportUiEvent) {
        when (event) {
            is ExportUiEvent.PrepareExport -> prepareExport(event.projectId)
            is ExportUiEvent.CancelExport -> cancelExport()
            is ExportUiEvent.ResetForReExport -> resetForReExport()
            is ExportUiEvent.StartExport -> startExport(event.projectId, event.targetBitrate)
            is ExportUiEvent.SaveToGallery -> saveToGallery(event.filePath)
        }
    }

    private fun prepareExport(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            setState { copy(workingVideoPath = project?.workingVideoPath) }
            
            if (currentState.exportState == ExportState.Idle) {
                setState { copy(exportState = ExportState.Ready) }
            }
        }
    }

    private fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
        currentState.outputPath?.let { File(it).delete() }
        setState { copy(exportState = ExportState.Cancelled) }
    }

    private fun resetForReExport() {
        setState { copy(progress = 0f, exportState = ExportState.Ready) }
    }

    private fun startExport(
        projectId: String,
        targetBitrate: Int? = null
    ) {
        if (currentState.exportState == ExportState.Running) return
        setState { copy(exportState = ExportState.Running, progress = 0f) }

        viewModelScope.launch {
            try {
                val project = projectRepository.getProjectById(projectId)
                    ?: throw Exception("Project not found")
                val styleId = project.activeStyleId
                    ?: throw Exception("Style not assigned")
                val activeStyle = captionRepository.getStyleById(styleId)
                    ?: throw Exception("Style not found")

                val segments = captionRepository.getSegmentsOnce(projectId)
                val wordsList = captionRepository.getAllWordsForProject(projectId)
                val wordsMap = wordsList.groupBy { it.segmentId }

                val overlays = overlayRepository.getOverlaysOnce(projectId)
                val imageOverlayEffects = overlays.mapNotNull { overlay ->
                    try {
                        val inputStream = context.contentResolver.openInputStream(Uri.parse(overlay.imageUri))
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        if (bitmap != null) {
                            ImageOverlayEffect(
                                bitmap = bitmap,
                                positionX = overlay.positionX,
                                positionY = overlay.positionY,
                                scaleX = overlay.scaleX,
                                scaleY = overlay.scaleY,
                                startTimeMs = overlay.startTimeMs,
                                endTimeMs = overlay.endTimeMs
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                val isPortrait = project.videoRotation == 90 || project.videoRotation == 270
                val displayWidth  = if (isPortrait) project.videoHeight else project.videoWidth
                val displayHeight = if (isPortrait) project.videoWidth  else project.videoHeight

                val captionOverlayEffect = CaptionOverlayEffect(
                    segments = segments,
                    wordsMap = wordsMap,
                    style = activeStyle,
                    videoWidth = displayWidth,
                    videoHeight = displayHeight
                )

                val textureOverlays = ImmutableList.builder<TextureOverlay>()
                    .addAll(imageOverlayEffects)
                    .add(captionOverlayEffect)
                    .build()

                val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: context.filesDir.also { File(it, "exports").mkdirs() }
                if (!moviesDir.exists()) moviesDir.mkdirs()

                val outFile = File(moviesDir, "AutoCaptioner_${System.currentTimeMillis()}.mp4")
                setState { copy(outputPath = outFile.absolutePath) }

                val videoEffects: List<androidx.media3.common.Effect> =
                    listOf(OverlayEffect(textureOverlays))
                val audioProcessors: List<androidx.media3.common.audio.AudioProcessor> = emptyList()
                val effects = androidx.media3.transformer.Effects(audioProcessors, videoEffects)

                val editedMediaItem = EditedMediaItem.Builder(
                    MediaItem.fromUri(project.workingVideoPath)
                ).setEffects(effects).build()

                val encoderSettingsBuilder = androidx.media3.transformer.VideoEncoderSettings.Builder()
                if (targetBitrate != null) encoderSettingsBuilder.setBitrate(targetBitrate)
                val encoderSettings = encoderSettingsBuilder.build()

                val encoderFactory = androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            setState { copy(exportState = ExportState.Success) }
                            viewModelScope.launch {
                                val timestamp = System.currentTimeMillis()
                                projectRepository.updateProject(
                                    project.copy(
                                        status = ProjectStatus.EXPORTED,
                                        exportedVideoPath = outFile.absolutePath,
                                        updatedAt = timestamp
                                    )
                                )
                                exportedFileDao.insertExportedFile(
                                    ExportedFileEntity(
                                        id = UUID.randomUUID().toString(),
                                        projectId = project.id,
                                        videoFilePath = outFile.absolutePath,
                                        srtFilePath = null,
                                        exportedAt = timestamp,
                                        quality = targetBitrate?.let { "$it bps" }
                                    )
                                )
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            activeTransformer = null
                            setState { copy(exportState = ExportState.Error(exportException.message ?: "Unknown Export Error")) }
                        }
                    })
                    .build()

                activeTransformer = transformer
                transformer.start(editedMediaItem, outFile.absolutePath)
                trackProgress(transformer)

            } catch (e: Exception) {
                crashReporter.recordException(e)
                setState { copy(exportState = ExportState.Error(e.message ?: "Unknown error")) }
            }
        }
    }

    private fun saveToGallery(filePath: String) {
        viewModelScope.launch {
            try {
                mediaManager.saveVideoToGallery(filePath)
                setState { copy(exportState = ExportState.SavedToGallery) }
            } catch (e: Exception) {
                crashReporter.recordException(e)
                setState { copy(exportState = ExportState.Error("Failed to save to gallery: ${e.message}")) }
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

    private fun trackProgress(transformer: Transformer) {
        viewModelScope.launch {
            while (currentState.exportState == ExportState.Running) {
                val progressHolder = androidx.media3.transformer.ProgressHolder()
                val status = transformer.getProgress(progressHolder)
                if (status == Transformer.PROGRESS_STATE_AVAILABLE) {
                    setState { copy(progress = progressHolder.progress / 100f) }
                } else if (status == Transformer.PROGRESS_STATE_NOT_STARTED) {
                    setState { copy(progress = 0f) }
                }
                kotlinx.coroutines.delay(100.milliseconds)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeTransformer?.cancel()
        activeTransformer = null
    }
}

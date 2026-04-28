package com.dipdev.autocaptioner.ui.export

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.dipdev.autocaptioner.data.db.entity.ProjectStatus
import com.dipdev.autocaptioner.data.repository.CaptionRepository
import com.dipdev.autocaptioner.data.repository.ProjectRepository
import com.dipdev.autocaptioner.engine.CaptionOverlayEffect
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ExportState { IDLE, READY, ALREADY_EXPORTED, RUNNING, SUCCESS, CANCELLED, ERROR }

@UnstableApi
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository
) : ViewModel() {

    private val _exportState = MutableStateFlow(ExportState.IDLE)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _outputPath = MutableStateFlow<String?>(null)
    val outputPath: StateFlow<String?> = _outputPath.asStateFlow()

    private var activeTransformer: Transformer? = null

    /**
     * Called once when the export screen opens.
     * If this project has been exported before, restore the path and show
     * the ALREADY_EXPORTED state so the user can watch or re-export.
     */
    fun prepareExport(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val existingPath = project?.exportedVideoPath
            if (existingPath != null && File(existingPath).exists()) {
                _outputPath.value = existingPath
                _exportState.value = ExportState.ALREADY_EXPORTED
            } else if (_exportState.value == ExportState.IDLE) {
                _exportState.value = ExportState.READY
            }
        }
    }

    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
        _exportState.value = ExportState.CANCELLED
    }

    /** Reset to READY so the user can trigger a fresh export */
    fun resetForReExport() {
        _progress.value = 0f
        _exportState.value = ExportState.READY
    }

    fun startExport(projectId: String) {
        if (_exportState.value == ExportState.RUNNING) return
        _exportState.value = ExportState.RUNNING
        _progress.value = 0f

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

                val isPortrait = project.videoRotation == 90 || project.videoRotation == 270
                val displayWidth  = if (isPortrait) project.videoHeight else project.videoWidth
                val displayHeight = if (isPortrait) project.videoWidth  else project.videoHeight

                val overlay = CaptionOverlayEffect(
                    segments = segments,
                    wordsMap = wordsMap,
                    style = activeStyle,
                    videoWidth = displayWidth,
                    videoHeight = displayHeight
                )

                // Save inside getExternalFilesDir(Movies) — app-owned but scannable
                val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: context.filesDir.also { File(it, "exports").mkdirs() }
                if (!moviesDir.exists()) moviesDir.mkdirs()

                val outFile = File(moviesDir, "AutoCaptioner_${System.currentTimeMillis()}.mp4")
                _outputPath.value = outFile.absolutePath

                val videoEffects: List<androidx.media3.common.Effect> =
                    listOf(OverlayEffect(ImmutableList.of(overlay)))
                val audioProcessors: List<androidx.media3.common.audio.AudioProcessor> = emptyList()
                val effects = androidx.media3.transformer.Effects(audioProcessors, videoEffects)

                val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(project.workingVideoPath))
                    .setEffects(effects)
                    .build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            _exportState.value = ExportState.SUCCESS
                            viewModelScope.launch {
                                // Persist both status and the output path
                                projectRepository.updateProject(
                                    project.copy(
                                        status = ProjectStatus.EXPORTED,
                                        exportedVideoPath = outFile.absolutePath,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            _errorMessage.value = exportException.message ?: "Unknown Export Error"
                            _exportState.value = ExportState.ERROR
                        }
                    })
                    .build()

                activeTransformer = transformer
                transformer.start(editedMediaItem, outFile.absolutePath)
                trackProgress(transformer)

            } catch (e: Exception) {
                _exportState.value = ExportState.ERROR
                _errorMessage.value = e.message
            }
        }
    }

    /** Build a content:// URI via FileProvider and launch a share sheet */
    fun shareVideo(path: String): Intent {
        val file = File(path)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun trackProgress(transformer: Transformer) {
        viewModelScope.launch {
            while (_exportState.value == ExportState.RUNNING) {
                val progressHolder = androidx.media3.transformer.ProgressHolder()
                val status = transformer.getProgress(progressHolder)
                if (status == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _progress.value = progressHolder.progress / 100f
                } else if (status == Transformer.PROGRESS_STATE_NOT_STARTED) {
                    _progress.value = 0f
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }
}

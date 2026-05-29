package com.dipdev.aiautocaptioner.ui.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.engine.CaptionOverlayEffect
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

// Sealed class carries richer payload than an enum can
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

@UnstableApi
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _outputPath = MutableStateFlow<String?>(null)
    val outputPath: StateFlow<String?> = _outputPath.asStateFlow()

    private val _workingVideoPath = MutableStateFlow<String?>(null)
    val workingVideoPath: StateFlow<String?> = _workingVideoPath.asStateFlow()

    private var activeTransformer: Transformer? = null

    /**
     * Called once when the export screen opens.
     * If this project has been exported before, restore the path and show
     * the AlreadyExported state so the user can watch or re-export.
     */
    fun prepareExport(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            _workingVideoPath.value = project?.workingVideoPath
            val existingPath = project?.exportedVideoPath
            if (existingPath != null && File(existingPath).exists()) {
                _outputPath.value = existingPath
                _exportState.value = ExportState.AlreadyExported
            } else if (_exportState.value == ExportState.Idle) {
                _exportState.value = ExportState.Ready
            }
        }
    }

    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
        _exportState.value = ExportState.Cancelled
    }

    /** Reset to Ready so the user can trigger a fresh export */
    fun resetForReExport() {
        _progress.value = 0f
        _exportState.value = ExportState.Ready
    }

    fun startExport(
        projectId: String,
        targetBitrate: Int? = null,
        targetFps: Int? = null,
        targetHeight: Int? = null
    ) {
        if (_exportState.value == ExportState.Running) return
        _exportState.value = ExportState.Running
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

                val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: context.filesDir.also { File(it, "exports").mkdirs() }
                if (!moviesDir.exists()) moviesDir.mkdirs()

                val outFile = File(moviesDir, "AutoCaptioner_${System.currentTimeMillis()}.mp4")
                _outputPath.value = outFile.absolutePath

                val videoEffects: List<androidx.media3.common.Effect> =
                    listOf(OverlayEffect(ImmutableList.of<androidx.media3.effect.TextureOverlay>(overlay)))
                val audioProcessors: List<androidx.media3.common.audio.AudioProcessor> = emptyList()
                val effects = androidx.media3.transformer.Effects(audioProcessors, videoEffects)

                val editedMediaItem = EditedMediaItem.Builder(
                    MediaItem.fromUri(project.workingVideoPath)
                ).setEffects(effects).build()

                // Configure Export Quality (Bitrate determines exact file size)
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
                            _exportState.value = ExportState.Success
                            viewModelScope.launch {
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
                            _exportState.value =
                                ExportState.Error(exportException.message ?: "Unknown Export Error")
                        }
                    })
                    .build()

                activeTransformer = transformer
                transformer.start(editedMediaItem, outFile.absolutePath)
                trackProgress(transformer)

            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Copy the exported video into the system MediaStore (Gallery).
     * Works on Android 10+ (Q) with MediaStore API and on older versions
     * via legacy file copy to Pictures/Movies.
     */
    fun saveToGallery(filePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val sourceFile = File(filePath)
                    if (!sourceFile.exists()) return@withContext

                    val fileName = "AutoCaptioner_${System.currentTimeMillis()}.mp4"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH,
                                "${Environment.DIRECTORY_MOVIES}/AutoCaptioner")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                            ?: return@withContext

                        resolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(sourceFile).use { it.copyTo(out) }
                        }
                        values.clear()
                        values.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    } else {
                        @Suppress("DEPRECATION")
                        val destDir = File(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_MOVIES
                            ), "AutoCaptioner"
                        )
                        destDir.mkdirs()
                        sourceFile.copyTo(File(destDir, fileName), overwrite = true)
                        // Trigger media scan so Gallery picks it up
                        // Trigger media scan so Gallery picks it up (only on API < 29)
                        @Suppress("DEPRECATION")
                        context.sendBroadcast(
                            @Suppress("DEPRECATION")
                            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                                data = android.net.Uri.fromFile(File(destDir, fileName))
                            }
                        )
                    }

                    _exportState.value = ExportState.SavedToGallery
                } catch (e: Exception) {
                    _exportState.value = ExportState.Error("Failed to save to gallery: ${e.message}")
                }
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
            while (_exportState.value == ExportState.Running) {
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

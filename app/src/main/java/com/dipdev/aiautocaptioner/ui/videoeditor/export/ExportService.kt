package com.dipdev.aiautocaptioner.ui.videoeditor.export

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.dipdev.aiautocaptioner.core.utils.FileUtils
import com.dipdev.aiautocaptioner.data.model.Clip
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var transformer: Transformer? = null
    private var tempOutputFile: File? = null
    private var isExporting = false

    fun startExport(
        scope: CoroutineScope,
        originalPath: String,
        clips: List<Clip>,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isExporting) return
        isExporting = true

        try {
            tempOutputFile = FileUtils.createTempVideoFile(context)
            val tempFile = tempOutputFile ?: throw IllegalStateException("Could not create temp file")

            // Merge adjacent contiguous clips to prevent FFmpeg micro-stutters
            val mergedClips = mutableListOf<Clip>()
            var currentMerged: Clip? = null
            for (c in clips) {
                if (currentMerged == null) {
                    currentMerged = c
                } else if (currentMerged.endTrimMs == c.startTrimMs) {
                    currentMerged = currentMerged.copy(endTrimMs = c.endTrimMs)
                } else {
                    mergedClips.add(currentMerged)
                    currentMerged = c
                }
            }
            if (currentMerged != null) {
                mergedClips.add(currentMerged)
            }

            val editedMediaItems = mergedClips.map { clip ->
                val mediaItem = MediaItem.Builder()
                    .setUri(originalPath)
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
                        isExporting = false
                        onSuccess(tempFile)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        isExporting = false
                        tempFile.delete()
                        tempOutputFile = null
                        onError(exportException.message ?: "Unknown error during trim")
                    }
                })
                .build()

            transformer?.start(composition, tempFile.absolutePath)

            scope.launch {
                val progressHolder = androidx.media3.transformer.ProgressHolder()
                while (transformer != null && isExporting) {
                    val progressState = transformer?.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress)
                    } else if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) {
                        onProgress(0)
                    }
                    delay(500.milliseconds)
                }
            }

        } catch (e: Exception) {
            isExporting = false
            onError(e.message ?: "Failed to process video")
        }
    }

    fun cancel() {
        isExporting = false
        transformer?.cancel()
        transformer = null
        tempOutputFile?.delete()
        tempOutputFile = null
    }
}

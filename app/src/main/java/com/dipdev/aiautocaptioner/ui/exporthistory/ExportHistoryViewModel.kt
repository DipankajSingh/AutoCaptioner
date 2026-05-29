package com.dipdev.aiautocaptioner.ui.exporthistory

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.db.dao.ExportedFileDao
import com.dipdev.aiautocaptioner.data.db.entity.ExportedFileEntity
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
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

@HiltViewModel
class ExportHistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportedFileDao: ExportedFileDao,
    private val captionRepository: CaptionRepository
) : ViewModel() {

    private val _exports = MutableStateFlow<List<ExportedFileEntity>>(emptyList())
    val exports: StateFlow<List<ExportedFileEntity>> = _exports.asStateFlow()

    fun loadExports(projectId: String) {
        viewModelScope.launch {
            exportedFileDao.getExportsForProject(projectId).collect {
                _exports.value = it
            }
        }
    }

    fun saveVideoToGallery(export: ExportedFileEntity) {
        val path = export.videoFilePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val sourceFile = File(path)
            if (!sourceFile.exists()) return@launch

            val fileName = "AutoCaptioner_Export_${export.exportedAt}.mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AutoCaptioner")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@launch

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "AutoCaptioner")
                destDir.mkdirs()
                val destFile = File(destDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                @Suppress("DEPRECATION")
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = android.net.Uri.fromFile(destFile)
                })
            }
        }
    }

    fun shareVideo(export: ExportedFileEntity) {
        val path = export.videoFilePath ?: return
        val file = File(path)
        if (!file.exists()) return
        
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Video").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareSrt(export: ExportedFileEntity) {
        viewModelScope.launch {
            val srtFile = generateOrGetSrt(export)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", srtFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-subrip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share SRT").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun saveSrt(export: ExportedFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val srtFile = generateOrGetSrt(export)
            val fileName = "AutoCaptioner_Export_${export.exportedAt}.srt"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@launch
                
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(srtFile).use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(destDir, fileName)
                srtFile.copyTo(destFile, overwrite = true)
            }
        }
    }

    fun deleteExport(export: ExportedFileEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                export.videoFilePath?.let { File(it).delete() }
                export.srtFilePath?.let { File(it).delete() }
                exportedFileDao.deleteExportedFile(export)
            }
        }
    }

    private suspend fun generateOrGetSrt(export: ExportedFileEntity): File {
        return withContext(Dispatchers.IO) {
            if (export.srtFilePath != null && File(export.srtFilePath).exists()) {
                File(export.srtFilePath)
            } else {
                val segments = captionRepository.getSegmentsOnce(export.projectId)
                val sb = java.lang.StringBuilder()
                segments.forEachIndexed { index, segment ->
                    sb.append(index + 1).append("\n")
                    sb.append(formatSrtTime(segment.startTimeMs)).append(" --> ").append(formatSrtTime(segment.endTimeMs)).append("\n")
                    sb.append(segment.text).append("\n\n")
                }
                val srtFile = File(context.cacheDir, "export_${export.id}.srt")
                srtFile.writeText(sb.toString())
                srtFile
            }
        }
    }

    private fun formatSrtTime(timeMs: Long): String {
        val hours = timeMs / 3600000
        val minutes = (timeMs % 3600000) / 60000
        val seconds = (timeMs % 60000) / 1000
        val millis = timeMs % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}

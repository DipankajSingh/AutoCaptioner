package com.dipdev.aiautocaptioner.core.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashReporter: com.dipdev.aiautocaptioner.core.logging.CrashReporter
) {
    companion object {
        private const val TAG = "FileStorageManager"
    }

    fun getProjectDir(projectId: String): File {
        val dir = File(context.filesDir, "projects/$projectId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun copyUriToInternalStorage(uri: Uri, destFile: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open input stream for $uri")
        input.use { source ->
            destFile.outputStream().use { output ->
                source.copyTo(output)
            }
        }
        if (!destFile.exists() || destFile.length() == 0L) {
            throw IOException("Copy produced an empty file for $uri at ${destFile.absolutePath}")
        }
    }

    fun deleteProjectDir(projectId: String) {
        try {
            val projectDir = File(context.filesDir, "projects/$projectId")
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
                Log.i(TAG, "Deleted project files: ${projectDir.absolutePath}")
            }
        } catch (e: Exception) {
            crashReporter.recordException(e)
            Log.e(TAG, "Failed to delete project files", e)
        }
    }

    fun deleteThumbnailCache(videoPath: String) {
        try {
            val videoHash = videoPath.hashCode().toString()
            val cacheDir = File(context.cacheDir, "thumbnails/$videoHash")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.i(TAG, "Deleted thumbnail cache: ${cacheDir.absolutePath}")
            }
        } catch (e: Exception) {
            crashReporter.recordException(e)
            Log.e(TAG, "Failed to delete thumbnail cache", e)
        }
    }

    fun duplicateProjectFiles(oldProjectId: String, newProjectId: String) {
        val originalProjectDir = File(context.filesDir, "projects/$oldProjectId")
        val newProjectDir = File(context.filesDir, "projects/$newProjectId")
        
        if (originalProjectDir.exists()) {
            originalProjectDir.copyRecursively(newProjectDir, overwrite = true)
        }
    }
}

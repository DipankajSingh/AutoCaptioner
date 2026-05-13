package com.dipdev.aiautocaptioner.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.dipdev.aiautocaptioner.data.db.dao.ProjectDao
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// @Singleton means only one instance exists for the whole app
// @Inject constructor tells Hilt: "create this automatically,
// injecting the parameters from the DI graph"
@Singleton
class ProjectRepository @Inject constructor(
    // Hilt injects these automatically from DatabaseModule + AppModule
    private val projectDao: ProjectDao,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ProjectRepository"
    }

    // ---- Observe all projects ----
    // Returns a Flow that automatically emits updates when DB changes
    // The home screen collects this to show the project list
    fun getAllProjects(): Flow<List<ProjectEntity>> =
        projectDao.getAllProjects()

    // ---- Get single project ----
    suspend fun getProjectById(projectId: String): ProjectEntity? =
        projectDao.getProjectById(projectId)

    // ---- Import a video ----
    // This is the main entry point when user picks a video from gallery
    // It does several things:
    // 1. Reads video metadata (duration, dimensions, fps, rotation)
    // 2. Creates the project folder in internal storage
    // 3. Copies the video file into internal storage
    // 4. Extracts and saves the thumbnail
    // 5. Saves the project to the database
    // Returns the new projectId so the caller can navigate to ProcessingScreen
    suspend fun importVideo(videoUri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1 — Read video metadata using MediaMetadataRetriever
                val retriever = MediaMetadataRetriever()
                val durationMs: Long
                val width: Int
                val height: Int
                val rotation: Int
                val fps: Float
                val fileName: String?

                try {
                    retriever.setDataSource(context, videoUri)

                    durationMs = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L

                    width = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0

                    height = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0

                    rotation = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull() ?: 0

                    val fpsString = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    fps = parseFps(fpsString) ?: 30f

                    fileName = getFileName(videoUri) ?: "Video"
                } finally {
                    retriever.release() // always released — even if extraction throws
                }

                // Step 2 — Create project folder in internal storage
                val projectId = UUID.randomUUID().toString()
                val projectDir = File(context.filesDir, "projects/$projectId")
                projectDir.mkdirs()

                Log.i(TAG, "Created project directory: ${projectDir.absolutePath}")

                // Step 3 — Copy video to internal storage
                val videoFile = File(projectDir, "original.mp4")
                context.contentResolver.openInputStream(videoUri)?.use { input ->
                    videoFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Could not open video file")

                Log.i(TAG, "Video copied to: ${videoFile.absolutePath}")

                // Step 4 — Extract thumbnail (first frame of video)
                val thumbnailFile = File(projectDir, "thumbnail.jpg")
                extractThumbnail(videoUri, thumbnailFile)

                // Step 5 — Save project to database
                val now = System.currentTimeMillis()
                val project = ProjectEntity(
                    id = projectId,
                    title = fileName.removeSuffix(".mp4")
                        .removeSuffix(".mov")
                        .removeSuffix(".mkv"),
                    originalVideoUri = videoUri.toString(),
                    workingVideoPath = videoFile.absolutePath,
                    thumbnailPath = if (thumbnailFile.exists())
                        thumbnailFile.absolutePath else null,
                    videoDurationMs = durationMs,
                    videoWidth = width,
                    videoHeight = height,
                    videoRotation = rotation,
                    videoFps = fps,
                    status = ProjectStatus.IMPORTED,
                    hasVisitedCaptionEditor = false,
                    createdAt = now,
                    updatedAt = now
                )

                projectDao.insertProject(project)
                Log.i(TAG, "Project saved to DB: $projectId")

                Result.success(projectId)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to import video", e)
                Result.failure(e)
            }
        }
    }

    // ---- Update project status ----
    // Called at each step of the processing pipeline
    suspend fun updateStatus(projectId: String, status: ProjectStatus) {
        projectDao.updateStatus(projectId, status)
    }

    // ---- Update audio path ----
    // Called after audio extraction is complete
    suspend fun updateAudioPath(projectId: String, audioPath: String) {
        projectDao.updateAudioPath(projectId, audioPath)
    }

    // ---- Update visited caption editor flag ----
    suspend fun updateVisitedCaptionEditor(projectId: String, hasVisited: Boolean = true) {
        projectDao.updateVisitedCaptionEditor(projectId, hasVisited)
    }

    // ---- Update project complete entity ----
    suspend fun updateProject(project: ProjectEntity) {
        projectDao.updateProject(project)
    }

    // ---- Rename project ----
    suspend fun renameProject(projectId: String, newTitle: String) {
        val project = projectDao.getProjectById(projectId) ?: return
        projectDao.updateProject(
            project.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis())
        )
        Log.i(TAG, "Project renamed to: $newTitle")
    }

    // ---- Delete project ----
    // Also deletes all files from internal storage
    suspend fun deleteProject(projectId: String) {
        withContext(Dispatchers.IO) {
            // Get project to find the file path
            val project = projectDao.getProjectById(projectId) ?: return@withContext

            // Delete project folder and all its contents
            // (video, audio, thumbnail, any exports)
            val projectDir = File(context.filesDir, "projects/$projectId")
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
                Log.i(TAG, "Deleted project files: ${projectDir.absolutePath}")
            }

            // Delete from database
            // Room's CASCADE will automatically delete all segments + words
            projectDao.deleteProject(project)
            Log.i(TAG, "Deleted project from DB: $projectId")
        }
    }

    // ---- Helper: Extract thumbnail ----
    // Gets the first frame of the video and saves it as JPEG
    private fun extractThumbnail(videoUri: Uri, outputFile: File) {
        try {
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(context, videoUri)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                retriever.release() // always released even if getFrameAtTime throws
            }

            if (bitmap != null) {
                outputFile.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Thumbnail failure is non-fatal — app works without it
            Log.w(TAG, "Failed to extract thumbnail: ${e.message}")
        }
    }

    // ---- Helper: Parse FPS string ----
    // MediaMetadataRetriever returns fps in different formats
    // "30" → 30.0f
    // "30/1" → 30.0f
    // "29.97" → 29.97f
    private fun parseFps(fpsString: String?): Float? {
        if (fpsString == null) return null
        return try {
            if (fpsString.contains("/")) {
                // Format: "numerator/denominator"
                val parts = fpsString.split("/")
                parts[0].toFloat() / parts[1].toFloat()
            } else {
                fpsString.toFloat()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- Helper: Get filename from URI ----
    private fun getFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
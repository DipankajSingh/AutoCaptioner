package com.dipdev.aiautocaptioner.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.dipdev.aiautocaptioner.data.db.dao.ProjectDao
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction
import com.dipdev.aiautocaptioner.data.db.AppDatabase

// @Singleton means only one instance exists for the whole app
// @Inject constructor tells Hilt: "create this automatically,
// injecting the parameters from the DI graph"
@Singleton
class ProjectRepository @Inject constructor(
    // Hilt injects these automatically from DatabaseModule + AppModule
    private val projectDao: ProjectDao,
    private val db: AppDatabase,
    @ApplicationContext private val context: Context,
    private val crashReporter: CrashReporter
) {

    companion object {
        private const val TAG = "ProjectRepository"
    }

    // Returns projects bundled with their exported files
    fun getProjectsWithExportedFiles(): Flow<List<com.dipdev.aiautocaptioner.data.db.entity.ProjectWithExportedFiles>> =
        projectDao.getProjectsWithExportedFiles()

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
    suspend fun importVideo(videoUri: Uri, creationMode: com.dipdev.aiautocaptioner.data.db.entity.CreationMode = com.dipdev.aiautocaptioner.data.db.entity.CreationMode.ADVANCED): Result<String> {
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

                // Step 3 — Take persistable URI permission or securely copy video
                var finalVideoPath = videoUri.toString()
                try {
                    context.contentResolver.takePersistableUriPermission(
                        videoUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // Photo Picker URIs don't support persistable permissions. 
                    // Copy to internal storage so we don't lose the video reference on app restart.
                    Log.w(TAG, "Could not take persistable permission for $videoUri, copying file.", e)
                    val videoFile = File(projectDir, "original_video.mp4")
                    context.contentResolver.openInputStream(videoUri)?.use { input ->
                        videoFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    finalVideoPath = videoFile.absolutePath
                }

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
                    originalVideoUri = finalVideoPath,
                    workingVideoPath = finalVideoPath,
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
                    updatedAt = now,
                    creationMode = creationMode
                )

                projectDao.insertProject(project)
                Log.i(TAG, "Project saved to DB: $projectId")

                Result.success(projectId)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to import video", e)
                crashReporter.recordException(e)
                Result.failure(e)
            }
        }
    }

    // ---- Create Project for Recording ----
    suspend fun createEmptyProjectForRecording(): Pair<String, File> {
        return withContext(Dispatchers.IO) {
            val projectId = UUID.randomUUID().toString()
            val projectDir = File(context.filesDir, "projects/$projectId")
            projectDir.mkdirs()
            val videoFile = File(projectDir, "original_video.mp4")
            Pair(projectId, videoFile)
        }
    }

    // ---- Finalize Recorded Video ----
    suspend fun finalizeRecordedProject(projectId: String, videoFile: File, backgroundType: String? = null, backgroundValue: String? = null): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!videoFile.exists()) {
                    return@withContext Result.failure(Exception("Recorded video file not found"))
                }
                val videoUri = Uri.fromFile(videoFile)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri)

                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                val width = widthStr?.toIntOrNull() ?: 1080
                val height = heightStr?.toIntOrNull() ?: 1920
                val rotation = rotationStr?.toIntOrNull() ?: 0
                val fps = 30f // Assuming 30fps for recordings

                retriever.release()

                val projectDir = videoFile.parentFile
                val thumbnailFile = File(projectDir, "thumbnail.jpg")
                extractThumbnail(videoUri, thumbnailFile)

                val now = System.currentTimeMillis()
                val project = ProjectEntity(
                    id = projectId,
                    title = "Recording ${System.currentTimeMillis()}",
                    originalVideoUri = videoFile.absolutePath,
                    workingVideoPath = videoFile.absolutePath,
                    thumbnailPath = if (thumbnailFile.exists()) thumbnailFile.absolutePath else null,
                    videoDurationMs = durationMs,
                    videoWidth = width,
                    videoHeight = height,
                    videoRotation = rotation,
                    videoFps = fps,
                    status = ProjectStatus.IMPORTED,
                    hasVisitedCaptionEditor = false,
                    facelessBackgroundType = backgroundType,
                    facelessBackgroundValue = backgroundValue,
                    createdAt = now,
                    updatedAt = now
                )

                projectDao.insertProject(project)
                Result.success(projectId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to finalize recorded project", e)
                crashReporter.recordException(e)
                Result.failure(e)
            }
        }
    }

    // ---- Update project status ----
    // Called at each step of the processing pipeline
    suspend fun updateStatus(projectId: String, status: ProjectStatus) {
        projectDao.updateStatus(projectId, status)
    }

    // ---- Update working video path ----
    suspend fun updateWorkingVideoPath(projectId: String, videoPath: String) {
        projectDao.updateWorkingVideoPath(projectId, videoPath)
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
        projectDao.renameProject(projectId, newTitle.trim())
        Log.i(TAG, "Project renamed to: $newTitle")
    }

    // ---- Delete project ----
    // Also deletes all files from internal storage
    suspend fun deleteProject(projectId: String) {
        withContext(Dispatchers.IO) {
            // Get project to find the file path
            val project = projectDao.getProjectById(projectId) ?: return@withContext

            // Delete from database FIRST
            // Room's CASCADE will automatically delete all segments + words
            projectDao.deleteProject(project)
            Log.i(TAG, "Deleted project from DB: $projectId")

            // Delete project folder and all its contents
            // (video, audio, thumbnail, any exports)
            try {
                val projectDir = File(context.filesDir, "projects/$projectId")
                if (projectDir.exists()) {
                    projectDir.deleteRecursively()
                    Log.i(TAG, "Deleted project files: ${projectDir.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete project files", e)
            }

            // Clear thumbnail cache
            try {
                val videoHash = project.workingVideoPath.hashCode().toString()
                val cacheDir = File(context.cacheDir, "thumbnails/$videoHash")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                    Log.i(TAG, "Deleted thumbnail cache: ${cacheDir.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete thumbnail cache", e)
            }
        }
    }

    // ---- Duplicate project ----
    suspend fun duplicateProject(projectId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get original project
                val originalProject = projectDao.getProjectById(projectId)
                    ?: return@withContext Result.failure(Exception("Project not found"))

                // 2. Generate new ID
                val newProjectId = UUID.randomUUID().toString()

                // 3. Duplicate files on disk
                val originalProjectDir = File(context.filesDir, "projects/$projectId")
                val newProjectDir = File(context.filesDir, "projects/$newProjectId")
                
                if (originalProjectDir.exists()) {
                    originalProjectDir.copyRecursively(newProjectDir, overwrite = true)
                }

                // 4. Update file paths by swapping the projectId in the path
                val newOriginalVideoUri = originalProject.originalVideoUri.replace(projectId, newProjectId)
                val newWorkingVideoPath = originalProject.workingVideoPath.replace(projectId, newProjectId)
                val newAudioPath = originalProject.audioPath?.replace(projectId, newProjectId)
                val newThumbnailPath = originalProject.thumbnailPath?.replace(projectId, newProjectId)
                val newExportedVideoPath = originalProject.exportedVideoPath?.replace(projectId, newProjectId)

                // 5. Create new project entity
                val now = System.currentTimeMillis()
                val newProject = originalProject.copy(
                    id = newProjectId,
                    title = "Copy of ${originalProject.title}",
                    createdAt = now,
                    updatedAt = now,
                    originalVideoUri = newOriginalVideoUri,
                    workingVideoPath = newWorkingVideoPath,
                    audioPath = newAudioPath,
                    thumbnailPath = newThumbnailPath,
                    exportedVideoPath = newExportedVideoPath
                )

                // 6. Duplicate entities in database transaction
                db.withTransaction {
                    // Insert new project
                    projectDao.insertProject(newProject)

                    // Get and duplicate segments
                    val segments = db.captionSegmentDao().getSegmentsForProjectOnce(projectId)
                    val oldToNewSegmentId = mutableMapOf<String, String>()
                    val newSegments = segments.map { segment ->
                        val newSegmentId = UUID.randomUUID().toString()
                        oldToNewSegmentId[segment.id] = newSegmentId
                        segment.copy(id = newSegmentId, projectId = newProjectId)
                    }
                    if (newSegments.isNotEmpty()) {
                        db.captionSegmentDao().insertAll(newSegments)
                    }

                    // Get and duplicate words
                    val words = db.captionWordDao().getAllWordsForProject(projectId)
                    val newWords = words.map { word ->
                        val newWordId = UUID.randomUUID().toString()
                        val newSegmentId = oldToNewSegmentId[word.segmentId] ?: word.segmentId
                        word.copy(id = newWordId, projectId = newProjectId, segmentId = newSegmentId)
                    }
                    if (newWords.isNotEmpty()) {
                        db.captionWordDao().insertAll(newWords)
                    }
                }

                Log.i(TAG, "Project duplicated: $projectId -> $newProjectId")
                Result.success(newProjectId)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to duplicate project", e)
                crashReporter.recordException(e)
                Result.failure(e)
            }
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
            crashReporter.recordException(e)
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            null
        }
    }
}
package com.dipdev.aiautocaptioner.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val fps: Float
)

@Singleton
class MediaProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashReporter: CrashReporter
) {
    companion object {
        private const val TAG = "MediaProcessor"
    }

    fun extractMetadata(videoUri: Uri): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0

            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0

            val fpsString = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val fps = parseFps(fpsString) ?: 30f

            return VideoMetadata(durationMs, width, height, rotation, fps)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun extractThumbnail(videoUri: Uri, outputFile: File) {
        try {
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(context, videoUri)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                retriever.release()
            }

            if (bitmap != null) {
                outputFile.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract thumbnail: ${e.message}")
            crashReporter.recordException(e)
        }
    }

    private fun parseFps(fpsString: String?): Float? {
        if (fpsString == null) return null
        return try {
            if (fpsString.contains("/")) {
                val parts = fpsString.split("/")
                parts[0].toFloat() / parts[1].toFloat()
            } else {
                fpsString.toFloat()
            }
        } catch (_: Exception) {
            null
        }
    }
}

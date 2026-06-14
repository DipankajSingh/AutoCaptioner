package com.dipdev.aiautocaptioner.core.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

object ThumbnailExtractor {

    /**
     * Extracts an evenly spaced list of thumbnails from a video file.
     *
     * @param videoPath Absolute path to the video.
     * @param startMs The start time in milliseconds.
     * @param endMs The end time in milliseconds.
     * @param count The number of thumbnails to extract.
     * @return A list of extracted Bitmaps.
     */
    suspend fun extractThumbnails(
        context: android.content.Context,
        videoPath: String,
        startMs: Long,
        endMs: Long,
        count: Int
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        val retriever = MediaMetadataRetriever()
        
        try {
            if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                retriever.setDataSource(context, android.net.Uri.parse(videoPath))
            } else {
                retriever.setDataSource(videoPath)
            }
            
            // Calculate the step interval between frames
            val duration = endMs - startMs
            val stepMs = if (count > 1) duration / (count - 1) else 0
            
            for (i in 0 until count) {
                val timeMs = startMs + (i * stepMs)
                // MediaMetadataRetriever takes time in microseconds
                val bitmap = retriever.getFrameAtTime(
                    timeMs * 1000, 
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                
                if (bitmap != null) {
                    // Scale down the bitmap to save memory (e.g. 100px height)
                    val targetHeight = 100
                    val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val targetWidth = (targetHeight * aspectRatio).toInt()
                    
                    val scaledBitmap = bitmap.scale(targetWidth, targetHeight)
                    bitmaps.add(scaledBitmap)
                    
                    if (scaledBitmap != bitmap) {
                        bitmap.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return@withContext bitmaps
    }
}

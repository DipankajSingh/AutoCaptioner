package com.dipdev.aiautocaptioner.core.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import androidx.core.net.toUri
import androidx.core.graphics.scale

class ThumbnailManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Memory cache: max size in bytes
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory
    
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            val timeMs = key.substringAfter("_").toLongOrNull() ?: return
            // Post update off the LruCache lock to prevent deadlock
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                _thumbnails.update { current ->
                    val next = current.toMutableMap()
                    next.remove(timeMs)
                    next
                }
            }
        }
    }
    
    // Active jobs for cancellation
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    
    // The current state of available thumbnails for the UI
    private val _thumbnails = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, Bitmap>> = _thumbnails.asStateFlow()

    private var currentVideoPath: String = ""
    private var cacheDir: File? = null
    
    private var retriever: MediaMetadataRetriever? = null
    private var targetThumbWidth: Int = -1
    private val targetThumbHeight: Int = 120
    
    fun setVideoPath(videoPath: String) {
        if (currentVideoPath == videoPath) return
        
        // Cleanup old
        clearMemoryCache()
        retriever?.release()
        
        currentVideoPath = videoPath
        val videoHash = videoPath.hashCode().toString()
        cacheDir = File(context.cacheDir, "thumbnails/$videoHash").apply { mkdirs() }
        
        var newRetriever: MediaMetadataRetriever? = null
        try {
            newRetriever = MediaMetadataRetriever().apply {
                if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                    setDataSource(context, videoPath.toUri())
                } else {
                    setDataSource(videoPath)
                }
                
                // Pre-calculate thumbnail dimensions to use native hardware scaling
                val widthStr = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotationStr = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                
                val width = widthStr?.toIntOrNull() ?: 1920
                val height = heightStr?.toIntOrNull() ?: 1080
                val rotation = rotationStr?.toIntOrNull() ?: 0
                
                val (actualWidth, actualHeight) = if (rotation == 90 || rotation == 270) {
                    Pair(height, width)
                } else {
                    Pair(width, height)
                }
                
                val aspectRatio = actualWidth.toFloat() / actualHeight.toFloat()
                targetThumbWidth = (targetThumbHeight * aspectRatio).toInt()
            }
            retriever = newRetriever
        } catch (e: Exception) {
            e.printStackTrace()
            newRetriever?.release()
            retriever = null
        }
    }

    /**
     * The UI calls this with the list of exact timestamps it needs right now.
     * @param timestamps List of times in milliseconds.
     */
    fun requestThumbnails(timestamps: List<Long>) {
        // Cancel jobs for timestamps that are no longer requested
        val unneededJobs = activeJobs.keys - timestamps.toSet()
        for (time in unneededJobs) {
            activeJobs[time]?.cancel()
            activeJobs.remove(time)
        }

        // Process requested timestamps
        for (timeMs in timestamps) {
            val key = "${currentVideoPath.hashCode()}_$timeMs"
            
            // 1. Check Memory Cache (L1)
            val cachedBitmap = memoryCache.get(key)
            if (cachedBitmap != null) {
                updateState(timeMs, cachedBitmap)
                continue
            }
            
            // If already loading, skip
            if (activeJobs.containsKey(timeMs)) continue
            
            // Extract from Disk/Video
            activeJobs[timeMs] = scope.launch {
                val bitmap = loadOrExtractFrame(timeMs)
                if (bitmap != null) {
                    memoryCache.put(key, bitmap)
                    updateState(timeMs, bitmap)
                }
                activeJobs.remove(timeMs)
            }
        }
    }

    private suspend fun loadOrExtractFrame(timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        // 2. Check Disk Cache (L2)
        val file = File(cacheDir, "$timeMs.jpg")
        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) return@withContext bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Extract from Video using stable API with hardware downscaling
        val r = retriever ?: return@withContext null
        
        try {
            val raw = r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            val scaledBitmap = if (raw != null && targetThumbWidth > 0) {
                val scaled = raw.scale(targetThumbWidth, targetThumbHeight)
                if (scaled != raw) raw.recycle()
                scaled
            } else raw

            if (scaledBitmap != null) {
                FileOutputStream(file).use { out ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                return@withContext scaledBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun updateState(timeMs: Long, bitmap: Bitmap) {
        _thumbnails.update { current ->
            current.toMutableMap().apply { put(timeMs, bitmap) }
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
        _thumbnails.value = emptyMap()
        for (job in activeJobs.values) {
            job.cancel()
        }
        activeJobs.clear()
    }

    fun release() {
        clearMemoryCache()
        retriever?.release()
        retriever = null
        scope.cancel()
    }
}

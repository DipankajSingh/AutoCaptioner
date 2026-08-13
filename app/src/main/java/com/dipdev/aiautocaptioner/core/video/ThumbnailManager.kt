package com.dipdev.aiautocaptioner.core.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class ThumbnailManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val poolSemaphore = Semaphore(3)

    // ── Memory Cache (L1) ────────────────────────────────────────────────────

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 1/8th of available RAM


    private val evictionChannel = Channel<Long>(Channel.UNLIMITED)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            // Fix B1: trySend is non-blocking — safe to call while holding the LruCache lock
            val timeMs = key.substringAfterLast("_").toLongOrNull() ?: return
            evictionChannel.trySend(timeMs)
        }
    }

    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val _thumbnails = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, Bitmap>> = _thumbnails.asStateFlow()


    private var currentVideoPath: String = ""

    private var stableVideoId: String = ""
    private var cacheDir: File? = null

    private val retrieverPool = ConcurrentLinkedQueue<MediaMetadataRetriever>()
    private var targetThumbWidth: Int = -1
    private val targetThumbHeight: Int = 120


    @Volatile private var isResetting = false

    init {
        scope.launch {
            evictionChannel.consumeEach { timeMs ->
                _thumbnails.update { current -> current - timeMs }
            }
        }
    }

    fun setVideoPath(videoPath: String) {
        if (currentVideoPath == videoPath) return

        isResetting = true

        clearMemoryCache()
        retrieverPool.forEach { it.release() }
        retrieverPool.clear()

        currentVideoPath = videoPath

        stableVideoId = computeStableId(videoPath)
        cacheDir = File(context.cacheDir, "thumbnails/$stableVideoId").apply { mkdirs() }
        cleanupOldCacheDirectories()

        try {
            for (i in 0 until 3) {
                val r = MediaMetadataRetriever()
                try {
                    if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                        r.setDataSource(context, videoPath.toUri())
                    } else {
                        r.setDataSource(videoPath)
                    }

                    if (i == 0) {
                        val widthStr = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val heightStr = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        val rotationStr = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

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
                    retrieverPool.add(r)
                } catch (e: Exception) {
                    try { r.release() } catch (_: Exception) {}
                    throw e
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ThumbnailManager", "Error", e)
            retrieverPool.forEach { it.release() }
            retrieverPool.clear()
        } finally {
            isResetting = false
        }
    }


    private fun computeStableId(videoPath: String): String {
        val file = File(videoPath)
        return if (file.exists()) {
            "${videoPath.hashCode()}_${file.lastModified()}"
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(videoPath.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }.take(16)
        }
    }

    private fun cleanupOldCacheDirectories() {
        scope.launch(Dispatchers.IO) {
            val thumbnailsBaseDir = File(context.cacheDir, "thumbnails")
            if (thumbnailsBaseDir.exists()) {
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                thumbnailsBaseDir.listFiles()?.forEach { dir ->
                    if (dir.isDirectory && dir != cacheDir && dir.lastModified() < oneDayAgo) {
                        dir.deleteRecursively()
                    }
                }
            }
        }
    }


    fun requestThumbnails(timestamps: List<Long>) {
        val unneededJobs = activeJobs.keys - timestamps.toSet()
        for (time in unneededJobs) {
            activeJobs[time]?.cancel()
            activeJobs.remove(time)
        }

        if (unneededJobs.isNotEmpty()) {
            _thumbnails.update { current ->
                val next = current.toMutableMap()
                unneededJobs.forEach { time -> next.remove(time) }
                next
            }
        }

        for (timeMs in timestamps) {
            val key = "${stableVideoId}_$timeMs"

            val cached = memoryCache.get(key)
            if (cached != null) {
                updateState(timeMs, cached)
                continue
            }

            if (activeJobs.containsKey(timeMs)) continue

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
        val file = File(cacheDir, "${stableVideoId}_${timeMs}.jpg")

        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) return@withContext bitmap
            } catch (e: Exception) {
                android.util.Log.e("ThumbnailManager", "Error", e)
            }
        }

        poolSemaphore.withPermit {
            val r = retrieverPool.poll() ?: return@withContext null

            if (isResetting) {
                retrieverPool.offer(r)
                return@withContext null
            }

            try {

                val raw = r.getFrameAtTime(
                    timeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
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
                android.util.Log.e("ThumbnailManager", "Error", e)
            } finally {
                retrieverPool.offer(r)
            }
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
        for (job in activeJobs.values) job.cancel()
        activeJobs.clear()
    }

    fun release() {
        clearMemoryCache()
        retrieverPool.forEach { it.release() }
        retrieverPool.clear()
        evictionChannel.close()
        scope.cancel()
    }
}

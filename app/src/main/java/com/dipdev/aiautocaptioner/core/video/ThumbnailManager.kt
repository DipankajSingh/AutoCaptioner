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

    /**
     * Fix B1: LruCache.entryRemoved is called while the cache holds its internal lock.
     * Posting updates to StateFlow via Handler.post can deadlock under contention.
     * We now use a [Channel.trySend] which is non-blocking and safe inside the lock.
     */
    private val evictionChannel = Channel<Long>(Channel.UNLIMITED)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            // Fix B1: trySend is non-blocking — safe to call while holding the LruCache lock
            val timeMs = key.substringAfterLast("_").toLongOrNull() ?: return
            evictionChannel.trySend(timeMs)
        }
    }

    // Active loading jobs, keyed by timestamp
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    // UI-facing thumbnail state
    private val _thumbnails = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, Bitmap>> = _thumbnails.asStateFlow()

    // ── Video source state ───────────────────────────────────────────────────

    private var currentVideoPath: String = ""
    /**
     * Fix B3: stable cache directory ID, computed once per video path.
     * For file:// paths we hash path + lastModified; for content:// URIs we SHA-256 the URI string,
     * since content URI hashCode() is not stable across process restarts.
     */
    private var stableVideoId: String = ""
    private var cacheDir: File? = null

    private val retrieverPool = ConcurrentLinkedQueue<MediaMetadataRetriever>()
    private var targetThumbWidth: Int = -1
    private val targetThumbHeight: Int = 120

    /**
     * Fix B4: volatile flag guards against using a retriever that was released during a concurrent
     * setVideoPath call. Jobs check this flag immediately after polling from the pool.
     */
    @Volatile private var isResetting = false

    init {
        // Fix B1: collect eviction events off the LruCache lock in a dedicated coroutine
        scope.launch {
            evictionChannel.consumeEach { timeMs ->
                _thumbnails.update { current -> current - timeMs }
            }
        }
    }

    fun setVideoPath(videoPath: String) {
        if (currentVideoPath == videoPath) return

        // Fix B4: signal in-flight jobs to discard any polled retrievers
        isResetting = true

        clearMemoryCache()
        retrieverPool.forEach { it.release() }
        retrieverPool.clear()

        currentVideoPath = videoPath

        // Fix B3: compute a stable ID for the disk cache directory
        stableVideoId = computeStableId(videoPath)
        cacheDir = File(context.cacheDir, "thumbnails/$stableVideoId").apply { mkdirs() }
        cleanupOldCacheDirectories()

        try {
            for (i in 0 until 3) {
                val r = MediaMetadataRetriever().apply {
                    if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                        setDataSource(context, videoPath.toUri())
                    } else {
                        setDataSource(videoPath)
                    }
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
            retrieverPool.forEach { it.release() }
            retrieverPool.clear()
        } finally {
            // Fix B4: re-enable job processing after pool is ready
            isResetting = false
        }
    }

    /**
     * Fix B3: Generate a stable, consistent cache directory ID.
     * - File paths: hash(path) + "_" + lastModified (stable across restarts)
     * - Content URIs: SHA-256 of the URI string (stable, since URI string doesn't change for same asset)
     */
    private fun computeStableId(videoPath: String): String {
        val file = File(videoPath)
        return if (file.exists()) {
            "${videoPath.hashCode()}_${file.lastModified()}"
        } else {
            // content:// or other URIs — SHA-256 is stable across restarts
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

    /**
     * The UI calls this with the list of exact timestamps it needs right now.
     * Cancels jobs for timestamps that are no longer needed.
     */
    fun requestThumbnails(timestamps: List<Long>) {
        // Cancel jobs for timestamps no longer in view
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

            // L1: Memory cache hit
            val cached = memoryCache.get(key)
            if (cached != null) {
                updateState(timeMs, cached)
                continue
            }

            // Skip if already loading
            if (activeJobs.containsKey(timeMs)) continue

            // Launch extraction job
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
        // Fix B3: disk filename uses stableVideoId prefix, stable across process restarts
        val file = File(cacheDir, "${stableVideoId}_${timeMs}.jpg")

        // L2: Disk cache hit
        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) return@withContext bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // L3: Decode from video
        poolSemaphore.withPermit {
            val r = retrieverPool.poll() ?: return@withContext null

            // Fix B4: discard retrievers polled during a reset cycle
            if (isResetting) {
                retrieverPool.offer(r)
                return@withContext null
            }

            try {
                /**
                 * Fix B2: OPTION_CLOSEST_SYNC returns the nearest keyframe, avoiding the
                 * costly multi-frame decode path that OPTION_CLOSEST requires. For thumbnails
                 * (preview accuracy is unimportant) this is the correct trade-off.
                 */
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
                e.printStackTrace()
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
        evictionChannel.close()   // Fix B1: shut down the eviction consumer coroutine cleanly
        scope.cancel()
    }
}

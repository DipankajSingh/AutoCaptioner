package com.dipdev.aiautocaptioner.core.whisper

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed class WhisperException(message: String) : Exception(message) {
    class ModelNotFound(path: String) : WhisperException("Model file not found: $path")
    class ModelLoadFailed : WhisperException("Could not load the AI model")
    class NotReady : WhisperException("Model is not loaded")
}

class WhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperEngine"

        init {
            System.loadLibrary("whisper-lib")
        }
    }

    // Opaque 64-bit handle returned by loadModel() in jni_bridge.cpp.
    // 0L means no model is loaded.  Using @Volatile ensures the write is
    // visible across threads without full synchronisation overhead for reads.
    @Volatile private var nativeHandle: Long = 0L

    // Set after transcription when language = "auto".  Read by TranscriptionManager
    // to surface the detected language to the UI.
    @Volatile var lastDetectedLanguage: String? = null
        private set

    // Mutex guards load / unload / transcribe so they never interleave.
    // This is the Kotlin complement to removing the unprotected static g_ctx.
    private val engineMutex = Mutex()

    // -------------------------------------------------------
    // JNI function declarations — signatures mirror jni_bridge.cpp exactly.
    // loadModel now returns a Long (the native pointer cast to jlong).
    // All other functions that operate on the context now accept that handle.
    // -------------------------------------------------------
    private external fun loadModel(modelPath: String): Long
    private external fun transcribe(handle: Long, audioData: FloatArray, language: String, translateToEnglish: Boolean, nThreads: Int, initialPrompt: String? = null, listener: ProgressListener? = null): ByteArray?
    private external fun isModelLoaded(handle: Long): Boolean
    private external fun freeModel(handle: Long)
    private external fun getDetectedLanguage(handle: Long): String?
    private external fun transcribeWithTimestamps(
        handle: Long,
        audioData: FloatArray,
        language: String,
        translateToEnglish: Boolean,
        nThreads: Int,
        initialPrompt: String? = null,
        listener: ProgressListener? = null,
        segmentListener: SegmentListener? = null
    ): ByteArray?

    @Keep
    fun interface ProgressListener {
        @Keep
        fun onProgress(progress: Int)
    }

    @Keep
    fun interface SegmentListener {
        @Keep
        fun onSegment(textBytes: ByteArray, startMs: Long, endMs: Long)
    }

    // -------------------------------------------------------
    // Public API
    // -------------------------------------------------------

    /**
     * Call this once before transcribing.
     * modelFile must be in internal storage (not assets).
     * Returns true if model loaded successfully.
     *
     * If a model is already loaded it is released first, making it safe to
     * call initialize() again when the user switches models.
     */
    suspend fun initialize(modelFile: File) {
        withContext(Dispatchers.IO) {
            engineMutex.withLock {
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file does not exist")
                    throw WhisperException.ModelNotFound(modelFile.name)
                }

                if (nativeHandle != 0L) {
                    freeModel(nativeHandle)
                    nativeHandle = 0L
                }

                val handle = loadModel(modelFile.absolutePath)
                if (handle == 0L) {
                    Log.e(TAG, "Failed to initialise model")
                    throw WhisperException.ModelLoadFailed()
                }
                nativeHandle = handle
            }
        }
    }

    /**
     * Transcribe raw 16 kHz mono float32 audio samples.
     * language → "en", "hi", "auto", etc.
     */
    suspend fun transcribeAudio(
        samples: FloatArray,
        language: String = "en",
        translateToEnglish: Boolean = false,
        initialPrompt: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                val handle = nativeHandle
                if (handle == 0L) {
                    Log.e(TAG, "Cannot transcribe — model not loaded")
                    return@withContext ""
                }
                val listener = onProgress?.let { ProgressListener { progress -> it(progress) } }
                val resultBytes = transcribe(handle, samples, language, translateToEnglish, getOptimalThreads(), initialPrompt, listener)
                lastDetectedLanguage = getDetectedLanguage(handle)
                if (resultBytes != null) String(resultBytes, Charsets.UTF_8) else ""
            }
        }
    }

    /**
     * Transcribe with per-word timestamps. Returns empty list if model not loaded.
     */
    suspend fun transcribeWithWordTimestamps(
        samples: FloatArray,
        language: String = "en",
        translateToEnglish: Boolean = false,
        initialPrompt: String? = null,
        onProgress: ((Int) -> Unit)? = null,
        onSegmentDecoded: ((text: String, startMs: Long, endMs: Long) -> Unit)? = null
    ): List<WordTimestamp> {
        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                val handle = nativeHandle
                if (handle == 0L) return@withContext emptyList()
                val listener = onProgress?.let { ProgressListener { progress -> it(progress) } }
                val segListener = onSegmentDecoded?.let { cb -> SegmentListener { textBytes, startMs, endMs -> cb(String(textBytes, Charsets.UTF_8), startMs, endMs) } }
                val rawBytes = transcribeWithTimestamps(handle, samples, language, translateToEnglish, getOptimalThreads(), initialPrompt, listener, segListener)
                    ?: return@withContext emptyList()

                // Capture the language whisper actually used (matters when language = "auto")
                lastDetectedLanguage = getDetectedLanguage(handle)
                
                val rawString = String(rawBytes, Charsets.UTF_8)
                val entries = rawString.split("\n")
                
                entries.mapNotNull { entry ->
                    if (entry.isBlank()) return@mapNotNull null
                    val parts = entry.split("\t")
                    if (parts.size != 4) return@mapNotNull null
                    WordTimestamp(
                        word = parts[0].trim(),
                        startTimeMs = parts[1].toLongOrNull() ?: return@mapNotNull null,
                        endTimeMs = parts[2].toLongOrNull() ?: return@mapNotNull null,
                        confidence = parts[3].toFloatOrNull() ?: 1.0f
                    )
                }.filter { it.word.trim().isNotBlank() && !it.word.startsWith("[") }
            }
        }
    }

    data class WordTimestamp(
        val word: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val confidence: Float
    )

    /**
     * Returns true if a model is currently loaded and ready to transcribe.
     * Uses the Kotlin-side handle so no JNI round-trip is needed.
     */
    fun isReady(): Boolean = nativeHandle != 0L

    /**
     * Compute optimal thread count based on available cores and current thermal state.
     * On ARM big.LITTLE chips, scheduling across all cores bottlenecks to the
     * slowest core and causes thermal throttling.  When the device is already hot,
     * we reduce threads further to prevent the OS from aggressively throttling.
     */
    private fun getOptimalThreads(): Int {
        val maxThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return maxThreads

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return maxThreads
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE     -> maxThreads
            PowerManager.THERMAL_STATUS_LIGHT    -> (maxThreads - 1).coerceAtLeast(1)
            PowerManager.THERMAL_STATUS_MODERATE -> (maxThreads / 2).coerceAtLeast(1)
            else                                 -> 1  // SEVERE, CRITICAL, EMERGENCY, SHUTDOWN
        }
    }

    /**
     * Releases the native whisper context and frees model memory (75–466 MB).
     * Call this immediately after transcription is complete — do not wait for
     * onDestroy.  The model will be reloaded on the next initialize() call.
     */
    suspend fun release() {
        withContext(Dispatchers.IO) {
            engineMutex.withLock {
                val handle = nativeHandle
                if (handle != 0L) {
                    // Zero out the handle before calling freeModel so any concurrent
                    // isReady() check sees "not loaded" immediately.
                    nativeHandle = 0L
                    freeModel(handle)
                }
            }
        }
    }


}
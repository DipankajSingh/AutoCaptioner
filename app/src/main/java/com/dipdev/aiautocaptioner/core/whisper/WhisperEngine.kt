package com.dipdev.aiautocaptioner.core.whisper

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class WhisperEngine(@Suppress("UNUSED_PARAMETER") context: Context) {

    companion object {
        private const val TAG = "WhisperEngine"

        // Cap at 4 — on ARM big.LITTLE chips (Snapdragon, Dimensity, Exynos)
        // scheduling GGML barrier-synchronised matrix ops across efficiency
        // cores bottlenecks throughput to the slowest core and causes thermal
        // throttling.  4 performance cores is the practical sweet spot.
        private val THREAD_COUNT: Int =
            Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

        init {
            System.loadLibrary("whisper-lib")
        }
    }

    // Opaque 64-bit handle returned by loadModel() in jni_bridge.cpp.
    // 0L means no model is loaded.  Using @Volatile ensures the write is
    // visible across threads without full synchronisation overhead for reads.
    @Volatile private var nativeHandle: Long = 0L

    // Mutex guards load / unload / transcribe so they never interleave.
    // This is the Kotlin complement to removing the unprotected static g_ctx.
    private val engineMutex = Mutex()

    // -------------------------------------------------------
    // JNI function declarations — signatures mirror jni_bridge.cpp exactly.
    // loadModel now returns a Long (the native pointer cast to jlong).
    // All other functions that operate on the context now accept that handle.
    // -------------------------------------------------------
    private external fun loadModel(modelPath: String): Long
    private external fun transcribe(handle: Long, audioData: FloatArray, language: String, nThreads: Int, listener: ProgressListener? = null): String
    private external fun isModelLoaded(handle: Long): Boolean
    private external fun freeModel(handle: Long)
    private external fun transcribeWithTimestamps(
        handle: Long,
        audioData: FloatArray,
        language: String,
        nThreads: Int,
        listener: ProgressListener? = null
    ): Array<String>?

    @Keep
    fun interface ProgressListener {
        @Keep
        fun onProgress(progress: Int)
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
    suspend fun initialize(modelFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file does not exist: ${modelFile.absolutePath}")
                    return@withContext false
                }

                // Release any previously loaded model before loading a new one.
                // This is the safe path for model switching in V2.
                if (nativeHandle != 0L) {
                    freeModel(nativeHandle)
                    nativeHandle = 0L
                }

                val handle = loadModel(modelFile.absolutePath)
                return@withContext if (handle != 0L) {
                    nativeHandle = handle
                    true
                } else {
                    Log.e(TAG, "Failed to initialise model")
                    false
                }
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
                val result = transcribe(handle, samples, language, THREAD_COUNT, listener)
                result
            }
        }
    }

    /**
     * Transcribe with per-word timestamps. Returns empty list if model not loaded.
     */
    suspend fun transcribeWithWordTimestamps(
        samples: FloatArray,
        language: String = "en",
        onProgress: ((Int) -> Unit)? = null
    ): List<WordTimestamp> {
        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                val handle = nativeHandle
                if (handle == 0L) return@withContext emptyList()
                val listener = onProgress?.let { ProgressListener { progress -> it(progress) } }
                val raw = transcribeWithTimestamps(handle, samples, language, THREAD_COUNT, listener)
                    ?: return@withContext emptyList()
                raw.mapNotNull { entry ->
                    val parts = entry.split("|")
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
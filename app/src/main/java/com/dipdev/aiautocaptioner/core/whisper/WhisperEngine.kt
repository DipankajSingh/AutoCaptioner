package com.dipdev.aiautocaptioner.core.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperEngine"

        // Determine optimal thread count once — use all available cores, cap at 8
        // to avoid thermal throttling on high-core-count devices.
        private val THREAD_COUNT: Int =
            Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

        init {
            System.loadLibrary("whisper-lib")
        }
    }

    // -------------------------------------------------------
    // JNI functions — signatures must match jni_bridge.cpp exactly.
    // Both transcribe functions now accept nThreads as a parameter.
    // -------------------------------------------------------
    private external fun loadModel(modelPath: String): Boolean
    private external fun transcribe(audioData: FloatArray, language: String, nThreads: Int): String
    private external fun isModelLoaded(): Boolean
    private external fun freeModel()
    private external fun transcribeWithTimestamps(
        audioData: FloatArray,
        language: String,
        nThreads: Int
    ): Array<String>?

    // -------------------------------------------------------
    // Public API
    // -------------------------------------------------------

    /**
     * Call this once before transcribing.
     * modelFile must be in internal storage (not assets).
     * Returns true if model loaded successfully.
     */
    suspend fun initialize(modelFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file does not exist: ${modelFile.absolutePath}")
                return@withContext false
            }
            val success = loadModel(modelFile.absolutePath)
            if (success) {
                Log.i(TAG, "Model initialized successfully (threads=$THREAD_COUNT)")
            } else {
                Log.e(TAG, "Failed to initialize model")
            }
            success
        }
    }

    /**
     * Transcribe raw 16kHz mono float32 audio samples.
     * language → "en", "hi", "auto", etc.
     */
    suspend fun transcribeAudio(
        samples: FloatArray,
        language: String = "en"
    ): String {
        return withContext(Dispatchers.Default) {
            if (!isModelLoaded()) {
                Log.e(TAG, "Cannot transcribe — model not loaded")
                return@withContext ""
            }
            Log.i(TAG, "Transcribing ${samples.size} samples (lang=$language, threads=$THREAD_COUNT)...")
            val result = transcribe(samples, language, THREAD_COUNT)
            Log.i(TAG, "Result: $result")
            result
        }
    }

    /**
     * Transcribe with per-word timestamps. Returns empty list if model not loaded.
     */
    suspend fun transcribeWithWordTimestamps(
        samples: FloatArray,
        language: String = "en"
    ): List<WordTimestamp> {
        return withContext(Dispatchers.Default) {
            if (!isModelLoaded()) return@withContext emptyList()
            val raw = transcribeWithTimestamps(samples, language, THREAD_COUNT)
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

    data class WordTimestamp(
        val word: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val confidence: Float
    )

    /** Check if model is ready to use. */
    fun isReady(): Boolean = isModelLoaded()

    /**
     * Call in onDestroy() or when done transcribing.
     * Frees the model from RAM (~75-466MB depending on model).
     */
    fun release() {
        freeModel()
        Log.i(TAG, "WhisperEngine released")
    }

    /**
     * Copies model from assets folder to internal storage.
     * Call only during first launch / model download.
     */
    suspend fun copyModelFromAssets(assetFileName: String): File {
        return withContext(Dispatchers.IO) {
            val outputFile = File(context.filesDir, assetFileName)
            if (!outputFile.exists()) {
                Log.i(TAG, "Copying model from assets to internal storage...")
                context.assets.open(assetFileName).use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Model copied to: ${outputFile.absolutePath}")
            } else {
                Log.i(TAG, "Model already exists at: ${outputFile.absolutePath}")
            }
            outputFile
        }
    }
}
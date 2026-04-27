package com.dipdev.autocaptioner.core.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperEngine"

        // This must match the library name in CMakeLists.txt
        // add_library(whisper-lib ...) → System.loadLibrary("whisper-lib")
        init {
            System.loadLibrary("whisper-lib")
        }
    }

    // -------------------------------------------------------
    // These 4 functions are implemented in jni_bridge.cpp
    // The names here MUST match the Kotlin class name
    // used in the JNI function names in jni_bridge.cpp
    // -------------------------------------------------------
    private external fun loadModel(modelPath: String): Boolean
    private external fun transcribe(audioData: FloatArray, language: String): String
    private external fun isModelLoaded(): Boolean
    private external fun freeModel()
    private external fun transcribeWithTimestamps(audioData: FloatArray, language: String): Array<String>?
    // -------------------------------------------------------
    // Public API — what the rest of your app calls
    // -------------------------------------------------------

    /**
     * Call this once when app starts.
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
                Log.i(TAG, "Model initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize model")
            }
            success
        }
    }

    /**
     * Transcribe raw 16kHz mono float32 audio samples.
     * Returns the transcribed text string.
     * language → "en", "hi", "auto" etc.
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
            Log.i(TAG, "Transcribing ${samples.size} samples...")
            val result = transcribe(samples, language)
            Log.i(TAG, "Result: $result")
            result
        }
    }

    suspend fun transcribeWithWordTimestamps(
        samples: FloatArray,
        language: String = "en"
    ): List<WordTimestamp> {
        return withContext(Dispatchers.Default) {
            if (!isModelLoaded()) return@withContext emptyList()
            val raw = transcribeWithTimestamps(samples, language) ?: return@withContext emptyList()
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

    /**
     * Check if model is ready to use
     */
    fun isReady(): Boolean = isModelLoaded()

    /**
     * Call this in onDestroy() or when done transcribing.
     * Frees the model from RAM (~150MB for base.en)
     */
    fun release() {
        freeModel()
        Log.i(TAG, "WhisperEngine released")
    }

    /**
     * Copies model from assets folder to internal storage.
     * Call this only during first launch / model download.
     * Internal storage path is where whisper.cpp can read it.
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
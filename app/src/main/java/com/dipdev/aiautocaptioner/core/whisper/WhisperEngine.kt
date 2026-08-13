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
}

class WhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperEngine"

        init {
            System.loadLibrary("whisper-lib")
        }
    }


    @Volatile private var nativeHandle: Long = 0L


    @Volatile var lastDetectedLanguage: String? = null
        private set

    private val engineMutex = Mutex()


    private external fun loadModel(modelPath: String): Long
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


    suspend fun transcribeWithWordTimestamps(
        samples: FloatArray,
        language: String = "en",
        translateToEnglish: Boolean = false,
        initialPrompt: String? = null,
        onProgress: ((Int) -> Unit)? = null,
        onSegmentDecoded: ((text: String, startMs: Long, endMs: Long) -> Unit)? = null
    ): List<WordTimestamp> {
        val whisperLang = WhisperLanguages.whisperCode(language)
        return withContext(Dispatchers.IO) {
            engineMutex.withLock {
                val handle = nativeHandle
                if (handle == 0L) return@withContext emptyList()
                val listener = onProgress?.let { ProgressListener { progress -> it(progress) } }
                val segListener = onSegmentDecoded?.let { cb -> SegmentListener { textBytes, startMs, endMs -> cb(String(textBytes, Charsets.UTF_8), startMs, endMs) } }
                val rawBytes = transcribeWithTimestamps(handle, samples, whisperLang, translateToEnglish, getOptimalThreads(), initialPrompt, listener, segListener)
                    ?: return@withContext emptyList()

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


    fun isReady(): Boolean = nativeHandle != 0L

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


    suspend fun release() {
        withContext(Dispatchers.IO) {
            engineMutex.withLock {
                val handle = nativeHandle
                if (handle != 0L) {
                    nativeHandle = 0L
                    freeModel(handle)
                }
            }
        }
    }


}
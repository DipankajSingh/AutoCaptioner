package com.dipdev.aiautocaptioner.core.benchmark

import android.app.ActivityManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.dipdev.aiautocaptioner.core.audio.AudioExtractionUseCase
import com.dipdev.aiautocaptioner.core.whisper.WhisperEngine
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SingleRunResult(
    val clipName: String,
    val clipDurationMs: Long,
    val iteration: Int,
    val isWarmup: Boolean,
    val extractionTimeMs: Long,
    val whisperTimeMs: Long,
    val rtf: Float,
    val wordCount: Int,
    val transcriptText: String,
    val detectedLanguage: String?,
    val thermalStatusStart: String,
    val thermalStatusEnd: String,
    val availMemMb: Long
)

data class BenchmarkSummary(
    val timestamp: String,
    val deviceModel: String,
    val osVersion: String,
    val config: BenchConfig,
    val runResults: List<SingleRunResult>,
    val jsonPath: String,
    val csvPath: String
)

@Singleton
class BenchmarkRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperEngine: WhisperEngine,
    private val audioExtractionUseCase: AudioExtractionUseCase,
    private val modelRepository: ModelRepository
) {
    companion object {
        private const val TAG = "BenchmarkRunner"
    }

    suspend fun runBenchmark(
        config: BenchConfig = BenchConfig(),
        customCorpusDir: File? = null,
        customOutputDir: File? = null
    ): BenchmarkSummary = withContext(Dispatchers.IO) {
        val corpusDir = customCorpusDir
            ?: File(context.getExternalFilesDir(null), "benchmark/corpus").apply { mkdirs() }
        val outputDir = customOutputDir
            ?: File(context.getExternalFilesDir(null), "benchmark/results").apply { mkdirs() }

        Log.i(TAG, "Starting benchmark harness. Corpus dir: ${corpusDir.absolutePath}")

        val modelFile = modelRepository.getModelFile(config.modelId)
            ?: File(context.getExternalFilesDir(null), "models/${config.modelId}.bin")

        if (!modelFile.exists()) {
            val errorMsg = "Model file not found for id '${config.modelId}' at path: ${modelFile.absolutePath}"
            Log.e(TAG, errorMsg)
            throw IllegalArgumentException(errorMsg)
        }

        if (!whisperEngine.isReady()) {
            Log.i(TAG, "Initializing WhisperEngine with model: ${modelFile.name}")
            whisperEngine.initialize(modelFile)
        }

        val mediaExtensions = setOf("mp4", "m4a", "wav", "mp3", "mkv", "aac", "flac")
        val corpusFiles = corpusDir.listFiles()?.filter { file ->
            file.isFile && file.extension.lowercase() in mediaExtensions
        }?.sortedBy { it.name } ?: emptyList()

        if (corpusFiles.isEmpty()) {
            Log.w(TAG, "No media files found in corpus directory: ${corpusDir.absolutePath}")
        }

        val results = mutableListOf<SingleRunResult>()
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = dateFormat.format(Date())

        for (clipFile in corpusFiles) {
            Log.i(TAG, "Processing corpus clip: ${clipFile.name}")
            val durationMs = getMediaDurationMs(clipFile)

            // Extract Audio
            val extractStart = SystemClock.elapsedRealtime()
            val samples = try {
                audioExtractionUseCase.extractAudioFloatArray(clipFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Audio extraction failed for ${clipFile.name}: ${e.message}", e)
                continue
            }
            val extractDurationMs = SystemClock.elapsedRealtime() - extractStart

            // Warm-up run (discarded)
            Log.i(TAG, "Executing warm-up run for ${clipFile.name}...")
            runSingleTranscription(
                samples = samples,
                config = config,
                clipName = clipFile.name,
                durationMs = durationMs,
                iteration = 0,
                isWarmup = true,
                extractionTimeMs = extractDurationMs
            )

            // Measured runs
            for (i in 1..config.iterations) {
                Log.i(TAG, "Executing measured run $i/${config.iterations} for ${clipFile.name}...")
                val runRes = runSingleTranscription(
                    samples = samples,
                    config = config,
                    clipName = clipFile.name,
                    durationMs = durationMs,
                    iteration = i,
                    isWarmup = false,
                    extractionTimeMs = extractDurationMs
                )
                results.add(runRes)

                // 1 second pause between runs to cool down
                kotlinx.coroutines.delay(1000)
            }
        }

        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

        // Save JSON Report
        val jsonFile = File(outputDir, "results_${timestamp}.json")
        val jsonReport = JSONObject().apply {
            put("timestamp", timestamp)
            put("deviceModel", deviceModel)
            put("osVersion", osVersion)
            put("config", config.toJson())
            val runsArray = JSONArray()
            results.forEach { res ->
                runsArray.put(JSONObject().apply {
                    put("clipName", res.clipName)
                    put("clipDurationMs", res.clipDurationMs)
                    put("iteration", res.iteration)
                    put("extractionTimeMs", res.extractionTimeMs)
                    put("whisperTimeMs", res.whisperTimeMs)
                    put("rtf", res.rtf)
                    put("wordCount", res.wordCount)
                    put("transcriptText", res.transcriptText)
                    put("detectedLanguage", res.detectedLanguage ?: "")
                    put("thermalStatusStart", res.thermalStatusStart)
                    put("thermalStatusEnd", res.thermalStatusEnd)
                    put("availMemMb", res.availMemMb)
                })
            }
            put("runs", runsArray)
        }
        jsonFile.writeText(jsonReport.toString(2))

        // Save CSV Report
        val csvFile = File(outputDir, "results_${timestamp}.csv")
        val csvHeader = "Timestamp,Device,ClipName,DurationMs,Iteration,ExtractMs,WhisperMs,RTF,WordCount,DetectedLang,ThermalStart,ThermalEnd,AvailMemMb\n"
        val csvLines = StringBuilder(csvHeader)
        results.forEach { res ->
            csvLines.append("$timestamp,\"$deviceModel\",\"${res.clipName}\",${res.clipDurationMs},${res.iteration},${res.extractionTimeMs},${res.whisperTimeMs},${res.rtf},${res.wordCount},\"${res.detectedLanguage ?: ""}\",${res.thermalStatusStart},${res.thermalStatusEnd},${res.availMemMb}\n")
        }
        csvFile.writeText(csvLines.toString())

        Log.i(TAG, "Benchmark completed. Results written to: ${jsonFile.absolutePath} and ${csvFile.absolutePath}")

        BenchmarkSummary(
            timestamp = timestamp,
            deviceModel = deviceModel,
            osVersion = osVersion,
            config = config,
            runResults = results,
            jsonPath = jsonFile.absolutePath,
            csvPath = csvFile.absolutePath
        )
    }

    private suspend fun runSingleTranscription(
        samples: FloatArray,
        config: BenchConfig,
        clipName: String,
        durationMs: Long,
        iteration: Int,
        isWarmup: Boolean,
        extractionTimeMs: Long
    ): SingleRunResult {
        val thermalStart = getThermalStatus()
        val memMb = getAvailableMemoryMb()

        val whisperStart = SystemClock.elapsedRealtime()
        val words = whisperEngine.transcribeWithWordTimestamps(
            samples = samples,
            language = config.language,
            translateToEnglish = config.translateToEnglish
        )
        val whisperDurationMs = SystemClock.elapsedRealtime() - whisperStart
        val thermalEnd = getThermalStatus()

        val rtf = if (durationMs > 0) whisperDurationMs.toFloat() / durationMs.toFloat() else 0f
        val fullText = words.joinToString(" ") { it.word }

        return SingleRunResult(
            clipName = clipName,
            clipDurationMs = durationMs,
            iteration = iteration,
            isWarmup = isWarmup,
            extractionTimeMs = extractionTimeMs,
            whisperTimeMs = whisperDurationMs,
            rtf = rtf,
            wordCount = words.size,
            transcriptText = fullText,
            detectedLanguage = whisperEngine.lastDetectedLanguage,
            thermalStatusStart = thermalStart,
            thermalStatusEnd = thermalEnd,
            availMemMb = memMb
        )
    }

    private fun getMediaDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            timeStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun getThermalStatus(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "NONE"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "UNKNOWN"
            }
        }
        return "UNSUPPORTED"
    }

    private fun getAvailableMemoryMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return -1L
        val memoryInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / (1024 * 1024)
    }
}

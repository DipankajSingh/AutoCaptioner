package com.dipdev.aiautocaptioner.core.diagnostics

import org.json.JSONObject
import java.util.UUID

/**
 * Data class capturing comprehensive telemetry and diagnostic metrics for a single transcription run (T1).
 */
data class TranscriptionRunRecord(
    val runId: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val appVersion: String = "",
    val modelId: String = "",
    val languageCode: String = "",
    val translateToEnglish: Boolean = false,
    val isFirstRun: Boolean = false,
    val isRegenerating: Boolean = false,

    // Input Audio Properties
    val clipDurationMs: Long = 0L,
    val sourceSampleRate: Int = 0,
    val channelCount: Int = 0,
    val codecMime: String = "",
    val resampledSampleCount: Long = 0L,
    val windowCount: Int = 0,

    // Stage Timings (ms)
    val modelDownloadDurationMs: Long = 0L,
    val modelLoadDurationMs: Long = 0L,
    val extractionDurationMs: Long = 0L,
    val whisperWallDurationMs: Long = 0L,
    val saveDurationMs: Long = 0L,
    val totalDurationMs: Long = 0L,

    // Whisper Native Internals (ms)
    val sampleMs: Float = 0f,
    val encodeMs: Float = 0f,
    val decodeMs: Float = 0f,
    val fallbackCountP: Int = 0,
    val fallbackCountH: Int = 0,
    val wordCount: Int = 0,

    // Resource & Environment Metrics
    val threadsRequested: Int = 0,
    val thermalStatusStart: String = "UNKNOWN",
    val thermalStatusEnd: String = "UNKNOWN",
    val thermalStatusMax: String = "UNKNOWN",
    val isPowerSaveMode: Boolean = false,
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val availMemMbStart: Long = -1L,
    val isLowRamDevice: Boolean = false,
    val nativeHeapSizeMbEnd: Long = -1L,

    // Outcome
    val outcome: String = "SUCCESS", // SUCCESS, CANCELLED, ERROR
    val errorType: String = "",

    // System Info
    val cpuVariant: String = "baseline",
    val cpuFeatures: String = "",
    val deviceManufacturer: String = "",
    val deviceModel: String = "",
    val sdkInt: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("runId", runId)
            put("timestampMs", timestampMs)
            put("appVersion", appVersion)
            put("modelId", modelId)
            put("languageCode", languageCode)
            put("translateToEnglish", translateToEnglish)
            put("isFirstRun", isFirstRun)
            put("isRegenerating", isRegenerating)

            put("clipDurationMs", clipDurationMs)
            put("sourceSampleRate", sourceSampleRate)
            put("channelCount", channelCount)
            put("codecMime", codecMime)
            put("resampledSampleCount", resampledSampleCount)
            put("windowCount", windowCount)

            put("modelDownloadDurationMs", modelDownloadDurationMs)
            put("modelLoadDurationMs", modelLoadDurationMs)
            put("extractionDurationMs", extractionDurationMs)
            put("whisperWallDurationMs", whisperWallDurationMs)
            put("saveDurationMs", saveDurationMs)
            put("totalDurationMs", totalDurationMs)

            put("sampleMs", sampleMs.toDouble())
            put("encodeMs", encodeMs.toDouble())
            put("decodeMs", decodeMs.toDouble())
            put("fallbackCountP", fallbackCountP)
            put("fallbackCountH", fallbackCountH)
            put("wordCount", wordCount)

            put("threadsRequested", threadsRequested)
            put("thermalStatusStart", thermalStatusStart)
            put("thermalStatusEnd", thermalStatusEnd)
            put("thermalStatusMax", thermalStatusMax)
            put("isPowerSaveMode", isPowerSaveMode)
            put("batteryLevel", batteryLevel)
            put("isCharging", isCharging)
            put("availMemMbStart", availMemMbStart)
            put("isLowRamDevice", isLowRamDevice)
            put("nativeHeapSizeMbEnd", nativeHeapSizeMbEnd)

            put("outcome", outcome)
            put("errorType", errorType)

            put("cpuVariant", cpuVariant)
            put("cpuFeatures", cpuFeatures)
            put("deviceManufacturer", deviceManufacturer)
            put("deviceModel", deviceModel)
            put("sdkInt", sdkInt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TranscriptionRunRecord {
            return TranscriptionRunRecord(
                runId = json.optString("runId", ""),
                timestampMs = json.optLong("timestampMs", 0L),
                appVersion = json.optString("appVersion", ""),
                modelId = json.optString("modelId", ""),
                languageCode = json.optString("languageCode", ""),
                translateToEnglish = json.optBoolean("translateToEnglish", false),
                isFirstRun = json.optBoolean("isFirstRun", false),
                isRegenerating = json.optBoolean("isRegenerating", false),

                clipDurationMs = json.optLong("clipDurationMs", 0L),
                sourceSampleRate = json.optInt("sourceSampleRate", 0),
                channelCount = json.optInt("channelCount", 0),
                codecMime = json.optString("codecMime", ""),
                resampledSampleCount = json.optLong("resampledSampleCount", 0L),
                windowCount = json.optInt("windowCount", 0),

                modelDownloadDurationMs = json.optLong("modelDownloadDurationMs", 0L),
                modelLoadDurationMs = json.optLong("modelLoadDurationMs", 0L),
                extractionDurationMs = json.optLong("extractionDurationMs", 0L),
                whisperWallDurationMs = json.optLong("whisperWallDurationMs", 0L),
                saveDurationMs = json.optLong("saveDurationMs", 0L),
                totalDurationMs = json.optLong("totalDurationMs", 0L),

                sampleMs = json.optDouble("sampleMs", 0.0).toFloat(),
                encodeMs = json.optDouble("encodeMs", 0.0).toFloat(),
                decodeMs = json.optDouble("decodeMs", 0.0).toFloat(),
                fallbackCountP = json.optInt("fallbackCountP", 0),
                fallbackCountH = json.optInt("fallbackCountH", 0),
                wordCount = json.optInt("wordCount", 0),

                threadsRequested = json.optInt("threadsRequested", 0),
                thermalStatusStart = json.optString("thermalStatusStart", "UNKNOWN"),
                thermalStatusEnd = json.optString("thermalStatusEnd", "UNKNOWN"),
                thermalStatusMax = json.optString("thermalStatusMax", "UNKNOWN"),
                isPowerSaveMode = json.optBoolean("isPowerSaveMode", false),
                batteryLevel = json.optInt("batteryLevel", -1),
                isCharging = json.optBoolean("isCharging", false),
                availMemMbStart = json.optLong("availMemMbStart", -1L),
                isLowRamDevice = json.optBoolean("isLowRamDevice", false),
                nativeHeapSizeMbEnd = json.optLong("nativeHeapSizeMbEnd", -1L),

                outcome = json.optString("outcome", "SUCCESS"),
                errorType = json.optString("errorType", ""),

                cpuVariant = json.optString("cpuVariant", "baseline"),
                cpuFeatures = json.optString("cpuFeatures", ""),
                deviceManufacturer = json.optString("deviceManufacturer", ""),
                deviceModel = json.optString("deviceModel", ""),
                sdkInt = json.optInt("sdkInt", 0)
            )
        }
    }
}

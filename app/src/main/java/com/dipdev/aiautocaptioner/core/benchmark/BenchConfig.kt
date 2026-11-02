package com.dipdev.aiautocaptioner.core.benchmark

import org.json.JSONObject

/**
 * Benchmark configuration for running performance and accuracy tests on Whisper transcription.
 */
data class BenchConfig(
    val modelId: String = "tiny.en",
    val threads: Int = 0,
    val flashAttention: Boolean = true,
    val fallbackPolicy: String = "default",
    val vadEnabled: Boolean = false,
    val resamplerV2: Boolean = false,
    val normalize: Boolean = false,
    val audioCtx: Int = 0,
    val iterations: Int = 3,
    val language: String = "en",
    val translateToEnglish: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("modelId", modelId)
            put("threads", threads)
            put("flashAttention", flashAttention)
            put("fallbackPolicy", fallbackPolicy)
            put("vadEnabled", vadEnabled)
            put("resamplerV2", resamplerV2)
            put("normalize", normalize)
            put("audioCtx", audioCtx)
            put("iterations", iterations)
            put("language", language)
            put("translateToEnglish", translateToEnglish)
        }
    }

    companion object {
        fun fromJson(jsonStr: String): BenchConfig {
            val json = JSONObject(jsonStr)
            return BenchConfig(
                modelId = json.optString("modelId", "tiny.en"),
                threads = json.optInt("threads", 0),
                flashAttention = json.optBoolean("flashAttention", true),
                fallbackPolicy = json.optString("fallbackPolicy", "default"),
                vadEnabled = json.optBoolean("vadEnabled", false),
                resamplerV2 = json.optBoolean("resamplerV2", false),
                normalize = json.optBoolean("normalize", false),
                audioCtx = json.optInt("audioCtx", 0),
                iterations = json.optInt("iterations", 3),
                language = json.optString("language", "en"),
                translateToEnglish = json.optBoolean("translateToEnglish", false)
            )
        }
    }
}

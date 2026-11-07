package com.dipdev.aiautocaptioner.core.diagnostics

import android.content.Context
import android.util.Log
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TxAnalytics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "TxAnalytics"
    }

    /**
     * Logs transcription run metrics (event: `tx_run`).
     * Never throws exceptions; capped at <= 25 parameters.
     */
    fun logRun(record: TranscriptionRunRecord) {
        try {
            Firebase.analytics.logEvent("tx_run") {
                param("run_id", record.runId.take(100))
                param("model_id", record.modelId.take(100))
                param("language_code", record.languageCode.take(100))
                param("translate", if (record.translateToEnglish) 1L else 0L)
                param("clip_duration_ms", record.clipDurationMs)
                param("window_count", record.windowCount.toLong())
                param("model_download_ms", record.modelDownloadDurationMs)
                param("model_load_ms", record.modelLoadDurationMs)
                param("extraction_ms", record.extractionDurationMs)
                param("whisper_wall_ms", record.whisperWallDurationMs)
                param("save_ms", record.saveDurationMs)
                param("total_ms", record.totalDurationMs)
                param("sample_ms", record.sampleMs.toDouble())
                param("encode_ms", record.encodeMs.toDouble())
                param("decode_ms", record.decodeMs.toDouble())
                param("fallback_count_p", record.fallbackCountP.toLong())
                param("fallback_count_h", record.fallbackCountH.toLong())
                param("word_count", record.wordCount.toLong())
                param("threads_requested", record.threadsRequested.toLong())
                param("thermal_max", record.thermalStatusMax.take(100))
                param("battery_level", record.batteryLevel.toLong())
                param("outcome", record.outcome.take(100))
                param("error_type", record.errorType.take(100))
                param("cpu_variant", record.cpuVariant.take(100))
                param("sdk_int", record.sdkInt.toLong())
            }
        } catch (t: Throwable) {
            try { Log.w(TAG, "Failed to log tx_run event", t) } catch (_: Throwable) {}
        }
    }

    /**
     * Logs device CPU & RAM hardware metrics (event: `tx_device`).
     * Logged once per install / app version.
     */
    fun logDevice(cpuVariant: String, cpuFeatures: String, totalRamMb: Int) {
        try {
            Firebase.analytics.logEvent("tx_device") {
                param("cpu_variant", cpuVariant.take(100))
                param("cpu_features", cpuFeatures.take(100))
                param("total_ram_mb", totalRamMb.toLong())
                param("device_manufacturer", android.os.Build.MANUFACTURER.take(100))
                param("device_model", android.os.Build.MODEL.take(100))
                param("sdk_int", android.os.Build.VERSION.SDK_INT.toLong())
            }
        } catch (t: Throwable) {
            try { Log.w(TAG, "Failed to log tx_device event", t) } catch (_: Throwable) {}
        }
    }

    /**
     * Logs model download performance (event: `model_download`).
     */
    fun logModelDownload(modelId: String, sizeMb: Long, durationMs: Long, networkType: String, outcome: String) {
        try {
            Firebase.analytics.logEvent("model_download") {
                param("model_id", modelId.take(100))
                param("size_mb", sizeMb)
                param("duration_ms", durationMs)
                param("network_type", networkType.take(100))
                param("outcome", outcome.take(100))
            }
        } catch (t: Throwable) {
            try { Log.w(TAG, "Failed to log model_download event", t) } catch (_: Throwable) {}
        }
    }
}

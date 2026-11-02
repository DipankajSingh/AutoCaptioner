package com.dipdev.aiautocaptioner.core.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BenchmarkReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BenchmarkReceiver"
        const val ACTION_RUN_BENCHMARK = "com.dipdev.aiautocaptioner.RUN_BENCHMARK"
    }

    @Inject
    lateinit var benchmarkRunner: BenchmarkRunner

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_BENCHMARK) return

        val modelId = intent.getStringExtra("model") ?: "tiny.en"
        val threads = intent.getIntExtra("threads", 0)
        val flashAttention = intent.getBooleanExtra("flashAttention", true)
        val fallbackPolicy = intent.getStringExtra("fallbackPolicy") ?: "default"
        val vadEnabled = intent.getBooleanExtra("vadEnabled", false)
        val resamplerV2 = intent.getBooleanExtra("resamplerV2", false)
        val normalize = intent.getBooleanExtra("normalize", false)
        val audioCtx = intent.getIntExtra("audioCtx", 0)
        val iterations = intent.getIntExtra("iterations", 3)
        val language = intent.getStringExtra("language") ?: "en"
        val translateToEnglish = intent.getBooleanExtra("translateToEnglish", false)

        val config = BenchConfig(
            modelId = modelId,
            threads = threads,
            flashAttention = flashAttention,
            fallbackPolicy = fallbackPolicy,
            vadEnabled = vadEnabled,
            resamplerV2 = resamplerV2,
            normalize = normalize,
            audioCtx = audioCtx,
            iterations = iterations,
            language = language,
            translateToEnglish = translateToEnglish
        )

        Log.i(TAG, "Received benchmark broadcast trigger with config: $config")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val summary = benchmarkRunner.runBenchmark(config)
                Log.i(TAG, "Benchmark execution complete. Results saved to: ${summary.jsonPath}")
            } catch (e: Exception) {
                Log.e(TAG, "Benchmark execution failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

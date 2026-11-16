package com.dipdev.aiautocaptioner.core.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class NormalizationMode {
    OFF,
    PEAK_0_9,
    RMS_TARGET
}

data class PreprocessorConfig(
    val normalizationMode: NormalizationMode = NormalizationMode.OFF,
    val rmsTargetDbfs: Float = -20f,
    val maxGainCapDb: Float = 20f,
    val highPass80Hz: Boolean = false
)

/**
 * Audio preprocessor for 16 kHz mono PCM signals.
 * Implements level normalization (E4.3) and high-pass filtering (E4.4).
 */
@Singleton
class AudioPreprocessor @Inject constructor() {

    /**
     * Applies enabled preprocessing stages to 16 kHz mono audio samples.
     */
    fun process(samples: FloatArray, config: PreprocessorConfig = PreprocessorConfig()): FloatArray {
        if (samples.isEmpty()) return samples

        var current = samples

        // Stage 4: High-Pass Filter (applied first so rumble does not bias normalization)
        if (config.highPass80Hz) {
            current = applyHighPass80Hz(current)
        }

        // Stage 3: Level Normalization
        if (config.normalizationMode != NormalizationMode.OFF) {
            current = applyNormalization(current, config)
        }

        return current
    }

    private fun applyNormalization(samples: FloatArray, config: PreprocessorConfig): FloatArray {
        return when (config.normalizationMode) {
            NormalizationMode.OFF -> samples
            NormalizationMode.PEAK_0_9 -> normalizePeak(
                samples = samples,
                targetPeak = 0.9f,
                maxGainCapDb = config.maxGainCapDb
            )
            NormalizationMode.RMS_TARGET -> normalizeRms(
                samples = samples,
                targetDbfs = config.rmsTargetDbfs,
                maxGainCapDb = config.maxGainCapDb
            )
        }
    }

    /**
     * Peak normalization: scales maximum absolute sample value to targetPeak (default 0.9),
     * subject to a maximum gain cap to avoid amplifying pure silence/noise.
     */
    fun normalizePeak(samples: FloatArray, targetPeak: Float = 0.9f, maxGainCapDb: Float = 20f): FloatArray {
        var currentPeak = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > currentPeak) currentPeak = a
        }

        // If essentially silence (< -100 dBFS), do not amplify
        if (currentPeak < 1e-5f) return samples

        val desiredGain = targetPeak / currentPeak
        val maxGain = 10.0.pow(maxGainCapDb / 20.0).toFloat()
        val gain = min(desiredGain, maxGain)

        // If gain is essentially 1.0 (less than 0.1 dB change), return as is
        if (abs(gain - 1.0f) < 0.01f) return samples

        val result = FloatArray(samples.size)
        for (i in samples.indices) {
            result[i] = (samples[i] * gain).coerceIn(-1.0f, 1.0f)
        }
        return result
    }

    /**
     * RMS normalization: scales signal RMS energy to targetDbfs (e.g. -20 dBFS),
     * subject to maxGainCapDb and a peak limiter (0.95 headroom) to prevent digital clipping.
     */
    fun normalizeRms(samples: FloatArray, targetDbfs: Float = -20f, maxGainCapDb: Float = 20f): FloatArray {
        var sumSq = 0.0
        for (s in samples) {
            sumSq += s * s
        }
        val rms = sqrt(sumSq / samples.size).toFloat()

        // If noise floor / silence (< -80 dBFS), do not amplify pure background noise
        if (rms < 1e-4f) return samples

        val targetLinear = 10.0.pow(targetDbfs / 20.0).toFloat()
        val desiredGain = targetLinear / rms
        val maxGain = 10.0.pow(maxGainCapDb / 20.0).toFloat()
        val gain = min(desiredGain, maxGain)

        val result = FloatArray(samples.size)
        var maxPeak = 0f
        for (i in samples.indices) {
            val scaled = samples[i] * gain
            result[i] = scaled
            val a = abs(scaled)
            if (a > maxPeak) maxPeak = a
        }

        // Peak limiter: if boosting causes peaks to exceed 0.95, apply headroom scaling
        if (maxPeak > 0.95f) {
            val limiterScale = 0.95f / maxPeak
            for (i in result.indices) {
                result[i] *= limiterScale
            }
        }

        return result
    }

    internal fun applyHighPass80Hz(samples: FloatArray): FloatArray {
        // Placeholder for Stage 4 (E4.4)
        return samples
    }
}

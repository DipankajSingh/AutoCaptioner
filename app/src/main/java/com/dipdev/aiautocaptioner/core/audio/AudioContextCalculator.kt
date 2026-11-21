package com.dipdev.aiautocaptioner.core.audio

/**
 * Calculates reduced audio context size for clips <= 30 seconds (E4.6).
 * Whisper encoder processes 50 positions per audio second (20 ms / position).
 * Context size is rounded up to the nearest multiple of 32 for optimal SIMD/memory alignment.
 * Returns 0 (default 1500) for clips >= 30 seconds or invalid sample counts.
 */
object AudioContextCalculator {

    fun calculate(sampleCount: Int, sampleRate: Int = 16000): Int {
        val durationSeconds = sampleCount.toFloat() / sampleRate
        if (durationSeconds <= 0f || durationSeconds >= 30f) return 0
        val rawPositions = (durationSeconds * 50).toInt()
        val safeMultiple = 32
        val rounded = ((rawPositions + safeMultiple - 1) / safeMultiple) * safeMultiple
        return rounded.coerceIn(32, 1500)
    }
}

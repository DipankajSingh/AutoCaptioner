package com.dipdev.aiautocaptioner.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class AudioPreprocessorTest {

    private val preprocessor = AudioPreprocessor()

    @Test
    fun `process with default config passes samples through unchanged`() {
        val input = floatArrayOf(0.1f, -0.5f, 0.3f, 0f)
        val output = preprocessor.process(input, PreprocessorConfig())
        assertEquals(input.size, output.size)
        for (i in input.indices) {
            assertEquals(input[i], output[i], 1e-6f)
        }
    }

    @Test
    fun `process with empty array returns empty array safely`() {
        val empty = FloatArray(0)
        val output = preprocessor.process(empty, PreprocessorConfig(normalizationMode = NormalizationMode.PEAK_0_9))
        assertEquals(0, output.size)
    }

    @Test
    fun `normalizePeak scales quiet tone to target peak of 0_9`() {
        val sampleRate = 16000
        val count = 1600 // 0.1s
        // Sine wave with peak amplitude 0.09 (-21 dBFS)
        val input = FloatArray(count) { i ->
            0.09f * sin(2.0 * PI * 400.0 * i / sampleRate).toFloat()
        }

        val output = preprocessor.normalizePeak(input, targetPeak = 0.9f, maxGainCapDb = 20f)
        var maxPeak = 0f
        for (s in output) {
            val a = abs(s)
            if (a > maxPeak) maxPeak = a
        }

        assertEquals("Peak amplitude should be scaled to 0.9", 0.9f, maxPeak, 1e-2f)
    }

    @Test
    fun `normalizePeak respects max gain cap on very quiet audio`() {
        val sampleRate = 16000
        val count = 1600
        // Very quiet signal with peak 0.001 (-60 dBFS)
        val input = FloatArray(count) { i ->
            0.001f * sin(2.0 * PI * 400.0 * i / sampleRate).toFloat()
        }

        // With a 20 dB gain cap, maximum amplification is 10x
        val output = preprocessor.normalizePeak(input, targetPeak = 0.9f, maxGainCapDb = 20f)
        var maxPeak = 0f
        for (s in output) {
            val a = abs(s)
            if (a > maxPeak) maxPeak = a
        }

        // Expected: 0.001 * 10 = 0.01 (gain cap prevents 900x amplification)
        assertEquals("Peak should be capped at 10x gain", 0.01f, maxPeak, 1e-3f)
    }

    @Test
    fun `normalizePeak scales down overly loud near-clipping audio to 0_9`() {
        val sampleRate = 16000
        val count = 1600
        // Full scale signal (peak 1.0)
        val input = FloatArray(count) { i ->
            sin(2.0 * PI * 400.0 * i / sampleRate).toFloat()
        }

        val output = preprocessor.normalizePeak(input, targetPeak = 0.9f, maxGainCapDb = 20f)
        var maxPeak = 0f
        for (s in output) {
            val a = abs(s)
            if (a > maxPeak) maxPeak = a
        }

        assertEquals("Peak amplitude should be attenuated to 0.9", 0.9f, maxPeak, 1e-2f)
    }

    @Test
    fun `normalizePeak handles pure silence without division by zero or NaN`() {
        val silence = FloatArray(1000) { 0f }
        val output = preprocessor.normalizePeak(silence, targetPeak = 0.9f)
        for (s in output) {
            assertEquals(0f, s, 1e-6f)
            assertTrue("Sample must not be NaN or Infinite", !s.isNaN() && !s.isInfinite())
        }
    }

    @Test
    fun `normalizeRms scales signal towards target RMS while enforcing peak limiter headroom`() {
        val sampleRate = 16000
        val count = 16000 // 1.0s
        // Sine wave with initial RMS = 0.05 / sqrt(2) ~= 0.0353 (-29 dBFS)
        val input = FloatArray(count) { i ->
            0.05f * sin(2.0 * PI * 500.0 * i / sampleRate).toFloat()
        }

        // Target: -20 dBFS (linear target = 0.10)
        val output = preprocessor.normalizeRms(input, targetDbfs = -20f, maxGainCapDb = 20f)

        var sumSq = 0.0
        var maxPeak = 0f
        for (s in output) {
            sumSq += s * s
            val a = abs(s)
            if (a > maxPeak) maxPeak = a
        }
        val outputRms = sqrt(sumSq / output.size).toFloat()
        val measuredDbfs = 20.0 * log10(outputRms.toDouble())

        // Target is -20 dBFS, verify measured RMS is close to -20 dBFS
        assertEquals("Measured RMS should match target -20 dBFS", -20.0, measuredDbfs, 0.5)
        assertTrue("Peak must not exceed limiter headroom (0.95)", maxPeak <= 0.95f)
    }

    @Test
    fun `normalizeRms handles pure silence safely`() {
        val silence = FloatArray(500) { 0f }
        val output = preprocessor.normalizeRms(silence, targetDbfs = -20f)
        for (s in output) {
            assertEquals(0f, s, 1e-6f)
            assertTrue("Sample must not be NaN or Infinite", !s.isNaN() && !s.isInfinite())
        }
    }
}

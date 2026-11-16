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

    @Test
    fun `applyHighPass80Hz attenuates 30 Hz sub-audible rumble by at least 15 dB`() {
        val sampleRate = 16000
        val durationSec = 1.0
        val count = (sampleRate * durationSec).toInt()

        // 30 Hz rumble tone at full scale (amplitude 1.0 = 0 dBFS)
        val input = FloatArray(count) { i ->
            sin(2.0 * PI * 30.0 * i / sampleRate).toFloat()
        }

        val output = preprocessor.applyHighPass80Hz(input, sampleRate)

        // Exclude initial filter settling transient (first 200 samples)
        val steadyState = output.copyOfRange(200, count)
        var maxPeak = 0f
        for (s in steadyState) {
            val a = abs(s)
            if (a > maxPeak) maxPeak = a
        }

        val measuredAttenDbfs = 20.0 * log10(maxPeak.toDouble())
        // Theoretical at 30 Hz is ~ -17 dBFS
        assertTrue(
            "30 Hz rumble should be attenuated by >= 15 dB (measured: $measuredAttenDbfs dBFS, peak: $maxPeak)",
            measuredAttenDbfs <= -15.0
        )
    }

    @Test
    fun `applyHighPass80Hz preserves 1000 Hz speech frequency with less than 0_1 dB loss`() {
        val sampleRate = 16000
        val durationSec = 0.5
        val count = (sampleRate * durationSec).toInt()
        val amplitude = 0.707f

        // 1000 Hz speech frequency
        val input = FloatArray(count) { i ->
            amplitude * sin(2.0 * PI * 1000.0 * i / sampleRate).toFloat()
        }

        val output = preprocessor.applyHighPass80Hz(input, sampleRate)

        // Exclude initial filter transient
        val steadyInput = input.copyOfRange(100, count)
        val steadyOutput = output.copyOfRange(100, count)

        var sumSqIn = 0.0
        for (s in steadyInput) sumSqIn += s * s
        val rmsIn = sqrt(sumSqIn / steadyInput.size)

        var sumSqOut = 0.0
        for (s in steadyOutput) sumSqOut += s * s
        val rmsOut = sqrt(sumSqOut / steadyOutput.size)

        val deltaDb = abs(20.0 * log10(rmsOut / rmsIn))
        assertTrue(
            "1000 Hz passband loss must be < 0.1 dB (measured delta: $deltaDb dB)",
            deltaDb < 0.1
        )
    }

    @Test
    fun `process removes low frequency rumble before level normalization`() {
        val sampleRate = 16000
        val count = 16000 // 1.0s

        // Signal combining loud 25 Hz rumble (amplitude 0.8) + quiet 1000 Hz speech (amplitude 0.08)
        val input = FloatArray(count) { i ->
            0.8f * sin(2.0 * PI * 25.0 * i / sampleRate).toFloat() +
            0.08f * sin(2.0 * PI * 1000.0 * i / sampleRate).toFloat()
        }

        // Enable both high-pass 80 Hz and peak normalization to 0.9
        val config = PreprocessorConfig(
            highPass80Hz = true,
            normalizationMode = NormalizationMode.PEAK_0_9
        )
        val output = preprocessor.process(input, config)

        // Steady state
        val steadyOutput = output.copyOfRange(500, count)
        var maxPeak = 0f
        for (s in steadyOutput) {
            val a = abs(s)
            if (a > maxPeak) maxPeak = a
        }

        // With 25 Hz rumble filtered out, the 1000 Hz speech tone is boosted significantly
        // (without high-pass it would be limited to ~0.09 by the 0.8 rumble peak)
        assertTrue("Speech peak should be boosted significantly (measured: $maxPeak)", maxPeak >= 0.75f)
    }
}

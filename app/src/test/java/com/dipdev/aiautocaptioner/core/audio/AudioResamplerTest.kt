package com.dipdev.aiautocaptioner.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class AudioResamplerTest {

    @Test
    fun `AudioResampler create returns PassthroughResampler when sample rate is 16000`() {
        val resampler = AudioResampler.create(fromRate = 16000, toRate = 16000)
        assertTrue(resampler is PassthroughResampler)

        val input = floatArrayOf(0.1f, -0.2f, 0.5f, -0.9f)
        val output = resampler.process(input)
        assertEquals(input.size, output.size)
        for (i in input.indices) {
            assertEquals(input[i], output[i], 1e-6f)
        }
        val flushed = resampler.flush()
        assertEquals(0, flushed.size)
    }

    @Test
    fun `AudioResampler create returns BandLimitedResampler when useResamplerV2 is true`() {
        val resampler = AudioResampler.create(fromRate = 48000, toRate = 16000, useResamplerV2 = true)
        assertTrue(resampler is BandLimitedResampler)
    }

    @Test
    fun `StreamingLinearResampler produces consistent output whether processed in one shot or multiple chunks`() {
        val fromRate = 48000
        val toRate = 16000
        val durationSec = 0.5
        val sampleCount = (fromRate * durationSec).toInt()

        // 440 Hz test tone at 48 kHz
        val input = FloatArray(sampleCount) { i ->
            sin(2.0 * PI * 440.0 * i / fromRate).toFloat()
        }

        // 1. One-shot execution
        val oneShotResampler = StreamingLinearResampler(fromRate, toRate)
        val oneShotChunk = oneShotResampler.process(input)
        val oneShotFlush = oneShotResampler.flush()
        val oneShotTotal = FloatArray(oneShotChunk.size + oneShotFlush.size).apply {
            oneShotChunk.copyInto(this, 0)
            oneShotFlush.copyInto(this, oneShotChunk.size)
        }

        // 2. Multi-chunk streaming execution
        val streamingResampler = StreamingLinearResampler(fromRate, toRate)
        val streamingOutputList = ArrayList<FloatArray>()
        val chunkSize = 353 // Arbitrary non-power-of-two chunk size to stress chunk boundaries
        var offset = 0
        while (offset < input.size) {
            val end = (offset + chunkSize).coerceAtMost(input.size)
            val chunk = input.copyOfRange(offset, end)
            val res = streamingResampler.process(chunk)
            if (res.isNotEmpty()) {
                streamingOutputList.add(res)
            }
            offset = end
        }
        val flushRes = streamingResampler.flush()
        if (flushRes.isNotEmpty()) {
            streamingOutputList.add(flushRes)
        }

        val totalStreamingCount = streamingOutputList.sumOf { it.size }
        val streamingTotal = FloatArray(totalStreamingCount)
        var streamOffset = 0
        for (chunk in streamingOutputList) {
            chunk.copyInto(streamingTotal, streamOffset)
            streamOffset += chunk.size
        }

        assertEquals("Total samples must match expected output size", oneShotTotal.size, streamingTotal.size)
        for (i in oneShotTotal.indices) {
            assertEquals("Sample $i must match across one-shot and streaming", oneShotTotal[i], streamingTotal[i], 1e-4f)
        }
    }

    @Test
    fun `BandLimitedResampler 20 kHz tone at 48 kHz is attenuated by at least 60 dB at 16 kHz output`() {
        // Acceptance Gate E4.2: 20 kHz tone at 48 kHz input must be >= 60 dB below full scale at 16 kHz output
        val fromRate = 48000
        val toRate = 16000
        val durationSec = 0.5
        val sampleCount = (fromRate * durationSec).toInt()

        // 20 kHz sine wave at full scale (amplitude 1.0 = 0 dBFS)
        val input = FloatArray(sampleCount) { i ->
            sin(2.0 * PI * 20000.0 * i / fromRate).toFloat()
        }

        val resampler = BandLimitedResampler(fromRate, toRate)
        val processed = resampler.process(input)
        val flushed = resampler.flush()

        val output = FloatArray(processed.size + flushed.size).apply {
            processed.copyInto(this, 0)
            flushed.copyInto(this, processed.size)
        }

        assertTrue("Output must contain samples", output.size > 100)

        // Exclude filter transient edges (first and last 50 samples)
        val trimmed = output.copyOfRange(50, output.size - 50)
        var peakAmplitude = 0f
        for (s in trimmed) {
            val a = abs(s)
            if (a > peakAmplitude) peakAmplitude = a
        }

        val peakDbfs = if (peakAmplitude > 0f) 20.0 * log10(peakAmplitude.toDouble()) else -120.0
        // Expected <= -60.0 dBFS (peak amplitude < 0.001)
        assertTrue(
            "20 kHz tone must be attenuated by >= 60 dB (measured: $peakDbfs dBFS, peak: $peakAmplitude)",
            peakDbfs <= -60.0
        )
    }

    @Test
    fun `BandLimitedResampler preserves 1 kHz passband tone energy within 1 dB`() {
        val fromRate = 48000
        val toRate = 16000
        val durationSec = 0.5
        val sampleCount = (fromRate * durationSec).toInt()
        val amplitude = 0.707f

        // 1 kHz sine wave (well within 7.6 kHz passband)
        val input = FloatArray(sampleCount) { i ->
            amplitude * sin(2.0 * PI * 1000.0 * i / fromRate).toFloat()
        }

        val resampler = BandLimitedResampler(fromRate, toRate)
        val processed = resampler.process(input)
        val flushed = resampler.flush()

        val output = FloatArray(processed.size + flushed.size).apply {
            processed.copyInto(this, 0)
            flushed.copyInto(this, processed.size)
        }

        // Exclude filter transient edges
        val trimmedInput = input.copyOfRange(100, input.size - 100)
        val trimmedOutput = output.copyOfRange(100, output.size - 100)

        var sumSqIn = 0.0
        for (s in trimmedInput) sumSqIn += s * s
        val rmsIn = sqrt(sumSqIn / trimmedInput.size)

        var sumSqOut = 0.0
        for (s in trimmedOutput) sumSqOut += s * s
        val rmsOut = sqrt(sumSqOut / trimmedOutput.size)

        val deltaDb = abs(20.0 * log10(rmsOut / rmsIn))
        assertTrue(
            "1 kHz tone energy change must be within 1 dB (measured delta: $deltaDb dB, rmsIn=$rmsIn, rmsOut=$rmsOut)",
            deltaDb <= 1.0
        )
    }

    @Test
    fun `BandLimitedResampler produces consistent output across arbitrary streaming chunk boundaries`() {
        val fromRate = 44100
        val toRate = 16000
        val sampleCount = 4410 // 0.1s

        val input = FloatArray(sampleCount) { i ->
            sin(2.0 * PI * 800.0 * i / fromRate).toFloat()
        }

        // 1. One-shot
        val oneShotResampler = BandLimitedResampler(fromRate, toRate)
        val oneShot = oneShotResampler.process(input)
        val oneShotFlush = oneShotResampler.flush()
        val oneShotTotal = FloatArray(oneShot.size + oneShotFlush.size).apply {
            oneShot.copyInto(this, 0)
            oneShotFlush.copyInto(this, oneShot.size)
        }

        // 2. Streaming chunks
        val streamingResampler = BandLimitedResampler(fromRate, toRate)
        val streamingList = ArrayList<FloatArray>()
        val chunkSize = 250
        var offset = 0
        while (offset < input.size) {
            val end = (offset + chunkSize).coerceAtMost(input.size)
            val chunk = input.copyOfRange(offset, end)
            val res = streamingResampler.process(chunk)
            if (res.isNotEmpty()) streamingList.add(res)
            offset = end
        }
        val flushRes = streamingResampler.flush()
        if (flushRes.isNotEmpty()) streamingList.add(flushRes)

        val streamingTotal = FloatArray(streamingList.sumOf { it.size })
        var streamOffset = 0
        for (chunk in streamingList) {
            chunk.copyInto(streamingTotal, streamOffset)
            streamOffset += chunk.size
        }

        assertEquals(oneShotTotal.size, streamingTotal.size)
        for (i in oneShotTotal.indices) {
            assertEquals("Sample $i mismatch", oneShotTotal[i], streamingTotal[i], 1e-4f)
        }
    }
}

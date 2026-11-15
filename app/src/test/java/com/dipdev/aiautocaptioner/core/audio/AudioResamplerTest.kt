package com.dipdev.aiautocaptioner.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

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
}

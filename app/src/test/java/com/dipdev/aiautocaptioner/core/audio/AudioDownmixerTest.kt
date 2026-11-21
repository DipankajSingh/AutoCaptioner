package com.dipdev.aiautocaptioner.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class AudioDownmixerTest {

    @Test
    fun `mono audio passes through unchanged`() {
        val input = floatArrayOf(0.1f, -0.2f, 0.5f, 0.9f)
        val result = AudioDownmixer.downmix(input, channelCount = 1)
        assertArrayEquals(input, result, 0.0001f)
    }

    @Test
    fun `empty input returns empty output`() {
        val input = FloatArray(0)
        val result = AudioDownmixer.downmix(input, channelCount = 2)
        assertEquals(0, result.size)
    }

    @Test
    fun `in-phase stereo averages channels cleanly`() {
        // Left and Right are identical in-phase signals
        val input = floatArrayOf(
            0.4f, 0.4f,
            -0.6f, -0.6f,
            0.8f, 0.8f
        )
        val result = AudioDownmixer.downmix(input, channelCount = 2)
        val expected = floatArrayOf(0.4f, -0.6f, 0.8f)
        assertArrayEquals(expected, result, 0.0001f)
    }

    @Test
    fun `antiphase stereo avoids cancellation by selecting louder channel`() {
        // Left has amplitude 0.6, Right has inverted smaller amplitude -0.3
        // If averaged naively: (0.6 - 0.3) / 2 = 0.15 (severe speech degradation/attenuation)
        // With correlation-aware downmixing: negative cross correlation detected,
        // energyL (0.6^2) > energyR (0.3^2), so Left is selected -> 0.6
        val input = floatArrayOf(
            0.6f, -0.3f,
            -0.4f, 0.2f,
            0.8f, -0.4f
        )
        val result = AudioDownmixer.downmix(input, channelCount = 2)
        val expected = floatArrayOf(0.6f, -0.4f, 0.8f)
        assertArrayEquals(expected, result, 0.0001f)
    }

    @Test
    fun `antiphase stereo selects right channel when right is louder`() {
        // Left has amplitude 0.2, Right has inverted larger amplitude -0.8
        val input = floatArrayOf(
            0.2f, -0.8f,
            -0.1f, 0.4f,
            0.3f, -0.9f
        )
        val result = AudioDownmixer.downmix(input, channelCount = 2)
        val expected = floatArrayOf(-0.8f, 0.4f, -0.9f)
        assertArrayEquals(expected, result, 0.0001f)
    }

    @Test
    fun `completely canceled antiphase stereo does not become zero`() {
        // Perfect phase inversion L = -R
        // Naive average would be identically 0.0f (silence!)
        val input = floatArrayOf(
            0.5f, -0.5f,
            -0.7f, 0.7f,
            0.3f, -0.3f
        )
        val result = AudioDownmixer.downmix(input, channelCount = 2)
        // Energy is equal, energyL >= energyR is true, chooses Left
        val expected = floatArrayOf(0.5f, -0.7f, 0.3f)
        assertArrayEquals(expected, result, 0.0001f)
        var maxAbs = 0f
        for (v in result) {
            if (abs(v) > maxAbs) maxAbs = abs(v)
        }
        assertEquals(0.7f, maxAbs, 0.0001f)
    }

    @Test
    fun `multichannel 4-channel downmix computes channel average`() {
        val input = floatArrayOf(
            0.1f, 0.2f, 0.3f, 0.4f, // avg = 0.25
            0.0f, -0.4f, 0.2f, 0.2f  // avg = 0.0
        )
        val result = AudioDownmixer.downmix(input, channelCount = 4)
        val expected = floatArrayOf(0.25f, 0.0f)
        assertArrayEquals(expected, result, 0.0001f)
    }
}

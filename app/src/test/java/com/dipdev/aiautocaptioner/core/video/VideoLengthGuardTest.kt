package com.dipdev.aiautocaptioner.core.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VideoLengthGuardTest {

    private lateinit var guard: VideoLengthGuard

    @Before
    fun setUp() {
        guard = VideoLengthGuard()
    }

    @Test
    fun `video with non-positive duration returns NONE warn level`() {
        val resultZero = guard.evaluate(0L)
        assertEquals(VideoWarnLevel.NONE, resultZero.warnLevel)
        assertEquals(0, resultZero.estimatedTimeSeconds)
        assertTrue(resultZero.canProceed)

        val resultNegative = guard.evaluate(-5000L)
        assertEquals(VideoWarnLevel.NONE, resultNegative.warnLevel)
        assertEquals(0, resultNegative.estimatedTimeSeconds)
    }

    @Test
    fun `video under 10 minutes returns NONE warn level`() {
        // 9 minutes 59 seconds = 599,000 ms
        val result = guard.evaluate(599_000L, modelId = "base.en")
        assertEquals(VideoWarnLevel.NONE, result.warnLevel)
        assertTrue(result.canProceed)
        assertTrue(result.estimatedTimeSeconds > 0)
    }

    @Test
    fun `video at exactly 10 minutes triggers WARN level`() {
        // 10 minutes = 600,000 ms
        val result = guard.evaluate(600_000L, modelId = "base.en")
        assertEquals(VideoWarnLevel.WARN, result.warnLevel)
        assertTrue(result.canProceed)
        assertTrue(result.recommendationMessage.contains("10 minutes"))
    }

    @Test
    fun `video between 10 and 30 minutes triggers WARN level`() {
        // 20 minutes = 1,200,000 ms
        val result = guard.evaluate(1_200_000L, modelId = "base.en")
        assertEquals(VideoWarnLevel.WARN, result.warnLevel)
        assertTrue(result.canProceed)
        assertTrue(result.recommendationMessage.contains("10 minutes"))
    }

    @Test
    fun `video at 29 minutes 59 seconds triggers WARN level`() {
        // 29m 59s = 1,799,000 ms
        val result = guard.evaluate(1_799_000L, modelId = "base.en")
        assertEquals(VideoWarnLevel.WARN, result.warnLevel)
    }

    @Test
    fun `video at exactly 30 minutes triggers STRONG_WARN level`() {
        // 30 minutes = 1,800,000 ms
        val result = guard.evaluate(1_800_000L, modelId = "base.en")
        assertEquals(VideoWarnLevel.STRONG_WARN, result.warnLevel)
        assertTrue(result.canProceed)
        assertTrue(result.recommendationMessage.contains("30 minutes"))
    }

    @Test
    fun `video over 30 minutes triggers STRONG_WARN level`() {
        // 45 minutes = 2,700,000 ms
        val result = guard.evaluate(2_700_000L, modelId = "small.en")
        assertEquals(VideoWarnLevel.STRONG_WARN, result.warnLevel)
        assertTrue(result.canProceed)
        assertTrue(result.recommendationMessage.contains("30 minutes"))
    }

    @Test
    fun `model tier multiplier affects estimated processing time`() {
        val durationMs = 600_000L // 10 minutes = 20 windows (30s each)
        val tinyResult = guard.evaluate(durationMs, modelId = "tiny.en")
        val baseResult = guard.evaluate(durationMs, modelId = "base.en")
        val smallResult = guard.evaluate(durationMs, modelId = "small.en")

        // 20 windows * 2.0 = 40s
        assertEquals(40, tinyResult.estimatedTimeSeconds)
        // 20 windows * 3.5 = 70s
        assertEquals(70, baseResult.estimatedTimeSeconds)
        // 20 windows * 7.0 = 140s
        assertEquals(140, smallResult.estimatedTimeSeconds)
    }
}

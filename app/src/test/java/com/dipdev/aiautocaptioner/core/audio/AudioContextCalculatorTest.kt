package com.dipdev.aiautocaptioner.core.audio

import com.dipdev.aiautocaptioner.core.benchmark.BenchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioContextCalculatorTest {

    @Test
    fun `calculate with empty or negative samples returns zero`() {
        assertEquals(0, AudioContextCalculator.calculate(0))
        assertEquals(0, AudioContextCalculator.calculate(-16000))
    }

    @Test
    fun `calculate for 1 second yields aligned positions`() {
        // 1.0 second * 50 = 50 raw positions -> rounded up to next multiple of 32 = 64
        val ctx = AudioContextCalculator.calculate(16000)
        assertEquals(64, ctx)
        assertEquals(0, ctx % 32)
    }

    @Test
    fun `calculate for 5 seconds yields 256 positions`() {
        // 5.0 seconds * 50 = 250 raw positions -> ceil to multiple of 32 = 256
        val ctx = AudioContextCalculator.calculate(5 * 16000)
        assertEquals(256, ctx)
        assertEquals(0, ctx % 32)
    }

    @Test
    fun `calculate for 10 seconds yields 512 positions`() {
        // 10.0 seconds * 50 = 500 raw positions -> ceil to multiple of 32 = 512
        val ctx = AudioContextCalculator.calculate(10 * 16000)
        assertEquals(512, ctx)
        assertEquals(0, ctx % 32)
    }

    @Test
    fun `calculate for 15 seconds yields 768 positions`() {
        // 15.0 seconds * 50 = 750 raw positions -> ceil to multiple of 32 = 768
        val ctx = AudioContextCalculator.calculate(15 * 16000)
        assertEquals(768, ctx)
        assertEquals(0, ctx % 32)
    }

    @Test
    fun `calculate for 20 seconds yields 1024 positions`() {
        // 20.0 seconds * 50 = 1000 raw positions -> ceil to multiple of 32 = 1024
        val ctx = AudioContextCalculator.calculate(20 * 16000)
        assertEquals(1024, ctx)
        assertEquals(0, ctx % 32)
    }

    @Test
    fun `calculate for 25 seconds yields 1280 positions`() {
        // 25.0 seconds * 50 = 1250 raw positions -> ceil to multiple of 32 = 1280
        val ctx = AudioContextCalculator.calculate(25 * 16000)
        assertEquals(1280, ctx)
        assertEquals(0, ctx % 32)
    }

    @Test
    fun `calculate for clips greater than or equal to 30 seconds returns zero`() {
        // Exactly 30 seconds -> 0 (default context 1500)
        assertEquals(0, AudioContextCalculator.calculate(30 * 16000))
        // 35 seconds -> 0
        assertEquals(0, AudioContextCalculator.calculate(35 * 16000))
        // 60 seconds -> 0
        assertEquals(0, AudioContextCalculator.calculate(60 * 16000))
    }

    @Test
    fun `calculate for sweep of durations is always 32-aligned and leq 1500`() {
        for (sec in 1..29) {
            val ctx = AudioContextCalculator.calculate(sec * 16000)
            assertTrue("Expected ctx > 0 for $sec s, got $ctx", ctx > 0)
            assertTrue("Expected ctx <= 1500 for $sec s, got $ctx", ctx <= 1500)
            assertEquals("Expected 32-alignment for $sec s, got $ctx", 0, ctx % 32)
        }
    }

    @Test
    fun `BenchConfig handles audioCtx and audioCtxReduction defaults and custom values`() {
        val defaultCfg = BenchConfig()
        assertEquals(0, defaultCfg.audioCtx)
        assertFalse(defaultCfg.audioCtxReduction)

        val customCfg = BenchConfig(
            modelId = "base.en",
            audioCtx = 512,
            audioCtxReduction = true
        )
        assertEquals("base.en", customCfg.modelId)
        assertEquals(512, customCfg.audioCtx)
        assertTrue(customCfg.audioCtxReduction)
    }
}

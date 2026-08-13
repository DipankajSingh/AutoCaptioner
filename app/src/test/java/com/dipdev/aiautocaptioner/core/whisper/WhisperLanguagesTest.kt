package com.dipdev.aiautocaptioner.core.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperLanguagesTest {

    @Test
    fun testWhisperCodeMapping() {
        assertEquals("hi", WhisperLanguages.whisperCode("hinglish"))
        assertEquals("zh", WhisperLanguages.whisperCode("zh-TW"))
        assertEquals("en", WhisperLanguages.whisperCode("en"))
        assertEquals("es", WhisperLanguages.whisperCode("es"))
    }

    @Test
    fun testOrderedCodes_IndianLocale() {
        val codes = WhisperLanguages.orderedCodes("IN", "hi")
        // Should start with auto
        assertEquals("auto", codes[0])
        // Should contain Hindi and Hinglish high up
        assertTrue(codes.contains("hi"))
        assertTrue(codes.contains("hinglish"))
        // Check first few (order depends on implementation, but hi/hinglish/en are expected for IN)
        assertTrue(codes.take(5).contains("hi"))
        assertTrue(codes.take(5).contains("hinglish"))
    }

    @Test
    fun testOrderedCodes_USLocale() {
        val codes = WhisperLanguages.orderedCodes("US", "en")
        assertEquals("auto", codes[0])
        assertEquals("en", codes[1])
        assertEquals("es", codes[2])
    }

    @Test
    fun testOrderedCodes_NullInputs() {
        val codes = WhisperLanguages.orderedCodes(null, null)
        assertEquals("auto", codes[0])
        assertTrue(codes.size > 1) // Should still return default UI_CODES
    }
}

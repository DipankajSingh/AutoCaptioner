package com.dipdev.aiautocaptioner.data.source

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class WhisperModelRegistryTest {

    @Test
    fun `getModels returns list of models from assets`() {
        // Arrange
        val context = mockk<Context>()
        val assets = mockk<AssetManager>()
        val json = """
            [
              {
                "id": "tiny.en",
                "displayName": "Tiny — English only",
                "description": "Fastest model.",
                "sizeBytes": 77704715,
                "downloadUrl": "url",
                "minRamMb": 512,
                "accuracy": 2,
                "speed": 5,
                "languages": ["en"],
                "isMultilingual": false
              }
            ]
        """.trimIndent()
        
        every { context.assets } returns assets
        every { assets.open("whisper_models.json") } returns ByteArrayInputStream(json.toByteArray())
        
        val registry = WhisperModelRegistry(context)

        // Act
        val models = registry.getModels()

        // Assert
        assertEquals(1, models.size)
        assertEquals("tiny.en", models[0].id)
        assertEquals("Tiny — English only", models[0].displayName)
        assertEquals(77704715L, models[0].sizeBytes)
        assertEquals(listOf("en"), models[0].languages)
    }

    @Test
    fun `getModels returns empty list on error`() {
        // Arrange
        val context = mockk<Context>()
        val assets = mockk<AssetManager>()
        
        every { context.assets } returns assets
        every { assets.open("whisper_models.json") } throws Exception("File not found")
        
        val registry = WhisperModelRegistry(context)

        // Act
        val models = registry.getModels()

        // Assert
        assertTrue(models.isEmpty())
    }
}

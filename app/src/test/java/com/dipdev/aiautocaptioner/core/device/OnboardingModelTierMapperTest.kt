package com.dipdev.aiautocaptioner.core.device

import com.dipdev.aiautocaptioner.data.model.WhisperModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class OnboardingModelTierMapperTest {

    private lateinit var mapper: OnboardingModelTierMapper
    private lateinit var mockModelRecommendationUseCase: ModelRecommendationUseCase

    private fun createFakeModel(id: String, sizeMb: Int): WhisperModel {
        return WhisperModel(
            id = id,
            displayName = "Model $id",
            description = "Desc $id",
            sizeMb = sizeMb,
            downloadUrl = "http://fake.url/$id",
            languages = listOf("en"),
            isMultilingual = false,
            minRamMb = sizeMb * 2
        )
    }

    @Before
    fun setup() {
        mockModelRecommendationUseCase = mock {
            on { getRecommendation(any(), any()) } doReturn ModelRecommendation("model_2", 123)
        }
        mapper = OnboardingModelTierMapper(mockModelRecommendationUseCase)
    }

    @Test
    fun `mapToTiers handles empty list`() {
        val tiers = mapper.mapToTiers(emptyList(), "en")
        assertTrue(tiers.isEmpty())
    }

    @Test
    fun `mapToTiers handles list of size 1`() {
        val models = listOf(createFakeModel("model_1", 100))
        val tiers = mapper.mapToTiers(models, "en")
        
        assertEquals(1, tiers.size)
        assertEquals("Standard", tiers[0].tierName)
        assertEquals("model_1", tiers[0].model.id)
    }

    @Test
    fun `mapToTiers handles list of size 2`() {
        val models = listOf(
            createFakeModel("model_1", 100),
            createFakeModel("model_2", 200)
        )
        val tiers = mapper.mapToTiers(models, "en")
        
        assertEquals(2, tiers.size)
        assertEquals("Fast & Light", tiers[0].tierName)
        assertEquals("model_1", tiers[0].model.id)
        assertFalse(tiers[0].isRecommended)

        assertEquals("Pro Quality", tiers[1].tierName)
        assertEquals("model_2", tiers[1].model.id)
        assertTrue(tiers[1].isRecommended)
        assertEquals(123, tiers[1].recommendedReasonResId)
    }

    @Test
    fun `mapToTiers handles list of size 3`() {
        val models = listOf(
            createFakeModel("model_1", 100),
            createFakeModel("model_2", 200),
            createFakeModel("model_3", 300)
        )
        val tiers = mapper.mapToTiers(models, "en")
        
        assertEquals(3, tiers.size)
        assertEquals("Fast & Light", tiers[0].tierName)
        assertEquals("model_1", tiers[0].model.id)

        assertEquals("Standard", tiers[1].tierName)
        assertEquals("model_2", tiers[1].model.id)
        assertTrue(tiers[1].isRecommended)

        assertEquals("Pro Quality", tiers[2].tierName)
        assertEquals("model_3", tiers[2].model.id)
    }

    @Test
    fun `mapToTiers handles list of size 5`() {
        val models = listOf(
            createFakeModel("model_1", 100),
            createFakeModel("model_2", 200),
            createFakeModel("model_3", 300),
            createFakeModel("model_4", 400),
            createFakeModel("model_5", 500)
        )
        // size / 2 = 2
        val tiers = mapper.mapToTiers(models, "en")
        
        assertEquals(3, tiers.size)
        assertEquals("Fast & Light", tiers[0].tierName)
        assertEquals("model_1", tiers[0].model.id)

        assertEquals("Standard", tiers[1].tierName)
        assertEquals("model_3", tiers[1].model.id)

        assertEquals("Pro Quality", tiers[2].tierName)
        assertEquals("model_5", tiers[2].model.id)
    }
}

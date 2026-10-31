package com.dipdev.aiautocaptioner.core.device

import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelRecommendationUseCaseTest {

    private lateinit var mockDeviceCapabilityUseCase: DeviceCapabilityUseCase
    private lateinit var useCase: ModelRecommendationUseCase

    private fun createModel(id: String, sizeBytes: Long, languages: List<String>, isMultilingual: Boolean, minRamMb: Int): WhisperModel {
        return WhisperModel(
            id = id,
            displayName = "Model $id",
            description = "Desc $id",
            sizeBytes = sizeBytes,
            downloadUrl = "http://fake.url/$id",
            minRamMb = minRamMb,
            accuracy = 4,
            speed = 5,
            languages = languages,
            isMultilingual = isMultilingual
        )
    }

    @Before
    fun setup() {
        mockDeviceCapabilityUseCase = mockk()
        every { mockDeviceCapabilityUseCase.isModelRamCompatible(any()) } returns true
        useCase = ModelRecommendationUseCase(mockDeviceCapabilityUseCase)
    }

    @Test
    fun `hinglish language recommends hi hinglish swift`() {
        val models = listOf(
            createModel("hi.hinglish.swift", 59_768_832L, listOf("hi", "hinglish"), false, 512),
            createModel("hi.hinglish.q5", 547_384_320L, listOf("hi", "hinglish"), false, 2048),
            createModel("hi.hinglish.q8", 834_670_592L, listOf("hi", "hinglish"), false, 2048),
            createModel("base", 147_951_482L, listOf("en", "hi"), true, 1024)
        )

        every { mockDeviceCapabilityUseCase.getRecommendedModelWithReason("hinglish") } returns
                RecommendationResult("hi.hinglish.swift", R.string.recommend_reason_mid_range)

        val compatible = useCase.filterCompatibleModels(models, "hinglish")
        val rec = useCase.getRecommendation(compatible, "hinglish")

        // Swift should be sorted first due to smaller size
        assertEquals("hi.hinglish.swift", compatible[0].id)
        assertEquals("hi.hinglish.swift", rec.modelId)
        assertEquals(R.string.recommend_reason_mid_range, rec.reasonResId)
    }

    @Test
    fun `filterAndSortForLanguage places language-specific lightweight model first`() {
        val models = listOf(
            createModel("base", 147_951_482L, listOf("en"), true, 1024),
            createModel("hi.hinglish.q5", 547_384_320L, listOf("hi", "hinglish"), false, 2048),
            createModel("hi.hinglish.swift", 59_768_832L, listOf("hi", "hinglish"), false, 512)
        )

        val filtered = WhisperModel.filterAndSortForLanguage(models, "hinglish")

        assertEquals(3, filtered.size)
        // Swift first (non-multilingual, lowest size)
        assertEquals("hi.hinglish.swift", filtered[0].id)
        // Q5 second (non-multilingual, larger size)
        assertEquals("hi.hinglish.q5", filtered[1].id)
        // Multilingual base last
        assertEquals("base", filtered[2].id)
    }
}

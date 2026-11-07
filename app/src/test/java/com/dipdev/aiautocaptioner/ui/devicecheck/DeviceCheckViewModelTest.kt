package com.dipdev.aiautocaptioner.ui.devicecheck

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.dipdev.aiautocaptioner.core.device.DeviceCapabilityInfo
import com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase
import com.dipdev.aiautocaptioner.core.device.ModelRecommendation
import com.dipdev.aiautocaptioner.core.device.ModelRecommendationUseCase
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceCheckViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val modelRepository: ModelRepository = mockk(relaxed = true)
    private val deviceCapabilityUseCase: DeviceCapabilityUseCase = mockk(relaxed = true)
    private val modelRecommendationUseCase: ModelRecommendationUseCase = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { deviceCapabilityUseCase.getDeviceInfo() } returns DeviceCapabilityInfo(
            totalRamMb = 4096,
            availableStorageGb = 10f,
            androidVersion = 30,
            cpuAbi = "arm64-v8a"
        )
        every { settingsRepository.lastLanguageFlow } returns flowOf("hi")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `checkDevice with non-English language recommends non-English model`() {
        val tinyEn = WhisperModel("tiny.en", "Tiny EN", "Desc", 77_000_000L, "url", 512, 2, 5, listOf("en"), false)
        val hiBase = WhisperModel("hi.base", "Base HI", "Desc", 147_000_000L, "url", 1024, 3, 4, listOf("hi"), false)
        val baseMulti = WhisperModel("base", "Base Multi", "Desc", 147_000_000L, "url", 1024, 3, 4, listOf("multilingual"), true)

        val allModels = listOf(tinyEn, hiBase, baseMulti)
        val compatibleForHi = listOf(hiBase, baseMulti)

        every { modelRepository.getAvailableModels() } returns allModels
        every { modelRecommendationUseCase.filterCompatibleModels(allModels, "hi") } returns compatibleForHi
        every { modelRecommendationUseCase.getRecommendation(compatibleForHi, "hi") } returns ModelRecommendation("hi.base", null)

        val savedStateHandle = SavedStateHandle(mapOf("languageCode" to "hi"))
        val viewModel = DeviceCheckViewModel(
            context = context,
            modelRepository = modelRepository,
            deviceCapabilityUseCase = deviceCapabilityUseCase,
            modelRecommendationUseCase = modelRecommendationUseCase,
            settingsRepository = settingsRepository,
            savedStateHandle = savedStateHandle
        )

        val state = viewModel.uiState.value
        assertEquals("hi", state.language)
        assertEquals("hi.base", state.recommendedModelId)
        assertFalse(state.recommendedModelId == "tiny.en")
    }
}

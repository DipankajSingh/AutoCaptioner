package com.dipdev.aiautocaptioner.ui.recorder

import androidx.lifecycle.SavedStateHandle
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.engine.effects.CameraEffectManager
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartRecorderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var cameraEffectManager: CameraEffectManager
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: SmartRecorderViewModel

    private val aspectRatioFlow = MutableStateFlow("PORTRAIT_9_16")
    private val qualityFlow = MutableStateFlow("MEDIUM")
    private val filterFlow = MutableStateFlow(CreatorFilter.NATURAL)
    private val smoothnessFlow = MutableStateFlow(0.35f)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true) {
            coEvery { lastAspectRatioFlow } returns aspectRatioFlow
            coEvery { lastRecordingQualityFlow } returns qualityFlow
            coEvery { selectedCreatorFilterFlow } returns filterFlow
            coEvery { skinSmoothnessIntensityFlow } returns smoothnessFlow
        }
        projectRepository = mockk(relaxed = true)
        cameraEffectManager = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle()

        viewModel = SmartRecorderViewModel(
            settingsRepository,
            projectRepository,
            cameraEffectManager,
            savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads default smoothness and filter from SettingsRepository`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(CreatorFilter.NATURAL, state.activeFilter)
        assertEquals(0.35f, state.smoothnessIntensity, 0.001f)
        verify { cameraEffectManager.setActiveFilter(CreatorFilter.NATURAL) }
        verify { cameraEffectManager.setSmoothnessIntensity(0.35f) }
    }

    @Test
    fun `toggleFilterCarousel and toggleSmoothnessSlider are mutually exclusive`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isFilterCarouselVisible)
        assertFalse(viewModel.uiState.value.isSmoothnessSliderVisible)

        viewModel.toggleFilterCarousel()
        assertTrue(viewModel.uiState.value.isFilterCarouselVisible)
        assertFalse(viewModel.uiState.value.isSmoothnessSliderVisible)

        viewModel.toggleSmoothnessSlider()
        assertFalse(viewModel.uiState.value.isFilterCarouselVisible)
        assertTrue(viewModel.uiState.value.isSmoothnessSliderVisible)

        viewModel.dismissSubControls()
        assertFalse(viewModel.uiState.value.isFilterCarouselVisible)
        assertFalse(viewModel.uiState.value.isSmoothnessSliderVisible)
    }

    @Test
    fun `selectFilter updates state, GPU manager, persists choice and auto-dismisses badge`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.selectFilter(CreatorFilter.WARM_GLOW)
        assertEquals(CreatorFilter.WARM_GLOW, viewModel.uiState.value.activeFilter)
        assertEquals(CreatorFilter.WARM_GLOW.displayName, viewModel.uiState.value.recentlySelectedFilterName)
        verify { cameraEffectManager.setActiveFilter(CreatorFilter.WARM_GLOW) }

        advanceUntilIdle()
        coVerify { settingsRepository.setCreatorFilter(CreatorFilter.WARM_GLOW) }

        advanceTimeBy(1900)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.recentlySelectedFilterName)
    }

    @Test
    fun `updateSmoothness clamps values, updates GPU manager and debounces persistence`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.updateSmoothness(1.5f)
        assertEquals(1.0f, viewModel.uiState.value.smoothnessIntensity, 0.001f)
        verify { cameraEffectManager.setSmoothnessIntensity(1.0f) }

        advanceTimeBy(300)
        coVerify(exactly = 0) { settingsRepository.setSkinSmoothnessIntensity(1.0f) }

        advanceTimeBy(300)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsRepository.setSkinSmoothnessIntensity(1.0f) }
    }

    @Test
    fun `resetStudioEffects restores default NATURAL filter and 0 smoothness`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.selectFilter(CreatorFilter.CINEMATIC)
        viewModel.updateSmoothness(0.8f)
        advanceUntilIdle()

        viewModel.resetStudioEffects()
        advanceUntilIdle()

        assertEquals(CreatorFilter.NATURAL, viewModel.uiState.value.activeFilter)
        assertEquals(0.0f, viewModel.uiState.value.smoothnessIntensity, 0.001f)
        verify { cameraEffectManager.setActiveFilter(CreatorFilter.NATURAL) }
        verify { cameraEffectManager.setSmoothnessIntensity(0.0f) }
    }
}

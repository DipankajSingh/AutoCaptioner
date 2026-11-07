package com.dipdev.aiautocaptioner.ui.processing

import androidx.lifecycle.SavedStateHandle
import com.dipdev.aiautocaptioner.core.device.DeviceCapabilityInfo
import com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase
import com.dipdev.aiautocaptioner.core.device.ModelRecommendationUseCase
import com.dipdev.aiautocaptioner.core.video.VideoLengthGuard
import com.dipdev.aiautocaptioner.core.video.VideoWarnLevel
import com.dipdev.aiautocaptioner.core.whisper.TranscriptionManager
import com.dipdev.aiautocaptioner.data.db.entity.CreationMode
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessingViewModelLongVideoTest {

    private val testDispatcher = StandardTestDispatcher()
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
    private val projectRepository: ProjectRepository = mockk(relaxed = true)
    private val modelRepository: ModelRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val deviceCapabilityUseCase: DeviceCapabilityUseCase = mockk(relaxed = true)
    private val modelRecommendationUseCase: ModelRecommendationUseCase = mockk(relaxed = true)
    private val transcriptionManager: TranscriptionManager = mockk(relaxed = true)
    private val videoLengthGuard = VideoLengthGuard()

    private val sampleModel = WhisperModel(
        id = "base.en",
        displayName = "Base English",
        description = "Test Model",
        sizeBytes = 142_000_000L,
        downloadUrl = "https://example.com/model",
        minRamMb = 2048,
        accuracy = 3,
        speed = 4,
        languages = listOf("en"),
        isMultilingual = false,
        isDownloaded = true
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { modelRepository.getActiveModel() } returns flowOf(sampleModel)
        every { modelRepository.getModelById("base.en") } returns sampleModel
        every { modelRepository.getAvailableModels() } returns listOf(sampleModel)
        every { settingsRepository.lastLanguageFlow } returns flowOf("en")
        every { settingsRepository.lastTranslateFlow } returns flowOf(false)
        every { deviceCapabilityUseCase.getDeviceInfo() } returns DeviceCapabilityInfo(
            totalRamMb = 4096,
            availableStorageGb = 10f,
            androidVersion = 30,
            cpuAbi = "arm64-v8a"
        )
        every { modelRecommendationUseCase.isCompatible(any(), any()) } returns true
        every { transcriptionManager.step } returns kotlinx.coroutines.flow.MutableStateFlow(ProcessingStep.Idle)
        every { transcriptionManager.streamedSegments } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        every { transcriptionManager.detectedLanguage } returns kotlinx.coroutines.flow.MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProcessingViewModel {
        return ProcessingViewModel(
            savedStateHandle = savedStateHandle,
            projectRepository = projectRepository,
            modelRepository = modelRepository,
            settingsRepository = settingsRepository,
            deviceCapabilityUseCase = deviceCapabilityUseCase,
            modelRecommendationUseCase = modelRecommendationUseCase,
            transcriptionManager = transcriptionManager,
            videoLengthGuard = videoLengthGuard
        )
    }

    @Test
    fun `short video under 10 minutes starts transcription without warning`() = runTest(testDispatcher) {
        val project = ProjectEntity(
            id = "proj-1",
            title = "Short Video",
            originalVideoUri = "content://video",
            workingVideoPath = "/path/video.mp4",
            videoDurationMs = 120_000L, // 2 minutes
            videoWidth = 1080,
            videoHeight = 1920,
            videoFps = 30f,
            status = ProjectStatus.IMPORTED,
            creationMode = CreationMode.ADVANCED,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        coEvery { projectRepository.getProjectById("proj-1") } returns project

        val viewModel = createViewModel()
        viewModel.setEvent(ProcessingUiEvent.PrepareForProject("proj-1"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.longVideoWarning)
        verify { transcriptionManager.startProcess(projectId = "proj-1", modelId = "base.en", any(), any(), any(), any()) }
    }

    @Test
    fun `video over 10 minutes shows long video warning before starting`() = runTest(testDispatcher) {
        val project = ProjectEntity(
            id = "proj-long",
            title = "Long Video",
            originalVideoUri = "content://video",
            workingVideoPath = "/path/video.mp4",
            videoDurationMs = 900_000L, // 15 minutes
            videoWidth = 1080,
            videoHeight = 1920,
            videoFps = 30f,
            status = ProjectStatus.IMPORTED,
            creationMode = CreationMode.ADVANCED,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        coEvery { projectRepository.getProjectById("proj-long") } returns project

        val viewModel = createViewModel()
        viewModel.setEvent(ProcessingUiEvent.PrepareForProject("proj-long"))
        advanceUntilIdle()

        val warning = viewModel.uiState.value.longVideoWarning
        assertNotNull(warning)
        assertEquals(VideoWarnLevel.WARN, warning?.guardResult?.warnLevel)
        assertEquals(900_000L, warning?.durationMs)
        // Transcription must NOT have started yet
        verify(exactly = 0) { transcriptionManager.startProcess(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `proceeding from long video warning starts transcription`() = runTest(testDispatcher) {
        val project = ProjectEntity(
            id = "proj-long",
            title = "Long Video",
            originalVideoUri = "content://video",
            workingVideoPath = "/path/video.mp4",
            videoDurationMs = 900_000L, // 15 minutes
            videoWidth = 1080,
            videoHeight = 1920,
            videoFps = 30f,
            status = ProjectStatus.IMPORTED,
            creationMode = CreationMode.ADVANCED,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        coEvery { projectRepository.getProjectById("proj-long") } returns project

        val viewModel = createViewModel()
        viewModel.setEvent(ProcessingUiEvent.PrepareForProject("proj-long"))
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.longVideoWarning)

        // User chooses Continue Anyway
        viewModel.setEvent(ProcessingUiEvent.DismissLongVideoWarning(proceed = true))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.longVideoWarning)
        verify(exactly = 1) { transcriptionManager.startProcess(projectId = "proj-long", modelId = "base.en", any(), any(), any(), any()) }
    }

    @Test
    fun `choosing faster model from warning opens model picker`() = runTest(testDispatcher) {
        val project = ProjectEntity(
            id = "proj-long",
            title = "Long Video",
            originalVideoUri = "content://video",
            workingVideoPath = "/path/video.mp4",
            videoDurationMs = 900_000L,
            videoWidth = 1080,
            videoHeight = 1920,
            videoFps = 30f,
            status = ProjectStatus.IMPORTED,
            creationMode = CreationMode.ADVANCED,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        coEvery { projectRepository.getProjectById("proj-long") } returns project

        val viewModel = createViewModel()
        viewModel.setEvent(ProcessingUiEvent.PrepareForProject("proj-long"))
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.longVideoWarning)

        viewModel.setEvent(ProcessingUiEvent.ChooseFasterModelFromWarning)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.longVideoWarning)
        assert(viewModel.uiState.value.step is ProcessingStep.SetupAI)
    }
}

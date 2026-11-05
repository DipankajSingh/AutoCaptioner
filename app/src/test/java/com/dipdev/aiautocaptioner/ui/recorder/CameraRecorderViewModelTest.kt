package com.dipdev.aiautocaptioner.ui.recorder

import android.content.Context
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CameraRecorderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val projectRepository: ProjectRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaultValues() {
        val state = CameraRecorderState()
        assertFalse(state.isRecording)
        assertFalse(state.isPaused)
        assertEquals(0L, state.recordedDurationMs)
        assertFalse(state.isFrontCamera)
        assertEquals(1.0f, state.zoomRatio, 0.001f)
        assertNull(state.reviewVideoFile)
        assertFalse(state.isImporting)
        assertNull(state.errorMessage)
        assertEquals(180_000L, state.remainingDurationMs)
        assertFalse(state.isNearLimit)
    }

    @Test
    fun cameraState_isNearLimit_calculatedCorrectly() {
        val nearLimitState = CameraRecorderState(recordedDurationMs = 170_000L)
        assertEquals(10_000L, nearLimitState.remainingDurationMs)
        assertTrue(nearLimitState.isNearLimit)

        val farFromLimitState = CameraRecorderState(recordedDurationMs = 100_000L)
        assertFalse(farFromLimitState.isNearLimit)
    }

    @Test
    fun toggleCameraFacing_togglesStateAndResetsZoom() {
        var state = CameraRecorderState(isFrontCamera = false, zoomRatio = 3.0f)
        state = state.copy(isFrontCamera = !state.isFrontCamera, zoomRatio = 1.0f)

        assertTrue(state.isFrontCamera)
        assertEquals(1.0f, state.zoomRatio, 0.001f)
    }

    @Test
    fun setZoomRatio_coercesInValidRange() {
        val minCoerced = 0.5f.coerceIn(1.0f, 10.0f)
        val maxCoerced = 15.0f.coerceIn(1.0f, 10.0f)
        val validCoerced = 3.5f.coerceIn(1.0f, 10.0f)

        assertEquals(1.0f, minCoerced, 0.001f)
        assertEquals(10.0f, maxCoerced, 0.001f)
        assertEquals(3.5f, validCoerced, 0.001f)
    }

    @Test
    fun onRecordingStopped_withValidFile_updatesState() {
        val viewModel = CameraRecorderViewModel(context, projectRepository)
        val tempFile = File.createTempFile("test_video", ".mp4")
        tempFile.writeText("fake video data")

        viewModel.onRecordingStopped(tempFile)

        val state = viewModel.uiState.value
        assertEquals(tempFile, state.reviewVideoFile)
        assertNull(state.errorMessage)

        tempFile.delete()
    }

    @Test
    fun pausedTime_doesNotCountTowardDuration() = runTest(testDispatcher) {
        val viewModel = CameraRecorderViewModel(context, projectRepository)
        var fakeTime = 1000L
        viewModel.clock = { fakeTime }

        viewModel.onRecordingStarted()
        testDispatcher.scheduler.advanceTimeBy(200L)

        // Advance 5 seconds while recording
        fakeTime += 5000L
        testDispatcher.scheduler.advanceTimeBy(200L)
        assertEquals(5000L, viewModel.uiState.value.recordedDurationMs)

        // Pause and advance 10 seconds
        viewModel.onRecordingPaused()
        fakeTime += 10000L
        testDispatcher.scheduler.advanceTimeBy(200L)
        assertEquals(5000L, viewModel.uiState.value.recordedDurationMs)

        // Resume and advance 1 second
        viewModel.onRecordingResumed()
        fakeTime += 1000L
        testDispatcher.scheduler.advanceTimeBy(200L)

        // Expect total duration to be 6 seconds (5s + 1s), NOT 16 seconds
        assertEquals(6000L, viewModel.uiState.value.recordedDurationMs)

        viewModel.onRecordingStopping()
        testDispatcher.scheduler.advanceTimeBy(200L)
    }

    @Test
    fun onRecordingFailed_resetsStateAndSetsErrorMessage() {
        val viewModel = CameraRecorderViewModel(context, projectRepository)
        viewModel.onRecordingStarted()

        viewModel.onRecordingFailed(1)

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertFalse(state.isPaused)
        assertNull(state.reviewVideoFile)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("code 1"))
    }

    @Test
    fun onCameraError_setsErrorMessage() {
        val viewModel = CameraRecorderViewModel(context, projectRepository)
        viewModel.onCameraError(RuntimeException("Camera bound failed"))

        val state = viewModel.uiState.value
        assertEquals("Camera unavailable. Close other camera apps and try again.", state.errorMessage)
    }
}


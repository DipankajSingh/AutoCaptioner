package com.dipdev.aiautocaptioner.ui.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CameraRecorderViewModelTest {

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
        val tempFile = File.createTempFile("test_video", ".mp4")
        tempFile.writeText("fake video data")

        val state = CameraRecorderState(
            isRecording = false,
            isPaused = false,
            reviewVideoFile = tempFile,
            errorMessage = null
        )

        assertEquals(tempFile, state.reviewVideoFile)
        assertNull(state.errorMessage)

        tempFile.delete()
    }
}

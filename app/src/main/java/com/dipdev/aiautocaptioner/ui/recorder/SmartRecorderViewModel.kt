package com.dipdev.aiautocaptioner.ui.recorder

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.engine.FacelessVideoRecorder
import com.dipdev.aiautocaptioner.ui.recorder.model.AspectRatio
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingQuality
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class RecordingMode {
    CAMERA, FACELESS
}

enum class RecordingState {
    IDLE, RECORDING, PAUSED, DONE
}

sealed class BackgroundState {
    data class SolidColor(val color: Color) : BackgroundState()
    data class ImageBitmap(
        val bitmap: Bitmap,
        val scale: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f
    ) : BackgroundState()
    data class Gradient(val colors: List<Color>) : BackgroundState()
}

data class SmartRecorderState(
    val recordingMode: RecordingMode = RecordingMode.CAMERA,
    val selectedBackground: BackgroundState = BackgroundState.Gradient(
        listOf(
            Color(0xFF4158D0.toInt()),
            Color(0xFFC850C0.toInt()),
            Color(0xFFFFCC70.toInt())
        )
    ),
    val recordingState: RecordingState = RecordingState.IDLE,
    val elapsedSeconds: Int = 0,
    val finishedProjectId: String? = null,
    val finishedVideoFile: File? = null,
    val isAudioMuted: Boolean = false,
    val showGrid: Boolean = false,
    val countdownTimer: Int = 0,
    val showTeleprompter: Boolean = false,
    val teleprompterText: String = "",
    val audioAmplitude: Float = 0f,
    val isCountdownActive: Boolean = false,
    val countdownRemaining: Int = 0,
    val isGestureDetectionEnabled: Boolean = false,
    val showRecorderOnboarding: Boolean = false,
    val aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val recordingQuality: RecordingQuality = RecordingQuality.MEDIUM,
    val showExitDialog: Boolean = false,
    val segments: List<RecordingSegment> = emptyList(),
    val currentSegmentStartMs: Long = 0L
) : UiState

@HiltViewModel
class SmartRecorderViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val projectRepository: ProjectRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<SmartRecorderState, UiEvent, UiEffect>(
    SmartRecorderState(
        recordingMode = if (savedStateHandle.get<String>("mode") == "FACELESS") RecordingMode.FACELESS else RecordingMode.CAMERA
    )
) {

    private var facelessRecorder: FacelessVideoRecorder? = null
    private var timerJob: Job? = null

    private var currentProjectId: String? = null
    private var currentOutputFile: File? = null

    private var recordingStartTimeMs: Long = 0L

    override fun handleEvent(event: UiEvent) {}

    init {
        viewModelScope.launch {
            settingsRepository.hasSeenRecorderOnboardingFlow.collect { seen ->
                setState { copy(showRecorderOnboarding = !seen) }
            }
        }
        viewModelScope.launch {
            settingsRepository.lastAspectRatioFlow.collect { name ->
                setState { copy(aspectRatio = AspectRatio.fromName(name)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.lastRecordingQualityFlow.collect { name ->
                setState { copy(recordingQuality = RecordingQuality.fromName(name)) }
            }
        }
    }

    fun dismissRecorderOnboarding() {
        setState { copy(showRecorderOnboarding = false) }
        viewModelScope.launch {
            settingsRepository.setHasSeenRecorderOnboarding()
        }
    }

    fun setRecordingMode(mode: RecordingMode) {
        if (currentState.recordingState == RecordingState.IDLE) {
            setState { copy(recordingMode = mode) }
            viewModelScope.launch {
                settingsRepository.setLastRecordingMode(mode.name)
            }
        }
    }

    fun cycleAspectRatio() {
        if (currentState.recordingState != RecordingState.IDLE) return
        val next = AspectRatio.cycle(currentState.aspectRatio)
        setState { copy(aspectRatio = next) }
        viewModelScope.launch {
            settingsRepository.setLastAspectRatio(next.name)
        }
    }

    fun cycleRecordingQuality() {
        if (currentState.recordingState != RecordingState.IDLE) return
        val next = RecordingQuality.cycle(currentState.recordingQuality)
        setState { copy(recordingQuality = next) }
        viewModelScope.launch {
            settingsRepository.setLastRecordingQuality(next.name)
        }
    }

    fun updateImageTransform(scale: Float, offsetX: Float, offsetY: Float) {
        val currentBg = currentState.selectedBackground
        if (currentBg is BackgroundState.ImageBitmap) {
            setState { copy(selectedBackground = currentBg.copy(scale = scale, offsetX = offsetX, offsetY = offsetY)) }
        }
    }

    fun setSelectedBackground(bg: BackgroundState) {
        setState { copy(selectedBackground = bg) }
    }

    fun toggleAudioMuted() {
        setState { copy(isAudioMuted = !currentState.isAudioMuted) }
    }

    fun toggleGrid() { setState { copy(showGrid = !currentState.showGrid) } }
    fun setCountdownTimer(seconds: Int) { setState { copy(countdownTimer = seconds) } }
    fun toggleTeleprompter() { setState { copy(showTeleprompter = !currentState.showTeleprompter) } }
    fun updateTeleprompterText(text: String) { setState { copy(teleprompterText = text) } }
    fun toggleGestureDetection() { setState { copy(isGestureDetectionEnabled = !currentState.isGestureDetectionEnabled) } }

    fun requestExitRecording() {
        val state = currentState.recordingState
        if (state == RecordingState.IDLE || state == RecordingState.DONE) return
        setState { copy(showExitDialog = true) }
    }

    fun dismissExitDialog() {
        setState { copy(showExitDialog = false) }
    }

    fun saveAndExit() {
        setState { copy(showExitDialog = false) }
        stopRecording()
    }

    fun discardRecording() {
        setState { copy(showExitDialog = false) }
        val projectId = currentProjectId
        viewModelScope.launch {
            if (projectId != null) {
                try { projectRepository.deleteProject(projectId) } catch (_: Exception) {}
            }
        }
        currentProjectId = null
        currentOutputFile = null
        facelessRecorder?.stop()
        facelessRecorder = null
        stopTimer()
        setState {
            copy(
                recordingState = RecordingState.IDLE,
                elapsedSeconds = 0,
                segments = emptyList(),
                currentSegmentStartMs = 0L
            )
        }
    }

    fun requestStartRecording(forceCountdown: Int = 0, onProceedToCameraX: () -> Unit) {
        if (currentState.recordingState != RecordingState.IDLE || currentState.isCountdownActive) return

        val timer = maxOf(currentState.countdownTimer, forceCountdown)
        if (timer > 0) {
            setState { copy(isCountdownActive = true, countdownRemaining = timer) }
            viewModelScope.launch {
                for (i in timer downTo 1) {
                    setState { copy(countdownRemaining = i) }
                    delay(1000)
                }
                setState { copy(isCountdownActive = false) }
                if (currentState.recordingMode == RecordingMode.FACELESS) {
                    startFacelessRecordingInternal()
                } else {
                    onProceedToCameraX()
                }
            }
        } else {
            if (currentState.recordingMode == RecordingMode.FACELESS) {
                startFacelessRecordingInternal()
            } else {
                onProceedToCameraX()
            }
        }
    }

    private fun startFacelessRecordingInternal() {
        if (currentState.recordingState != RecordingState.IDLE) return

        viewModelScope.launch {
            val (projectId, outputFile) = projectRepository.createEmptyProjectForRecording()
            currentProjectId = projectId
            currentOutputFile = outputFile

            facelessRecorder = FacelessVideoRecorder()

            val bgState = currentState.selectedBackground
            val bitmap = (bgState as? BackgroundState.ImageBitmap)?.bitmap
            val scale = (bgState as? BackgroundState.ImageBitmap)?.scale ?: 1f
            val offsetX = (bgState as? BackgroundState.ImageBitmap)?.offsetX ?: 0f
            val offsetY = (bgState as? BackgroundState.ImageBitmap)?.offsetY ?: 0f
            val color = (bgState as? BackgroundState.SolidColor)?.color?.toArgb()
            val gradientColors = (bgState as? BackgroundState.Gradient)?.colors?.map { it.toArgb() }

            val quality = currentState.recordingQuality
            val ratio = currentState.aspectRatio

            setState { copy(recordingState = RecordingState.RECORDING, segments = emptyList(), currentSegmentStartMs = System.currentTimeMillis()) }
            startTimer()

            facelessRecorder?.start(
                width = ratio.width,
                height = ratio.height,
                fps = quality.fps,
                videoBitrate = quality.videoBitrate,
                audioBitrate = quality.audioBitrate,
                backgroundBitmap = bitmap,
                backgroundColor = color,
                gradientColors = gradientColors,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                outputFile = outputFile,
                onComplete = { file ->
                    val pId = currentProjectId ?: return@start
                    finalizeRecording(pId, file, null, null)
                },
                onError = { e ->
                    e.printStackTrace()
                    val pId = currentProjectId
                    if (pId != null) {
                        viewModelScope.launch {
                            try { projectRepository.deleteProject(pId) } catch (_: Exception) {}
                        }
                    }
                    currentProjectId = null
                    currentOutputFile = null
                    stopTimer()
                    setState {
                        copy(
                            recordingState = RecordingState.IDLE,
                            elapsedSeconds = 0,
                            segments = emptyList(),
                            currentSegmentStartMs = 0L
                        )
                    }
                },
                onAmplitude = { amp ->
                    setState { copy(audioAmplitude = amp) }
                }
            )
        }
    }

    fun pauseRecording() {
        val state = currentState.recordingState
        if (state != RecordingState.RECORDING) return

        val elapsed = System.currentTimeMillis() - currentState.currentSegmentStartMs
        val newSegment = RecordingSegment(
            index = currentState.segments.size + 1,
            durationMs = elapsed
        )

        if (currentState.recordingMode == RecordingMode.FACELESS) {
            facelessRecorder?.pause()
        }

        stopTimer()
        setState {
            copy(
                recordingState = RecordingState.PAUSED,
                segments = segments + newSegment
            )
        }
    }

    fun resumeRecording() {
        if (currentState.recordingState != RecordingState.PAUSED) return

        if (currentState.recordingMode == RecordingMode.FACELESS) {
            facelessRecorder?.resume()
        }

        setState { copy(recordingState = RecordingState.RECORDING, currentSegmentStartMs = System.currentTimeMillis()) }
        startTimer()
    }

    fun stopFacelessRecording() {
        if (currentState.recordingState == RecordingState.RECORDING || currentState.recordingState == RecordingState.PAUSED) {
            facelessRecorder?.stop()
        }
    }

    fun prepareCameraRecordingFile(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val (projectId, outputFile) = projectRepository.createEmptyProjectForRecording()
            currentProjectId = projectId
            currentOutputFile = outputFile
            onReady(outputFile)
        }
    }

    fun onCameraRecordingStarted() {
        setState {
            copy(
                recordingState = RecordingState.RECORDING,
                segments = emptyList(),
                currentSegmentStartMs = System.currentTimeMillis()
            )
        }
        startTimer()
    }

    fun onCameraRecordingStopped() {
        stopTimer()
        val pId = currentProjectId
        val file = currentOutputFile
        if (pId != null && file != null) {
            finalizeRecording(pId, file, null, null)
        } else {
            setState { copy(recordingState = RecordingState.IDLE) }
        }
    }

    fun onCameraRecordingError() {
        val pId = currentProjectId
        if (pId != null) {
            viewModelScope.launch {
                try { projectRepository.deleteProject(pId) } catch (_: Exception) {}
            }
        }
        currentProjectId = null
        currentOutputFile = null
        stopTimer()
        setState {
            copy(
                recordingState = RecordingState.IDLE,
                elapsedSeconds = 0,
                segments = emptyList(),
                currentSegmentStartMs = 0L
            )
        }
    }

    fun stopRecording() {
        if (currentState.recordingState == RecordingState.IDLE || currentState.recordingState == RecordingState.DONE) return
        if (currentState.recordingMode == RecordingMode.FACELESS) {
            facelessRecorder?.stop()
        }
    }

    fun resetState() {
        val projectIdToDelete = currentProjectId
        setState {
            copy(
                recordingState = RecordingState.IDLE,
                finishedProjectId = null,
                finishedVideoFile = null,
                elapsedSeconds = 0,
                segments = emptyList(),
                currentSegmentStartMs = 0L
            )
        }
        currentProjectId = null
        currentOutputFile = null
        facelessRecorder?.stop()
        facelessRecorder = null
        stopTimer()
        if (projectIdToDelete != null) {
            viewModelScope.launch {
                projectRepository.deleteProject(projectIdToDelete)
            }
        }
    }

    private fun finalizeRecording(projectId: String, file: File, bgType: String?, bgValue: String?) {
        viewModelScope.launch {
            val result = projectRepository.finalizeRecordedProject(projectId, file, bgType, bgValue)
            if (result.isSuccess) {
                setState {
                    copy(
                        recordingState = RecordingState.DONE,
                        finishedProjectId = result.getOrNull(),
                        finishedVideoFile = file,
                        segments = emptyList(),
                        currentSegmentStartMs = 0L
                    )
                }
            } else {
                setState {
                    copy(
                        recordingState = RecordingState.IDLE,
                        segments = emptyList(),
                        currentSegmentStartMs = 0L
                    )
                }
            }
            stopTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        recordingStartTimeMs = System.currentTimeMillis()
        val startElapsed = currentState.elapsedSeconds
        timerJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val wallElapsed = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000).toInt()
                setState { copy(elapsedSeconds = startElapsed + wallElapsed) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        facelessRecorder?.stop()
        stopTimer()
    }
}

package com.dipdev.aiautocaptioner.ui.recorder

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.engine.FacelessVideoRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class RecordingMode {
    CAMERA, FACELESS
}

enum class RecordingState {
    IDLE, RECORDING, DONE
}

sealed class BackgroundState {
    data class SolidColor(val color: Color) : BackgroundState()
    data class ImageBitmap(val bitmap: Bitmap) : BackgroundState()
    data class Gradient(val colors: List<Color>) : BackgroundState()
}


data class SmartRecorderState(
    val recordingMode: RecordingMode = RecordingMode.CAMERA,
    val selectedBackground: BackgroundState = BackgroundState.SolidColor(Color.Black),
    val recordingState: RecordingState = RecordingState.IDLE,
    val elapsedSeconds: Int = 0,
    val finishedProjectId: String? = null,
    val isAudioMuted: Boolean = false,
    val showGrid: Boolean = false,
    val countdownTimer: Int = 0,
    val showTeleprompter: Boolean = false,
    val teleprompterText: String = "",
    val audioAmplitude: Float = 0f,
    val isCountdownActive: Boolean = false,
    val countdownRemaining: Int = 0
) : UiState

@HiltViewModel
class SmartRecorderViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val projectRepository: ProjectRepository
) : BaseViewModel<SmartRecorderState, UiEvent, UiEffect>(SmartRecorderState()) {

    val recordingMode: StateFlow<RecordingMode> = uiState.map { it.recordingMode }.stateInDefault(viewModelScope, currentState.recordingMode)

    val selectedBackground: StateFlow<BackgroundState> = uiState.map { it.selectedBackground }.stateInDefault(viewModelScope, currentState.selectedBackground)

    val recordingState: StateFlow<RecordingState> = uiState.map { it.recordingState }.stateInDefault(viewModelScope, currentState.recordingState)

    val elapsedSeconds: StateFlow<Int> = uiState.map { it.elapsedSeconds }.stateInDefault(viewModelScope, currentState.elapsedSeconds)

    // Emits the finalized projectId when recording is fully complete
    val finishedProjectId: StateFlow<String?> = uiState.map { it.finishedProjectId }.stateInDefault(viewModelScope, currentState.finishedProjectId)
    
    // State for Mute button in Camera mode
    val isAudioMuted: StateFlow<Boolean> = uiState.map { it.isAudioMuted }.stateInDefault(viewModelScope, currentState.isAudioMuted)

    // New Creator Tools States
    val showGrid: StateFlow<Boolean> = uiState.map { it.showGrid }.stateInDefault(viewModelScope, currentState.showGrid)

    private val _countdownTimer = MutableStateFlow(0) // 0, 3, 10
    val countdownTimer = _countdownTimer.asStateFlow()

    val showTeleprompter: StateFlow<Boolean> = uiState.map { it.showTeleprompter }.stateInDefault(viewModelScope, currentState.showTeleprompter)

    val teleprompterText: StateFlow<String> = uiState.map { it.teleprompterText }.stateInDefault(viewModelScope, currentState.teleprompterText)

    val audioAmplitude: StateFlow<Float> = uiState.map { it.audioAmplitude }.stateInDefault(viewModelScope, currentState.audioAmplitude)

    val isCountdownActive: StateFlow<Boolean> = uiState.map { it.isCountdownActive }.stateInDefault(viewModelScope, currentState.isCountdownActive)

    val countdownRemaining: StateFlow<Int> = uiState.map { it.countdownRemaining }.stateInDefault(viewModelScope, currentState.countdownRemaining)

    private var facelessRecorder: FacelessVideoRecorder? = null
    private var timerJob: Job? = null
    
    private var currentProjectId: String? = null
    private var currentOutputFile: File? = null

    override fun handleEvent(event: UiEvent) {}

    init {
        viewModelScope.launch {
            settingsRepository.lastRecordingModeFlow.collect { modeStr ->
                if (currentState.recordingState == RecordingState.IDLE) {
                    setState { copy(recordingMode = if (modeStr == "FACELESS") RecordingMode.FACELESS else RecordingMode.CAMERA) }
                }
            }
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

    fun requestStartRecording(onProceedToCameraX: () -> Unit) {
        if (currentState.recordingState != RecordingState.IDLE || currentState.isCountdownActive) return
        
        val timer = currentState.countdownTimer
        if (timer > 0) {
            setState { copy(isCountdownActive = true) }
            setState { copy(countdownRemaining = timer) }
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
            // Allocate directly to project directory
            val (projectId, outputFile) = projectRepository.createEmptyProjectForRecording()
            currentProjectId = projectId
            currentOutputFile = outputFile
            
            facelessRecorder = FacelessVideoRecorder()

            val bgState = currentState.selectedBackground
            val bitmap = (bgState as? BackgroundState.ImageBitmap)?.bitmap
            val color = (bgState as? BackgroundState.SolidColor)?.color?.toArgb()
            
            val bgType = if (bgState is BackgroundState.ImageBitmap) "image" else "color"
            val bgValue = color?.toString() ?: "" // Simple representation

            setState { copy(recordingState = RecordingState.RECORDING) }
            startTimer()

            facelessRecorder?.start(
                backgroundBitmap = bitmap,
                backgroundColor = color,
                outputFile = outputFile,
                onComplete = { file ->
                    finalizeRecording(projectId, file, bgType, bgValue)
                },
                onError = { e ->
                    e.printStackTrace()
                    setState { copy(recordingState = RecordingState.IDLE) }
                    stopTimer()
                },
                onAmplitude = { amp ->
                    setState { copy(audioAmplitude = amp) }
                }
            )
        }
    }

    fun stopFacelessRecording() {
        if (currentState.recordingState == RecordingState.RECORDING) {
            facelessRecorder?.stop()
        }
    }
    
    // For CameraX: Creates the file upfront so UI can configure output options
    fun prepareCameraRecordingFile(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val (projectId, outputFile) = projectRepository.createEmptyProjectForRecording()
            currentProjectId = projectId
            currentOutputFile = outputFile
            onReady(outputFile)
        }
    }

    fun onCameraRecordingStarted() {
        setState { copy(recordingState = RecordingState.RECORDING) }
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
        setState { copy(recordingState = RecordingState.IDLE) }
        stopTimer()
    }
    
    fun stopRecording() {
        if (currentState.recordingMode == RecordingMode.FACELESS) {
            stopFacelessRecording()
        } else {
            // CameraX is stopped from UI, but if we need a forced stop from VM:
            // The UI should really handle stopping CameraX.
            // We just let the UI call UI-side stop.
        }
    }
    
    private fun finalizeRecording(projectId: String, file: File, bgType: String?, bgValue: String?) {
        viewModelScope.launch {
            val result = projectRepository.finalizeRecordedProject(projectId, file, bgType, bgValue)
            if (result.isSuccess) {
                setState { copy(recordingState = RecordingState.DONE) }
                setState { copy(finishedProjectId = result.getOrNull()) }
            } else {
                setState { copy(recordingState = RecordingState.IDLE) }
            }
        }
    }

    fun resetState() {
        setState { copy(recordingState = RecordingState.IDLE) }
        setState { copy(finishedProjectId = null) }
        setState { copy(elapsedSeconds = 0) }
        currentProjectId = null
        currentOutputFile = null
    }

    private fun startTimer() {
        setState { copy(elapsedSeconds = 0) }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                setState { copy(elapsedSeconds = currentState.elapsedSeconds + 1) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}

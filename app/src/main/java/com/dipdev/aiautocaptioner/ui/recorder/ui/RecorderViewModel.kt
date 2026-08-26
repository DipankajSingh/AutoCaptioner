package com.dipdev.aiautocaptioner.ui.recorder.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.recorder.camera.ActiveRecording
import com.dipdev.aiautocaptioner.ui.recorder.camera.CameraEngine
import com.dipdev.aiautocaptioner.ui.recorder.camera.CameraState
import com.dipdev.aiautocaptioner.ui.recorder.recording.FacelessRecorder
import com.dipdev.aiautocaptioner.ui.recorder.model.AspectRatio
import com.dipdev.aiautocaptioner.ui.recorder.model.BackgroundState
import com.dipdev.aiautocaptioner.ui.recorder.model.RecorderEffect
import com.dipdev.aiautocaptioner.ui.recorder.model.RecorderEvent
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingMode
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingQuality
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingSegment
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingState
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

data class RecorderState(
    val recordingMode: RecordingMode = RecordingMode.CAMERA,
    val selectedBackground: BackgroundState = BackgroundState.Default,
    val recordingState: RecordingState = RecordingState.Idle,
    val elapsedSeconds: Int = 0,
    val finishedProjectId: String? = null,
    val finishedVideoFile: File? = null,
    val isAudioMuted: Boolean = false,
    val showGrid: Boolean = false,
    val countdownTimer: Int = 3,
    val showTeleprompter: Boolean = false,
    val teleprompterText: String = "",
    val audioAmplitude: Float = 0f,
    val isCountdownActive: Boolean = false,
    val countdownRemaining: Int = 0,
    val isGestureDetectionEnabled: Boolean = false,
    val aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val recordingQuality: RecordingQuality = RecordingQuality.MEDIUM,
    val showExitDialog: Boolean = false,
    val segments: List<RecordingSegment> = emptyList(),
    val currentSegmentStartMs: Long = 0L,
    val activeFilter: CreatorFilter = CreatorFilter.NATURAL,
    val smoothnessIntensity: Float = 0.35f,
    val isSmoothnessSliderVisible: Boolean = false,
    val recentlySelectedFilterName: String? = null,
    val shouldStartCameraRecording: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isTorchOn: Boolean = false,
) : com.dipdev.aiautocaptioner.ui.base.UiState

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val projectRepository: ProjectRepository,
    val crashReporter: CrashReporter,
    private val cameraEngine: CameraEngine,
    private val facelessRecorder: FacelessRecorder,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<RecorderState, RecorderEvent, RecorderEffect>(
    RecorderState(
        recordingMode = if (savedStateHandle.get<String>("mode") == "FACELESS") RecordingMode.FACELESS else RecordingMode.CAMERA
    )
) {

    private var timerJob: Job? = null
    private var filterBadgeTimerJob: Job? = null
    private var debounceSaveSmoothnessJob: Job? = null

    private var currentProjectId: String? = null
    private var currentOutputFile: File? = null
    @Volatile private var prewarmedProject: Pair<String, File>? = null

    @Volatile var activeCameraRecording: ActiveRecording? = null
        private set

    val cameraEngineRef: CameraEngine get() = cameraEngine

    override fun handleEvent(event: RecorderEvent) {
        when (event) {
            is RecorderEvent.SetRecordingMode -> setRecordingMode(event.mode)
            is RecorderEvent.CycleAspectRatio -> cycleAspectRatio()
            is RecorderEvent.CycleRecordingQuality -> cycleRecordingQuality()
            is RecorderEvent.SelectFilter -> selectFilter(event.filter)
            is RecorderEvent.UpdateSmoothness -> updateSmoothness(event.intensity)
            is RecorderEvent.ToggleGrid -> toggleGrid()
            is RecorderEvent.ToggleTeleprompter -> toggleTeleprompter()
            is RecorderEvent.UpdateTeleprompterText -> updateTeleprompterText(event.text)
            is RecorderEvent.ToggleGestureDetection -> toggleGestureDetection()
            is RecorderEvent.SetCountdownTimer -> setCountdownTimer(event.seconds)
            is RecorderEvent.ToggleSmoothnessSlider -> toggleSmoothnessSlider()
            is RecorderEvent.DismissSubControls -> dismissSubControls()
            is RecorderEvent.SetSelectedBackground -> setSelectedBackground(event.background)
            is RecorderEvent.ToggleMute -> toggleMute()
            is RecorderEvent.FlipCamera -> flipCamera()
            is RecorderEvent.ToggleTorch -> toggleTorch()
            is RecorderEvent.RequestExitRecording -> requestExitRecording()
            is RecorderEvent.DismissExitDialog -> dismissExitDialog()
            is RecorderEvent.SaveAndExit -> saveAndExit()
            is RecorderEvent.DiscardRecording -> discardRecording()
            is RecorderEvent.StartRecording -> startRecording()
            is RecorderEvent.PauseRecording -> pauseRecording()
            is RecorderEvent.ResumeRecording -> resumeRecording()
            is RecorderEvent.StopRecording -> stopRecording()
            is RecorderEvent.ResetState -> resetState()
        }
    }

    init {
        prewarmProject()
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
        viewModelScope.launch {
            settingsRepository.selectedCreatorFilterFlow.collect { filter ->
                setState { copy(activeFilter = filter) }
            }
        }
        viewModelScope.launch {
            settingsRepository.skinSmoothnessIntensityFlow.collect { intensity ->
                setState { copy(smoothnessIntensity = intensity) }
            }
        }
        viewModelScope.launch {
            settingsRepository.previewFpsFlow.collect { fps ->
                cameraEngine.setPreviewFps(fps)
            }
        }
        viewModelScope.launch {
            cameraEngine.state.collect { camState ->
                if (camState is CameraState.Error) {
                    setEffect(RecorderEffect.ShowError("Camera error occurred"))
                    setState {
                        copy(
                            recordingState = RecordingState.Failed(
                                RecordingError.Unknown("Camera error occurred")
                            )
                        )
                    }
                }
            }
        }
    }

    private fun prewarmProject() {
        viewModelScope.launch(Dispatchers.IO) {
            if (prewarmedProject == null) {
                try {
                    prewarmedProject = projectRepository.createEmptyProjectForRecording()
                } catch (e: Exception) {
                    crashReporter.recordException(e)
                }
            }
        }
    }

    private suspend fun getOrCreateProject(): Pair<String, File> {
        return prewarmedProject?.also {
            prewarmedProject = null
            prewarmProject()
        } ?: projectRepository.createEmptyProjectForRecording()
    }

    private fun setRecordingMode(mode: RecordingMode) {
        if (currentState.recordingState !is RecordingState.Idle) return
        if (currentState.isCountdownActive) return
        setState { copy(recordingMode = mode) }
        viewModelScope.launch {
            settingsRepository.setLastRecordingMode(mode.name)
        }
    }

    private fun cycleAspectRatio() {
        if (currentState.recordingState !is RecordingState.Idle) return
        val next = AspectRatio.cycle(currentState.aspectRatio)
        setState { copy(aspectRatio = next) }
        viewModelScope.launch {
            settingsRepository.setLastAspectRatio(next.name)
        }
    }

    private fun cycleRecordingQuality() {
        if (currentState.recordingState !is RecordingState.Idle) return
        val next = RecordingQuality.cycle(currentState.recordingQuality)
        setState { copy(recordingQuality = next) }
        viewModelScope.launch {
            settingsRepository.setLastRecordingQuality(next.name)
        }
    }

    private fun setSelectedBackground(bg: BackgroundState) {
        setState { copy(selectedBackground = bg) }
    }

    private fun toggleGrid() { setState { copy(showGrid = !showGrid) } }
    private fun setCountdownTimer(seconds: Int) { setState { copy(countdownTimer = seconds) } }
    private fun toggleTeleprompter() { setState { copy(showTeleprompter = !showTeleprompter) } }
    private fun updateTeleprompterText(text: String) { setState { copy(teleprompterText = text) } }
    private fun toggleGestureDetection() { setState { copy(isGestureDetectionEnabled = !isGestureDetectionEnabled) } }
    private fun toggleMute() { setState { copy(isAudioMuted = !isAudioMuted) } }

    private fun toggleSmoothnessSlider() {
        setState {
            copy(
                isSmoothnessSliderVisible = !isSmoothnessSliderVisible
            )
        }
    }

    private fun dismissSubControls() {
        if (currentState.isSmoothnessSliderVisible) {
            setState { copy(isSmoothnessSliderVisible = false) }
        }
    }

    private fun selectFilter(filter: CreatorFilter) {
        setState { copy(activeFilter = filter, recentlySelectedFilterName = filter.displayName) }
        viewModelScope.launch {
            settingsRepository.setCreatorFilter(filter)
        }
        filterBadgeTimerJob?.cancel()
        filterBadgeTimerJob = viewModelScope.launch {
            delay(1800.milliseconds)
            setState { copy(recentlySelectedFilterName = null) }
        }
    }

    private fun updateSmoothness(intensity: Float) {
        val clamped = intensity.coerceIn(0.0f, 1.0f)
        setState { copy(smoothnessIntensity = clamped) }
        debounceSaveSmoothnessJob?.cancel()
        debounceSaveSmoothnessJob = viewModelScope.launch {
            delay(500.milliseconds)
            settingsRepository.setSkinSmoothnessIntensity(clamped)
        }
    }

    private fun flipCamera() {
        cameraEngine.flipCamera()
        setState { copy(isFrontCamera = !isFrontCamera) }
    }

    private fun toggleTorch() {
        val newState = !currentState.isTorchOn
        cameraEngine.setTorch(newState)
        setState { copy(isTorchOn = newState) }
    }

    private fun requestExitRecording() {
        val state = currentState.recordingState
        if (state is RecordingState.Idle || state is RecordingState.Finalized || state is RecordingState.Failed) return
        setState { copy(showExitDialog = true) }
    }

    private fun dismissExitDialog() {
        setState { copy(showExitDialog = false) }
    }

    private fun saveAndExit() {
        setState { copy(showExitDialog = false) }
        stopRecording()
    }

    private fun discardRecording() {
        setState { copy(showExitDialog = false) }
        val projectId = currentProjectId
        val file = currentOutputFile
        viewModelScope.launch {
            if (projectId != null) {
                try { projectRepository.deleteProject(projectId) } catch (_: Exception) {}
            }
        }
        file?.delete()
        currentProjectId = null
        currentOutputFile = null
        facelessRecorder.release()
        stopTimer()
        setState {
            copy(
                recordingState = RecordingState.Idle,
                elapsedSeconds = 0,
                segments = emptyList(),
                currentSegmentStartMs = 0L
            )
        }
    }

    fun startRecording() {
        if (currentState.recordingState !is RecordingState.Idle && currentState.recordingState !is RecordingState.Failed) return
        if (currentState.isCountdownActive) return

        val timer = currentState.countdownTimer
        if (timer > 0) {
            setState { copy(isCountdownActive = true, countdownRemaining = timer) }
            viewModelScope.launch {
                try {
                    for (i in timer downTo 1) {
                        setState { copy(countdownRemaining = i) }
                        delay(1000.milliseconds)
                    }
                    launchRecording()
                } finally {
                    setState { copy(isCountdownActive = false) }
                }
            }
        } else {
            launchRecording()
        }
    }

    private fun launchRecording() {
        if (currentState.recordingMode == RecordingMode.FACELESS) {
            launchFacelessRecording()
        } else {
            setState { copy(shouldStartCameraRecording = true) }
        }
    }

    fun clearShouldStartCameraRecording() {
        setState { copy(shouldStartCameraRecording = false) }
    }

    fun startCameraRecording(file: java.io.File) {
        val quality = currentState.recordingQuality
        val ratio = currentState.aspectRatio
        activeCameraRecording = cameraEngine.startRecording(
            file = file,
            videoWidth = ratio.width,
            videoHeight = ratio.height,
            videoBitrate = quality.videoBitrate,
            videoFrameRate = quality.fps,
            audioBitrate = quality.audioBitrate,
            listener = object : com.dipdev.aiautocaptioner.ui.recorder.camera.RecordingListener {
                override fun onRecordingStarted() {
                    onCameraRecordingStarted()
                }
                override fun onRecordingFinished(file: java.io.File) {
                    activeCameraRecording = null
                    onCameraRecordingStopped()
                }
                override fun onRecordingError(error: Throwable) {
                    activeCameraRecording = null
                    onCameraRecordingError()
                }
            }
        )
    }

    fun stopCameraRecording() {
        activeCameraRecording?.stop()
        activeCameraRecording = null
    }

    fun prepareCameraRecordingFile(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val (projectId, outputFile) = getOrCreateProject()
            currentProjectId = projectId
            currentOutputFile = outputFile
            onReady(outputFile)
        }
    }

    fun onCameraRecordingStarted() {
        setState {
            copy(
                recordingState = RecordingState.Recording,
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
            finalizeRecording(pId, file)
        } else {
            setState { copy(recordingState = RecordingState.Idle) }
        }
    }

    fun onCameraRecordingError() {
        val pId = currentProjectId
        val file = currentOutputFile
        if (pId != null) {
            viewModelScope.launch {
                try { projectRepository.deleteProject(pId) } catch (_: Exception) {}
            }
        }
        file?.delete()
        currentProjectId = null
        currentOutputFile = null
        stopTimer()
        setEffect(RecorderEffect.ShowError("Camera recording error"))
        setState {
            copy(
                recordingState = RecordingState.Failed(RecordingError.Unknown("Camera recording error")),
                elapsedSeconds = 0,
                segments = emptyList(),
                currentSegmentStartMs = 0L
            )
        }
    }

    private fun launchFacelessRecording() {
        viewModelScope.launch {
            val (projectId, outputFile) = getOrCreateProject()
            currentProjectId = projectId
            currentOutputFile = outputFile

            val bgState = currentState.selectedBackground
            val color = (bgState as? BackgroundState.SolidColor)?.color?.toArgb()
            val gradientColors = (bgState as? BackgroundState.Gradient)?.colors?.map { it.toArgb() }

            val quality = currentState.recordingQuality
            val ratio = currentState.aspectRatio

            setState {
                copy(
                    recordingState = RecordingState.Recording,
                    segments = emptyList(),
                    currentSegmentStartMs = System.currentTimeMillis()
                )
            }
            startTimer()

            withContext(Dispatchers.IO) {
                facelessRecorder.startFacelessRecording(
                    file = outputFile,
                    width = ratio.width,
                    height = ratio.height,
                    fps = quality.fps,
                    videoBitrate = quality.videoBitrate,
                    audioBitrate = quality.audioBitrate,
                    backgroundColor = color,
                    gradientColors = gradientColors,
                    muted = currentState.isAudioMuted,
                    onComplete = { file ->
                        val pId = currentProjectId ?: return@startFacelessRecording
                        finalizeRecording(pId, file)
                    },
                    onError = { e ->
                        val pId = currentProjectId
                        val file = currentOutputFile
                        if (pId != null) {
                            viewModelScope.launch {
                                try { projectRepository.deleteProject(pId) } catch (_: Exception) {}
                            }
                        }
                        file?.delete()
                        currentProjectId = null
                        currentOutputFile = null
                        stopTimer()
                        setEffect(RecorderEffect.ShowError(e.message ?: "Recording failed"))
                        setState {
                            copy(
                                recordingState = RecordingState.Failed(RecordingError.Unknown(e.message ?: "Faceless recording error")),
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
    }

    private fun pauseRecording() {
        val state = currentState.recordingState
        if (state !is RecordingState.Recording) return

        val elapsed = System.currentTimeMillis() - currentState.currentSegmentStartMs
        val newSegment = RecordingSegment(
            index = currentState.segments.size + 1,
            durationMs = elapsed
        )

        if (currentState.recordingMode == RecordingMode.FACELESS) {
            facelessRecorder.pause()
        }

        stopTimer()
        setState {
            copy(
                recordingState = RecordingState.Paused,
                segments = segments + newSegment
            )
        }
    }

    private fun resumeRecording() {
        if (currentState.recordingState !is RecordingState.Paused) return

        if (currentState.recordingMode == RecordingMode.FACELESS) {
            facelessRecorder.resume()
        }

        setState { copy(recordingState = RecordingState.Recording, currentSegmentStartMs = System.currentTimeMillis()) }
        startTimer()
    }

    private fun stopRecording() {
        if (currentState.recordingState is RecordingState.Idle || currentState.recordingState is RecordingState.Finalized) return
        if (currentState.recordingMode == RecordingMode.FACELESS) {
            facelessRecorder.stop()
        } else {
            stopCameraRecording()
        }
    }

    fun resetState() {
        val projectIdToDelete = currentProjectId
        stopCameraRecording()
        setState {
            copy(
                recordingState = RecordingState.Idle,
                finishedProjectId = null,
                finishedVideoFile = null,
                elapsedSeconds = 0,
                segments = emptyList(),
                currentSegmentStartMs = 0L
            )
        }
        currentProjectId = null
        currentOutputFile = null
        facelessRecorder.release()
        stopTimer()
        prewarmProject()
        if (projectIdToDelete != null) {
            viewModelScope.launch {
                projectRepository.deleteProject(projectIdToDelete)
            }
        }
    }

    private fun finalizeRecording(projectId: String, file: File) {
        viewModelScope.launch {
            val result = projectRepository.finalizeRecordedProject(projectId, file, null, null)
            if (result.isSuccess) {
                setState {
                    copy(
                        recordingState = RecordingState.Finalized(projectId, file),
                        finishedProjectId = result.getOrNull(),
                        finishedVideoFile = file,
                        segments = emptyList(),
                        currentSegmentStartMs = 0L
                    )
                }
            } else {
                setState {
                    copy(
                        recordingState = RecordingState.Idle,
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
        val startElapsed = currentState.elapsedSeconds
        val startTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(500.milliseconds)
                val wallElapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                setState { copy(elapsedSeconds = startElapsed + wallElapsed) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        stopCameraRecording()
        facelessRecorder.release()
        cameraEngine.close()
        stopTimer()
    }
}

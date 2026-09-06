package com.dipdev.aiautocaptioner.ui.recorder

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.camera.Camera2Session
import com.dipdev.aiautocaptioner.core.camera.CameraCapabilities
import com.dipdev.aiautocaptioner.core.camera.CameraFacing
import com.dipdev.aiautocaptioner.core.camera.RecordingAspectRatio
import com.dipdev.aiautocaptioner.core.camera.RecordingQuality
import com.dipdev.aiautocaptioner.core.gl.RendererThread
import com.dipdev.aiautocaptioner.core.media.RecordingSession
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State ─────────────────────────────────────────────────────────────────────

data class RecorderState(
    val permissionPhase: PermissionPhase = PermissionPhase.Checking,
    val cameraFacing: CameraFacing = CameraFacing.BACK,
    val quality: RecordingQuality = RecordingQuality.DEFAULT,
    val availableQualities: List<RecordingQuality> = RecordingQuality.ALL,
    val aspectRatio: RecordingAspectRatio = RecordingAspectRatio.DEFAULT,
    val torchOn: Boolean = false,
    val zoomRatio: Float = 1f,
    val recordingPhase: RecordingPhase = RecordingPhase.Idle,
    val elapsedMs: Long = 0L
) : UiState

sealed interface PermissionPhase {
    data object Checking : PermissionPhase
    data object Granted : PermissionPhase
    data class Denied(
        val cameraPermanent: Boolean,
        val micPermanent: Boolean
    ) : PermissionPhase
}

sealed interface RecordingPhase {
    data object Idle : RecordingPhase
    data object Previewing : RecordingPhase
    data object Recording : RecordingPhase
    data object Paused : RecordingPhase
    data object Finalizing : RecordingPhase
    data class Error(val message: String) : RecordingPhase
}

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface RecorderEvent : UiEvent {
    data class PermissionsResult(
        val cameraGranted: Boolean,
        val micGranted: Boolean,
        val cameraPermanentlyDenied: Boolean,
        val micPermanentlyDenied: Boolean
    ) : RecorderEvent
    data object SwitchCamera : RecorderEvent
    data class SelectQuality(val quality: RecordingQuality) : RecorderEvent
    data class SelectAspectRatio(val ratio: RecordingAspectRatio) : RecorderEvent
    data object ToggleTorch : RecorderEvent
    data class SetZoom(val ratio: Float) : RecorderEvent
    data class TapToFocus(val x: Float, val y: Float) : RecorderEvent
    data object StartRecording : RecorderEvent
    data object PauseRecording : RecorderEvent
    data object ResumeRecording : RecorderEvent
    data object StopRecording : RecorderEvent
    data object NavigateBack : RecorderEvent
}

// ── Effects ───────────────────────────────────────────────────────────────────

sealed interface RecorderEffect : UiEffect {
    data class NavigateToEditor(val projectId: String) : RecorderEffect
    data object NavigateBack : RecorderEffect
    data class ShowError(val message: String) : RecorderEffect
    data object LaunchPermissionRequest : RecorderEffect
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val application: Application,
    private val projectRepository: ProjectRepository
) : BaseViewModel<RecorderState, RecorderEvent, RecorderEffect>(RecorderState()) {

    private val context: Context get() = application.applicationContext

    /**
     * Exposed directly (not in state) so the Compose screen can pass it to [CameraPreviewView]
     * without boxing it into an observable. The RendererThread lifecycle matches the ViewModel's.
     */
    val rendererThread: RendererThread = RendererThread(
        onCameraSurfaceReady = { surface -> onCameraSurfaceReady(surface) }
    ).also { it.start() }

    private val capabilities = CameraCapabilities(context)
    private var cameraSession: Camera2Session? = null
    private var recordingSession: RecordingSession? = null
    private var currentProjectId: String? = null
    private var timerJob: Job? = null

    // Deferred camera surface — RendererThread delivers it asynchronously.
    private var pendingCameraSurface: android.view.Surface? = null
    private var cameraReady = false

    override fun handleEvent(event: RecorderEvent) {
        when (event) {
            is RecorderEvent.PermissionsResult -> handlePermissions(event)
            is RecorderEvent.SwitchCamera -> switchCamera()
            is RecorderEvent.SelectQuality -> changeQuality(event.quality)
            is RecorderEvent.SelectAspectRatio -> changeAspectRatio(event.ratio)
            is RecorderEvent.ToggleTorch -> toggleTorch()
            is RecorderEvent.SetZoom -> setZoom(event.ratio)
            is RecorderEvent.TapToFocus -> tapToFocus(event.x, event.y)
            is RecorderEvent.StartRecording -> startRecording()
            is RecorderEvent.PauseRecording -> pauseRecording()
            is RecorderEvent.ResumeRecording -> resumeRecording()
            is RecorderEvent.StopRecording -> stopRecording()
            is RecorderEvent.NavigateBack -> setEffect(RecorderEffect.NavigateBack)
        }
    }

    // ── Permission ────────────────────────────────────────────────────────────

    private fun handlePermissions(event: RecorderEvent.PermissionsResult) {
        if (event.cameraGranted && event.micGranted) {
            setState { copy(permissionPhase = PermissionPhase.Granted) }
            openCamera()
        } else {
            setState {
                copy(
                    permissionPhase = PermissionPhase.Denied(
                        cameraPermanent = event.cameraPermanentlyDenied,
                        micPermanent = event.micPermanentlyDenied
                    )
                )
            }
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private fun openCamera() {
        viewModelScope.launch {
            val facing = currentState.cameraFacing
            val cameraId = capabilities.findCameraId(facing)
            if (cameraId == null) {
                setEffect(RecorderEffect.ShowError(context.getString(
                    com.dipdev.aiautocaptioner.R.string.recorder_error_no_camera
                )))
                return@launch
            }

            val ratio = currentState.aspectRatio
            val quality = currentState.quality
            val supportedQualities = capabilities.supportedQualities(cameraId, ratio)
            val effectiveQuality = if (quality in supportedQualities) quality
                                   else supportedQualities.lastOrNull() ?: quality

            val streamSize = capabilities.bestCameraStreamSize(cameraId, effectiveQuality, ratio)
            rendererThread.setCameraFrameSize(streamSize.width, streamSize.height)
            rendererThread.setAspectRatio(ratio)

            // Wait for both the GL input surface (RendererThread → camera OES texture)
            // AND the preview output surface (TextureView → previewWindowSurface) to be
            // ready before opening Camera2. Without this, early frames hit a null
            // previewWindowSurface and the draw loop falls back to the offscreen buffer,
            // producing a permanently black preview.
            val cameraSurface = awaitCameraSurface()
            awaitPreviewSurface()

            val session = Camera2Session(context, cameraId, cameraSurface)
            rendererThread.setTransformParams(
                session.sensorOrientation,
                mirrorPreviewForFrontCamera = facing == CameraFacing.FRONT
            )
            session.open()
            cameraSession = session

            setState {
                copy(
                    availableQualities = supportedQualities,
                    quality = effectiveQuality,
                    recordingPhase = RecordingPhase.Previewing
                )
            }
        }
    }

    private fun switchCamera() {
        if (currentState.recordingPhase is RecordingPhase.Recording ||
            currentState.recordingPhase is RecordingPhase.Paused) return

        val newFacing = if (currentState.cameraFacing == CameraFacing.BACK)
            CameraFacing.FRONT else CameraFacing.BACK

        cameraSession?.close()
        cameraSession = null
        // NOTE: do NOT reset cameraReady / pendingCameraSurface here.
        // The OES SurfaceTexture is created once by RendererThread and stays alive for
        // the entire ViewModel lifetime. onCameraSurfaceReady only fires once, so resetting
        // these flags would cause awaitCameraSurface() to hang forever on the next open.

        setState { copy(cameraFacing = newFacing, torchOn = false) }
        openCamera()
    }

    private fun changeQuality(quality: RecordingQuality) {
        if (currentState.recordingPhase is RecordingPhase.Recording ||
            currentState.recordingPhase is RecordingPhase.Paused) return
        if (quality == currentState.quality) return

        cameraSession?.close()
        cameraSession = null
        // NOTE: same as switchCamera — do not reset cameraReady/pendingCameraSurface.

        setState { copy(quality = quality) }
        openCamera()
    }

    private fun changeAspectRatio(ratio: RecordingAspectRatio) {
        if (currentState.recordingPhase is RecordingPhase.Recording ||
            currentState.recordingPhase is RecordingPhase.Paused) return
        if (ratio == currentState.aspectRatio) return

        cameraSession?.close()
        cameraSession = null
        // NOTE: same as switchCamera — do not reset cameraReady/pendingCameraSurface.

        setState { copy(aspectRatio = ratio) }
        openCamera()
    }

    private fun toggleTorch() {
        val newTorch = !currentState.torchOn
        cameraSession?.setTorch(newTorch)
        setState { copy(torchOn = newTorch) }
    }

    private fun setZoom(ratio: Float) {
        cameraSession?.setZoom(ratio)
        setState { copy(zoomRatio = ratio) }
    }

    private fun tapToFocus(x: Float, y: Float) {
        // Camera2Session does not expose tap-to-focus in Stage 1–3; no-op for now.
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    private fun startRecording() {
        if (currentState.recordingPhase != RecordingPhase.Previewing) return
        viewModelScope.launch {
            setState { copy(recordingPhase = RecordingPhase.Recording) }
            try {
                val (projectId, outputFile) = projectRepository.createEmptyProjectForRecording()
                currentProjectId = projectId

                val session = RecordingSession(
                    outputFile = outputFile,
                    quality = currentState.quality,
                    aspectRatio = currentState.aspectRatio,
                    rendererThread = rendererThread,
                    onDurationCapReached = { setEvent(RecorderEvent.StopRecording) }
                )
                recordingSession = session
                session.start()
                startElapsedTimer()
            } catch (e: Exception) {
                setState { copy(recordingPhase = RecordingPhase.Error(e.message ?: "Recording failed")) }
            }
        }
    }

    private fun pauseRecording() {
        if (currentState.recordingPhase != RecordingPhase.Recording) return
        recordingSession?.pause()
        timerJob?.cancel()
        setState { copy(recordingPhase = RecordingPhase.Paused) }
    }

    private fun resumeRecording() {
        if (currentState.recordingPhase != RecordingPhase.Paused) return
        recordingSession?.resume()
        startElapsedTimer()
        setState { copy(recordingPhase = RecordingPhase.Recording) }
    }

    private fun stopRecording() {
        val phase = currentState.recordingPhase
        if (phase != RecordingPhase.Recording && phase != RecordingPhase.Paused) return
        viewModelScope.launch {
            timerJob?.cancel()
            setState { copy(recordingPhase = RecordingPhase.Finalizing, elapsedMs = 0L) }
            try {
                val session = recordingSession ?: return@launch
                session.stop()
                recordingSession = null

                val projectId = currentProjectId ?: return@launch
                val outputFile = session.stop() // already finalized; file path is the same
                projectRepository.finalizeRecordedProject(projectId, outputFile)
                    .onSuccess { setEffect(RecorderEffect.NavigateToEditor(projectId)) }
                    .onFailure { e ->
                        setEffect(RecorderEffect.ShowError(e.message
                            ?: context.getString(com.dipdev.aiautocaptioner.R.string.recorder_error_generic)))
                        setState { copy(recordingPhase = RecordingPhase.Previewing) }
                    }
            } catch (e: Exception) {
                setEffect(RecorderEffect.ShowError(e.message
                    ?: context.getString(com.dipdev.aiautocaptioner.R.string.recorder_error_generic)))
                setState { copy(recordingPhase = RecordingPhase.Previewing) }
            }
        }
    }

    private fun startElapsedTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(100)
                val session = recordingSession ?: break
                setState { copy(elapsedMs = session.elapsedMs) }
            }
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun onCameraSurfaceReady(surface: android.view.Surface) {
        pendingCameraSurface = surface
        cameraReady = true
    }

    private suspend fun awaitCameraSurface(): android.view.Surface {
        while (!cameraReady || pendingCameraSurface == null) {
            delay(16)
        }
        return pendingCameraSurface!!
    }

    /** Polls until [RendererThread.isPreviewSurfaceReady] is true (TextureView surface attached). */
    private suspend fun awaitPreviewSurface() {
        while (!rendererThread.isPreviewSurfaceReady) {
            delay(16)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        recordingSession = null
        cameraSession?.close()
        rendererThread.release()
    }
}

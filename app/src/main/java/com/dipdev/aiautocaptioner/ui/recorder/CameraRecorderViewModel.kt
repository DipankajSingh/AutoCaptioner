package com.dipdev.aiautocaptioner.ui.recorder

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.db.entity.CreationMode
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Max video duration strictly capped at 3 minutes (180,000 ms). */
private const val MAX_RECORDING_DURATION_MS = 180_000L
private const val NEAR_LIMIT_THRESHOLD_MS = 15_000L

data class CameraRecorderState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordedDurationMs: Long = 0L,
    val isFrontCamera: Boolean = false,
    val zoomRatio: Float = 1.0f,
    val reviewVideoFile: File? = null,
    val isImporting: Boolean = false,
    val errorMessage: String? = null
) {
    val remainingDurationMs: Long
        get() = (MAX_RECORDING_DURATION_MS - recordedDurationMs).coerceAtLeast(0L)

    val isNearLimit: Boolean
        get() = remainingDurationMs <= NEAR_LIMIT_THRESHOLD_MS && remainingDurationMs > 0L
}

@HiltViewModel
class CameraRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraRecorderState())
    val uiState: StateFlow<CameraRecorderState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var tempOutputFile: File? = null

    /** Clock source, overridable in unit tests. */
    var clock: () -> Long = { SystemClock.elapsedRealtime() }

    fun createTempOutputFile(): File {
        val file = File(context.cacheDir, "recorded_clip_${System.currentTimeMillis()}.mp4")
        tempOutputFile = file
        return file
    }

    fun onRecordingStarted() {
        _uiState.update {
            it.copy(
                isRecording = true,
                isPaused = false,
                recordedDurationMs = 0L,
                errorMessage = null
            )
        }
        startTimer()
    }

    fun onRecordingPaused() {
        _uiState.update { it.copy(isPaused = true) }
    }

    fun onRecordingResumed() {
        _uiState.update { it.copy(isPaused = false) }
    }

    fun onRecordingStopping() {
        stopTimer()
    }

    fun onRecordingStopped(file: File?) {
        stopTimer()
        val validFile = if (file != null && file.exists() && file.length() > 0) file else null
        _uiState.update {
            it.copy(
                isRecording = false,
                isPaused = false,
                reviewVideoFile = validFile,
                errorMessage = if (validFile == null) "Failed to record video file." else null
            )
        }
    }

    fun onRecordingFailed(errorCode: Int) {
        stopTimer()
        tempOutputFile?.delete()
        tempOutputFile = null
        _uiState.update {
            it.copy(
                isRecording = false,
                isPaused = false,
                reviewVideoFile = null,
                errorMessage = "Recording failed (code $errorCode). Please try again."
            )
        }
    }

    fun onCameraError(e: Throwable) {
        _uiState.update {
            it.copy(errorMessage = "Camera unavailable. Close other camera apps and try again.")
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleCameraFacing() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera, zoomRatio = 1.0f) }
    }

    fun setZoomRatio(ratio: Float) {
        _uiState.update { it.copy(zoomRatio = ratio.coerceIn(1.0f, 10.0f)) }
    }

    fun retakeRecording() {
        stopTimer()
        tempOutputFile?.delete()
        tempOutputFile = null
        _uiState.update {
            CameraRecorderState(isFrontCamera = it.isFrontCamera)
        }
    }

    fun confirmVideoAndImport(onSuccess: (String) -> Unit) {
        val file = _uiState.value.reviewVideoFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val uri = Uri.fromFile(file)
            val result = projectRepository.importVideo(uri, creationMode = CreationMode.ADVANCED)
            result.onSuccess { projectId ->
                file.delete()
                _uiState.update { it.copy(isImporting = false) }
                onSuccess(projectId)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = error.message ?: "Failed to import recorded video"
                    )
                }
            }
        }
    }

    private fun startTimer() {
        stopTimer()
        timerJob = viewModelScope.launch {
            var last = clock()
            var accumulated = _uiState.value.recordedDurationMs
            while (_uiState.value.isRecording) {
                val now = clock()
                if (!_uiState.value.isPaused) {
                    accumulated += (now - last)
                }
                last = now
                val capped = accumulated.coerceAtMost(MAX_RECORDING_DURATION_MS)
                _uiState.update { it.copy(recordedDurationMs = capped) }
                if (capped >= MAX_RECORDING_DURATION_MS) break
                delay(100L)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        tempOutputFile?.delete()
    }
}


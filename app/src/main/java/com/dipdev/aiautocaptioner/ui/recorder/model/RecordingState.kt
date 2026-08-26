package com.dipdev.aiautocaptioner.ui.recorder.model

import java.io.File

sealed interface RecordingState {
    data object Idle : RecordingState
    data object Countdown : RecordingState
    data object Recording : RecordingState
    data object Paused : RecordingState
    data class Finalized(val projectId: String, val file: File) : RecordingState
    data class Failed(val error: RecordingError) : RecordingState
}

sealed interface RecordingError {
    data object CameraInUse : RecordingError
    data object AudioInitFailed : RecordingError
    data object MuxerFailed : RecordingError
    data class Unknown(val message: String, val cause: Throwable? = null) : RecordingError
}

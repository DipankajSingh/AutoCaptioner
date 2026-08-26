package com.dipdev.aiautocaptioner.ui.recorder.recording

import android.util.Log
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class CameraXRecorder : Recorder {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state

    private val _elapsedMs = MutableStateFlow(0L)
    override val elapsedMs: StateFlow<Long> = _elapsedMs

    override fun startFacelessRecording(
        file: File,
        width: Int,
        height: Int,
        fps: Int,
        videoBitrate: Int,
        audioBitrate: Int,
        backgroundColor: Int?,
        gradientColors: List<Int>?,
        muted: Boolean,
        onComplete: (File) -> Unit,
        onError: (Throwable) -> Unit,
        onAmplitude: ((Float) -> Unit)?
    ) {
        throw UnsupportedOperationException("Use FacelessRecorder for faceless recording")
    }

    override fun pause() {
        _state.update { if (it is RecordingState.Recording) RecordingState.Paused else it }
    }

    override fun resume() {
        _state.update { if (it is RecordingState.Paused) RecordingState.Recording else it }
    }

    override fun stop() {
        _state.update { RecordingState.Idle }
    }

    override fun release() {
        _state.update { RecordingState.Idle }
    }

    companion object {
        private const val TAG = "CameraXRecorder"
    }
}

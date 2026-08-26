package com.dipdev.aiautocaptioner.ui.recorder.recording

import com.dipdev.aiautocaptioner.engine.FacelessVideoRecorder
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class FacelessRecorder(
    private val crashReporter: CrashReporter
) : Recorder {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state

    private val _elapsedMs = MutableStateFlow(0L)
    override val elapsedMs: StateFlow<Long> = _elapsedMs

    private var delegate: FacelessVideoRecorder? = null

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
        if (_state.value !is RecordingState.Idle) {
            onError(IllegalStateException("Already recording"))
            return
        }

        _state.update { RecordingState.Recording }
        _elapsedMs.value = 0L

        delegate = FacelessVideoRecorder(crashReporter)
        delegate?.start(
            width = width,
            height = height,
            fps = fps,
            videoBitrate = videoBitrate,
            audioBitrate = audioBitrate,
            backgroundColor = backgroundColor,
            gradientColors = gradientColors,
            muted = muted,
            outputFile = file,
            onComplete = { output ->
                _state.update { RecordingState.Idle }
                onComplete(output)
            },
            onError = { e ->
                _state.update { RecordingState.Idle }
                onError(e)
            },
            onAmplitude = onAmplitude
        )
    }

    override fun pause() {
        delegate?.pause()
        _state.update { if (it is RecordingState.Recording) RecordingState.Paused else it }
    }

    override fun resume() {
        delegate?.resume()
        _state.update { if (it is RecordingState.Paused) RecordingState.Recording else it }
    }

    override fun stop() {
        delegate?.stop()
    }

    override fun release() {
        delegate?.stop()
        delegate = null
        _state.update { RecordingState.Idle }
    }
}

package com.dipdev.aiautocaptioner.ui.recorder.recording

import com.dipdev.aiautocaptioner.engine.FacelessVideoRecorder
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class FacelessRecorder(
    private val crashReporter: CrashReporter
) : Recorder {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state

    private val _elapsedMs = MutableStateFlow(0L)
    override val elapsedMs: StateFlow<Long> = _elapsedMs

    private var delegate: FacelessVideoRecorder? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var elapsedTimerJob: Job? = null

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

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.update { RecordingState.Recording }
        _elapsedMs.value = 0L
        startElapsedTimer()

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
                stopElapsedTimer()
                _state.update { RecordingState.Idle }
                onComplete(output)
            },
            onError = { e ->
                stopElapsedTimer()
                _state.update { RecordingState.Idle }
                onError(e)
            },
            onAmplitude = onAmplitude
        )
    }

    override fun pause() {
        if (delegate == null) return
        delegate?.pause()
        stopElapsedTimer()
        _state.update { if (it is RecordingState.Recording) RecordingState.Paused else it }
    }

    override fun resume() {
        if (delegate == null) return
        delegate?.resume()
        _state.update { if (it is RecordingState.Paused) RecordingState.Recording else it }
        startElapsedTimer()
    }

    override fun stop() {
        delegate?.stop()
    }

    override fun release() {
        stopElapsedTimer()
        delegate?.stop()
        delegate = null
        _state.update { RecordingState.Idle }
        scope.cancel()
    }

    private fun startElapsedTimer() {
        stopElapsedTimer()
        val startTime = System.currentTimeMillis()
        val startOffset = _elapsedMs.value
        elapsedTimerJob = scope.launch {
            while (isActive) {
                delay(200L)
                _elapsedMs.value = startOffset + (System.currentTimeMillis() - startTime)
            }
        }
    }

    private fun stopElapsedTimer() {
        elapsedTimerJob?.cancel()
        elapsedTimerJob = null
    }
}

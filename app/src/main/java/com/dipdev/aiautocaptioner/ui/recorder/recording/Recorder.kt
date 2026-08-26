package com.dipdev.aiautocaptioner.ui.recorder.recording

import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingState
import kotlinx.coroutines.flow.StateFlow

interface Recorder {
    val state: StateFlow<RecordingState>
    val elapsedMs: StateFlow<Long>

    fun startFacelessRecording(
        file: java.io.File,
        width: Int,
        height: Int,
        fps: Int,
        videoBitrate: Int,
        audioBitrate: Int,
        backgroundColor: Int?,
        gradientColors: List<Int>?,
        muted: Boolean,
        onComplete: (java.io.File) -> Unit,
        onError: (Throwable) -> Unit,
        onAmplitude: ((Float) -> Unit)? = null
    )

    fun pause()
    fun resume()
    fun stop()
    fun release()
}

package com.dipdev.aiautocaptioner.ui.recorder.camera

import android.media.Image
import android.view.TextureView
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface CameraEngine {
    val state: StateFlow<CameraState>
    val textureView: TextureView

    fun open()
    fun close()
    fun flipCamera()
    fun setTorch(enabled: Boolean)
    fun setAspectAndPreview(ar: Int, arWidth: Int, arHeight: Int)
    fun setFrameAnalyzer(analyzer: FrameAnalyzer?)
    fun startRecording(
        file: File,
        videoWidth: Int,
        videoHeight: Int,
        videoBitrate: Int,
        listener: RecordingListener
    ): ActiveRecording
    fun release()
}

fun interface FrameAnalyzer {
    fun analyze(image: Image, rotationDegrees: Int)
}

interface RecordingListener {
    fun onRecordingStarted()
    fun onRecordingFinished(file: File)
    fun onRecordingError(error: Throwable)
}

interface ActiveRecording {
    fun pause()
    fun resume()
    fun stop()
}

sealed interface CameraState {
    data object Idle : CameraState
    data object Active : CameraState
    data object Error : CameraState
}

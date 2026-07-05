package com.dipdev.aiautocaptioner.ui.recorder

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.engine.FacelessVideoRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class SmartRecorderViewModel @Inject constructor() : ViewModel() {
    private val _recordingMode = MutableStateFlow(RecordingMode.CAMERA)
    val recordingMode = _recordingMode.asStateFlow()

    private val _selectedBackground = MutableStateFlow<BackgroundState>(BackgroundState.SolidColor(Color.Black))
    val selectedBackground = _selectedBackground.asStateFlow()

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState = _recordingState.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds = _elapsedSeconds.asStateFlow()

    private val _outputUri = MutableStateFlow<Uri?>(null)
    val outputUri = _outputUri.asStateFlow()

    private var facelessRecorder: FacelessVideoRecorder? = null
    private var timerJob: Job? = null

    fun setRecordingMode(mode: RecordingMode) {
        if (_recordingState.value == RecordingState.IDLE) {
            _recordingMode.value = mode
        }
    }

    fun setSelectedBackground(bg: BackgroundState) {
        _selectedBackground.value = bg
    }

    fun startFacelessRecording(context: Context) {
        if (_recordingState.value != RecordingState.IDLE) return
        
        facelessRecorder = FacelessVideoRecorder()
        val outputFile = File(context.cacheDir, "faceless_video_${System.currentTimeMillis()}.mp4")

        val bgState = _selectedBackground.value
        val bitmap = (bgState as? BackgroundState.ImageBitmap)?.bitmap
        val color = (bgState as? BackgroundState.SolidColor)?.color?.toArgb()
        // For gradient, one could render it to a bitmap, but for now we'll just fall back or pass color

        _recordingState.value = RecordingState.RECORDING
        startTimer()

        facelessRecorder?.start(
            context = context,
            backgroundBitmap = bitmap,
            backgroundColor = color,
            outputFile = outputFile,
            onComplete = { file ->
                _recordingState.value = RecordingState.DONE
                _outputUri.value = Uri.fromFile(file)
                stopTimer()
            },
            onError = { e ->
                e.printStackTrace()
                _recordingState.value = RecordingState.IDLE
                stopTimer()
            }
        )
    }

    fun stopFacelessRecording() {
        if (_recordingState.value == RecordingState.RECORDING) {
            facelessRecorder?.stop()
        }
    }

    fun onCameraRecordingStarted() {
        _recordingState.value = RecordingState.RECORDING
        startTimer()
    }

    fun onCameraRecordingStopped(uri: Uri) {
        _recordingState.value = RecordingState.DONE
        _outputUri.value = uri
        stopTimer()
    }

    fun onCameraRecordingError() {
        _recordingState.value = RecordingState.IDLE
        stopTimer()
    }

    fun resetState() {
        _recordingState.value = RecordingState.IDLE
        _outputUri.value = null
        _elapsedSeconds.value = 0
    }

    private fun startTimer() {
        _elapsedSeconds.value = 0
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _elapsedSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}

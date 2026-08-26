package com.dipdev.aiautocaptioner.ui.recorder.model

import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import com.dipdev.aiautocaptioner.ui.base.UiEvent

sealed interface RecorderEvent : UiEvent {
    data class SetRecordingMode(val mode: RecordingMode) : RecorderEvent
    data class CycleAspectRatio(val current: AspectRatio) : RecorderEvent
    data class CycleRecordingQuality(val current: RecordingQuality) : RecorderEvent
    data class SelectFilter(val filter: CreatorFilter) : RecorderEvent
    data class UpdateSmoothness(val intensity: Float) : RecorderEvent
    data object ToggleGrid : RecorderEvent
    data object ToggleTeleprompter : RecorderEvent
    data class UpdateTeleprompterText(val text: String) : RecorderEvent
    data object ToggleGestureDetection : RecorderEvent
    data class SetCountdownTimer(val seconds: Int) : RecorderEvent
    data object ToggleFilterCarousel : RecorderEvent
    data object ToggleSmoothnessSlider : RecorderEvent
    data object DismissSubControls : RecorderEvent
    data class SetSelectedBackground(val background: BackgroundState) : RecorderEvent
    data object ToggleMute : RecorderEvent
    data object FlipCamera : RecorderEvent
    data object ToggleTorch : RecorderEvent
    data object RequestExitRecording : RecorderEvent
    data object DismissExitDialog : RecorderEvent
    data object SaveAndExit : RecorderEvent
    data object DiscardRecording : RecorderEvent
    data object StartRecording : RecorderEvent
    data object PauseRecording : RecorderEvent
    data object ResumeRecording : RecorderEvent
    data object StopRecording : RecorderEvent
    data object ResetState : RecorderEvent
}

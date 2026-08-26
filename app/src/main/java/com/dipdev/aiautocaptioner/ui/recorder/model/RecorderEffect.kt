package com.dipdev.aiautocaptioner.ui.recorder.model

import com.dipdev.aiautocaptioner.ui.base.UiEffect

sealed interface RecorderEffect : UiEffect {
    data class NavigateToEditor(val projectId: String) : RecorderEffect
    data object NavigateBack : RecorderEffect
    data class ShowError(val message: String) : RecorderEffect
}

package com.dipdev.aiautocaptioner.ui.settings

import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val glassmorphism: Boolean = true,
    val showTimelineThumbnails: Boolean = false,
    val telemetryEnabled: Boolean = true,
    val previewFps: Int = 30,
    val activeModelName: String? = null
) : UiState

sealed interface SettingsUiEvent : UiEvent {
    data class SetGlassmorphism(val enabled: Boolean) : SettingsUiEvent
    data class SetShowTimelineThumbnails(val enabled: Boolean) : SettingsUiEvent
    data class SetTelemetryEnabled(val enabled: Boolean) : SettingsUiEvent
    data class SetPreviewFps(val fps: Int) : SettingsUiEvent
}

sealed interface SettingsUiEffect : UiEffect

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository
) : BaseViewModel<SettingsUiState, SettingsUiEvent, SettingsUiEffect>(SettingsUiState()) {

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.glassmorphismFlow,
                settingsRepository.showTimelineThumbnailsFlow,
                settingsRepository.telemetryEnabledFlow,
                settingsRepository.previewFpsFlow
            ) { glass, thumb, telemetry, previewFps ->
                SettingsUiState(glass, thumb, telemetry, previewFps)
            }.distinctUntilChanged().collect { state ->
                setState { state }
            }
        }
        // Collect active model name independently
        viewModelScope.launch {
            modelRepository.getActiveModel().collect { model ->
                setState { copy(activeModelName = model?.displayName?.split("\u2014")?.first()?.trim()) }
            }
        }
    }

    override fun handleEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.SetGlassmorphism -> {
                viewModelScope.launch { settingsRepository.setGlassmorphismEnabled(event.enabled) }
            }
            is SettingsUiEvent.SetShowTimelineThumbnails -> {
                viewModelScope.launch { settingsRepository.setShowTimelineThumbnails(event.enabled) }
            }
            is SettingsUiEvent.SetTelemetryEnabled -> {
                viewModelScope.launch { settingsRepository.setTelemetryEnabled(event.enabled) }
            }
            is SettingsUiEvent.SetPreviewFps -> {
                viewModelScope.launch { settingsRepository.setPreviewFps(event.fps) }
            }
        }
    }
}

package com.dipdev.aiautocaptioner.ui.settings

import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.repository.AppTheme
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
    val theme: AppTheme = AppTheme.TRUE_BLACK,
    val glassmorphism: Boolean = true,
    val showTimelineThumbnails: Boolean = false,
    val telemetryEnabled: Boolean = true
) : UiState

sealed interface SettingsUiEvent : UiEvent {
    data class SetGlassmorphism(val enabled: Boolean) : SettingsUiEvent
    data class SetShowTimelineThumbnails(val enabled: Boolean) : SettingsUiEvent
    data class SetTelemetryEnabled(val enabled: Boolean) : SettingsUiEvent
}

sealed interface SettingsUiEffect : UiEffect

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : BaseViewModel<SettingsUiState, SettingsUiEvent, SettingsUiEffect>(SettingsUiState()) {

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.themeFlow,
                settingsRepository.glassmorphismFlow,
                settingsRepository.showTimelineThumbnailsFlow,
                settingsRepository.telemetryEnabledFlow
            ) { theme, glass, thumb, telemetry ->
                SettingsUiState(theme, glass, thumb, telemetry)
            }.distinctUntilChanged().collect { state ->
                setState { state }
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

        }
    }
}

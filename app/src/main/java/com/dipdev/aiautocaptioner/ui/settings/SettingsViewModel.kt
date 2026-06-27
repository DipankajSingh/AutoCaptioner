package com.dipdev.aiautocaptioner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.data.repository.AppTheme
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

data class SettingsUiState(
    val theme: AppTheme = AppTheme.EMERALD,
    val glassmorphism: Boolean = true,
    val showTimelineThumbnails: Boolean = false,
    val isLightTheme: Boolean = false
) : UiState

sealed interface SettingsUiEvent : UiEvent {
    data class SetTheme(val theme: AppTheme) : SettingsUiEvent
    data class SetGlassmorphism(val enabled: Boolean) : SettingsUiEvent
    data class SetShowTimelineThumbnails(val enabled: Boolean) : SettingsUiEvent
    data class SetLightTheme(val enabled: Boolean) : SettingsUiEvent
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
                settingsRepository.lightThemeFlow
            ) { theme, glass, thumb, light ->
                SettingsUiState(theme, glass, thumb, light)
            }.distinctUntilChanged().collect { state ->
                setState { state }
            }
        }
    }

    override fun handleEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.SetTheme -> {
                viewModelScope.launch { settingsRepository.setTheme(event.theme) }
            }
            is SettingsUiEvent.SetGlassmorphism -> {
                viewModelScope.launch { settingsRepository.setGlassmorphismEnabled(event.enabled) }
            }
            is SettingsUiEvent.SetShowTimelineThumbnails -> {
                viewModelScope.launch { settingsRepository.setShowTimelineThumbnails(event.enabled) }
            }
            is SettingsUiEvent.SetLightTheme -> {
                viewModelScope.launch { settingsRepository.setLightTheme(event.enabled) }
            }
        }
    }
}

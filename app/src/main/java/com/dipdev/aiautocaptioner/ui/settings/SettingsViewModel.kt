package com.dipdev.aiautocaptioner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.data.repository.AppTheme
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val themeFlow = settingsRepository.themeFlow.stateInDefault(scope = viewModelScope, initialValue = AppTheme.EMERALD)

    val glassmorphismFlow = settingsRepository.glassmorphismFlow.stateInDefault(scope = viewModelScope, initialValue = true)

    val showTimelineThumbnailsFlow = settingsRepository.showTimelineThumbnailsFlow.stateInDefault(scope = viewModelScope, initialValue = false)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    fun setGlassmorphismEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGlassmorphismEnabled(enabled)
        }
    }

    fun setShowTimelineThumbnails(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowTimelineThumbnails(enabled)
        }
    }
}

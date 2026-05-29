package com.dipdev.aiautocaptioner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.repository.AppTheme
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val themeFlow = settingsRepository.themeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppTheme.EMERALD
    )

    val glassmorphismFlow = settingsRepository.glassmorphismFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

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
}

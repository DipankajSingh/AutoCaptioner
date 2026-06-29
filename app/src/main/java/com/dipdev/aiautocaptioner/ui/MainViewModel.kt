package com.dipdev.aiautocaptioner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.data.repository.AppTheme
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

data class MainUiState(
    val startDestination: String? = null,
    val appTheme: AppTheme = AppTheme.DEEP_SPACE,
    val glassmorphismEnabled: Boolean = true
) : UiState

sealed interface MainUiEvent : UiEvent
sealed interface MainUiEffect : UiEffect

@HiltViewModel
class MainViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val captionRepository: CaptionRepository,
    private val settingsRepository: SettingsRepository
) : BaseViewModel<MainUiState, MainUiEvent, MainUiEffect>(MainUiState()) {

    init {
        decideStartDestination()
        
        viewModelScope.launch {
            combine(
                settingsRepository.themeFlow,
                settingsRepository.glassmorphismFlow
            ) { theme, glass ->
                Pair(theme, glass)
            }.distinctUntilChanged().collect { (theme, glass) ->
                setState { copy(appTheme = theme, glassmorphismEnabled = glass) }
            }
        }
    }

    override fun handleEvent(event: MainUiEvent) {
        // No events to handle
    }

    private fun decideStartDestination() {
        // Seed default styles concurrently — navigation doesn't depend on this.
        // Running it in parallel means we don't add its DB latency to startup time.
        viewModelScope.launch { captionRepository.initializeDefaultStyles() }

        // Only the two fast checks (DataStore read + file-exists) gate navigation.
        // DataStore is a proto file read (~5ms), file-exists is OS stat (~1ms).
        viewModelScope.launch {
            val onboardingDone = modelRepository.isOnboardingComplete().first()

            val dest = when {
                !onboardingDone -> "onboarding"
                else            -> "home"
            }
            setState { copy(startDestination = dest) }
        }
    }
}
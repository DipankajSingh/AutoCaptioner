package com.dipdev.aiautocaptioner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.repository.AppTheme
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val captionRepository: CaptionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // null = still deciding (show spinner)
    // non-null = decision made, navigate immediately
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    val appTheme: StateFlow<AppTheme> = settingsRepository.themeFlow.stateInDefault(scope = viewModelScope, initialValue = AppTheme.EMERALD)

    val isGlassmorphismEnabled: StateFlow<Boolean> = settingsRepository.glassmorphismFlow.stateInDefault(scope = viewModelScope, initialValue = true)

    init {
        decideStartDestination()
    }

    private fun decideStartDestination() {
        // Seed default styles concurrently — navigation doesn't depend on this.
        // Running it in parallel means we don't add its DB latency to startup time.
        viewModelScope.launch { captionRepository.initializeDefaultStyles() }

        // Only the two fast checks (DataStore read + file-exists) gate navigation.
        // DataStore is a proto file read (~5ms), file-exists is OS stat (~1ms).
        viewModelScope.launch {
            val onboardingDone = modelRepository.isOnboardingComplete().first()

            _startDestination.value = when {
                !onboardingDone -> "onboarding"
                else            -> "home"
            }
        }
    }
}
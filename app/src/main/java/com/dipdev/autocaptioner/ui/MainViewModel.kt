package com.dipdev.autocaptioner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.autocaptioner.data.repository.CaptionRepository
import com.dipdev.autocaptioner.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val captionRepository: CaptionRepository
) : ViewModel() {

    // null = still deciding (show spinner)
    // non-null = decision made, navigate immediately
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

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
                !onboardingDone                      -> "onboarding"
                !modelRepository.hasDownloadedModel() -> "device_check"
                else                                  -> "home"
            }
        }
    }
}
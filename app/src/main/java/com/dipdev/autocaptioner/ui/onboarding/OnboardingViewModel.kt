package com.dipdev.autocaptioner.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.autocaptioner.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    fun completeOnboarding() {
        viewModelScope.launch {
            modelRepository.setOnboardingComplete()
        }
    }
}
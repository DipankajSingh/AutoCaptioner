package com.dipdev.aiautocaptioner.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState

data object OnboardingUiState : UiState

sealed interface OnboardingUiEvent : UiEvent {
    data object CompleteOnboarding : OnboardingUiEvent
}

sealed interface OnboardingUiEffect : UiEffect

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : BaseViewModel<OnboardingUiState, OnboardingUiEvent, OnboardingUiEffect>(OnboardingUiState) {

    override fun handleEvent(event: OnboardingUiEvent) {
        when (event) {
            is OnboardingUiEvent.CompleteOnboarding -> {
                viewModelScope.launch {
                    modelRepository.setOnboardingComplete()
                }
            }
        }
    }
}
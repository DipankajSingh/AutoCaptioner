package com.dipdev.aiautocaptioner.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    WelcomeScreen(
        onGetStartedClick = {
            viewModel.setEvent(OnboardingUiEvent.CompleteOnboarding)
            onFinish()
        },
        modifier = modifier
    )
}

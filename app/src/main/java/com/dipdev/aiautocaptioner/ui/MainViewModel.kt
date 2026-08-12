package com.dipdev.aiautocaptioner.ui

import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.data.db.entity.CreationMode
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged

import com.dipdev.aiautocaptioner.ui.navigation.Screen

data class MainUiState(
    val startDestination: Screen? = null,
    val glassmorphismEnabled: Boolean = true,
    val sharedVideoUri: Uri? = null,
    val isImportingSharedVideo: Boolean = false,
    val importError: String? = null
) : UiState

sealed interface MainUiEvent : UiEvent
sealed interface MainUiEffect : UiEffect {
    data class NavigateToProcessing(val projectId: String) : MainUiEffect
    data class NavigateToVideoEditor(val projectId: String) : MainUiEffect
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val captionRepository: CaptionRepository,
    private val settingsRepository: SettingsRepository,
    private val projectRepository: ProjectRepository
) : BaseViewModel<MainUiState, MainUiEvent, MainUiEffect>(MainUiState()) {

    init {
        decideStartDestination()
        
        viewModelScope.launch {
            settingsRepository.glassmorphismFlow
                .distinctUntilChanged()
                .collect { glass ->
                    setState { copy(glassmorphismEnabled = glass) }
                }
        }
    }

    override fun handleEvent(event: MainUiEvent) {
        // No events to handle
    }
    
    fun onIntentReceived(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("video/") == true) {
            val uri = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri != null) {
                setState { copy(sharedVideoUri = uri, importError = null) }
            }
        }
    }
    
    fun clearSharedUri() {
        setState { copy(sharedVideoUri = null, importError = null) }
    }
    
    fun importSharedVideo(isQuickCaption: Boolean) {
        val uri = uiState.value.sharedVideoUri ?: return
        
        viewModelScope.launch {
            setState { copy(isImportingSharedVideo = true, importError = null) }
            
            val mode = if (isQuickCaption) CreationMode.QUICK_CAPTION else CreationMode.ADVANCED
            val result = projectRepository.importVideo(uri, mode)
            
            result.fold(
                onSuccess = { projectId ->
                    setState { copy(sharedVideoUri = null, isImportingSharedVideo = false) }
                    if (isQuickCaption) {
                        setEffect(MainUiEffect.NavigateToProcessing(projectId))
                    } else {
                        setEffect(MainUiEffect.NavigateToVideoEditor(projectId))
                    }
                },
                onFailure = { error ->
                    setState { 
                        copy(
                            isImportingSharedVideo = false, 
                            importError = error.message ?: "Failed to import video"
                        ) 
                    }
                }
            )
        }
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
                !onboardingDone -> Screen.Onboarding
                else            -> Screen.Home
            }
            setState { copy(startDestination = dest) }
        }
    }
}
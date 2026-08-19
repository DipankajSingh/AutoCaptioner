package com.dipdev.aiautocaptioner.ui.onboarding

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase
import com.dipdev.aiautocaptioner.core.device.ModelSafetyCheckState
import com.dipdev.aiautocaptioner.core.device.OnboardingModelTier
import com.dipdev.aiautocaptioner.core.device.OnboardingModelTierMapper
import com.dipdev.aiautocaptioner.core.whisper.ModelDownloadForegroundService
import com.dipdev.aiautocaptioner.core.whisper.ModelDownloadServiceManager
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SetupQualityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val modelRepository: ModelRepository,
    private val settingsRepository: com.dipdev.aiautocaptioner.data.repository.SettingsRepository,
    private val deviceCapabilityUseCase: DeviceCapabilityUseCase,
    private val tierMapper: OnboardingModelTierMapper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val languageCode: String = checkNotNull(savedStateHandle["languageCode"])

    private val _tiers = MutableStateFlow<List<OnboardingModelTier>>(emptyList())
    val tiers: StateFlow<List<OnboardingModelTier>> = _tiers.asStateFlow()

    private val _selectedModelId = MutableStateFlow<String?>(null)
    val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    private val _safetyCheckState = MutableStateFlow<ModelSafetyCheckState>(ModelSafetyCheckState.Idle)
    val safetyCheckState: StateFlow<ModelSafetyCheckState> = _safetyCheckState.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        val allModels = modelRepository.getAvailableModels()
        val filteredModels = com.dipdev.aiautocaptioner.data.model.WhisperModel.filterAndSortForLanguage(allModels, languageCode)
        val mappedTiers = tierMapper.mapToTiers(filteredModels, languageCode)
        _tiers.value = mappedTiers
        
        val recommended = mappedTiers.find { it.isRecommended }
        _selectedModelId.value = recommended?.model?.id ?: mappedTiers.firstOrNull()?.model?.id
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
    }

    fun onDownloadRequested() {
        val selectedTier = _tiers.value.find { it.model.id == _selectedModelId.value } ?: return
        val safetyState = deviceCapabilityUseCase.checkSafetyForModel(selectedTier.model.sizeMb.toLong())
        _safetyCheckState.value = safetyState

        if (safetyState is ModelSafetyCheckState.Passed) {
            startDownloadService(selectedTier.model.id)
        }
    }

    fun onCellularWarningAccepted() {
        val modelId = _selectedModelId.value ?: return
        startDownloadService(modelId)
        _safetyCheckState.value = ModelSafetyCheckState.Idle
    }

    fun clearSafetyCheckState() {
        _safetyCheckState.value = ModelSafetyCheckState.Idle
    }

    fun checkStorageAgain() {
        onDownloadRequested()
    }

    private fun startDownloadService(modelId: String) {
        val intent = Intent(context, ModelDownloadForegroundService::class.java).apply {
            putExtra(ModelDownloadForegroundService.EXTRA_MODEL_ID, modelId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancelDownload() {
        val intent = Intent(context, ModelDownloadForegroundService::class.java).apply {
            action = ModelDownloadForegroundService.ACTION_CANCEL_DOWNLOAD
        }
        ContextCompat.startForegroundService(context, intent)
    }

    suspend fun finalizeSetup() {
        // Active model is already set by the repository upon download completion, but we can do it here too just in case.
        val modelId = _selectedModelId.value ?: return
        modelRepository.setActiveModel(modelId)
        
        settingsRepository.saveLastLanguageSettings(languageCode, translateToEnglish = false)
        modelRepository.setOnboardingComplete()

        if (!com.dipdev.aiautocaptioner.BuildConfig.DEBUG) {
            val bundle = Bundle().apply {
                putString("language", languageCode)
                putString("model", modelId)
            }
            Firebase.analytics.logEvent("onboarding_complete", bundle)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // If the user navigates away before download completes, cancel it
        val state = ModelDownloadServiceManager.downloadState.value
        if (state != null && state !is DownloadState.Complete) {
            cancelDownload()
        }
    }
}

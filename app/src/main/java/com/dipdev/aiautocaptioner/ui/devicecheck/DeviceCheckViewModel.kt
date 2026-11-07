package com.dipdev.aiautocaptioner.ui.devicecheck

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase
import com.dipdev.aiautocaptioner.core.device.ModelSafetyCheckState
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceInfo(
    val totalRamMb: Int,
    val availableStorageGb: Float,
    val androidVersion: Int,
    val cpuAbi: String
)

data class DeviceCheckUiState(
    val deviceInfo: DeviceInfo? = null,
    val models: List<WhisperModel> = emptyList(),
    val recommendedModelId: String? = null,
    val recommendedReasonResId: Int? = null,
    val safetyState: ModelSafetyCheckState = ModelSafetyCheckState.Idle,
    val language: String = "en"
) : UiState

sealed interface DeviceCheckUiEvent : UiEvent {
    data class CheckSafety(val modelSizeMb: Long) : DeviceCheckUiEvent
    data object ResetSafetyState : DeviceCheckUiEvent
}

sealed interface DeviceCheckUiEffect : UiEffect

@HiltViewModel
class DeviceCheckViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val deviceCapabilityUseCase: DeviceCapabilityUseCase,
    private val modelRecommendationUseCase: com.dipdev.aiautocaptioner.core.device.ModelRecommendationUseCase,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<DeviceCheckUiState, DeviceCheckUiEvent, DeviceCheckUiEffect>(DeviceCheckUiState()) {

    init {
        val navLang: String? = savedStateHandle["languageCode"] ?: savedStateHandle["language"]
        if (navLang != null) {
            checkDevice(navLang)
        } else {
            viewModelScope.launch {
                val lastLang = try {
                    settingsRepository.lastLanguageFlow.first()
                } catch (_: Exception) {
                    "en"
                }
                checkDevice(lastLang)
            }
        }
    }

    override fun handleEvent(event: DeviceCheckUiEvent) {
        when (event) {
            is DeviceCheckUiEvent.CheckSafety -> {
                setState { copy(safetyState = deviceCapabilityUseCase.checkSafetyForModel(event.modelSizeMb)) }
            }
            is DeviceCheckUiEvent.ResetSafetyState -> {
                setState { copy(safetyState = ModelSafetyCheckState.Idle) }
            }
        }
    }

    fun checkDevice(language: String = currentState.language) {
        val info = deviceCapabilityUseCase.getDeviceInfo()
        val deviceInfo = DeviceInfo(
            totalRamMb = info.totalRamMb,
            availableStorageGb = info.availableStorageGb,
            androidVersion = info.androidVersion,
            cpuAbi = info.cpuAbi
        )

        val allModels = modelRepository.getAvailableModels()
        val compatibleModels = modelRecommendationUseCase.filterCompatibleModels(allModels, language)
        val recommendation = modelRecommendationUseCase.getRecommendation(compatibleModels, language)

        setState { 
            copy(
                deviceInfo = deviceInfo,
                models = allModels,
                recommendedModelId = recommendation.modelId.ifEmpty { null },
                recommendedReasonResId = recommendation.reasonResId,
                language = language
            )
        }
    }
}
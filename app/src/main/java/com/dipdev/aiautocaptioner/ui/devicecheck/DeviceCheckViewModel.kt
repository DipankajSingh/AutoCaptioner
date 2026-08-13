package com.dipdev.aiautocaptioner.ui.devicecheck

import android.content.Context
import com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase
import com.dipdev.aiautocaptioner.core.device.ModelSafetyCheckState
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val safetyState: ModelSafetyCheckState = ModelSafetyCheckState.Idle
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
    private val modelRecommendationUseCase: com.dipdev.aiautocaptioner.core.device.ModelRecommendationUseCase
) : BaseViewModel<DeviceCheckUiState, DeviceCheckUiEvent, DeviceCheckUiEffect>(DeviceCheckUiState()) {

    init {
        checkDevice()
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

    private fun checkDevice() {
        val info = deviceCapabilityUseCase.getDeviceInfo()
        val deviceInfo = DeviceInfo(
            totalRamMb = info.totalRamMb,
            availableStorageGb = info.availableStorageGb,
            androidVersion = info.androidVersion,
            cpuAbi = info.cpuAbi
        )

        val allModels = modelRepository.getAvailableModels()
        val compatibleModels = modelRecommendationUseCase.filterCompatibleModels(allModels, "en")
        val recommendation = modelRecommendationUseCase.getRecommendation(compatibleModels, "en")

        setState { 
            copy(
                deviceInfo = deviceInfo,
                models = allModels,
                recommendedModelId = recommendation.modelId.ifEmpty { null },
                recommendedReasonResId = recommendation.reasonResId
            )
        }
    }
}
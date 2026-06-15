package com.dipdev.aiautocaptioner.ui.devicecheck

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.ViewModel
import com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase
import com.dipdev.aiautocaptioner.core.device.ModelSafetyCheckState
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DeviceInfo(
    val totalRamMb: Int,
    val availableStorageGb: Float,
    val androidVersion: Int,
    val cpuAbi: String
)

// SafetyCheckState removed in favor of ModelSafetyCheckState from DeviceCapabilityUseCase

@HiltViewModel
class DeviceCheckViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val deviceCapabilityUseCase: DeviceCapabilityUseCase
) : ViewModel() {

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _models = MutableStateFlow<List<WhisperModel>>(emptyList())
    val models: StateFlow<List<WhisperModel>> = _models.asStateFlow()

    private val _recommendedModelId = MutableStateFlow<String?>(null)
    val recommendedModelId: StateFlow<String?> = _recommendedModelId.asStateFlow()

    private val _safetyState = MutableStateFlow<ModelSafetyCheckState>(ModelSafetyCheckState.Idle)
    val safetyState: StateFlow<ModelSafetyCheckState> = _safetyState.asStateFlow()

    fun checkSafety(modelSizeMb: Long) {
        _safetyState.value = deviceCapabilityUseCase.checkSafetyForModel(modelSizeMb)
    }

    fun resetSafetyState() {
        _safetyState.value = ModelSafetyCheckState.Idle
    }

    init {
        checkDevice()
    }

    private fun checkDevice() {
        val info = deviceCapabilityUseCase.getDeviceInfo()
        _deviceInfo.value = DeviceInfo(
            totalRamMb = info.totalRamMb,
            availableStorageGb = info.availableStorageGb,
            androidVersion = info.androidVersion,
            cpuAbi = info.cpuAbi
        )

        // Load models enriched with download status
        val allModels = modelRepository.getAvailableModels()
        _models.value = allModels

        // Recommend based on RAM
        _recommendedModelId.value = deviceCapabilityUseCase.getRecommendedModel("en")
    }
}
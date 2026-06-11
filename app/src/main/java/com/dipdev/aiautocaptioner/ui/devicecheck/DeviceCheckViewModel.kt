package com.dipdev.aiautocaptioner.ui.devicecheck

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.ViewModel
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

sealed class SafetyCheckState {
    object Idle : SafetyCheckState()
    data class StorageError(val requiredMb: Long) : SafetyCheckState()
    data class CellularWarning(val sizeMb: Long) : SafetyCheckState()
    object Passed : SafetyCheckState()
}

@HiltViewModel
class DeviceCheckViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _models = MutableStateFlow<List<WhisperModel>>(emptyList())
    val models: StateFlow<List<WhisperModel>> = _models.asStateFlow()

    private val _recommendedModelId = MutableStateFlow<String?>(null)
    val recommendedModelId: StateFlow<String?> = _recommendedModelId.asStateFlow()

    private val _safetyState = MutableStateFlow<SafetyCheckState>(SafetyCheckState.Idle)
    val safetyState: StateFlow<SafetyCheckState> = _safetyState.asStateFlow()

    fun checkSafety(modelSizeMb: Long) {
        val statFs = android.os.StatFs(context.filesDir.absolutePath)
        val availableMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)
        val requiredMb = (modelSizeMb * 1.5).toLong()

        if (availableMb < requiredMb) {
            _safetyState.value = SafetyCheckState.StorageError(requiredMb)
            return
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        val isCellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        if (isCellular) {
            _safetyState.value = SafetyCheckState.CellularWarning(modelSizeMb)
            return
        }

        _safetyState.value = SafetyCheckState.Passed
    }

    fun resetSafetyState() {
        _safetyState.value = SafetyCheckState.Idle
    }

    init {
        checkDevice()
    }

    private fun checkDevice() {
        // Get total RAM using ActivityManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / 1024 / 1024).toInt()

        // Get available internal storage
        val statFs = android.os.StatFs(context.filesDir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val availableStorageGb = availableBytes / 1024f / 1024f / 1024f

        val info = DeviceInfo(
            totalRamMb = totalRamMb,
            availableStorageGb = availableStorageGb,
            androidVersion = Build.VERSION.SDK_INT,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        )

        _deviceInfo.value = info

        // Load models enriched with download status
        val allModels = modelRepository.getAvailableModels()
        _models.value = allModels

        // Recommend based on RAM
        _recommendedModelId.value = when {
            totalRamMb >= 3000 -> "small.en"
            totalRamMb >= 1500 -> "base.en"
            else -> "tiny.en"
        }
    }
}
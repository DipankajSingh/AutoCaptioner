package com.dipdev.aiautocaptioner.core.device

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceCapabilityInfo(
    val totalRamMb: Int,
    val availableStorageGb: Float,
    val androidVersion: Int,
    val cpuAbi: String
)

sealed class ModelSafetyCheckState {
    data object Idle : ModelSafetyCheckState()
    data class StorageError(val requiredMb: Long) : ModelSafetyCheckState()
    data class CellularWarning(val sizeMb: Long) : ModelSafetyCheckState()
    data object Passed : ModelSafetyCheckState()
}

@Singleton
class DeviceCapabilityUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getDeviceInfo(): DeviceCapabilityInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / 1024 / 1024).toInt()

        val statFs = StatFs(context.filesDir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val availableStorageGb = availableBytes / 1024f / 1024f / 1024f

        return DeviceCapabilityInfo(
            totalRamMb = totalRamMb,
            availableStorageGb = availableStorageGb,
            androidVersion = Build.VERSION.SDK_INT,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        )
    }

    fun getTotalRamMb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / 1024 / 1024).toInt()
    }

    fun getRecommendedModel(language: String): String {
        val totalRamMb = getTotalRamMb()
        return when {
            language != "en" -> when {
                totalRamMb >= 6000 -> "small"
                totalRamMb >= 4000 -> "base"
                else -> "tiny"
            }
            else -> when {
                totalRamMb >= 6000 -> "small.en"
                totalRamMb >= 4000 -> "base.en"
                else -> "tiny.en"
            }
        }
    }

    fun checkSafetyForModel(modelSizeMb: Long): ModelSafetyCheckState {
        val statFs = StatFs(context.filesDir.absolutePath)
        val availableMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)
        val requiredMb = (modelSizeMb * 1.5).toLong()

        if (availableMb < requiredMb) {
            return ModelSafetyCheckState.StorageError(requiredMb)
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isCellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        if (isCellular) {
            return ModelSafetyCheckState.CellularWarning(modelSizeMb)
        }

        return ModelSafetyCheckState.Passed
    }
}

package com.dipdev.aiautocaptioner.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
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

    fun isModelRamCompatible(minRamMb: Int): Boolean = getTotalRamMb() >= minRamMb

    fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    fun isCharging(): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun getOptimalThreadCount(): Int {
        val maxThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return maxThreads

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return maxThreads
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE     -> maxThreads
            PowerManager.THERMAL_STATUS_LIGHT    -> (maxThreads - 1).coerceAtLeast(1)
            PowerManager.THERMAL_STATUS_MODERATE -> (maxThreads / 2).coerceAtLeast(1)
            else                                 -> 1  // SEVERE, CRITICAL, EMERGENCY, SHUTDOWN
        }
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

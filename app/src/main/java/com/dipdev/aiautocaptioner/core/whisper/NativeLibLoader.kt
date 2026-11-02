package com.dipdev.aiautocaptioner.core.whisper

import android.util.Log
import com.dipdev.aiautocaptioner.core.diagnostics.CpuCapability

object NativeLibLoader {
    private const val TAG = "NativeLibLoader"

    @Volatile
    var loadedVariant: String = "unknown"
        private set

    @Synchronized
    fun loadWhisperLibrary() {
        if (loadedVariant != "unknown") return

        val cpuInfo = CpuCapability.current
        var loaded = false

        if (cpuInfo.isArmv82Supported) {
            try {
                Log.i(TAG, "ARMv8.2-A features detected (asimddp + fp16). Attempting to load whisper-lib-v82...")
                System.loadLibrary("whisper-lib-v82")
                loadedVariant = "armv8.2-a"
                loaded = true
                Log.i(TAG, "Successfully loaded whisper-lib-v82")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "whisper-lib-v82 not found or failed to load. Falling back to baseline whisper-lib: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Error loading whisper-lib-v82: ${e.message}")
            }
        }

        if (!loaded) {
            try {
                Log.i(TAG, "Loading baseline whisper-lib...")
                System.loadLibrary("whisper-lib")
                loadedVariant = "baseline"
                Log.i(TAG, "Successfully loaded baseline whisper-lib")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load baseline whisper-lib: ${e.message}", e)
                throw e
            }
        }
    }
}

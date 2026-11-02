package com.dipdev.aiautocaptioner.core.diagnostics

import android.os.Build
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

data class CpuCapabilityInfo(
    val features: Set<String>,
    val cpuParts: List<String>,
    val coreCount: Int,
    val hasAsimddp: Boolean,
    val hasFphp: Boolean,
    val hasAsimdhp: Boolean,
    val hasI8mm: Boolean,
    val hasBf16: Boolean,
    val hasSve: Boolean,
    val isArmv82Supported: Boolean,
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val hardware: String = Build.HARDWARE,
    val board: String = Build.BOARD,
    val sdkInt: Int = Build.VERSION.SDK_INT
)

object CpuCapability {
    private const val TAG = "CpuCapability"

    val current: CpuCapabilityInfo by lazy { parseCpuInfo() }

    fun parseCpuInfo(cpuInfoContent: String? = null): CpuCapabilityInfo {
        val content = cpuInfoContent ?: readProcCpuInfo()

        val featureSets = mutableListOf<Set<String>>()
        val cpuParts = mutableListOf<String>()

        var currentFeatures = mutableSetOf<String>()

        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Features", ignoreCase = true) || trimmed.startsWith("flags", ignoreCase = true)) {
                val parts = trimmed.split(":")
                if (parts.size >= 2) {
                    val flags = parts[1].trim().lowercase().split("\\s+".toRegex()).toSet()
                    currentFeatures = flags.toMutableSet()
                    featureSets.add(flags)
                }
            } else if (trimmed.startsWith("CPU part", ignoreCase = true)) {
                val parts = trimmed.split(":")
                if (parts.size >= 2) {
                    cpuParts.add(parts[1].trim())
                }
            }
        }

        // Common features intersection across all reported cores
        val commonFeatures = if (featureSets.isNotEmpty()) {
            featureSets.reduce { acc, set -> acc.intersect(set) }
        } else emptySet()

        val hasAsimddp = commonFeatures.contains("asimddp") || commonFeatures.contains("dotprod")
        val hasFphp = commonFeatures.contains("fphp")
        val hasAsimdhp = commonFeatures.contains("asimdhp") || commonFeatures.contains("fp16")
        val hasI8mm = commonFeatures.contains("i8mm")
        val hasBf16 = commonFeatures.contains("bf16")
        val hasSve = commonFeatures.contains("sve")

        // ARMv8.2 requirement: asimddp AND (fphp OR asimdhp) present on every core
        val isArmv82Supported = hasAsimddp && (hasFphp || hasAsimdhp)

        val coreCount = Runtime.getRuntime().availableProcessors()

        return CpuCapabilityInfo(
            features = commonFeatures,
            cpuParts = cpuParts,
            coreCount = coreCount,
            hasAsimddp = hasAsimddp,
            hasFphp = hasFphp,
            hasAsimdhp = hasAsimdhp,
            hasI8mm = hasI8mm,
            hasBf16 = hasBf16,
            hasSve = hasSve,
            isArmv82Supported = isArmv82Supported
        )
    }

    private fun readProcCpuInfo(): String {
        return try {
            File("/proc/cpuinfo").readText()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read /proc/cpuinfo: ${e.message}")
            "unknown"
        }
    }
}

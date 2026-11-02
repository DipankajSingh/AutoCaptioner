package com.dipdev.aiautocaptioner.core.diagnostics

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DiagnosticsRepository"
        private const val MAX_RECORDS = 20
        private const val DIAGNOSTICS_FILE = "diagnostics_history.json"
    }

    private val mutex = Mutex()
    private val file by lazy { File(context.filesDir, DIAGNOSTICS_FILE) }

    suspend fun recordRun(record: TranscriptionRunRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val existing = loadRecordsInternal().toMutableList()
                existing.add(0, record) // Newest first
                val trimmed = existing.take(MAX_RECORDS)
                saveRecordsInternal(trimmed)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record transcription run diagnostics", e)
            }
        }
    }

    suspend fun getRecentRecords(): List<TranscriptionRunRecord> = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadRecordsInternal()
        }
    }

    suspend fun getFormattedDiagnosticsJson(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = loadRecordsInternal()
            val root = JSONObject().apply {
                put("cpuInfo", JSONObject().apply {
                    val cpu = CpuCapability.current
                    put("manufacturer", cpu.manufacturer)
                    put("model", cpu.model)
                    put("sdkInt", cpu.sdkInt)
                    put("coreCount", cpu.coreCount)
                    put("isArmv82Supported", cpu.isArmv82Supported)
                    put("hasAsimddp", cpu.hasAsimddp)
                    put("hasFphp", cpu.hasFphp)
                    put("features", cpu.features.joinToString(","))
                })
                val array = JSONArray()
                records.forEach { array.put(it.toJson()) }
                put("recentRuns", array)
            }
            root.toString(2)
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun loadRecordsInternal(): List<TranscriptionRunRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val array = JSONArray(content)
            val list = mutableListOf<TranscriptionRunRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(TranscriptionRunRecord.fromJson(obj))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load diagnostics history", e)
            emptyList()
        }
    }

    private fun saveRecordsInternal(records: List<TranscriptionRunRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        file.writeText(array.toString())
    }
}

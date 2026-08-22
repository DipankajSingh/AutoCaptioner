package com.dipdev.aiautocaptioner.data.source

import android.content.Context
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperModelRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashReporter: com.dipdev.aiautocaptioner.core.logging.CrashReporter
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun getModels(): List<WhisperModel> {
        return try {
            val jsonString = context.assets.open("whisper_models.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<WhisperModel>>(jsonString)
        } catch (e: Exception) {
            crashReporter.recordException(e)
            e.printStackTrace()
            emptyList()
        }
    }
}

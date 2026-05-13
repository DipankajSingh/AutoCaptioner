package com.dipdev.aiautocaptioner.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    // DataStore is our key-value store for simple settings
    // We use it to remember which model the user downloaded
    private val dataStore: DataStore<Preferences>,
    // The full list of available models from WhisperModule
    private val availableModels: List<WhisperModel>
) {

    companion object {
        private const val TAG = "ModelRepository"

        // DataStore key for storing the active model id
        // DataStore keys are typed — this one stores a String
        private val ACTIVE_MODEL_KEY = stringPreferencesKey("active_model_id")

        // DataStore key for storing onboarding completion flag
        private val ONBOARDING_DONE_KEY =
            stringPreferencesKey("onboarding_complete")
    }

    // OkHttp client configured for large file downloads
    // Longer timeouts because model files are 75-466MB
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)  // 5 min read timeout for large files
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    // ================================================================
    // ONBOARDING
    // ================================================================

    // Check if user has completed onboarding
    // Returns a Flow so SplashScreen can observe it
    fun isOnboardingComplete(): Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[ONBOARDING_DONE_KEY] == "true"
        }

    // Mark onboarding as done — called when user taps "Get Started"
    suspend fun setOnboardingComplete() {
        dataStore.edit { prefs ->
            prefs[ONBOARDING_DONE_KEY] = "true"
        }
    }

    // ================================================================
    // MODEL MANAGEMENT
    // ================================================================

    // Get the list of all available models, enriched with
    // download status (isDownloaded + localPath if present)
    fun getAvailableModels(): List<WhisperModel> {
        return availableModels.map { model ->
            val modelFile = getModelFile(model.id)
            model.copy(
                isDownloaded = modelFile.exists(),
                localPath = if (modelFile.exists()) modelFile.absolutePath else null
            )
        }
    }

    // Get a specific model by ID
    fun getModelById(modelId: String): WhisperModel? =
        getAvailableModels().find { it.id == modelId }

    // Get the currently active/selected model
    // Returns null if no model has been downloaded yet
    fun getActiveModel(): Flow<WhisperModel?> =
        dataStore.data.map { prefs ->
            val activeId = prefs[ACTIVE_MODEL_KEY]
            activeId?.let { getModelById(it) }
        }

    // Save which model the user selected as active
    suspend fun setActiveModel(modelId: String) {
        dataStore.edit { prefs ->
            prefs[ACTIVE_MODEL_KEY] = modelId
        }
        Log.i(TAG, "Active model set to: $modelId")
    }

    // Check if any model is downloaded and ready to use
    fun hasDownloadedModel(): Boolean =
        availableModels.any { getModelFile(it.id).exists() }

    // Get the file path where a model should be stored
    // All models stored in: /files/models/ggml-{modelId}.bin
    fun getModelFile(modelId: String): File {
        val modelsDir = File(context.filesDir, "models")
        modelsDir.mkdirs()
        return File(modelsDir, "ggml-$modelId.bin")
    }

    // Delete a downloaded model to free up storage
    suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            val file = getModelFile(modelId)
            if (file.exists()) {
                file.delete()
                Log.i(TAG, "Deleted model: $modelId")
            }
        }
    }

    // ================================================================
    // MODEL DOWNLOAD
    // ================================================================

    // Download a model with real-time progress reporting
    // Returns a Flow of DownloadState — the UI collects this
    // to show the progress bar and status messages
    fun downloadModel(modelId: String): Flow<DownloadState> = flow {
        val model = getModelById(modelId)
            ?: run {
                emit(DownloadState.Error("Model not found: $modelId"))
                return@flow
            }

        val outputFile = getModelFile(modelId)

        if (outputFile.exists()) {
            emit(DownloadState.Complete(outputFile.absolutePath))
            return@flow
        }

        emit(DownloadState.Starting)
        Log.i(TAG, "Starting download: ${model.displayName}")

        try {
            val request = Request.Builder().url(model.downloadUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("Download failed: ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Error("Empty response body"))
                return@flow
            }

            val totalBytes = body.contentLength()
            val tempFile = File(outputFile.parent, "${outputFile.name}.tmp")

            var downloadedBytes = 0L
            var lastProgressEmit = 0

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (progress > lastProgressEmit) {
                                lastProgressEmit = progress
                                emit(DownloadState.Downloading(
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                ))
                            }
                        }
                    }
                }
            }

            tempFile.renameTo(outputFile)
            setActiveModel(modelId)

            Log.i(TAG, "Download complete: ${outputFile.absolutePath}")
            emit(DownloadState.Complete(outputFile.absolutePath))

        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            File(outputFile.parent, "${outputFile.name}.tmp").delete()
            emit(DownloadState.Error(e.message ?: "Unknown error"))
        }
// flowOn moves the entire flow execution to IO thread
// while keeping emissions safe to collect on any thread
    }.flowOn(Dispatchers.IO)
}

// ================================================================
// Download state sealed class
// Each subclass represents a different state of the download
// The UI switches on this to show the right UI
// ================================================================
sealed class DownloadState {

    // Download is about to start — show "Preparing..." in UI
    data object Starting : DownloadState()

    // Download in progress — show progress bar
    data class Downloading(
        val progress: Int,          // 0-100
        val downloadedBytes: Long,  // for showing "45 MB / 142 MB"
        val totalBytes: Long
    ) : DownloadState()

    // Download finished successfully
    data class Complete(
        val filePath: String        // absolute path to the model file
    ) : DownloadState()

    // Something went wrong
    data class Error(
        val message: String
    ) : DownloadState()
}
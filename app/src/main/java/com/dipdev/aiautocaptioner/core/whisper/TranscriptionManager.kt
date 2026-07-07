package com.dipdev.aiautocaptioner.core.whisper

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.dipdev.aiautocaptioner.core.audio.AudioExtractionUseCase
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository

import com.dipdev.aiautocaptioner.ui.processing.ProcessingStep
import com.dipdev.aiautocaptioner.ui.processing.StreamedSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class TranscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository,
    private val modelRepository: ModelRepository,
    private val whisperEngine: WhisperEngine,
    private val crashReporter: CrashReporter,
    private val audioExtractionUseCase: AudioExtractionUseCase
) {
    companion object {
        private const val TAG = "TranscriptionManager"
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _step = MutableStateFlow<ProcessingStep>(ProcessingStep.Idle)
    val step: StateFlow<ProcessingStep> = _step.asStateFlow()

    private val _streamedSegments = MutableStateFlow<List<StreamedSegment>>(emptyList())
    val streamedSegments: StateFlow<List<StreamedSegment>> = _streamedSegments.asStateFlow()

    private val _segmentBuffer = Channel<StreamedSegment>(Channel.UNLIMITED)
    private var dripJob: Job? = null
    private var activeJob: Job? = null
    @Volatile private var isCancelled = false
    private var transcriptionStartTimeMs: Long = 0L
    private var activeProjectId: String? = null

    private fun updateNotification(text: String?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (text == null) {
            notificationManager.cancel(101)
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "transcription_channel", "Transcription", android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(context, "transcription_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AutoCaptioner Processing")
            .setContentText(text)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        notificationManager.notify(101, notification)
    }

    fun startProcess(projectId: String, modelId: String, language: String, translateToEnglish: Boolean) {
        if (activeJob?.isActive == true) return
        activeProjectId = projectId
        isCancelled = false
        _streamedSegments.value = emptyList()
        _step.value = ProcessingStep.Idle

        activeJob = managerScope.launch {
            try {
                // Ensure service is started to host the notification

                val model = modelRepository.getModelById(modelId) ?: throw Exception("Model not found")

                // Step 1: Download model if needed
                if (!model.isDownloaded) {
                    _step.value = ProcessingStep.DownloadingModel(modelName = model.displayName)
                    updateNotification("Downloading ${model.displayName}...")
                    
                    var downloadSuccess = false
                    modelRepository.downloadModel(modelId).collect { state ->
                        when (state) {
                            is DownloadState.Downloading -> {
                                _step.value = ProcessingStep.DownloadingModel(
                                    modelName = model.displayName,
                                    progress = state.progress,
                                    downloadedBytes = state.downloadedBytes,
                                    totalBytes = state.totalBytes
                                )
                                updateNotification("Downloading model... ${state.progress}%")
                            }
                            is DownloadState.Complete -> {
                                downloadSuccess = true
                            }
                            is DownloadState.Error -> {
                                throw Exception("Model download failed: ${state.message}")
                            }
                            else -> {}
                        }
                    }
                    if (!downloadSuccess) return@launch
                }

                // Proceed to processing
                startTranscription(projectId, language, translateToEnglish)

            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Process cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
                crashReporter.recordException(e)
                _step.value = ProcessingStep.Error(e.message ?: "Unknown error")
                updateNotification(null)
            }
        }
    }

    private suspend fun startTranscription(projectId: String, language: String, translateToEnglish: Boolean) {
        startDripFeed()

        val project = projectRepository.getProjectById(projectId) ?: run {
            _step.value = ProcessingStep.Error("Project not found")
            return
        }

        if (project.status == ProjectStatus.TRANSCRIBED || project.status == ProjectStatus.EXPORTED) {
            _step.value = ProcessingStep.Done
            updateNotification(null)
            return
        }

        _step.value = ProcessingStep.ExtractingAudio
        updateNotification("Extracting audio...")
        projectRepository.updateStatus(projectId, ProjectStatus.EXTRACTING_AUDIO)
        projectRepository.updateProject(project.copy(transcriptionLanguage = language))

        _step.value = ProcessingStep.LoadingModel
        updateNotification("Loading AI model...")
        val activeModelFile = modelRepository.getActiveModel().first()?.let { model ->
            modelRepository.getModelFile(model.id)
        }

        if (activeModelFile == null || !activeModelFile.exists()) {
            throw Exception("No model downloaded")
        }

        if (!whisperEngine.isReady()) {
            val success = whisperEngine.initialize(activeModelFile)
            if (!success) {
                throw Exception("Failed to load model")
            }
        }

        transcriptionStartTimeMs = System.currentTimeMillis()
        _step.value = ProcessingStep.Transcribing(0f)
        projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBING)

        val pcmSamples = audioExtractionUseCase.extractAudioFloatArray(project.workingVideoPath)

        val allWords = whisperEngine.transcribeWithWordTimestamps(
                samples = pcmSamples,
                language = language,
                translateToEnglish = translateToEnglish,
                onProgress = { percent ->
                    if (isCancelled) throw kotlinx.coroutines.CancellationException("Cancelled by user")
                    val progressFraction = percent / 100f
                    val elapsedMs = System.currentTimeMillis() - transcriptionStartTimeMs
                    val etaSecs: Int? = if (progressFraction > 0.05f) {
                        val estimatedTotalMs = elapsedMs / progressFraction
                        val remainingMs = estimatedTotalMs - elapsedMs
                        (remainingMs / 1000).toInt().coerceAtLeast(1)
                    } else null
                    _step.value = ProcessingStep.Transcribing(progressFraction, etaSecs)
                    updateNotification("Transcribing video... ${percent}%")
                },
                onSegmentDecoded = { text, startMs, endMs ->
                    if (isCancelled) throw kotlinx.coroutines.CancellationException("Cancelled by user")
                    val trimmed = text.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("[")) {
                        _segmentBuffer.trySend(StreamedSegment(trimmed, startMs, endMs))
                    }
                }
            )

        if (isCancelled) return

        if (allWords.isEmpty()) {
            throw Exception("No words transcribed")
        }

        _step.value = ProcessingStep.Saving
        updateNotification("Saving transcription...")
        
        val finalSegments = CaptionSegmenter.buildFinalSegments(allWords)

        captionRepository.saveTranscription(projectId, finalSegments)
        projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBED)

        flushDripFeed()
        _step.value = ProcessingStep.Done
        
        delay(2000.milliseconds)
        updateNotification(null)
    }

    private fun startDripFeed() {
        dripJob?.cancel()
        dripJob = managerScope.launch {
            for (segment in _segmentBuffer) {
                _streamedSegments.value = _streamedSegments.value + segment
                delay(400L.milliseconds)
            }
        }
    }

    private fun flushDripFeed() {
        dripJob?.cancel()
        dripJob = null
        val remaining = mutableListOf<StreamedSegment>()
        while (true) {
            val seg = _segmentBuffer.tryReceive().getOrNull() ?: break
            remaining.add(seg)
        }
        if (remaining.isNotEmpty()) {
            _streamedSegments.value = _streamedSegments.value + remaining
        }
    }

    fun cancel() {
        isCancelled = true
        _step.value = ProcessingStep.Cancelling
        activeJob?.cancel()
        activeJob = null
        managerScope.launch(Dispatchers.IO) {
            whisperEngine.release()
            updateNotification(null)
            activeProjectId?.let { pid ->
                val hasCaptions = captionRepository.getSegmentsForProject(pid).first().isNotEmpty()
                val status = if (hasCaptions) ProjectStatus.TRANSCRIBED else ProjectStatus.IMPORTED
                projectRepository.updateStatus(pid, status)
            }
            _step.value = ProcessingStep.Cancelled
        }
    }

    fun clearState() {
        _step.value = ProcessingStep.Idle
        _streamedSegments.value = emptyList()
    }
}

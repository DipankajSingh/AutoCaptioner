package com.dipdev.aiautocaptioner.core.whisper

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.dipdev.aiautocaptioner.core.audio.AudioExtractionUseCase
import com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase
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
import kotlinx.coroutines.sync.Mutex
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
    private val audioExtractionUseCase: AudioExtractionUseCase,
    private val deviceCapabilityUseCase: DeviceCapabilityUseCase
) {
    companion object {
        private const val TAG = "TranscriptionManager"
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _step = MutableStateFlow<ProcessingStep>(ProcessingStep.Idle)
    val step: StateFlow<ProcessingStep> = _step.asStateFlow()

    private val _streamedSegments = MutableStateFlow<List<StreamedSegment>>(emptyList())
    val streamedSegments: StateFlow<List<StreamedSegment>> = _streamedSegments.asStateFlow()

    private val _detectedLanguage = MutableStateFlow<String?>(null)
    val detectedLanguage: StateFlow<String?> = _detectedLanguage.asStateFlow()

    private val _segmentBuffer = Channel<StreamedSegment>(Channel.UNLIMITED)
    @Volatile private var dripJob: Job? = null
    private val jobMutex = Mutex()
    private var activeJob: Job? = null
    @Volatile private var isCancelled = false
    private var transcriptionStartTimeMs: Long = 0L
    @Volatile private var activeProjectId: String? = null

    // Notifications are now handled by TranscriptionForegroundService by observing the 'step' stateflow

    fun startProcess(projectId: String, modelId: String, language: String, translateToEnglish: Boolean, isRegenerating: Boolean = false, initialPrompt: String? = null) {
        if (activeJob?.isActive == true) {
            Log.w(TAG, "startProcess called while job active — cancelling previous")
            activeJob?.cancel()
        }
        activeProjectId = projectId
        isCancelled = false
        _streamedSegments.value = emptyList()
        _step.value = ProcessingStep.Idle

        activeJob = managerScope.launch {
            try {
                // Ensure service is started to host the notification and hold Wakelock
                val serviceIntent = Intent(context, TranscriptionForegroundService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)

                // Battery safety: abort if battery is critically low and not charging
                val batteryLevel = deviceCapabilityUseCase.getBatteryLevel()
                val charging = deviceCapabilityUseCase.isCharging()
                if (batteryLevel in 0..10 && !charging) {
                    throw Exception("Battery too low ($batteryLevel%). Please plug in and try again.")
                }

                val model = modelRepository.getModelById(modelId) ?: throw Exception("Model not found. Please try again.")

                // Step 1: Download model if needed
                if (!model.isDownloaded) {
                    _step.value = ProcessingStep.DownloadingModel(modelName = model.displayName)
                    
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
                            }
                            is DownloadState.Complete -> {
                                downloadSuccess = true
                            }
                            is DownloadState.Error -> {
                                throw Exception("Could not download the model. Check your internet connection and try again.")
                            }
                            else -> {}
                        }
                    }
                    if (!downloadSuccess) {
                        throw Exception("Model download did not complete")
                    }
                }

                // Proceed to processing
                startTranscription(projectId, language, translateToEnglish, isRegenerating, initialPrompt)

            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Process cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
                crashReporter.recordException(e)
                _step.value = ProcessingStep.Error(e.message ?: "Unknown error")
                activeProjectId?.let { pid ->
                    try {
                        val hasCaptions = captionRepository.getSegmentsForProject(pid).first().isNotEmpty()
                        val status = if (hasCaptions) ProjectStatus.TRANSCRIBED else ProjectStatus.IMPORTED
                        projectRepository.updateStatus(pid, status)
                    } catch (inner: Exception) {
                        Log.e(TAG, "Failed to revert status", inner)
                    }
                }
            } finally {
                whisperEngine.release()
                context.stopService(Intent(context, TranscriptionForegroundService::class.java))
            }
        }
    }

    private suspend fun startTranscription(projectId: String, language: String, translateToEnglish: Boolean, isRegenerating: Boolean, initialPrompt: String? = null) {
        startDripFeed()

        val project = projectRepository.getProjectById(projectId) ?: run {
            _step.value = ProcessingStep.Error("Project not found. Please try again.")
            return
        }

        if (!isRegenerating && (project.status == ProjectStatus.TRANSCRIBED || project.status == ProjectStatus.EXPORTED)) {
            _step.value = ProcessingStep.Done
            return
        }

        _step.value = ProcessingStep.ExtractingAudio
        projectRepository.updateStatus(projectId, ProjectStatus.EXTRACTING_AUDIO)
        projectRepository.updateProject(project.copy(transcriptionLanguage = language, initialPrompt = initialPrompt))

        _step.value = ProcessingStep.LoadingModel
        val activeModelFile = modelRepository.getActiveModel().first()?.let { model ->
            modelRepository.getModelFile(model.id)
        }

        if (activeModelFile == null || !activeModelFile.exists()) {
            throw Exception("No model downloaded. Please download a model first.")
        }

        if (!whisperEngine.isReady()) {
            whisperEngine.initialize(activeModelFile)
        }

        transcriptionStartTimeMs = System.currentTimeMillis()
        _step.value = ProcessingStep.Transcribing(0f)
        projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBING)

        val pcmSamples = audioExtractionUseCase.extractAudioFloatArray(project.workingVideoPath)

        val allWords = whisperEngine.transcribeWithWordTimestamps(
                samples = pcmSamples,
                language = language,
                translateToEnglish = translateToEnglish,
                initialPrompt = initialPrompt,
                onProgress = { percent ->
                    if (isCancelled) return@transcribeWithWordTimestamps
                    val progressFraction = percent / 100f
                    val elapsedMs = System.currentTimeMillis() - transcriptionStartTimeMs
                    val etaSecs: Int? = if (progressFraction > 0.05f) {
                        val estimatedTotalMs = elapsedMs / progressFraction
                        val remainingMs = estimatedTotalMs - elapsedMs
                        (remainingMs / 1000).toInt().coerceAtLeast(1)
                    } else null
                    _step.value = ProcessingStep.Transcribing(progressFraction, etaSecs)
                },
                onSegmentDecoded = { text, startMs, endMs ->
                    if (isCancelled) return@transcribeWithWordTimestamps
                    val trimmed = text.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("[")) {
                        _segmentBuffer.trySend(StreamedSegment(trimmed, startMs, endMs))
                    }
                }
            )

        if (isCancelled) return

        if (allWords.isEmpty()) {
            throw Exception("Could not detect any speech in this video. Make sure the video has clear audio.")
        }

        // Surface the language whisper actually used (relevant when user chose "auto")
        if (language == "auto" || language.isEmpty()) {
            _detectedLanguage.value = whisperEngine.lastDetectedLanguage
        } else {
            _detectedLanguage.value = language
        }

        _step.value = ProcessingStep.Saving
        
        val finalSegments = CaptionSegmenter.buildFinalSegments(allWords)

        captionRepository.saveTranscription(projectId, finalSegments)
        projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBED)

        flushDripFeed()
        _step.value = ProcessingStep.Done
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
        _detectedLanguage.value = null
    }
}

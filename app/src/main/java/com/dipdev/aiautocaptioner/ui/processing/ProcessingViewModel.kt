package com.dipdev.aiautocaptioner.ui.processing

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.core.whisper.CaptionSegmenter
import com.dipdev.aiautocaptioner.core.whisper.WhisperEngine
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.service.TranscriptionService
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

// ════════════════════════════════════════════════════════════════════════════════
// Processing Steps — represents the current state of the processing pipeline
// ════════════════════════════════════════════════════════════════════════════════
sealed class ProcessingStep {
    data object Idle : ProcessingStep()
    data object Ready : ProcessingStep()

    // First-time model setup
    data class SetupAI(val models: List<WhisperModel>, val recommendedModelId: String?) : ProcessingStep()
    data class DownloadingModel(
        val modelName: String,
        val progress: Int = 0,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0
    ) : ProcessingStep()

    data object ExtractingAudio : ProcessingStep()
    data object LoadingModel : ProcessingStep()
    data class Transcribing(val progress: Float = 0f, val estimatedSecondsRemaining: Int? = null) : ProcessingStep()
    data object Saving : ProcessingStep()
    data object Done : ProcessingStep()
    data object Cancelling : ProcessingStep()
    data object Cancelled : ProcessingStep()
    data class Error(val message: String) : ProcessingStep()
}

// ════════════════════════════════════════════════════════════════════════════════
// Streamed segment — a single caption chunk decoded by the AI in real-time
// ════════════════════════════════════════════════════════════════════════════════
data class StreamedSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

// ════════════════════════════════════════════════════════════════════════════════
// Safety check for first-time downloads
// ════════════════════════════════════════════════════════════════════════════════
sealed class ModelSafetyCheck {
    data object Idle : ModelSafetyCheck()
    data class StorageError(val requiredMb: Long) : ModelSafetyCheck()
    data class CellularWarning(val modelId: String, val sizeMb: Long) : ModelSafetyCheck()
    data object Passed : ModelSafetyCheck()
}

data class ProcessingUiState(
    val step: ProcessingStep = ProcessingStep.Idle,
    val selectedLanguage: String = "en",
    val workingVideoPath: String? = null,
    val streamedSegments: List<StreamedSegment> = emptyList(),
    val segmentCount: Int = 0,
    val safetyCheck: ModelSafetyCheck = ModelSafetyCheck.Idle,
    val activeModel: WhisperModel? = null
) : UiState

sealed class ProcessingUiEvent : UiEvent {
    data class PrepareForProject(val projectId: String) : ProcessingUiEvent()
    data class SelectLanguage(val language: String) : ProcessingUiEvent()
    data object ShowModelSetup : ProcessingUiEvent()
    data object ShowModelPicker : ProcessingUiEvent()
    data class CheckSafetyAndDownload(val modelId: String) : ProcessingUiEvent()
    data class ConfirmCellularDownload(val modelId: String) : ProcessingUiEvent()
    data class DownloadAndProcess(val modelId: String, val projectId: String) : ProcessingUiEvent()
    data object ResetSafetyCheck : ProcessingUiEvent()
    data object CancelModelSetup : ProcessingUiEvent()
    data object Cancel : ProcessingUiEvent()
    data class StartProcessing(val projectId: String) : ProcessingUiEvent()
}

sealed class ProcessingUiEffect : UiEffect {
    data object NavigateToStyleEditor : ProcessingUiEffect()
}

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository,
    private val modelRepository: ModelRepository,
    private val whisperEngine: WhisperEngine,
    private val crashReporter: CrashReporter,
    private val audioExtractionUseCase: com.dipdev.aiautocaptioner.core.audio.AudioExtractionUseCase,
    private val deviceCapabilityUseCase: com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase
) : BaseViewModel<ProcessingUiState, ProcessingUiEvent, ProcessingUiEffect>(ProcessingUiState()) {

    companion object {
        private const val TAG = "ProcessingVM"
        private const val SEGMENT_DRIP_DELAY_MS = 400L
    }

    private val _segmentBuffer = Channel<StreamedSegment>(Channel.UNLIMITED)
    private var dripJob: Job? = null
    private var activeJob: Job? = null
    @Volatile private var isCancelled = false
    private var transcriptionStartTimeMs: Long = 0L
    private var pendingProjectId: String? = null

    init {
        viewModelScope.launch {
            modelRepository.getActiveModel().collect { model ->
                setState { copy(activeModel = model) }
            }
        }
    }

    override fun handleEvent(event: ProcessingUiEvent) {
        when (event) {
            is ProcessingUiEvent.PrepareForProject -> prepareForProject(event.projectId)
            is ProcessingUiEvent.SelectLanguage -> selectLanguage(event.language)
            is ProcessingUiEvent.ShowModelSetup -> showModelSetup()
            is ProcessingUiEvent.ShowModelPicker -> showModelPicker()
            is ProcessingUiEvent.CheckSafetyAndDownload -> checkSafetyAndDownload(event.modelId)
            is ProcessingUiEvent.ConfirmCellularDownload -> confirmCellularDownload(event.modelId)
            is ProcessingUiEvent.DownloadAndProcess -> downloadAndProcess(event.modelId, event.projectId)
            is ProcessingUiEvent.ResetSafetyCheck -> resetSafetyCheck()
            is ProcessingUiEvent.CancelModelSetup -> cancelModelSetup()
            is ProcessingUiEvent.Cancel -> cancel()
            is ProcessingUiEvent.StartProcessing -> startProcessing(event.projectId)
        }
    }

    private fun startDripFeed() {
        dripJob?.cancel()
        dripJob = viewModelScope.launch {
            for (segment in _segmentBuffer) {
                setState { copy(streamedSegments = streamedSegments + segment) }
                delay(SEGMENT_DRIP_DELAY_MS.milliseconds)
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
            setState { copy(streamedSegments = streamedSegments + remaining) }
        }
    }

    private fun prepareForProject(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            setState { 
                copy(
                    workingVideoPath = project?.workingVideoPath,
                    selectedLanguage = project?.transcriptionLanguage ?: "en",
                    step = if (project?.status == ProjectStatus.TRANSCRIBED || project?.status == ProjectStatus.EXPORTED) {
                        ProcessingStep.Done
                    } else {
                        ProcessingStep.Ready
                    }
                )
            }
        }
    }

    private fun selectLanguage(language: String) {
        setState { copy(selectedLanguage = language) }
    }

    private fun showModelSetup() {
        val language = currentState.selectedLanguage
        val allModels = modelRepository.getAvailableModels()

        val compatibleModels = if (language != "en") {
            allModels.filter { it.isMultilingual }
        } else {
            allModels.filter { !it.isMultilingual }
        }

        val recommendedId = deviceCapabilityUseCase.getRecommendedModel(language)
        val finalRec = if (compatibleModels.any { it.id == recommendedId }) recommendedId else compatibleModels.firstOrNull()?.id

        setState { copy(step = ProcessingStep.SetupAI(models = compatibleModels, recommendedModelId = finalRec)) }
    }

    private fun showModelPicker() {
        val language = currentState.selectedLanguage
        val allModels = modelRepository.getAvailableModels()

        val compatibleModels = if (language != "en") {
            allModels.filter { it.isMultilingual }
        } else {
            allModels.filter { !it.isMultilingual }
        }

        val currentModelId = currentState.activeModel?.id
        val finalRec = currentModelId ?: compatibleModels.firstOrNull()?.id

        setState { copy(step = ProcessingStep.SetupAI(models = compatibleModels, recommendedModelId = finalRec)) }
    }

    private fun checkSafetyAndDownload(modelId: String) {
        val model = modelRepository.getModelById(modelId) ?: return

        when (val state = deviceCapabilityUseCase.checkSafetyForModel(model.sizeMb.toLong())) {
            is com.dipdev.aiautocaptioner.core.device.ModelSafetyCheckState.StorageError -> {
                setState { copy(safetyCheck = ModelSafetyCheck.StorageError(state.requiredMb)) }
            }
            is com.dipdev.aiautocaptioner.core.device.ModelSafetyCheckState.CellularWarning -> {
                setState { copy(safetyCheck = ModelSafetyCheck.CellularWarning(modelId, state.sizeMb)) }
            }
            else -> {
                setState { copy(safetyCheck = ModelSafetyCheck.Passed) }
                startModelDownload(modelId)
            }
        }
    }

    private fun resetSafetyCheck() {
        setState { copy(safetyCheck = ModelSafetyCheck.Idle) }
    }

    private fun cancelModelSetup() {
        if (currentState.step is ProcessingStep.SetupAI) {
            setState { copy(step = ProcessingStep.Ready) }
        }
    }

    private fun confirmCellularDownload(modelId: String) {
        setState { copy(safetyCheck = ModelSafetyCheck.Idle) }
        startModelDownload(modelId)
    }

    private fun downloadAndProcess(modelId: String, projectId: String) {
        pendingProjectId = projectId
        checkSafetyAndDownload(modelId)
    }

    private fun startModelDownload(modelId: String) {
        val model = modelRepository.getModelById(modelId) ?: return

        if (model.isDownloaded) {
            pendingProjectId?.let { startProcessing(it) }
            return
        }

        setState { copy(step = ProcessingStep.DownloadingModel(modelName = model.displayName)) }

        viewModelScope.launch {
            modelRepository.downloadModel(modelId).collect { state ->
                when (state) {
                    is DownloadState.Starting -> {
                        setState { copy(step = ProcessingStep.DownloadingModel(modelName = model.displayName)) }
                    }
                    is DownloadState.Downloading -> {
                        setState { 
                            copy(step = ProcessingStep.DownloadingModel(
                                modelName = model.displayName,
                                progress = state.progress,
                                downloadedBytes = state.downloadedBytes,
                                totalBytes = state.totalBytes
                            ))
                        }
                    }
                    is DownloadState.Complete -> {
                        Log.i(TAG, "Model download complete, starting processing")
                        pendingProjectId?.let { startProcessing(it) }
                    }
                    is DownloadState.Error -> {
                        setState { copy(step = ProcessingStep.Error("Model download failed: ${state.message}")) }
                    }
                }
            }
        }
    }

    private fun cancel() {
        isCancelled = true
        setState { copy(step = ProcessingStep.Cancelling) }
        activeJob?.cancel()
        activeJob = null
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            whisperEngine.release()
            context.stopService(Intent(context, TranscriptionService::class.java))
            setState { copy(step = ProcessingStep.Cancelled) }
        }
    }

    private fun startProcessing(projectId: String) {
        if (activeJob?.isActive == true) return
        isCancelled = false
        setState { copy(streamedSegments = emptyList(), segmentCount = 0, step = ProcessingStep.ExtractingAudio) }
        startDripFeed()
        val language = currentState.selectedLanguage

        activeJob = viewModelScope.launch {
            try {
                val project = projectRepository.getProjectById(projectId) ?: run {
                    setState { copy(step = ProcessingStep.Error("Project not found")) }
                    return@launch
                }

                if (project.status == ProjectStatus.TRANSCRIBED || project.status == ProjectStatus.EXPORTED) {
                    setState { copy(step = ProcessingStep.Done) }
                    return@launch
                }

                setState { copy(step = ProcessingStep.ExtractingAudio) }
                TranscriptionService.updateProgress("Extracting audio...")
                ContextCompat.startForegroundService(context, Intent(context, TranscriptionService::class.java))
                projectRepository.updateStatus(projectId, ProjectStatus.EXTRACTING_AUDIO)

                projectRepository.updateProject(project.copy(transcriptionLanguage = language))

                setState { copy(step = ProcessingStep.LoadingModel) }
                TranscriptionService.updateProgress("Loading AI model...")
                val activeModelFile = modelRepository.getActiveModel().first()?.let { model ->
                    modelRepository.getModelFile(model.id)
                }

                if (activeModelFile == null || !activeModelFile.exists()) {
                    setState { copy(step = ProcessingStep.Error("No model downloaded")) }
                    return@launch
                }

                if (!whisperEngine.isReady()) {
                    val success = whisperEngine.initialize(activeModelFile)
                    if (!success) {
                        setState { copy(step = ProcessingStep.Error("Failed to load model")) }
                        return@launch
                    }
                }

                transcriptionStartTimeMs = System.currentTimeMillis()
                setState { copy(step = ProcessingStep.Transcribing(0f)) }
                projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBING)

                val pcmSamples = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    audioExtractionUseCase.extractAudioFloatArray(project.workingVideoPath)
                }

                val allWords = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    whisperEngine.transcribeWithWordTimestamps(
                        samples = pcmSamples,
                        language = language,
                    onProgress = { percent ->
                        val progressFraction = percent / 100f
                        val elapsedMs = System.currentTimeMillis() - transcriptionStartTimeMs
                        val etaSecs: Int? = if (progressFraction > 0.05f) {
                            val estimatedTotalMs = elapsedMs / progressFraction
                            val remainingMs = estimatedTotalMs - elapsedMs
                            (remainingMs / 1000).toInt().coerceAtLeast(1)
                        } else null
                        setState { copy(step = ProcessingStep.Transcribing(progressFraction, etaSecs)) }
                        TranscriptionService.updateProgress("Transcribing video... ${percent}%")
                    },
                    onSegmentDecoded = { text, startMs, endMs ->
                        val trimmed = text.trim()
                        if (trimmed.isNotBlank() && !trimmed.startsWith("[")) {
                            _segmentBuffer.trySend(StreamedSegment(trimmed, startMs, endMs))
                        }
                    }
                )
                }

                if (isCancelled) return@launch

                if (allWords.isEmpty()) {
                    setState { copy(step = ProcessingStep.Error("No words transcribed")) }
                    return@launch
                }

                setState { copy(step = ProcessingStep.Saving) }
                TranscriptionService.updateProgress("Saving transcription...")
                
                val finalSegments = CaptionSegmenter.buildFinalSegments(allWords)

                captionRepository.saveTranscription(projectId, finalSegments)
                projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBED)
                setState { copy(segmentCount = finalSegments.size) }

                flushDripFeed()
                setState { copy(step = ProcessingStep.Done) }

                delay(2000.milliseconds)
                setEffect(ProcessingUiEffect.NavigateToStyleEditor)

            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Transcription cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
                crashReporter.recordException(e)
                setState { copy(step = ProcessingStep.Error(e.message ?: "Unknown error")) }
            } finally {
                whisperEngine.release()
                context.stopService(Intent(context, TranscriptionService::class.java))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            whisperEngine.release()
        }
    }
}

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
    val translateToEnglish: Boolean = false,
    val workingVideoPath: String? = null,
    val streamedSegments: List<StreamedSegment> = emptyList(),
    val segmentCount: Int = 0,
    val safetyCheck: ModelSafetyCheck = ModelSafetyCheck.Idle,
    val activeModel: WhisperModel? = null
) : UiState

sealed class ProcessingUiEvent : UiEvent {
    data class PrepareForProject(val projectId: String) : ProcessingUiEvent()
    data class SelectLanguage(val language: String) : ProcessingUiEvent()
    data class ToggleTranslation(val enabled: Boolean) : ProcessingUiEvent()
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
    private val crashReporter: CrashReporter,
    private val deviceCapabilityUseCase: com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase,
    private val transcriptionManager: com.dipdev.aiautocaptioner.core.whisper.TranscriptionManager
) : BaseViewModel<ProcessingUiState, ProcessingUiEvent, ProcessingUiEffect>(ProcessingUiState()) {

    companion object {
        private const val TAG = "ProcessingVM"
    }

    private var pendingProjectId: String? = null

    init {
        viewModelScope.launch {
            modelRepository.getActiveModel().collect { model ->
                setState { copy(activeModel = model) }
            }
        }
        
        viewModelScope.launch {
            transcriptionManager.step.collect { step ->
                if (step !is ProcessingStep.Idle) {
                    setState { copy(step = step) }
                    if (step is ProcessingStep.Done) {
                        delay(2000.milliseconds)
                        setEffect(ProcessingUiEffect.NavigateToStyleEditor)
                        transcriptionManager.clearState()
                    }
                }
            }
        }
        
        viewModelScope.launch {
            transcriptionManager.streamedSegments.collect { segments ->
                setState { copy(streamedSegments = segments, segmentCount = segments.size) }
            }
        }
    }

    override fun handleEvent(event: ProcessingUiEvent) {
        when (event) {
            is ProcessingUiEvent.PrepareForProject -> prepareForProject(event.projectId)
            is ProcessingUiEvent.SelectLanguage -> selectLanguage(event.language)
            is ProcessingUiEvent.ToggleTranslation -> setState { copy(translateToEnglish = event.enabled) }
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
        setState { 
            copy(
                selectedLanguage = language,
                translateToEnglish = if (language == "en") false else translateToEnglish
            ) 
        }
    }

    private fun showModelSetup() {
        val language = currentState.selectedLanguage
        val allModels = modelRepository.getAvailableModels()

        val compatibleModels = allModels.filter { model ->
            if (language == "en") {
                model.languages.contains("en")
            } else if (language == "auto") {
                model.isMultilingual
            } else {
                model.isMultilingual || model.languages.contains(language)
            }
        }

        val recommendedId = deviceCapabilityUseCase.getRecommendedModel(language)
        val finalRec = if (compatibleModels.any { it.id == recommendedId }) recommendedId else compatibleModels.firstOrNull()?.id

        setState { copy(step = ProcessingStep.SetupAI(models = compatibleModels, recommendedModelId = finalRec)) }
    }

    private fun showModelPicker() {
        val language = currentState.selectedLanguage
        val allModels = modelRepository.getAvailableModels()

        val compatibleModels = allModels.filter { model ->
            if (language == "en") {
                model.languages.contains("en")
            } else if (language == "auto") {
                model.isMultilingual
            } else {
                model.isMultilingual || model.languages.contains(language)
            }
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

        // We delegate downloading to the transcription manager so it runs in foreground
        pendingProjectId?.let {
            transcriptionManager.startProcess(
                projectId = it,
                modelId = modelId,
                language = currentState.selectedLanguage,
                translateToEnglish = currentState.translateToEnglish
            )
        }
    }

    private fun cancel() {
        transcriptionManager.cancel()
    }

    private fun startProcessing(projectId: String) {
        val activeModelId = currentState.activeModel?.id ?: return
        transcriptionManager.startProcess(
            projectId = projectId,
            modelId = activeModelId,
            language = currentState.selectedLanguage,
            translateToEnglish = currentState.translateToEnglish
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Removed local cleanup; TranscriptionManager handles cancellation on its end if needed
    }
}

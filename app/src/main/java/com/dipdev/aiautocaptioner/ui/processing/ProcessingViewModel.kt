package com.dipdev.aiautocaptioner.ui.processing

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.core.whisper.CaptionSegmenter
import com.dipdev.aiautocaptioner.core.whisper.WhisperEngine
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository

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
    val activeModel: WhisperModel? = null,
    val availableModels: List<WhisperModel> = emptyList(),
    val detectedLanguage: String? = null
) : UiState

sealed class ProcessingUiEvent : UiEvent {
    data class PrepareForProject(
        val projectId: String,
        val forceModelPicker: Boolean = false,
        val isRegenerating: Boolean = false
    ) : ProcessingUiEvent()
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
    data class StartTranscriptionExplicit(
        val projectId: String,
        val modelId: String,
        val language: String,
        val translateToEnglish: Boolean
    ) : ProcessingUiEvent()
    data object ResetToIdle : ProcessingUiEvent()
}

sealed class ProcessingUiEffect : UiEffect {
    data object NavigateToVideoEditor : ProcessingUiEffect()
    data object NavigateToCaptionEditor : ProcessingUiEffect()
}

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val crashReporter: CrashReporter,
    private val deviceCapabilityUseCase: com.dipdev.aiautocaptioner.core.device.DeviceCapabilityUseCase,
    private val transcriptionManager: com.dipdev.aiautocaptioner.core.whisper.TranscriptionManager
) : BaseViewModel<ProcessingUiState, ProcessingUiEvent, ProcessingUiEffect>(ProcessingUiState()) {

    companion object {
        private const val TAG = "ProcessingVM"
    }

    private var pendingProjectId: String? = null

    init {
        val isRegenerating = savedStateHandle.get<Boolean>("isRegenerating") ?: false
        if (isRegenerating) {
            transcriptionManager.clearState()
        }

        viewModelScope.launch {
            modelRepository.getActiveModel().collect { model ->
                setState { copy(activeModel = model) }
            }
        }
        
        viewModelScope.launch {
            val models = modelRepository.getAvailableModels()
            setState { copy(availableModels = models) }
        }

        viewModelScope.launch {
            settingsRepository.lastLanguageFlow.collect { lang ->
                setState { copy(selectedLanguage = lang) }
            }
        }

        viewModelScope.launch {
            settingsRepository.lastTranslateFlow.collect { translate ->
                setState { copy(translateToEnglish = translate) }
            }
        }
        
        viewModelScope.launch {
            transcriptionManager.step.collect { step ->
                if (step !is ProcessingStep.Idle) {
                    setState { copy(step = step) }
                    if (step is ProcessingStep.Done) {
                        // No delay — navigate immediately; EditorScreen shows in-app toast
                        val project = pendingProjectId?.let { projectRepository.getProjectById(it) }
                        if (project?.creationMode == com.dipdev.aiautocaptioner.data.db.entity.CreationMode.QUICK_CAPTION) {
                            setEffect(ProcessingUiEffect.NavigateToCaptionEditor)
                        } else {
                            setEffect(ProcessingUiEffect.NavigateToVideoEditor)
                        }
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

        viewModelScope.launch {
            transcriptionManager.detectedLanguage.collect { lang ->
                if (lang != null) setState { copy(detectedLanguage = lang) }
            }
        }
    }

    override fun handleEvent(event: ProcessingUiEvent) {
        when (event) {
            is ProcessingUiEvent.PrepareForProject -> prepareForProject(event.projectId, event.forceModelPicker, event.isRegenerating)
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
            is ProcessingUiEvent.StartTranscriptionExplicit -> startTranscriptionExplicit(event.projectId, event.modelId, event.language, event.translateToEnglish)
            is ProcessingUiEvent.ResetToIdle -> resetToIdle()
        }
    }



    private fun prepareForProject(projectId: String, forceModelPicker: Boolean, isRegenerating: Boolean) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val isAlreadyDone = project?.status == ProjectStatus.TRANSCRIBED ||
                                project?.status == ProjectStatus.EXPORTED
            setState { copy(workingVideoPath = project?.workingVideoPath) }

            if (isAlreadyDone && !forceModelPicker && !isRegenerating) {
                // Project already transcribed — go straight to editor
                if (project.creationMode == com.dipdev.aiautocaptioner.data.db.entity.CreationMode.QUICK_CAPTION) {
                    setEffect(ProcessingUiEffect.NavigateToCaptionEditor)
                } else {
                    setEffect(ProcessingUiEffect.NavigateToVideoEditor)
                }
                return@launch
            }
            
            if (forceModelPicker) {
                showModelPicker()
                return@launch
            }

            val activeModel = modelRepository.getActiveModel().first()
            if (activeModel != null) {
                // Model ready — start immediately with saved language
                startProcessing(projectId)
            } else {
                // First run — show model setup sheet
                showModelSetup()
            }
        }
    }

    private fun selectLanguage(language: String) {
        val translate = if (language == "en") false else currentState.translateToEnglish
        setState { copy(selectedLanguage = language, translateToEnglish = translate) }
        viewModelScope.launch {
            settingsRepository.saveLastLanguageSettings(language, translate)
        }
    }

    private fun showModelSetup() {
        val language = currentState.selectedLanguage
        val allModels = modelRepository.getAvailableModels()

        val compatibleModels = allModels.filter { model ->
            val langMatch = when {
                language == "en" -> model.languages.contains("en")
                language == "auto" -> model.isMultilingual
                else -> model.isMultilingual || model.languages.contains(language)
            }
            langMatch && deviceCapabilityUseCase.isModelRamCompatible(model.minRamMb)
        }

        val recommendedId = deviceCapabilityUseCase.getRecommendedModel(language)
        val finalRec = if (compatibleModels.any { it.id == recommendedId }) recommendedId else compatibleModels.firstOrNull()?.id

        setState { copy(step = ProcessingStep.SetupAI(models = compatibleModels, recommendedModelId = finalRec)) }
    }

    private fun showModelPicker() {
        val language = currentState.selectedLanguage
        val allModels = modelRepository.getAvailableModels()

        val compatibleModels = allModels.filter { model ->
            val langMatch = when {
                language == "en" -> model.languages.contains("en")
                language == "auto" -> model.isMultilingual
                else -> model.isMultilingual || model.languages.contains(language)
            }
            langMatch && deviceCapabilityUseCase.isModelRamCompatible(model.minRamMb)
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
        pendingProjectId = projectId
        val activeModelId = currentState.activeModel?.id ?: return
        val isRegenerating = savedStateHandle.get<Boolean>("isRegenerating") ?: false
        transcriptionManager.startProcess(
            projectId = projectId,
            modelId = activeModelId,
            language = currentState.selectedLanguage,
            translateToEnglish = currentState.translateToEnglish,
            isRegenerating = isRegenerating
        )
    }

    private fun startTranscriptionExplicit(projectId: String, modelId: String, language: String, translateToEnglish: Boolean) {
        pendingProjectId = projectId
        val isRegenerating = savedStateHandle.get<Boolean>("isRegenerating") ?: false
        
        viewModelScope.launch {
            settingsRepository.saveLastLanguageSettings(language, translateToEnglish)
            modelRepository.setActiveModel(modelId)
        }

        transcriptionManager.startProcess(
            projectId = projectId,
            modelId = modelId,
            language = language,
            translateToEnglish = translateToEnglish,
            isRegenerating = isRegenerating
        )
    }

    private fun resetToIdle() {
        transcriptionManager.clearState()
        setState { copy(step = ProcessingStep.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        // Removed local cleanup; TranscriptionManager handles cancellation on its end if needed
    }
}

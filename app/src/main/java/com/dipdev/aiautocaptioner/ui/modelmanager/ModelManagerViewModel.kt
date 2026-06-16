package com.dipdev.aiautocaptioner.ui.modelmanager

import androidx.lifecycle.ViewModel
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState

data class ModelManagerUiState(
    val availableModels: List<WhisperModel> = emptyList(),
    val activeModel: WhisperModel? = null,
    val downloadStates: Map<String, DownloadState> = emptyMap()
) : UiState

sealed interface ModelManagerUiEvent : UiEvent {
    data object RefreshModels : ModelManagerUiEvent
    data class SetActiveModel(val model: WhisperModel) : ModelManagerUiEvent
    data class StartDownload(val modelId: String) : ModelManagerUiEvent
    data class DeleteModel(val modelId: String) : ModelManagerUiEvent
}

sealed interface ModelManagerUiEffect : UiEffect

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : BaseViewModel<ModelManagerUiState, ModelManagerUiEvent, ModelManagerUiEffect>(
    ModelManagerUiState(availableModels = modelRepository.getAvailableModels())
) {

    init {
        viewModelScope.launch {
            modelRepository.getActiveModel().collect { activeModel ->
                setState { copy(activeModel = activeModel) }
            }
        }
    }

    override fun handleEvent(event: ModelManagerUiEvent) {
        when (event) {
            is ModelManagerUiEvent.RefreshModels -> {
                setState { copy(availableModels = modelRepository.getAvailableModels()) }
            }
            is ModelManagerUiEvent.SetActiveModel -> {
                viewModelScope.launch {
                    modelRepository.setActiveModel(event.model.id)
                }
            }
            is ModelManagerUiEvent.StartDownload -> {
                viewModelScope.launch {
                    modelRepository.downloadModel(event.modelId).collect { state ->
                        setState {
                            val newMap = downloadStates.toMutableMap()
                            newMap[event.modelId] = state
                            copy(downloadStates = newMap)
                        }
                        if (state is DownloadState.Complete) {
                            handleEvent(ModelManagerUiEvent.RefreshModels)
                        }
                    }
                }
            }
            is ModelManagerUiEvent.DeleteModel -> {
                viewModelScope.launch {
                    modelRepository.deleteModel(event.modelId)
                    setState {
                        val newMap = downloadStates.toMutableMap()
                        newMap.remove(event.modelId)
                        copy(downloadStates = newMap)
                    }
                    handleEvent(ModelManagerUiEvent.RefreshModels)
                }
            }
        }
    }
}

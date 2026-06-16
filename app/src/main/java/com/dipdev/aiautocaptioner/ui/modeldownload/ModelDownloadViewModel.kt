package com.dipdev.aiautocaptioner.ui.modeldownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState

data class ModelDownloadUiState(
    val downloadState: DownloadState = DownloadState.Starting,
    val modelName: String = ""
) : UiState

sealed interface ModelDownloadUiEvent : UiEvent {
    data class StartDownload(val modelId: String) : ModelDownloadUiEvent
    data class Retry(val modelId: String) : ModelDownloadUiEvent
}

sealed interface ModelDownloadUiEffect : UiEffect

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : BaseViewModel<ModelDownloadUiState, ModelDownloadUiEvent, ModelDownloadUiEffect>(ModelDownloadUiState()) {

    override fun handleEvent(event: ModelDownloadUiEvent) {
        when (event) {
            is ModelDownloadUiEvent.StartDownload -> startDownload(event.modelId)
            is ModelDownloadUiEvent.Retry -> {
                setState { copy(downloadState = DownloadState.Starting) }
                startDownload(event.modelId)
            }
        }
    }

    private fun startDownload(modelId: String) {
        val model = modelRepository.getModelById(modelId) ?: return
        setState { copy(modelName = model.displayName) }

        // If already downloaded just emit Complete immediately
        if (model.isDownloaded) {
            setState { copy(downloadState = DownloadState.Complete(model.localPath!!)) }
            return
        }

        viewModelScope.launch {
            modelRepository.downloadModel(modelId).collect { state ->
                setState { copy(downloadState = state) }
            }
        }
    }
}
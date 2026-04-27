package com.dipdev.autocaptioner.ui.modeldownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.autocaptioner.data.repository.DownloadState
import com.dipdev.autocaptioner.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Starting)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _modelName = MutableStateFlow("")
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    fun startDownload(modelId: String) {
        val model = modelRepository.getModelById(modelId) ?: return
        _modelName.value = model.displayName

        // If already downloaded just emit Complete immediately
        if (model.isDownloaded) {
            _downloadState.value = DownloadState.Complete(model.localPath!!)
            return
        }

        viewModelScope.launch {
            modelRepository.downloadModel(modelId).collect { state ->
                _downloadState.value = state
            }
        }
    }

    fun retry(modelId: String) {
        _downloadState.value = DownloadState.Starting
        startDownload(modelId)
    }
}
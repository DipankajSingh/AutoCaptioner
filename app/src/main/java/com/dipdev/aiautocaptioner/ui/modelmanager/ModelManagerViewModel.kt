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

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    // Main listing of all statically defined models with mapped dynamic properties (isDownloaded)
    private val _availableModels = MutableStateFlow(modelRepository.getAvailableModels())
    val availableModels: StateFlow<List<WhisperModel>> = _availableModels.asStateFlow()

    // Emits the currently active selected model
    val activeModel: StateFlow<WhisperModel?> = modelRepository.getActiveModel()
        .stateInDefault(scope = viewModelScope, initialValue = null)

    // Tracks downloading instances for any models triggered off cards
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    fun refreshModels() {
        _availableModels.value = modelRepository.getAvailableModels()
    }

    fun setActiveModel(model: WhisperModel) {
        viewModelScope.launch {
            modelRepository.setActiveModel(model.id)
        }
    }

    fun startDownload(modelId: String) {
        viewModelScope.launch {
            modelRepository.downloadModel(modelId).collect { state ->
                // Update tracking map dynamically
                _downloadStates.update { currentMap ->
                    val newMap = currentMap.toMutableMap()
                    newMap[modelId] = state
                    newMap
                }
                
                // If it hits complete, trigger an ambient repository scan
                if (state is DownloadState.Complete) {
                    refreshModels()
                }
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.deleteModel(modelId)
            // Immediately run a sweep to drop mapping references
            _downloadStates.update { currentMap ->
                val newMap = currentMap.toMutableMap()
                newMap.remove(modelId)
                newMap
            }
            refreshModels()
        }
    }
}

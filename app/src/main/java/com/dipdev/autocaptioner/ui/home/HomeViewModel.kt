package com.dipdev.autocaptioner.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.autocaptioner.data.db.entity.ProjectEntity
import com.dipdev.autocaptioner.data.repository.ModelRepository
import com.dipdev.autocaptioner.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val modelRepository: ModelRepository
) : ViewModel() {

    // stateIn converts a Flow into a StateFlow
    // SharingStarted.WhileSubscribed(5000) = keep the flow active
    // for 5 seconds after last subscriber leaves
    // (handles screen rotation without restarting the query)
    val projects: StateFlow<List<ProjectEntity>> = projectRepository
        .getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeModel = modelRepository.getActiveModel()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun importVideo(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            val result = projectRepository.importVideo(uri)
            _importState.value = result.fold(
                onSuccess = { projectId -> ImportState.Success(projectId) },
                onFailure = { e -> ImportState.Error(e.message ?: "Import failed") }
            )
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            projectRepository.deleteProject(project.id)
        }
    }

    fun renameProject(projectId: String, newTitle: String) {
        viewModelScope.launch {
            projectRepository.renameProject(projectId, newTitle)
        }
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }
}

sealed class ImportState {
    data object Idle : ImportState()
    data object Loading : ImportState()
    data class Success(val projectId: String) : ImportState()
    data class Error(val message: String) : ImportState()
}
package com.dipdev.aiautocaptioner.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    modelRepository: ModelRepository
) : ViewModel() {

    val projects: StateFlow<List<com.dipdev.aiautocaptioner.data.db.entity.ProjectWithExportedFiles>> = projectRepository
        .getProjectsWithExportedFiles()
        .stateInDefault(scope = viewModelScope, initialValue = emptyList())

    val activeModel = modelRepository.getActiveModel()
        .stateInDefault(scope = viewModelScope, initialValue = null)

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _announcementMessage = MutableStateFlow("")
    val announcementMessage: StateFlow<String> = _announcementMessage.asStateFlow()

    init {
        fetchAnnouncement()
    }

    private fun fetchAnnouncement() {
        val config = Firebase.remoteConfig
        config.fetchAndActivate().addOnCompleteListener { 
            _announcementMessage.value = config.getString("home_announcement_message")
        }
    }

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

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.duplicateProject(projectId)
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
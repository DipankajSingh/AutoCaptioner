package com.dipdev.aiautocaptioner.ui.home

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectWithExportedFiles
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val importState: ImportState = ImportState.Idle,
    val announcementMessage: String = "",
    val projects: List<ProjectWithExportedFiles>? = null,
    val activeModel: com.dipdev.aiautocaptioner.data.model.WhisperModel? = null,
    val lastRecordingMode: String = "CAMERA"
) : UiState

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val settingsRepository: com.dipdev.aiautocaptioner.data.repository.SettingsRepository,
    modelRepository: ModelRepository
) : BaseViewModel<HomeState, UiEvent, UiEffect>(HomeState()) {



    override fun handleEvent(event: UiEvent) {}

    init {
        fetchAnnouncement()
        viewModelScope.launch {
            projectRepository.getProjectsWithExportedFiles().collect { p ->
                setState { copy(projects = p) }
            }
        }
        viewModelScope.launch {
            modelRepository.getActiveModel().collect { m ->
                setState { copy(activeModel = m) }
            }
        }
        viewModelScope.launch {
            settingsRepository.lastRecordingModeFlow.collect { mode ->
                setState { copy(lastRecordingMode = mode) }
            }
        }
    }

    private fun fetchAnnouncement() {
        viewModelScope.launch {
            try {
                val config = Firebase.remoteConfig
                config.fetchAndActivate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        setState { copy(announcementMessage = config.getString("home_announcement_message")) }
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun importVideo(uri: Uri) {
        viewModelScope.launch {
            setState { copy(importState = ImportState.Loading) }
            val result = projectRepository.importVideo(uri)
            setState { 
                copy(importState = result.fold(
                    { projectId -> ImportState.Success(projectId) },
                    { e -> ImportState.Error(e.message ?: "Import failed") }
                ))
            }
        }
    }

    fun importVideoQuick(uri: Uri) {
        viewModelScope.launch {
            setState { copy(importState = ImportState.Loading) }
            val result = projectRepository.importVideo(uri)
            setState { 
                copy(importState = result.fold(
                    { projectId -> ImportState.QuickSuccess(projectId) },
                    { e -> ImportState.Error(e.message ?: "Import failed") }
                ))
            }
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
        setState { copy(importState = ImportState.Idle) }
    }
}

sealed class ImportState {
    data object Idle : ImportState()
    data object Loading : ImportState()
    data class Success(val projectId: String) : ImportState()           // → VideoEditor
    data class QuickSuccess(val projectId: String) : ImportState()      // → Processing directly
    data class Error(val message: String) : ImportState()
}

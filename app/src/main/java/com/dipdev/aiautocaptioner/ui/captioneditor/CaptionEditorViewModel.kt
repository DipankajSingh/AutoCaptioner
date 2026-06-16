package com.dipdev.aiautocaptioner.ui.captioneditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState

data class CaptionEditorUiState(
    val project: ProjectEntity? = null,
    val segments: List<CaptionSegmentEntity> = emptyList(),
    val searchQuery: String = "",
    val filteredSegments: List<CaptionSegmentEntity> = emptyList(),
    val wordsMap: Map<String, List<CaptionWordEntity>> = emptyMap(),
    val expandedSegmentId: String? = null,
    val retranscribeRequested: Boolean = false
) : UiState

sealed interface CaptionEditorUiEvent : UiEvent {
    data class LoadProject(val projectId: String) : CaptionEditorUiEvent
    data class ToggleSegmentExpanded(val segmentId: String) : CaptionEditorUiEvent
    data class UpdateSearchQuery(val query: String) : CaptionEditorUiEvent
    data class SaveSegmentText(val segment: CaptionSegmentEntity, val newText: String) : CaptionEditorUiEvent
    data class ToggleWordEmphasis(val word: CaptionWordEntity) : CaptionEditorUiEvent
    data class Retranscribe(val projectId: String) : CaptionEditorUiEvent
    data object RetranscribeHandled : CaptionEditorUiEvent
    data class ShareSrt(val projectId: String) : CaptionEditorUiEvent
}

sealed interface CaptionEditorUiEffect : UiEffect {
    data class ShareSrt(val content: String) : CaptionEditorUiEffect
}

@HiltViewModel
class CaptionEditorViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository
) : BaseViewModel<CaptionEditorUiState, CaptionEditorUiEvent, CaptionEditorUiEffect>(CaptionEditorUiState()) {

    private var wordsJob: kotlinx.coroutines.Job? = null

    override fun handleEvent(event: CaptionEditorUiEvent) {
        when (event) {
            is CaptionEditorUiEvent.LoadProject -> loadProject(event.projectId)
            is CaptionEditorUiEvent.ToggleSegmentExpanded -> {
                val newExpandedId = if (uiState.value.expandedSegmentId == event.segmentId) null else event.segmentId
                setState {
                    copy(expandedSegmentId = newExpandedId)
                }
                wordsJob?.cancel()
                if (newExpandedId != null) {
                    wordsJob = viewModelScope.launch {
                        captionRepository.getWordsForSegment(newExpandedId).collect { words ->
                            setState {
                                val newMap = wordsMap.toMutableMap()
                                newMap[newExpandedId] = words
                                copy(wordsMap = newMap)
                            }
                        }
                    }
                }
            }
            is CaptionEditorUiEvent.UpdateSearchQuery -> {
                setState {
                    val filtered = if (event.query.isBlank()) {
                        segments
                    } else {
                        segments.filter { it.text.contains(event.query, ignoreCase = true) }
                    }
                    copy(searchQuery = event.query, filteredSegments = filtered)
                }
            }
            is CaptionEditorUiEvent.SaveSegmentText -> saveSegmentText(event.segment, event.newText)
            is CaptionEditorUiEvent.ToggleWordEmphasis -> toggleWordEmphasis(event.word)
            is CaptionEditorUiEvent.Retranscribe -> {
                viewModelScope.launch {
                    projectRepository.updateStatus(event.projectId, ProjectStatus.IMPORTED)
                    setState { copy(retranscribeRequested = true) }
                }
            }
            is CaptionEditorUiEvent.RetranscribeHandled -> {
                setState { copy(retranscribeRequested = false) }
            }
            is CaptionEditorUiEvent.ShareSrt -> {
                viewModelScope.launch {
                    val content = captionRepository.buildSrtContent(event.projectId)
                    setEffect(CaptionEditorUiEffect.ShareSrt(content))
                }
            }
        }
    }

    private fun loadProject(projectId: String) {
        viewModelScope.launch {
            val proj = projectRepository.getProjectById(projectId)
            setState { copy(project = proj) }
            projectRepository.updateVisitedCaptionEditor(projectId, true)

            launch {
                captionRepository.getSegmentsForProject(projectId).collect { segs ->
                    setState {
                        val filtered = if (searchQuery.isBlank()) {
                            segs
                        } else {
                            segs.filter { it.text.contains(searchQuery, ignoreCase = true) }
                        }
                        copy(segments = segs, filteredSegments = filtered)
                    }
                }
            }

            launch {
                val words = captionRepository.getAllWordsForProject(projectId)
                setState { copy(wordsMap = words.groupBy { it.segmentId }) }
            }
        }
    }

    private fun saveSegmentText(segment: CaptionSegmentEntity, newText: String) {
        val trimmed = newText.trim()
        if (trimmed == segment.text) return  // nothing changed, skip DB round-trip

        viewModelScope.launch {
            captionRepository.updateSegment(segment.copy(text = trimmed))

            val oldWordsList = uiState.value.wordsMap[segment.id] ?: emptyList()
            val newEntities = CaptionAlignmentUtils.alignWords(
                oldWords = oldWordsList,
                newText = trimmed,
                segmentId = segment.id,
                projectId = segment.projectId,
                segmentStartTimeMs = segment.startTimeMs,
                segmentEndTimeMs = segment.endTimeMs
            )
            captionRepository.replaceWordsForSegment(segment.id, newEntities)
        }
    }

    private fun toggleWordEmphasis(word: CaptionWordEntity) {
        viewModelScope.launch {
            captionRepository.toggleEmphasis(
                wordId = word.id,
                isEmphasized = !word.isEmphasized,
                emphasisType = EmphasisType.BOUNCE
            )
        }
    }
}
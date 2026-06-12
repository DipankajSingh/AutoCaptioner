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

@HiltViewModel
class CaptionEditorViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository
) : ViewModel() {

    private val _project = MutableStateFlow<ProjectEntity?>(null)
    val project: StateFlow<ProjectEntity?> = _project.asStateFlow()

    private val _segments = MutableStateFlow<List<CaptionSegmentEntity>>(emptyList())
    val segments: StateFlow<List<CaptionSegmentEntity>> = _segments.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredSegments: StateFlow<List<CaptionSegmentEntity>> = combine(_segments, _searchQuery) { segs, query ->
        if (query.isBlank()) {
            segs
        } else {
            segs.filter { it.text.contains(query, ignoreCase = true) }
        }
    }.stateInDefault(viewModelScope, emptyList())

    private val _wordsMap = MutableStateFlow<Map<String, List<CaptionWordEntity>>>(emptyMap())
    val wordsMap: StateFlow<Map<String, List<CaptionWordEntity>>> = _wordsMap.asStateFlow()

    private val _expandedSegmentId = MutableStateFlow<String?>(null)
    val expandedSegmentId: StateFlow<String?> = _expandedSegmentId.asStateFlow()

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _project.value = projectRepository.getProjectById(projectId)
            projectRepository.updateVisitedCaptionEditor(projectId, true)

            launch {
                captionRepository.getSegmentsForProject(projectId).collect { segs ->
                    _segments.value = segs
                }
            }

            launch {
                captionRepository.getAllWordsForProjectFlow(projectId).collect { words ->
                    _wordsMap.value = words.groupBy { it.segmentId }
                }
            }
        }
    }

    fun toggleSegmentExpanded(segmentId: String) {
        _expandedSegmentId.value = if (_expandedSegmentId.value == segmentId) null else segmentId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Called when the user finishes editing a segment (focus lost / done).
     * NOT called on every keystroke — the text field handles its own local state.
     */
    fun saveSegmentText(segment: CaptionSegmentEntity, newText: String) {
        val trimmed = newText.trim()
        if (trimmed == segment.text) return  // nothing changed, skip DB round-trip

        viewModelScope.launch {
            captionRepository.updateSegment(segment.copy(text = trimmed))

            val newWordsList = if (trimmed.isEmpty()) listOf(" ") else trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
            val oldWordsList = _wordsMap.value[segment.id] ?: emptyList()

            if (newWordsList.size == oldWordsList.size) {
                // Same token count → just update strings, preserving timing/emphasis
                val updatedEntities = oldWordsList.zip(newWordsList) { entity, newWord ->
                    entity.copy(word = newWord)
                }
                captionRepository.updateWords(updatedEntities)
            } else {
                // Word count changed → redistribute time linearly
                val duration = segment.endTimeMs - segment.startTimeMs
                val timePerWord = if (newWordsList.isNotEmpty()) duration / newWordsList.size else 0L

                val newEntities = newWordsList.mapIndexed { index, word ->
                    val matchingOldWord = oldWordsList.find { it.word == word }
                    CaptionWordEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        projectId = segment.projectId,
                        segmentId = segment.id,
                        word = word,
                        index = index,
                        startTimeMs = segment.startTimeMs + (timePerWord * index),
                        endTimeMs = segment.startTimeMs + (timePerWord * (index + 1)),
                        confidence = 1.0f,
                        isEmphasized = matchingOldWord?.isEmphasized ?: false,
                        emphasisType = matchingOldWord?.emphasisType ?: EmphasisType.NONE
                    )
                }
                captionRepository.replaceWordsForSegment(segment.id, newEntities)
            }
        }
    }

    fun toggleWordEmphasis(word: CaptionWordEntity) {
        viewModelScope.launch {
            captionRepository.toggleEmphasis(
                wordId = word.id,
                isEmphasized = !word.isEmphasized,
                emphasisType = EmphasisType.BOUNCE
            )
        }
    }

    // ── Retranscribe ──────────────────────────────────────────────────────
    private val _retranscribeRequested = MutableStateFlow(false)
    val retranscribeRequested: StateFlow<Boolean> = _retranscribeRequested.asStateFlow()

    private val _srtContentToShare = MutableSharedFlow<String>()
    val srtContentToShare: SharedFlow<String> = _srtContentToShare.asSharedFlow()

    fun retranscribe(projectId: String) {
        viewModelScope.launch {
            projectRepository.updateStatus(projectId, ProjectStatus.IMPORTED)
            _retranscribeRequested.value = true
        }
    }

    fun retranscribeHandled() {
        _retranscribeRequested.value = false
    }

    fun shareSrt(projectId: String) {
        viewModelScope.launch {
            val content = captionRepository.buildSrtContent(projectId)
            _srtContentToShare.emit(content)
        }
    }

}
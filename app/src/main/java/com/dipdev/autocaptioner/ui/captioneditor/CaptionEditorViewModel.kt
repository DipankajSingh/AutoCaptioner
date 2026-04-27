package com.dipdev.autocaptioner.ui.captioneditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.autocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.autocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.autocaptioner.data.db.entity.EmphasisType
import com.dipdev.autocaptioner.data.db.entity.ProjectEntity
import com.dipdev.autocaptioner.data.repository.CaptionRepository
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
class CaptionEditorViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository
) : ViewModel() {

    private val _project = MutableStateFlow<ProjectEntity?>(null)
    val project: StateFlow<ProjectEntity?> = _project.asStateFlow()

    private val _segments = MutableStateFlow<List<CaptionSegmentEntity>>(emptyList())
    val segments: StateFlow<List<CaptionSegmentEntity>> = _segments.asStateFlow()

    private val _wordsMap = MutableStateFlow<Map<String, List<CaptionWordEntity>>>(emptyMap())
    val wordsMap: StateFlow<Map<String, List<CaptionWordEntity>>> = _wordsMap.asStateFlow()

    // Which segment is currently expanded in the editor
    private val _expandedSegmentId = MutableStateFlow<String?>(null)
    val expandedSegmentId: StateFlow<String?> = _expandedSegmentId.asStateFlow()

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _project.value = projectRepository.getProjectById(projectId)
            projectRepository.updateVisitedCaptionEditor(projectId, true)

            // Collect segments as a flow so edits update the UI automatically
            captionRepository.getSegmentsForProject(projectId).collect { segs ->
                _segments.value = segs

                // Load words for each segment correctly without blocking
                segs.forEach { seg ->
                    launch {
                        captionRepository.getWordsForSegment(seg.id).collect { words ->
                            _wordsMap.value = _wordsMap.value.toMutableMap().apply { put(seg.id, words) }
                        }
                    }
                }
            }
        }
    }

    fun toggleSegmentExpanded(segmentId: String) {
        _expandedSegmentId.value = if (_expandedSegmentId.value == segmentId) null else segmentId
    }

    fun updateSegmentText(segment: CaptionSegmentEntity, newText: String) {
        viewModelScope.launch {
            captionRepository.updateSegment(segment.copy(text = newText))
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
}
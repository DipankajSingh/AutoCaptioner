package com.dipdev.aiautocaptioner.ui.captioneditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /**
     * Called when the user finishes editing a segment (focus lost / done).
     * NOT called on every keystroke — the text field handles its own local state.
     */
    fun saveSegmentText(segment: CaptionSegmentEntity, newText: String) {
        val trimmed = newText.trim()
        if (trimmed == segment.text) return  // nothing changed, skip DB round-trip

        viewModelScope.launch {
            captionRepository.updateSegment(segment.copy(text = trimmed))

            val newWordsList = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
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

    fun retranscribe(projectId: String) {
        viewModelScope.launch {
            projectRepository.updateStatus(projectId, ProjectStatus.IMPORTED)
            _retranscribeRequested.value = true
        }
    }

    fun retranscribeHandled() {
        _retranscribeRequested.value = false
    }

    fun shareSrt(projectId: String, context: Context) {
        viewModelScope.launch {
            val segmentsList = captionRepository.getSegmentsOnce(projectId)
            val sb = java.lang.StringBuilder()
            segmentsList.forEachIndexed { index, segment ->
                sb.append(index + 1).append("\n")
                sb.append(formatSrtTime(segment.startTimeMs)).append(" --> ").append(formatSrtTime(segment.endTimeMs)).append("\n")
                sb.append(segment.text).append("\n\n")
            }
            val srtFile = File(context.cacheDir, "captions_$projectId.srt")
            srtFile.writeText(sb.toString())

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", srtFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-subrip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share SRT"))
        }
    }

    private fun formatSrtTime(timeMs: Long): String {
        val hours = timeMs / 3600000
        val minutes = (timeMs % 3600000) / 60000
        val seconds = (timeMs % 60000) / 1000
        val millis = timeMs % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
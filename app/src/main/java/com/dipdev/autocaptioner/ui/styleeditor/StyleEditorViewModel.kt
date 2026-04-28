package com.dipdev.autocaptioner.ui.styleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.autocaptioner.data.db.entity.AnimationType
import com.dipdev.autocaptioner.data.db.entity.BackgroundType
import com.dipdev.autocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.autocaptioner.data.db.entity.DisplayMode
import com.dipdev.autocaptioner.data.db.entity.KaraokeHighlightMode
import com.dipdev.autocaptioner.data.db.entity.ProjectEntity
import com.dipdev.autocaptioner.data.db.entity.TextAlignment
import com.dipdev.autocaptioner.data.repository.CaptionRepository
import com.dipdev.autocaptioner.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class StyleEditorViewModel @Inject constructor(
    private val captionRepository: CaptionRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _styles = MutableStateFlow<List<CaptionStyleEntity>>(emptyList())
    val styles: StateFlow<List<CaptionStyleEntity>> = _styles.asStateFlow()

    // The style currently being edited — starts as a copy of selected preset
    private val _activeStyle = MutableStateFlow<CaptionStyleEntity?>(null)
    val activeStyle: StateFlow<CaptionStyleEntity?> = _activeStyle.asStateFlow()

    private val _project = MutableStateFlow<ProjectEntity?>(null)
    val project: StateFlow<ProjectEntity?> = _project.asStateFlow()

    private val _segments = MutableStateFlow<List<com.dipdev.autocaptioner.data.db.entity.CaptionSegmentEntity>>(emptyList())
    val segments: StateFlow<List<com.dipdev.autocaptioner.data.db.entity.CaptionSegmentEntity>> = _segments.asStateFlow()

    private val _wordsMap = MutableStateFlow<Map<String, List<com.dipdev.autocaptioner.data.db.entity.CaptionWordEntity>>>(emptyMap())
    val wordsMap: StateFlow<Map<String, List<com.dipdev.autocaptioner.data.db.entity.CaptionWordEntity>>> = _wordsMap.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _selectedTab = MutableStateFlow(StyleTab.TEXT)
    val selectedTab: StateFlow<StyleTab> = _selectedTab.asStateFlow()

    fun loadStyles(projectId: String) {
        viewModelScope.launch {
            val projectEntity = projectRepository.getProjectById(projectId)
            _project.value = projectEntity
            
            launch {
                captionRepository.getAllStyles().collect { list ->
                    _styles.value = list
                    if (_activeStyle.value == null) {
                        val activeId = projectEntity?.activeStyleId
                        _activeStyle.value = if (activeId != null) {
                            list.find { it.id == activeId } ?: list.firstOrNull()
                        } else {
                            list.firstOrNull()
                        }
                    }
                }
            }

            launch {
                captionRepository.getSegmentsForProject(projectId).collect { segs ->
                    _segments.value = segs
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
    }

    fun updatePlaybackPosition(ms: Long) {
        _currentPositionMs.value = ms
    }

    fun selectPreset(style: CaptionStyleEntity) {
        // Make a working copy so edits don't overwrite the preset directly
        _activeStyle.value = style.copy(
            id = UUID.randomUUID().toString(),
            isDefault = false,
            name = "Custom"
        )
    }

    fun selectTab(tab: StyleTab) {
        _selectedTab.value = tab
    }

    fun updateFontSize(size: Float) {
        _activeStyle.value = _activeStyle.value?.copy(fontSize = size)
    }

    fun updateFontWeight(weight: Int) {
        _activeStyle.value = _activeStyle.value?.copy(fontWeight = weight)
    }

    fun updateTextColor(color: Long) {
        _activeStyle.value = _activeStyle.value?.copy(textColor = color)
    }

    fun updateHighlightColor(color: Long) {
        _activeStyle.value = _activeStyle.value?.copy(highlightColor = color)
    }

    fun updateOutlineWidth(width: Float) {
        _activeStyle.value = _activeStyle.value?.copy(outlineWidth = width)
    }

    fun updateOutlineColor(color: Long) {
        _activeStyle.value = _activeStyle.value?.copy(outlineColor = color)
    }

    fun updateBackgroundType(type: BackgroundType) {
        _activeStyle.value = _activeStyle.value?.copy(backgroundType = type)
    }

    fun updateBackgroundColor(color: Long) {
        _activeStyle.value = _activeStyle.value?.copy(backgroundColor = color)
    }

    fun updateBackgroundOpacity(opacity: Float) {
        _activeStyle.value = _activeStyle.value?.copy(backgroundOpacity = opacity)
    }

    fun updateDisplayMode(mode: DisplayMode) {
        _activeStyle.value = _activeStyle.value?.copy(displayMode = mode)
    }

    fun updateWordEnterAnimation(anim: AnimationType) {
        _activeStyle.value = _activeStyle.value?.copy(wordEnterAnimation = anim)
    }

    fun updateWordExitAnimation(anim: AnimationType) {
        _activeStyle.value = _activeStyle.value?.copy(wordExitAnimation = anim)
    }

    fun updateKaraokeHighlightMode(mode: KaraokeHighlightMode) {
        _activeStyle.value = _activeStyle.value?.copy(karaokeHighlightMode = mode)
    }

    fun updatePositionY(y: Float) {
        _activeStyle.value = _activeStyle.value?.copy(positionY = y)
    }

    fun updateAlignment(alignment: TextAlignment) {
        _activeStyle.value = _activeStyle.value?.copy(alignment = alignment)
    }

    fun updateMaxWordsPerLine(count: Int) {
        _activeStyle.value = _activeStyle.value?.copy(maxWordsPerLine = count)
    }

    fun updateMaxLines(count: Int) {
        _activeStyle.value = _activeStyle.value?.copy(maxLines = count)
    }

    fun updateRemovePunctuation(remove: Boolean) {
        _activeStyle.value = _activeStyle.value?.copy(removePunctuation = remove)
    }

    fun saveAndApply(projectId: String) {
        viewModelScope.launch {
            val style = _activeStyle.value ?: return@launch
            val styleToSave = if (style.name == "Custom") {
                style.copy(name = "Custom ${System.currentTimeMillis() % 1000}")
            } else style
            captionRepository.saveStyle(styleToSave)
            // Link style to project
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            projectRepository.updateProject(
                project.copy(
                    activeStyleId = styleToSave.id,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}

enum class StyleTab(val label: String) {
    TEXT("Text"),
    COLOR("Color"),
    ANIMATION("Animation"),
    PRESETS("Presets")
}
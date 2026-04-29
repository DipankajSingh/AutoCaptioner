package com.dipdev.autocaptioner.ui.styleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class StyleEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val captionRepository: CaptionRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    // ---- ExoPlayer lives in the ViewModel so it survives navigation ----
    // Initialised lazily once we know the video path from the project
    var exoPlayer: ExoPlayer? = null
        private set

    // Track one word-collector job per segment to avoid unbounded coroutine growth
    private val wordJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    private val _videoDurationMs = MutableStateFlow(0L)
    val videoDurationMs: StateFlow<Long> = _videoDurationMs.asStateFlow()

    fun initPlayer(videoPath: String) {
        if (exoPlayer != null) return           // already alive — don't recreate
        val player = ExoPlayer.Builder(appContext).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.parse(videoPath)))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && _videoDurationMs.value == 0L) {
                        _videoDurationMs.value = duration.coerceAtLeast(0L)
                    }
                }
            })
        }
        exoPlayer = player
    }

    fun seekTo(ms: Long) {
        exoPlayer?.seekTo(ms)
        _currentPositionMs.value = ms
    }

    override fun onCleared() {
        super.onCleared()
        wordJobs.values.forEach { it.cancel() }
        wordJobs.clear()
        exoPlayer?.release()
        exoPlayer = null
    }

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

                    val newIds = segs.map { it.id }.toSet()
                    // Cancel watchers for segments no longer present
                    val removed = wordJobs.keys - newIds
                    removed.forEach { id -> wordJobs.remove(id)?.cancel() }

                    // Start watchers only for new segments
                    segs.forEach { seg ->
                        if (!wordJobs.containsKey(seg.id)) {
                            wordJobs[seg.id] = launch {
                                captionRepository.getWordsForSegment(seg.id).collect { words ->
                                    val current = _wordsMap.value
                                    if (current[seg.id] != words) {
                                        _wordsMap.value = current.toMutableMap().apply { put(seg.id, words) }
                                    }
                                }
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
        // Reuse the existing Custom style ID if we already have one,
        // so we don't accumulate orphaned Custom rows in the DB.
        val existingCustomId = _activeStyle.value
            ?.takeIf { !it.isDefault }?.id
            ?: UUID.randomUUID().toString()
        _activeStyle.value = style.copy(
            id = existingCustomId,
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
        var style = _activeStyle.value?.copy(displayMode = mode) ?: return
        if (mode == DisplayMode.KARAOKE_FILL || mode == DisplayMode.PHRASE) {
            style = style.copy(
                wordEnterAnimation = AnimationType.NONE,
                wordExitAnimation = AnimationType.NONE
            )
        }
        if (mode == DisplayMode.TYPEWRITER) {
            style = style.copy(wordEnterAnimation = AnimationType.TYPEWRITER)
        }
        _activeStyle.value = style
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

    fun updateBackgroundPaddingH(v: Float) { _activeStyle.value = _activeStyle.value?.copy(backgroundPaddingH = v) }
    fun updateBackgroundPaddingV(v: Float) { _activeStyle.value = _activeStyle.value?.copy(backgroundPaddingV = v) }
    fun updateBackgroundCornerRadius(v: Float) { _activeStyle.value = _activeStyle.value?.copy(backgroundCornerRadius = v) }
    fun updateShadowRadius(v: Float)  { _activeStyle.value = _activeStyle.value?.copy(shadowRadius = v) }
    fun updateShadowColor(v: Long)    { _activeStyle.value = _activeStyle.value?.copy(shadowColor = v) }
    fun updateAnimationDurationMs(v: Int) { _activeStyle.value = _activeStyle.value?.copy(animationDurationMs = v) }
    fun updateLetterSpacing(v: Float) { _activeStyle.value = _activeStyle.value?.copy(letterSpacing = v) }
    fun updateIsItalic(v: Boolean)    { _activeStyle.value = _activeStyle.value?.copy(isItalic = v) }

    fun saveAndApply(projectId: String) {
        viewModelScope.launch {
            val style = _activeStyle.value ?: return@launch
            val styleToSave = if (style.name == "Custom" || style.isDefault) {
                style.copy(
                    id = if (style.isDefault) UUID.randomUUID().toString() else style.id,
                    name = "Custom ${System.currentTimeMillis() % 1000}",
                    isDefault = false
                )
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

    fun saveAsNewPreset(presetName: String) {
        viewModelScope.launch {
            val style = _activeStyle.value ?: return@launch
            val newPreset = style.copy(
                id = UUID.randomUUID().toString(),
                name = presetName,
                isDefault = false
            )
            captionRepository.saveStyle(newPreset)
            _activeStyle.value = newPreset
        }
    }

    fun deletePreset(style: CaptionStyleEntity) {
        viewModelScope.launch {
            if (!style.isDefault) {
                captionRepository.deleteStyle(style)
                // If we deleted the active style, switch to the first available
                if (_activeStyle.value?.id == style.id) {
                    _activeStyle.value = _styles.value.firstOrNull()
                }
            }
        }
    }
}

enum class StyleTab(val label: String) {
    TEXT("Text"),
    COLOR("Color"),
    ANIMATION("Animation"),
    PRESETS("Presets")
}

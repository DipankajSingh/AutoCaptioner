package com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor

import android.annotation.SuppressLint
import android.app.Activity
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dipdev.aiautocaptioner.data.db.entity.AnimationType
import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextAlignment
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import androidx.core.net.toUri
import com.revenuecat.purchases.restorePurchasesWith

import com.dipdev.aiautocaptioner.data.billing.PremiumManager
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import com.revenuecat.purchases.Purchases

data class StyleEditorUiState(
    val isPremium: Boolean = false,
    val videoDurationMs: Long = 0L,
    val styles: List<CaptionStyleEntity> = emptyList(),
    val activeStyle: CaptionStyleEntity? = null,
    val project: ProjectEntity? = null,
    val segments: List<CaptionSegmentEntity> = emptyList(),
    val wordsMap: Map<String, List<CaptionWordEntity>> = emptyMap(),
    val selectedTab: StyleTab = StyleTab.PRESETS,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isPurchaseLoading: Boolean = false
) : UiState

sealed interface StyleEditorUiEvent : UiEvent {
    data object UnlockPremiumMock : StyleEditorUiEvent
    data class InitPlayer(val videoPath: String) : StyleEditorUiEvent
    data class SeekTo(val ms: Long) : StyleEditorUiEvent
    data class LoadStyles(val projectId: String) : StyleEditorUiEvent
    data class SelectPreset(val style: CaptionStyleEntity) : StyleEditorUiEvent
    data class SelectTab(val tab: StyleTab) : StyleEditorUiEvent
    data class UpdateFontSize(val size: Float) : StyleEditorUiEvent
    data class UpdateFontWeight(val weight: Int) : StyleEditorUiEvent
    data class UpdateTextColor(val color: Long) : StyleEditorUiEvent
    data class UpdateHighlightColor(val color: Long) : StyleEditorUiEvent
    data class UpdateOutlineWidth(val width: Float) : StyleEditorUiEvent
    data class UpdateOutlineColor(val color: Long) : StyleEditorUiEvent
    data class UpdateBackgroundType(val type: BackgroundType) : StyleEditorUiEvent
    data class UpdateBackgroundColor(val color: Long) : StyleEditorUiEvent
    data class UpdateBackgroundOpacity(val opacity: Float) : StyleEditorUiEvent
    data class UpdateDisplayMode(val mode: DisplayMode) : StyleEditorUiEvent
    data class UpdateWordEnterAnimation(val anim: AnimationType) : StyleEditorUiEvent
    data class UpdateKaraokeHighlightMode(val mode: KaraokeHighlightMode) : StyleEditorUiEvent
    data class UpdatePositionY(val y: Float) : StyleEditorUiEvent
    data class UpdateAlignment(val alignment: TextAlignment) : StyleEditorUiEvent
    data class UpdateMaxWordsPerLine(val count: Int) : StyleEditorUiEvent
    data class UpdateMaxLines(val count: Int) : StyleEditorUiEvent
    data class UpdateRemovePunctuation(val remove: Boolean) : StyleEditorUiEvent
    data class UpdateBackgroundPaddingH(val v: Float) : StyleEditorUiEvent
    data class UpdateBackgroundPaddingV(val v: Float) : StyleEditorUiEvent
    data class UpdateBackgroundCornerRadius(val v: Float) : StyleEditorUiEvent
    data class UpdateAnimationDurationMs(val v: Int) : StyleEditorUiEvent
    data class UpdateLetterSpacing(val v: Float) : StyleEditorUiEvent
    data class UpdateIsItalic(val v: Boolean) : StyleEditorUiEvent
    data class SaveAndApply(val projectId: String) : StyleEditorUiEvent
    data class SaveAsNewPreset(val presetName: String) : StyleEditorUiEvent
    data class DeletePreset(val style: CaptionStyleEntity) : StyleEditorUiEvent
    data object Undo : StyleEditorUiEvent
    data object Redo : StyleEditorUiEvent
    data class PurchaseLifetime(val activity: Activity) : StyleEditorUiEvent
    data object RestorePurchases : StyleEditorUiEvent
}

sealed interface StyleEditorUiEffect : UiEffect

@HiltViewModel
class StyleEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val captionRepository: CaptionRepository,
    private val projectRepository: ProjectRepository,
    private val premiumManager: PremiumManager
) : BaseViewModel<StyleEditorUiState, StyleEditorUiEvent, StyleEditorUiEffect>(StyleEditorUiState()) {

    init {
        viewModelScope.launch {
            premiumManager.isPremiumFlow.collect { p -> setState { copy(isPremium = p) } }
        }
    }

    override fun handleEvent(event: StyleEditorUiEvent) {
        when (event) {
            is StyleEditorUiEvent.UnlockPremiumMock -> unlockPremiumMock()
            is StyleEditorUiEvent.InitPlayer -> initPlayer(event.videoPath)
            is StyleEditorUiEvent.SeekTo -> seekTo(event.ms)
            is StyleEditorUiEvent.LoadStyles -> loadStyles(event.projectId)
            is StyleEditorUiEvent.SelectPreset -> selectPreset(event.style)
            is StyleEditorUiEvent.SelectTab -> selectTab(event.tab)
            is StyleEditorUiEvent.UpdateFontSize -> updateFontSize(event.size)
            is StyleEditorUiEvent.UpdateFontWeight -> updateFontWeight(event.weight)
            is StyleEditorUiEvent.UpdateTextColor -> updateTextColor(event.color)
            is StyleEditorUiEvent.UpdateHighlightColor -> updateHighlightColor(event.color)
            is StyleEditorUiEvent.UpdateOutlineWidth -> updateOutlineWidth(event.width)
            is StyleEditorUiEvent.UpdateOutlineColor -> updateOutlineColor(event.color)
            is StyleEditorUiEvent.UpdateBackgroundType -> updateBackgroundType(event.type)
            is StyleEditorUiEvent.UpdateBackgroundColor -> updateBackgroundColor(event.color)
            is StyleEditorUiEvent.UpdateBackgroundOpacity -> updateBackgroundOpacity(event.opacity)
            is StyleEditorUiEvent.UpdateDisplayMode -> updateDisplayMode(event.mode)
            is StyleEditorUiEvent.UpdateWordEnterAnimation -> updateWordEnterAnimation(event.anim)
            is StyleEditorUiEvent.UpdateKaraokeHighlightMode -> updateKaraokeHighlightMode(event.mode)
            is StyleEditorUiEvent.UpdatePositionY -> updatePositionY(event.y)
            is StyleEditorUiEvent.UpdateAlignment -> updateAlignment(event.alignment)
            is StyleEditorUiEvent.UpdateMaxWordsPerLine -> updateMaxWordsPerLine(event.count)
            is StyleEditorUiEvent.UpdateMaxLines -> updateMaxLines(event.count)
            is StyleEditorUiEvent.UpdateRemovePunctuation -> updateRemovePunctuation(event.remove)
            is StyleEditorUiEvent.UpdateBackgroundPaddingH -> updateBackgroundPaddingH(event.v)
            is StyleEditorUiEvent.UpdateBackgroundPaddingV -> updateBackgroundPaddingV(event.v)
            is StyleEditorUiEvent.UpdateBackgroundCornerRadius -> updateBackgroundCornerRadius(event.v)
            is StyleEditorUiEvent.UpdateAnimationDurationMs -> updateAnimationDurationMs(event.v)
            is StyleEditorUiEvent.UpdateLetterSpacing -> updateLetterSpacing(event.v)
            is StyleEditorUiEvent.UpdateIsItalic -> updateIsItalic(event.v)
            is StyleEditorUiEvent.SaveAndApply -> saveAndApply(event.projectId)
            is StyleEditorUiEvent.SaveAsNewPreset -> saveAsNewPreset(event.presetName)
            is StyleEditorUiEvent.DeletePreset -> deletePreset(event.style)
            is StyleEditorUiEvent.Undo -> undo()
            is StyleEditorUiEvent.Redo -> redo()
            is StyleEditorUiEvent.PurchaseLifetime -> purchaseLifetime()
            is StyleEditorUiEvent.RestorePurchases -> restorePurchases()
        }
    }

    private fun unlockPremiumMock() {
        // No-op, mock is removed. Purchase is handled by RevenueCat Paywall directly in UI.
    }

    private fun purchaseLifetime() {
        viewModelScope.launch {
            setState { copy(isPurchaseLoading = true) }
            setState { copy(isPurchaseLoading = false) }
        }
    }

    private fun restorePurchases() {
        viewModelScope.launch {
            setState { copy(isPurchaseLoading = true) }
            try {
                Purchases.sharedInstance.restorePurchasesWith(
                    onSuccess = { _ ->
                        setState { copy(isPurchaseLoading = false) }
                    },
                    onError = { _ ->
                        setState { copy(isPurchaseLoading = false) }
                    }
                )
            } catch (_: Exception) {
                setState { copy(isPurchaseLoading = false) }
            }
        }
    }

    // ---- ExoPlayer lives in the ViewModel so it survives navigation ----
    // Initialised lazily once we know the video path from the project
    var exoPlayer: ExoPlayer? = null
        private set

    // Track one word-collector job per segment to avoid unbounded coroutine growth
    // (Removed in favor of a single project-level word flow)
    private fun initPlayer(videoPath: String) {
        if (exoPlayer != null) return           // already alive — don't recreate
        val player = ExoPlayer.Builder(appContext).build().apply {
            setMediaItem(MediaItem.fromUri(videoPath.toUri()))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && uiState.value.videoDurationMs == 0L) {
                        setState { copy(videoDurationMs = duration.coerceAtLeast(0L)) }
                    }
                }
            })
        }
        exoPlayer = player
    }

    private fun seekTo(ms: Long) {
        exoPlayer?.seekTo(ms)
    }

    @SuppressLint("EmptySuperCall")
    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        exoPlayer = null
    }

    private val undoStack = mutableListOf<CaptionStyleEntity>()
    private val redoStack = mutableListOf<CaptionStyleEntity>()

    private var lastPushTime = 0L
    private var lastPushProperty = ""

    private fun pushState(propertyName: String) {
        val current = uiState.value.activeStyle ?: return
        val now = System.currentTimeMillis()
        
        if (propertyName == lastPushProperty && now - lastPushTime < 500) {
            lastPushTime = now
            return
        }

        lastPushProperty = propertyName
        lastPushTime = now
        
        undoStack.add(current)
        if (undoStack.size > 30) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        setState { copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    private fun undo() {
        if (undoStack.isEmpty()) return
        val current = uiState.value.activeStyle ?: return
        redoStack.add(current)
        setState { copy(activeStyle = undoStack.removeAt(undoStack.size - 1)) }
        updateUndoRedoState()
        lastPushProperty = ""
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        val current = uiState.value.activeStyle ?: return
        undoStack.add(current)
        setState { copy(activeStyle = redoStack.removeAt(redoStack.size - 1)) }
        updateUndoRedoState()
        lastPushProperty = ""
    }


    private fun loadStyles(projectId: String) {
        viewModelScope.launch {
            val projectEntity = projectRepository.getProjectById(projectId)
            setState { copy(project = projectEntity) }

            launch {
                captionRepository.getAllStyles().collect { list ->
                    setState { copy(styles = list) }
                    if (uiState.value.activeStyle == null) {
                        val activeId = projectEntity?.activeStyleId
                        setState {
                            copy(
                                activeStyle = if (activeId != null) {
                                    list.find { it.id == activeId } ?: list.firstOrNull()
                                } else {
                                    list.firstOrNull()
                                }
                            )
                        }
                    }
                }
            }

            launch {
                captionRepository.getSegmentsForProject(projectId).collect { segs ->
                    setState { copy(segments = segs) }
                }
            }

            launch {
                val words = captionRepository.getAllWordsForProject(projectId)
                val grouped = words.groupBy { it.segmentId }
                setState { copy(wordsMap = grouped) }
            }
        }
    }

        // The preview component handles playback position locally now

    private fun selectPreset(style: CaptionStyleEntity) {
        pushState("preset")
        // Reuse the existing Custom style ID if we already have one,
        // so we don't accumulate orphaned Custom rows in the DB.
        val existingCustomId = uiState.value.activeStyle
            ?.takeIf { !it.isDefault }?.id
            ?: UUID.randomUUID().toString()
        setState {
            copy(
                activeStyle = style.copy(
                    id = existingCustomId,
                    isDefault = false,
                    name = "Custom"
                )
            )
        }
    }

    private fun selectTab(tab: StyleTab) {
        setState { copy(selectedTab = tab) }
    }

    private fun updateFontSize(size: Float) {
        pushState("fontSize")
        setState { copy(activeStyle = activeStyle?.copy(fontSize = size)) }
    }

    private fun updateFontWeight(weight: Int) {
        pushState("fontWeight")
        setState { copy(activeStyle = activeStyle?.copy(fontWeight = weight)) }
    }

    private fun updateTextColor(color: Long) {
        pushState("textColor")
        setState { copy(activeStyle = activeStyle?.copy(textColor = color)) }
    }

    private fun updateHighlightColor(color: Long) {
        pushState("highlightColor")
        setState { copy(activeStyle = activeStyle?.copy(highlightColor = color)) }
    }

    private fun updateOutlineWidth(width: Float) {
        pushState("outlineWidth")
        setState { copy(activeStyle = activeStyle?.copy(outlineWidth = width)) }
    }

    private fun updateOutlineColor(color: Long) {
        pushState("outlineColor")
        setState { copy(activeStyle = activeStyle?.copy(outlineColor = color)) }
    }

    private fun updateBackgroundType(type: BackgroundType) {
        pushState("backgroundType")
        setState { copy(activeStyle = activeStyle?.copy(backgroundType = type)) }
    }

    private fun updateBackgroundColor(color: Long) {
        pushState("backgroundColor")
        setState { copy(activeStyle = activeStyle?.copy(backgroundColor = color)) }
    }

    private fun updateBackgroundOpacity(opacity: Float) {
        pushState("backgroundOpacity")
        setState { copy(activeStyle = activeStyle?.copy(backgroundOpacity = opacity)) }
    }

    private fun updateDisplayMode(mode: DisplayMode) {
        pushState("displayMode")
        var style = uiState.value.activeStyle?.copy(displayMode = mode) ?: return
        if (mode == DisplayMode.KARAOKE_FILL || mode == DisplayMode.PHRASE) {
            style = style.copy(
                wordEnterAnimation = AnimationType.NONE,
                wordExitAnimation = AnimationType.NONE
            )
        }
        if (mode == DisplayMode.TYPEWRITER) {
            style = style.copy(wordEnterAnimation = AnimationType.TYPEWRITER)
        }
        setState { copy(activeStyle = style) }
    }

    private fun updateWordEnterAnimation(anim: AnimationType) {
        pushState("wordEnterAnimation")
        setState { copy(activeStyle = activeStyle?.copy(wordEnterAnimation = anim)) }
    }

    private fun updateKaraokeHighlightMode(mode: KaraokeHighlightMode) {
        pushState("karaokeHighlightMode")
        setState { copy(activeStyle = activeStyle?.copy(karaokeHighlightMode = mode)) }
    }

    private fun updatePositionY(y: Float) {
        pushState("positionY")
        setState { copy(activeStyle = activeStyle?.copy(positionY = y)) }
    }

    private fun updateAlignment(alignment: TextAlignment) {
        pushState("alignment")
        setState { copy(activeStyle = activeStyle?.copy(alignment = alignment)) }
    }

    private fun updateMaxWordsPerLine(count: Int) {
        pushState("maxWordsPerLine")
        setState { copy(activeStyle = activeStyle?.copy(maxWordsPerLine = count)) }
    }

    private fun updateMaxLines(count: Int) {
        pushState("maxLines")
        setState { copy(activeStyle = activeStyle?.copy(maxLines = count)) }
    }

    private fun updateRemovePunctuation(remove: Boolean) {
        pushState("removePunctuation")
        setState { copy(activeStyle = activeStyle?.copy(removePunctuation = remove)) }
    }

    private fun updateBackgroundPaddingH(v: Float) { pushState("backgroundPaddingH"); setState { copy(activeStyle = activeStyle?.copy(backgroundPaddingH = v)) } }
    private fun updateBackgroundPaddingV(v: Float) { pushState("backgroundPaddingV"); setState { copy(activeStyle = activeStyle?.copy(backgroundPaddingV = v)) } }
    private fun updateBackgroundCornerRadius(v: Float) { pushState("backgroundCornerRadius"); setState { copy(activeStyle = activeStyle?.copy(backgroundCornerRadius = v)) } }
    private fun updateAnimationDurationMs(v: Int) { pushState("animationDurationMs"); setState { copy(activeStyle = activeStyle?.copy(animationDurationMs = v)) } }
    private fun updateLetterSpacing(v: Float) { pushState("letterSpacing"); setState { copy(activeStyle = activeStyle?.copy(letterSpacing = v)) } }
    private fun updateIsItalic(v: Boolean) { pushState("isItalic"); setState { copy(activeStyle = activeStyle?.copy(isItalic = v)) } }

    private fun saveAndApply(projectId: String) {
        viewModelScope.launch {
            val style = uiState.value.activeStyle ?: return@launch
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

    private fun saveAsNewPreset(presetName: String) {
        viewModelScope.launch {
            val style = uiState.value.activeStyle ?: return@launch
            val newPreset = style.copy(
                id = UUID.randomUUID().toString(),
                name = presetName,
                isDefault = false
            )
            captionRepository.saveStyle(newPreset)
            setState { copy(activeStyle = newPreset) }
        }
    }

    private fun deletePreset(style: CaptionStyleEntity) {
        viewModelScope.launch {
            if (!style.isDefault) {
                captionRepository.deleteStyle(style)
                // If we deleted the active style, switch to the first available
                if (uiState.value.activeStyle?.id == style.id) {
                    setState { copy(activeStyle = uiState.value.styles.firstOrNull()) }
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

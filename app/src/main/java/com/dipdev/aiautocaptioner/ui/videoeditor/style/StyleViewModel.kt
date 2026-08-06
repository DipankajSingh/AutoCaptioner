package com.dipdev.aiautocaptioner.ui.videoeditor.style

import android.app.Activity
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.dipdev.aiautocaptioner.data.db.entity.AnimationType
import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextAlignment
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.ui.captioneditor.CaptionAlignmentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.revenuecat.purchases.restorePurchasesWith

import com.dipdev.aiautocaptioner.data.billing.PremiumManager
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.ui.base.BaseViewModel
import com.dipdev.aiautocaptioner.ui.base.UiEffect
import com.dipdev.aiautocaptioner.ui.base.UiEvent
import com.dipdev.aiautocaptioner.ui.base.UiState
import com.dipdev.aiautocaptioner.R
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
    data class LoadStyles(val projectId: String) : StyleEditorUiEvent
    data class SelectPreset(val style: CaptionStyleEntity) : StyleEditorUiEvent
    data class SelectTab(val tab: StyleTab) : StyleEditorUiEvent
    data class UpdateStyle(val propertyName: String, val transform: (CaptionStyleEntity) -> CaptionStyleEntity) : StyleEditorUiEvent
    data class SaveAndApply(val projectId: String) : StyleEditorUiEvent
    data class SaveAsNewPreset(val presetName: String) : StyleEditorUiEvent
    data class DeletePreset(val style: CaptionStyleEntity) : StyleEditorUiEvent
    data object Undo : StyleEditorUiEvent
    data object Redo : StyleEditorUiEvent
    data class PurchaseLifetime(val activity: Activity) : StyleEditorUiEvent
    data object RestorePurchases : StyleEditorUiEvent
    /** Inline quick-edit from VideoEditor popup: update a segment's text without entering full CaptionEditor. */
    data class UpdateSegmentText(val segmentId: String, val newText: String) : StyleEditorUiEvent
    /** Fix 9: marks the caption editor as visited so the export warning does not reappear */
    data class MarkCaptionEditorVisited(val projectId: String) : StyleEditorUiEvent
}

sealed interface StyleEditorUiEffect : UiEffect

@HiltViewModel
class StyleViewModel @Inject constructor(
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
            is StyleEditorUiEvent.LoadStyles -> loadStyles(event.projectId)
            is StyleEditorUiEvent.SelectPreset -> selectPreset(event.style)
            is StyleEditorUiEvent.SelectTab -> selectTab(event.tab)
            is StyleEditorUiEvent.UpdateStyle -> updateStyle(event.propertyName, event.transform)
            is StyleEditorUiEvent.SaveAndApply -> saveAndApply(event.projectId)
            is StyleEditorUiEvent.SaveAsNewPreset -> saveAsNewPreset(event.presetName)
            is StyleEditorUiEvent.DeletePreset -> deletePreset(event.style)
            is StyleEditorUiEvent.Undo -> undo()
            is StyleEditorUiEvent.Redo -> redo()
            is StyleEditorUiEvent.PurchaseLifetime -> purchaseLifetime()
            is StyleEditorUiEvent.RestorePurchases -> restorePurchases()
            is StyleEditorUiEvent.UpdateSegmentText -> updateSegmentText(event.segmentId, event.newText)
            is StyleEditorUiEvent.MarkCaptionEditorVisited -> markCaptionEditorVisited(event.projectId)
        }
    }

    private fun purchaseLifetime() {
        // Fix 15: Purchase is handled by RevenueCat Paywall directly in UI — no-op here.
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

    private fun updateSegmentText(segmentId: String, newText: String) {
        viewModelScope.launch {
            val current = uiState.value.segments.find { it.id == segmentId } ?: return@launch
            
            // Align and update child words
            val oldWords = uiState.value.wordsMap[segmentId] ?: emptyList()
            val alignedWords = CaptionAlignmentUtils.alignWords(
                oldWords = oldWords,
                newText = newText,
                segmentId = current.id,
                projectId = current.projectId,
                segmentStartTimeMs = current.startTimeMs,
                segmentEndTimeMs = current.endTimeMs
            )
            captionRepository.replaceWordsForSegment(current.id, alignedWords)
            
            val updated = current.copy(text = newText, isEdited = true)
            captionRepository.updateSegment(updated)
            
            // Refresh local state immediately so the caption track re-renders without waiting for Flow
            setState {
                val newMap = wordsMap.toMutableMap()
                newMap[segmentId] = alignedWords
                copy(
                    segments = segments.map { if (it.id == segmentId) updated else it },
                    wordsMap = newMap
                )
            }
        }
    }

    // Fix A: ExoPlayer ownership moved to SharedPlayerViewModel (navigation-graph-scoped).
    // Fix 15: purchaseLifetime() is a no-op — RevenueCat Paywall handles purchases in UI.
    // Fix 16: @SuppressLint("EmptySuperCall") annotation removed — it was incorrect.

    /**
     * Fix 9: Persists [ProjectEntity.hasVisitedCaptionEditor] = true so the "Export Anyway"
     * warning does not reappear on subsequent export attempts.
     */
    private fun markCaptionEditorVisited(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            projectRepository.updateProject(project.copy(hasVisitedCaptionEditor = true))
            setState { copy(project = project.copy(hasVisitedCaptionEditor = true)) }
        }
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
        val restored = undoStack.removeAt(undoStack.size - 1)
        setState { copy(activeStyle = restored) }
        updateUndoRedoState()
        lastPushProperty = ""
        scheduleAutoSave(restored)
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        val current = uiState.value.activeStyle ?: return
        undoStack.add(current)
        val restored = redoStack.removeAt(redoStack.size - 1)
        setState { copy(activeStyle = restored) }
        updateUndoRedoState()
        lastPushProperty = ""
        scheduleAutoSave(restored)
    }


    private fun loadStyles(projectId: String) {

        loadStylesJob?.cancel()
        loadStylesJob = viewModelScope.launch {
            val projectEntity = projectRepository.getProjectById(projectId) ?: return@launch
            setState { copy(project = projectEntity) }

            var styleIdPersisted = projectEntity.activeStyleId != null

            launch {
                captionRepository.getAllStyles().collect { list ->
                    setState { copy(styles = list) }
                    if (uiState.value.activeStyle == null) {
                        val activeId = projectEntity.activeStyleId
                        val resolvedStyle = if (activeId != null) {
                            list.find { it.id == activeId } ?: list.firstOrNull()
                        } else {
                            list.firstOrNull()
                        }
                        setState { copy(activeStyle = resolvedStyle) }

                        // Persist activeStyleId once so export always has it
                        if (resolvedStyle != null && !styleIdPersisted) {
                            styleIdPersisted = true
                            projectRepository.updateProject(
                                projectEntity.copy(
                                    activeStyleId = resolvedStyle.id,
                                    updatedAt = System.currentTimeMillis()
                                )
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

    private var autoSaveJob: Job? = null
    private var loadStylesJob: Job? = null

    /**
     * Schedules a debounced DB write so that any style change (position drag,
     * font size, etc.) survives back-navigation without the user tapping
     * "Save & Apply".
     *
     * Strategy:
     *  - If [style] is unchanged from what's already stored for its ID (e.g. an
     *    untouched default preset), just update [ProjectEntity.activeStyleId] to
     *    point at it.
     *  - Otherwise, upsert a stable draft row (id = "draft_<projectId>") so we
     *    never accumulate infinite custom rows on every slider drag. The draft
     *    holds the modified values so export picks them up.
     *
     * Note: modification is detected by comparing [style] to its stored row
     * (data-class equality), NOT by `isDefault` — a preset the user edited still
     * has isDefault=true, and skipping the draft for it would silently lose the
     * edit in exported videos.
     */
    private fun scheduleAutoSave(style: CaptionStyleEntity) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(500L) // debounce — only save after user stops editing
            val projectId = uiState.value.project?.id ?: return@launch
            val stored = captionRepository.getStyleById(style.id)
            val isUnmodified = stored != null && stored == style

            val styleToLink = if (isUnmodified) {
                style
            } else {
                val draftId = "draft_$projectId"
                val draft = style.copy(
                    id = draftId,
                    name = "Draft",
                    isDefault = false
                )
                captionRepository.saveStyle(draft)
                draft
            }

            val project = projectRepository.getProjectById(projectId) ?: return@launch
            projectRepository.updateProject(
                project.copy(
                    activeStyleId = styleToLink.id,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun selectPreset(style: CaptionStyleEntity) {
        pushState("preset")
        setState { copy(activeStyle = style) }
        // Immediately persist the selected preset so Back doesn't lose it.
        // No draft needed — the preset already exists in the DB.
        viewModelScope.launch {
            val projectId = uiState.value.project?.id ?: return@launch
            val project = projectRepository.getProjectById(projectId) ?: return@launch
            projectRepository.updateProject(
                project.copy(
                    activeStyleId = style.id,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun selectTab(tab: StyleTab) {
        setState { copy(selectedTab = tab) }
    }

    private fun updateStyle(propertyName: String, transform: (CaptionStyleEntity) -> CaptionStyleEntity) {
        pushState(propertyName)
        var style = uiState.value.activeStyle?.let(transform) ?: return
        
        if (propertyName == "displayMode") {
            // Enforce forced animations per DisplayModeBehavior rules
            val forcedEnter = com.dipdev.aiautocaptioner.engine.DisplayModeBehavior.forcedEnterAnimation(style.displayMode)
            val forcedExit = com.dipdev.aiautocaptioner.engine.DisplayModeBehavior.forcedExitAnimation(style.displayMode)
            style = style.copy(
                wordEnterAnimation = forcedEnter ?: style.wordEnterAnimation,
                wordExitAnimation = forcedExit ?: style.wordExitAnimation
            )
        }
        
        setState { copy(activeStyle = style) }
        // Auto-save any property change debounced so Back doesn't lose it
        scheduleAutoSave(style)
    }

    private fun saveAndApply(projectId: String) {
        viewModelScope.launch {
            val style = uiState.value.activeStyle ?: return@launch
            val styleToSave = if (style.isDefault || style.name == "Custom") {
                style.copy(
                    id = UUID.randomUUID().toString(),
                    name = "Custom ${System.currentTimeMillis() % 1000}",
                    isDefault = false
                )
            } else style

            captionRepository.saveStyle(styleToSave)
            setState { copy(activeStyle = styleToSave) }

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

    override fun onCleared() {
        autoSaveJob?.cancel()
    }
}

enum class StyleTab(val labelResId: Int) {
    TEXT(R.string.style_tab_text),
    COLOR(R.string.style_tab_color),
    ANIMATION(R.string.style_tab_animation),
    PRESETS(R.string.style_tab_presets)
}

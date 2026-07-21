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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
            val updated = current.copy(text = newText, isEdited = true)
            captionRepository.updateSegment(updated)
            // Refresh local state immediately so the caption track re-renders without waiting for Flow
            setState {
                copy(segments = segments.map { if (it.id == segmentId) updated else it })
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
        setState {
            copy(activeStyle = style)
        }
    }

    private fun selectTab(tab: StyleTab) {
        setState { copy(selectedTab = tab) }
    }

    private fun updateStyle(propertyName: String, transform: (CaptionStyleEntity) -> CaptionStyleEntity) {
        pushState(propertyName)
        var style = uiState.value.activeStyle?.let(transform) ?: return
        
        if (propertyName == "displayMode") {
            if (style.displayMode == DisplayMode.KARAOKE_FILL || style.displayMode == DisplayMode.PHRASE) {
                style = style.copy(
                    wordEnterAnimation = AnimationType.NONE,
                    wordExitAnimation = AnimationType.NONE
                )
            }
            if (style.displayMode == DisplayMode.TYPEWRITER) {
                style = style.copy(wordEnterAnimation = AnimationType.TYPEWRITER)
            }
        }
        
        setState { copy(activeStyle = style) }
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
}

enum class StyleTab(val label: String) {
    TEXT("Text"),
    COLOR("Color"),
    ANIMATION("Animation"),
    PRESETS("Presets")
}

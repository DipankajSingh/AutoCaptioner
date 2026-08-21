package com.dipdev.aiautocaptioner.ui.videoeditor.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity

@Stable
class EditorScreenState(
    initialSelectedClipId: String?,
    initialZoomLevel: Float,
    initialCurrentMode: EditorMode,
    initialInlineEditText: String
) {
    var selectedClipId by mutableStateOf(initialSelectedClipId)
    var zoomLevel by mutableFloatStateOf(initialZoomLevel)
    var currentMode by mutableStateOf(initialCurrentMode)
    
    var showDiscardDialog by mutableStateOf(false)
    var selectedCaptionSegment by mutableStateOf<CaptionSegmentEntity?>(null)
    var inlineEditText by mutableStateOf(initialInlineEditText)
    var showTextColorMenu by mutableStateOf(false)
    var showTextSizeSlider by mutableStateOf(false)
    var showFontList by mutableStateOf(false)
    var cropOverlay by mutableStateOf<ImageOverlayEntity?>(null)
    
    var showTranscriptionBottomSheet by mutableStateOf(false)
    var showExportWarning by mutableStateOf(false)
    var pendingImagePlayheadMs by mutableLongStateOf(0L)
}

val EditorScreenStateSaver: Saver<EditorScreenState, Any> = listSaver(
    save = {
        listOf(
            it.selectedClipId,
            it.zoomLevel,
            it.currentMode.name,
            it.inlineEditText
        )
    },
    restore = {
        EditorScreenState(
            initialSelectedClipId = it[0] as String?,
            initialZoomLevel = it[1] as Float,
            initialCurrentMode = EditorMode.valueOf(it[2] as String),
            initialInlineEditText = it[3] as String
        )
    }
)

@Composable
fun rememberEditorScreenState(): EditorScreenState {
    return rememberSaveable(saver = EditorScreenStateSaver) { 
        EditorScreenState(
            initialSelectedClipId = null,
            initialZoomLevel = 1f,
            initialCurrentMode = EditorMode.VIDEO,
            initialInlineEditText = ""
        )
    }
}

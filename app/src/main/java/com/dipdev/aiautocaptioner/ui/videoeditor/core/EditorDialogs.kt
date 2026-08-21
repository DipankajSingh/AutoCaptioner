package com.dipdev.aiautocaptioner.ui.videoeditor.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R

/**
 * "Discard Unsaved Edits & Exit?" confirmation dialog.
 */
@Composable
fun DiscardEditsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    com.dipdev.aiautocaptioner.ui.components.UniversalDialog(
        type = com.dipdev.aiautocaptioner.ui.components.DialogType.WARNING,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.editor_discard_title),
        body = stringResource(R.string.editor_discard_body),
        confirmText = stringResource(R.string.editor_discard),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.editor_cancel),
        onDismiss = onDismiss
    )
}

@Composable
fun EditorDialogs(
    showDiscardDialog: Boolean,
    showTranscriptionBottomSheet: Boolean,
    showTextColorMenu: Boolean,
    showExportWarning: Boolean,
    
    projectId: String,
    selectedTextOverlay: com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity?,
    processingStep: com.dipdev.aiautocaptioner.ui.processing.ProcessingStep,
    
    availableModels: List<com.dipdev.aiautocaptioner.data.model.WhisperModel>,
    initialModelId: String?,
    initialLanguage: String,
    initialTranslate: Boolean,
    initialPrompt: String,
    segmentsEmpty: Boolean,
    streamedSegments: List<com.dipdev.aiautocaptioner.ui.processing.StreamedSegment>,
    
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissTranscription: () -> Unit,
    onStartTranscription: (String, String, Boolean, String) -> Unit,
    onPrepareTranscription: (String) -> Unit,
    onCancelProcessing: () -> Unit,
    onDismissTextColorMenu: () -> Unit,
    onUpdateTextOverlay: (com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity) -> Unit,
    onDismissExportWarning: () -> Unit,
    onConfirmExportWarning: () -> Unit
) {
    if (showDiscardDialog) {
        DiscardEditsDialog(
            onConfirm = onConfirmDiscard,
            onDismiss = onDismissDiscard
        )
    }

    if (showTranscriptionBottomSheet) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            onPrepareTranscription(projectId)
        }
        
        com.dipdev.aiautocaptioner.ui.processing.components.TranscriptionBottomSheet(
            onDismiss = onDismissTranscription,
            availableModels = availableModels,
            initialModelId = initialModelId,
            initialLanguage = initialLanguage,
            initialTranslate = initialTranslate,
            initialPrompt = initialPrompt,
            skipUi = segmentsEmpty,
            onStart = onStartTranscription
        )
    }

    com.dipdev.aiautocaptioner.ui.processing.components.TranscriptionOverlay(
        step = processingStep,
        streamedSegments = streamedSegments,
        onCancel = onCancelProcessing
    )

    if (showTextColorMenu && selectedTextOverlay != null) {
        com.dipdev.aiautocaptioner.ui.videoeditor.text.TextOverlayColorPickerPopup(
            textColorArgb = selectedTextOverlay.textColorArgb,
            backgroundColorArgb = selectedTextOverlay.backgroundColorArgb,
            onColorChanged = { field, color ->
                val updated = when (field) {
                    com.dipdev.aiautocaptioner.ui.videoeditor.text.TextOverlayColorField.TEXT -> selectedTextOverlay.copy(textColorArgb = color)
                    com.dipdev.aiautocaptioner.ui.videoeditor.text.TextOverlayColorField.BACKGROUND -> selectedTextOverlay.copy(
                        backgroundColorArgb = color, 
                        backgroundOpacity = 1f, 
                        backgroundStyle = "SOLID"
                    )
                }
                onUpdateTextOverlay(updated)
            },
            onDismissRequest = onDismissTextColorMenu
        )
    }

    if (showExportWarning) {
        com.dipdev.aiautocaptioner.ui.components.UniversalDialog(
            type = com.dipdev.aiautocaptioner.ui.components.DialogType.WARNING,
            title = stringResource(id = R.string.export_no_captions_title),
            body = stringResource(id = R.string.export_no_captions_body),
            confirmText = stringResource(id = R.string.export_anyway),
            onConfirm = onConfirmExportWarning,
            dismissText = stringResource(id = android.R.string.cancel),
            onDismissRequest = onDismissExportWarning
        )
    }
}

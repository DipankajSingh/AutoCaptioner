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

package com.dipdev.aiautocaptioner.ui.videoeditor.core

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R

/**
 * "Delete Project?" confirmation dialog.
 */
@Composable
fun DeleteProjectDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    com.dipdev.aiautocaptioner.ui.components.UniversalDialog(
        type = com.dipdev.aiautocaptioner.ui.components.DialogType.ERROR,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.editor_delete_title),
        body = stringResource(R.string.editor_delete_body),
        confirmText = stringResource(R.string.editor_delete),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.editor_cancel),
        onDismiss = onDismiss
    )
}

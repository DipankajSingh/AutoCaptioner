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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_delete_title)) },
        text = { Text(stringResource(R.string.editor_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.editor_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        }
    )
}

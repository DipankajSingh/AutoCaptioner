package com.dipdev.aiautocaptioner.ui.videoeditor.core

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * "Save changes?" dialog shown when the user attempts to leave EditorScreen with unsaved edits.
 */
@Composable
fun UnsavedEditsDialog(
    onSaveAndContinue: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save changes?") },
        text = { Text("You have unsaved edits. Do you want to apply them before leaving?") },
        confirmButton = {
            TextButton(onClick = onSaveAndContinue) {
                Text("Save & Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard")
            }
        }
    )
}

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
        title = { Text("Delete Project?") },
        text = { Text("Are you sure you want to delete this project? Your edits will be lost.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

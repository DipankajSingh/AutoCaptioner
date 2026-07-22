package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.processing.ModelSafetyCheck

@Composable
fun CancelProcessDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_cancel_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.dialog_cancel_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dialog_stop_exit), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_keep_processing))
            }
        }
    )
}

@Composable
fun SafetyCheckDialogs(
    safetyCheck: ModelSafetyCheck?,
    onDismiss: () -> Unit,
    onProceed: (String) -> Unit
) {
    when (val check = safetyCheck) {
        is ModelSafetyCheck.StorageError -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.dialog_storage_title)) },
                text = { Text(stringResource(R.string.dialog_storage_body, check.requiredMb)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_okay))
                    }
                }
            )
        }
        is ModelSafetyCheck.CellularWarning -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.dialog_cellular_title)) },
                text = { Text(stringResource(R.string.dialog_cellular_body, check.sizeMb)) },
                confirmButton = {
                    TextButton(onClick = { onProceed(check.modelId) }) {
                        Text(stringResource(R.string.dialog_download_anyway))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.processing_cancel))
                    }
                }
            )
        }
        else -> {
            // No active dialog
        }
    }
}

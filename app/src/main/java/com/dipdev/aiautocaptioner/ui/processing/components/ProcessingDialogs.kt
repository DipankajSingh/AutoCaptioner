package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.dipdev.aiautocaptioner.ui.processing.ModelSafetyCheck

@Composable
fun CancelProcessDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel Process?", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to stop? Your progress will be lost.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Stop & Exit", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Processing")
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
                title = { Text("Not enough storage") },
                text = { Text("You need at least ${check.requiredMb} MB of free space. Please free up storage and try again.") },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Okay")
                    }
                }
            )
        }
        is ModelSafetyCheck.CellularWarning -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Download over Cellular?") },
                text = { Text("You are about to download ${check.sizeMb} MB over a cellular connection. This may consume a large amount of your data plan.") },
                confirmButton = {
                    TextButton(onClick = { onProceed(check.modelId) }) {
                        Text("Download anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            )
        }
        else -> {
            // No active dialog
        }
    }
}

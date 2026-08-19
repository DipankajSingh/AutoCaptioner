package com.dipdev.aiautocaptioner.ui.onboarding.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.UniversalDialog
import com.dipdev.aiautocaptioner.ui.components.DialogType

@Composable
fun CellularWarningDialog(
    sizeMb: Long,
    onWaitForWifi: () -> Unit,
    onDownloadAnyway: () -> Unit
) {
    UniversalDialog(
        type = DialogType.WARNING,
        title = stringResource(R.string.dialog_cellular_title),
        body = stringResource(R.string.dialog_cellular_body, sizeMb),
        confirmText = stringResource(R.string.dialog_download_anyway),
        onConfirm = onDownloadAnyway,
        dismissText = stringResource(R.string.dialog_wait_wifi),
        onDismiss = onWaitForWifi,
        onDismissRequest = onWaitForWifi
    )
}

@Composable
fun StorageErrorDialog(
    requiredMb: Long,
    onCheckAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Not Enough Storage") },
        text = { Text("You need at least $requiredMb MB of free space to download and install this engine. Please free up some space and try again.") },
        confirmButton = {
            TextButton(onClick = onCheckAgain) {
                Text("Check Again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

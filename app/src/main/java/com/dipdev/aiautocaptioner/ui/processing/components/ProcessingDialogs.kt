package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.processing.ModelSafetyCheck

@Composable
fun CancelProcessDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    com.dipdev.aiautocaptioner.ui.components.UniversalDialog(
        type = com.dipdev.aiautocaptioner.ui.components.DialogType.WARNING,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.dialog_cancel_title),
        body = stringResource(R.string.dialog_cancel_body),
        confirmText = stringResource(R.string.dialog_stop_exit),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.dialog_keep_processing),
        onDismiss = onDismiss
    )
}

@Composable
fun SafetyCheckDialogs(
    safetyCheck: ModelSafetyCheck?,
    onDismiss: () -> Unit,
    onProceed: (String) -> Unit
) {
    when (safetyCheck) {
        is ModelSafetyCheck.StorageError -> {
            com.dipdev.aiautocaptioner.ui.components.UniversalDialog(
                type = com.dipdev.aiautocaptioner.ui.components.DialogType.ERROR,
                onDismissRequest = onDismiss,
                title = stringResource(R.string.dialog_storage_title),
                body = stringResource(R.string.dialog_storage_body, safetyCheck.requiredMb),
                confirmText = stringResource(R.string.dialog_okay),
                onConfirm = onDismiss
            )
        }
        is ModelSafetyCheck.CellularWarning -> {
            com.dipdev.aiautocaptioner.ui.components.UniversalDialog(
                type = com.dipdev.aiautocaptioner.ui.components.DialogType.WARNING,
                onDismissRequest = onDismiss,
                title = stringResource(R.string.dialog_cellular_title),
                body = stringResource(R.string.dialog_cellular_body, safetyCheck.sizeMb),
                confirmText = stringResource(R.string.dialog_download_anyway),
                onConfirm = { onProceed(safetyCheck.modelId) },
                dismissText = stringResource(R.string.processing_cancel),
                onDismiss = onDismiss
            )
        }
        else -> {
            // No active dialog
        }
    }
}

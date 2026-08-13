package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R

/**
 * A generic rename dialog with a pre-filled [OutlinedTextField].
 *
 * @param initialValue  Text pre-filled in the field when the dialog opens.
 * @param title         Dialog title string.
 * @param label         Field label string.
 * @param onConfirm     Called with the new (trimmed) name when the user confirms.
 *                      Not called if the value is blank.
 * @param onDismiss     Called when the dialog should be closed without saving.
 */
@Composable
fun RenameDialog(
    initialValue: String,
    title: String? = null,
    label: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val resolvedTitle = title ?: stringResource(R.string.rename_title)
    val resolvedLabel = label ?: stringResource(R.string.rename_name_label)
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    UniversalDialog(
        type = DialogType.INFO,
        onDismissRequest = onDismiss,
        title = resolvedTitle,
        content = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                label         = { Text(resolvedLabel) },
                singleLine    = true,
                shape         = RoundedCornerShape(4.dp), // Flattened shape
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmText = stringResource(R.string.rename_title),
        onConfirm = { if (text.isNotBlank()) { onConfirm(text.trim()); onDismiss() } },
        isConfirmEnabled = text.isNotBlank(),
        dismissText = stringResource(R.string.processing_cancel),
        onDismiss = onDismiss
    )
}

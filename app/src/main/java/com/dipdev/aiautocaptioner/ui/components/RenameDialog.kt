package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
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

    FlatAlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(resolvedTitle) },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                label         = { Text(resolvedLabel) },
                singleLine    = true,
                shape         = RoundedCornerShape(4.dp), // Flattened shape
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            AppPrimaryButton(
                onClick  = { if (text.isNotBlank()) { onConfirm(text.trim()); onDismiss() } },
                enabled  = text.isNotBlank()
            ) { Text(stringResource(R.string.rename_title)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.processing_cancel)) }
        }
    )
}

package com.dipdev.autocaptioner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    title: String = "Rename",
    label: String = "Name",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(title) },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                label         = { Text(label) },
                singleLine    = true,
                shape         = RoundedCornerShape(10.dp),
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { if (text.isNotBlank()) { onConfirm(text.trim()); onDismiss() } },
                enabled  = text.isNotBlank()
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

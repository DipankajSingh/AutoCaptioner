package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AccentRose

@Composable
fun ExitRecordingDialog(
    onSaveAndExit: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.8f),
        title = {
            Text(
                text = stringResource(R.string.recorder_exit_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.recorder_exit_message),
                color = Color.White.copy(alpha = 0.8f)
            )
        },
        confirmButton = {
            TextButton(onClick = onSaveAndExit) {
                Text(
                    text = stringResource(R.string.recorder_save_and_exit),
                    color = AccentRose,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.recorder_cancel),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                TextButton(onClick = onDiscard) {
                    Text(
                        text = stringResource(R.string.recorder_discard),
                        color = AccentRose,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    )
}

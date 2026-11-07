package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.core.video.VideoGuardResult
import com.dipdev.aiautocaptioner.core.video.VideoWarnLevel
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

@Composable
fun LongVideoWarningDialog(
    guardResult: VideoGuardResult,
    videoDurationMs: Long,
    onContinue: () -> Unit,
    onChooseFasterModel: () -> Unit,
    onTrimFirst: () -> Unit,
    onCancel: () -> Unit
) {
    val isStrong = guardResult.warnLevel == VideoWarnLevel.STRONG_WARN
    val formattedDuration = remember(videoDurationMs) {
        val totalSecs = (videoDurationMs / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        if (mins >= 60) {
            val hours = mins / 60
            val remMins = mins % 60
            "${hours}h ${remMins}m"
        } else {
            "${mins}m ${secs}s"
        }
    }

    val formattedEstimate = remember(guardResult.estimatedTimeSeconds) {
        val estSecs = guardResult.estimatedTimeSeconds.coerceAtLeast(0)
        if (estSecs >= 60) {
            "${estSecs / 60} min"
        } else {
            "${estSecs}s"
        }
    }

    com.dipdev.aiautocaptioner.ui.components.UniversalDialog(
        type = if (isStrong) com.dipdev.aiautocaptioner.ui.components.DialogType.ERROR
               else com.dipdev.aiautocaptioner.ui.components.DialogType.WARNING,
        onDismissRequest = onCancel,
        title = stringResource(
            if (isStrong) R.string.video_guard_dialog_title_strong
            else R.string.video_guard_dialog_title
        ),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info badges row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.video_guard_video_length, formattedDuration),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.video_guard_estimated_time, formattedEstimate),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isStrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                // Advisory message
                Text(
                    text = stringResource(
                        if (isStrong) R.string.video_guard_strong_note
                        else R.string.video_guard_short_form_note
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Alternative Actions
                OutlinedButton(
                    onClick = onChooseFasterModel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.video_guard_choose_faster))
                }

                OutlinedButton(
                    onClick = onTrimFirst,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.video_guard_trim_first))
                }
            }
        },
        confirmText = stringResource(R.string.video_guard_continue),
        onConfirm = onContinue,
        dismissText = stringResource(R.string.video_guard_cancel),
        onDismiss = onCancel
    )
}


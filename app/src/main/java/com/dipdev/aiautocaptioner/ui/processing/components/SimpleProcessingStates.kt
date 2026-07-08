package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.foundation.layout.*
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.AudioWaveformAnimation
import com.dipdev.aiautocaptioner.ui.components.ProcessingStateHeader
import com.dipdev.aiautocaptioner.ui.components.FullScreenStateContainer

@Composable
fun ExtractingAudioView(onCancel: () -> Unit) {
    FullScreenStateContainer(
        graphicContent = {
            AudioWaveformAnimation(modifier = Modifier.size(120.dp))
        },
        textContent = {
            Spacer(modifier = Modifier.height(32.dp))
            ProcessingStateHeader(
                title = "Extracting Audio",
                subtitle = "Pulling audio track from your video..."
            )
        },
        actionContent = {
            Spacer(modifier = Modifier.height(32.dp))
            AppOutlinedButton(onClick = onCancel) {
                Text("Cancel", maxLines = 1)
            }
        }
    )
}

@Composable
fun LoadingModelView() {
    FullScreenStateContainer(
        graphicContent = {
            CircularProgressIndicator(
                color = AccentCyan,
                modifier = Modifier.size(48.dp),
                strokeCap = StrokeCap.Round
            )
        },
        textContent = {
            Spacer(modifier = Modifier.height(24.dp))
            ProcessingStateHeader(
                title = "Loading AI Model",
                subtitle = "Loading Whisper model into memory..."
            )
        },
        actionContent = {}
    )
}

@Composable
fun SavingView() {
    FullScreenStateContainer(
        graphicContent = {
            CircularProgressIndicator(
                color = AccentCyan,
                modifier = Modifier.size(48.dp),
                strokeCap = StrokeCap.Round
            )
        },
        textContent = {
            Spacer(modifier = Modifier.height(24.dp))
            ProcessingStateHeader(title = "Saving Captions")
        },
        actionContent = {}
    )
}

@Composable
fun CancellingView() {
    FullScreenStateContainer(
        graphicContent = {},
        textContent = {
            Spacer(modifier = Modifier.height(24.dp))
            ProcessingStateHeader(
                title = "Cancelling",
                subtitle = "Stopping AI processing..."
            )
        },
        actionContent = {}
    )
}

@Composable
fun CancelledView(onRetry: () -> Unit, onGoBack: () -> Unit) {
    FullScreenStateContainer(
        graphicContent = {},
        textContent = {
            ProcessingStateHeader(
                title = "Cancelled",
                subtitle = "Transcription was stopped."
            )
        },
        actionContent = {
            Spacer(modifier = Modifier.height(24.dp))
            AppPrimaryButton(onClick = onRetry) {
                Text("Try Again", maxLines = 1)
            }
            Spacer(modifier = Modifier.height(12.dp))
            AppOutlinedButton(onClick = onGoBack) {
                Text("Go Back", maxLines = 1)
            }
        }
    )
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, onGoBack: () -> Unit) {
    FullScreenStateContainer(
        graphicContent = {
            Icon(
                imageVector = FeatherIcons.AlertTriangle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
        },
        textContent = {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingStateHeader(
                title = "Processing Failed",
                subtitle = message
            )
        },
        actionContent = {
            Spacer(modifier = Modifier.height(24.dp))
            AppPrimaryButton(onClick = onRetry) {
                Text("Retry", maxLines = 1)
            }
            Spacer(modifier = Modifier.height(12.dp))
            AppOutlinedButton(onClick = onGoBack) {
                Text("Go Back", maxLines = 1)
            }
        }
    )
}

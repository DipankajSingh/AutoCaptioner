package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.AudioWaveformAnimation
import com.dipdev.aiautocaptioner.ui.components.ProcessingStateHeader

@Composable
fun ExtractingAudioView(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AudioWaveformAnimation(modifier = Modifier.size(120.dp))
        Spacer(modifier = Modifier.height(32.dp))
        ProcessingStateHeader(
            title = "Extracting Audio",
            subtitle = "Pulling audio track from your video..."
        )
        Spacer(modifier = Modifier.height(32.dp))
        AppOutlinedButton(onClick = onCancel) {
            Text("Cancel", maxLines = 1)
        }
    }
}

@Composable
fun LoadingModelView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(24.dp))
        ProcessingStateHeader(
            title = "Loading AI Model",
            subtitle = "Loading Whisper model into memory..."
        )
    }
}

@Composable
fun SavingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(24.dp))
        ProcessingStateHeader(title = "Saving Captions")
    }
}

@Composable
fun CancellingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        ProcessingStateHeader(
            title = "Cancelling",
            subtitle = "Stopping AI processing..."
        )
    }
}

@Composable
fun CancelledView(onRetry: () -> Unit, onGoBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ProcessingStateHeader(
            title = "Cancelled",
            subtitle = "Transcription was stopped."
        )
        Spacer(modifier = Modifier.height(24.dp))
        AppPrimaryButton(onClick = onRetry) {
            Text("Try Again", maxLines = 1)
        }
        Spacer(modifier = Modifier.height(12.dp))
        AppOutlinedButton(onClick = onGoBack) {
            Text("Go Back", maxLines = 1)
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, onGoBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        ProcessingStateHeader(
            title = "Processing Failed",
            subtitle = message
        )
        Spacer(modifier = Modifier.height(24.dp))
        AppPrimaryButton(onClick = onRetry) {
            Text("Retry", maxLines = 1)
        }
        Spacer(modifier = Modifier.height(12.dp))
        AppOutlinedButton(onClick = onGoBack) {
            Text("Go Back", maxLines = 1)
        }
    }
}

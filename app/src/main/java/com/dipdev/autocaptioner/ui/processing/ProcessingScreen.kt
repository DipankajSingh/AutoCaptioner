package com.dipdev.autocaptioner.ui.processing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProcessingScreen(
    projectId: String,
    onDone: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val step by viewModel.step.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.startProcessing(projectId)
    }

    LaunchedEffect(step) {
        if (step is ProcessingStep.Done) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val current = step) {
            is ProcessingStep.Idle,
            is ProcessingStep.ExtractingAudio -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Extracting Audio",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pulling audio track from your video...",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            is ProcessingStep.Transcribing -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Transcribing",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI is generating captions with word timestamps...\nThis may take a moment.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            is ProcessingStep.Saving -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Saving Captions",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            is ProcessingStep.Done -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Captions Ready!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            is ProcessingStep.Error -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Processing Failed",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = current.message,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.startProcessing(projectId) }) {
                    Text("Retry")
                }
            }
        }
    }
}
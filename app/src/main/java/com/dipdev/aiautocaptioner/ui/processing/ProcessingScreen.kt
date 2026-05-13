package com.dipdev.aiautocaptioner.ui.processing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dipdev.aiautocaptioner.ui.components.LanguageDropdown

@Composable
fun ProcessingScreen(
    projectId: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val step by viewModel.step.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.prepareForProject(projectId)
    }

    LaunchedEffect(step) {
        if (step is ProcessingStep.Done) onDone()
    }

    // Cancel the active job when system back is pressed during processing
    BackHandler(enabled = step is ProcessingStep.ExtractingAudio || step is ProcessingStep.Transcribing) {
        viewModel.cancel()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val current = step) {

            // ── Ready: user selects language and taps Start ─────────────
            is ProcessingStep.Idle,
            is ProcessingStep.Ready -> {
                Text(
                    text = "Ready to Transcribe",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "The AI will analyse your video and generate\nword-level captions with timestamps.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Language selector
                LanguageDropdown(
                    selectedLanguage  = selectedLanguage,
                    onLanguageSelected = { viewModel.selectLanguage(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.startProcessing(projectId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Start Transcription", fontSize = 16.sp, maxLines = 1)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Cancel")
                }
            }

            // ── Extracting audio ─────────────────────────────────────────
            is ProcessingStep.ExtractingAudio -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("Extracting Audio", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pulling audio track from your video...",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { viewModel.cancel() },
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Cancel", maxLines = 1) }
            }

            // ── Transcribing ─────────────────────────────────────────────
            is ProcessingStep.Transcribing -> {
                val animatedProgress by animateFloatAsState(
                    targetValue = current.progress,
                    animationSpec = androidx.compose.animation.core.tween(400),
                    label = "transcription_progress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("Transcribing", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (current.progress > 0f)
                        "${(current.progress * 100).toInt()}% complete"
                    else
                        "AI is generating captions with word timestamps...\nThis may take a moment.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { viewModel.cancel() },
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Cancel", maxLines = 1) }
            }

            // ── Saving ───────────────────────────────────────────────────
            is ProcessingStep.Saving -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("Saving Captions", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }

            // ── Done ─────────────────────────────────────────────────────
            is ProcessingStep.Done -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Captions Ready!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            // ── Cancelled ────────────────────────────────────────────────
            is ProcessingStep.Cancelled -> {
                Text("Cancelled", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Transcription was stopped.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.startProcessing(projectId) },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Try Again", maxLines = 1) }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Go Back", maxLines = 1) }
            }

            // ── Error ────────────────────────────────────────────────────
            is ProcessingStep.Error -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Processing Failed", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = current.message,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.startProcessing(projectId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Retry", maxLines = 1) }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Go Back", maxLines = 1) }
            }
        }
    }
}

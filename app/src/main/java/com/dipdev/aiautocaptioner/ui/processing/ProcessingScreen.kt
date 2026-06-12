package com.dipdev.aiautocaptioner.ui.processing


import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.LanguageDropdown
import com.dipdev.aiautocaptioner.ui.components.ProcessingStateHeader
import com.dipdev.aiautocaptioner.ui.components.RoundedProgressBar
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    projectId: String,
    onNavigateToStyleEditor: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    onNavigateToVideoEditor:()->Unit,
    onNavigateToDeviceCheck: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val workingVideoPath by viewModel.workingVideoPath.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) {
        viewModel.prepareForProject(projectId)
    }



    // Cancel the active job when system back is pressed during processing
    BackHandler(enabled = step is ProcessingStep.ExtractingAudio || step is ProcessingStep.Transcribing) {
        viewModel.cancel()
    }

    Column(
        modifier = Modifier.fillMaxSize(),


    ) {
        Row(
            modifier = Modifier
        ){
            OutlinedButton(onCancel) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = "Back to Home")
            }
            OutlinedButton(onClick = onNavigateToVideoEditor) {
                Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Edit the video again")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val current = step) {

            // ── Ready: user selects language and taps Start ─────────────
            is ProcessingStep.Idle,
            is ProcessingStep.Ready -> {
                
                workingVideoPath?.let { path ->
                    VideoPlayerCard(
                        path = path,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f) // Takes up remaining space
                            .padding(bottom = 16.dp)
                    )
                }

                // Language selector
                LanguageDropdown(
                    selectedLanguage   = selectedLanguage,
                    onLanguageSelected = { viewModel.selectLanguage(it) },
                    isMultilingual     = activeModel?.isMultilingual ?: true
                )


                AppPrimaryButton(
                    onClick = { 
                        if (activeModel == null) {
                            onNavigateToDeviceCheck()
                        } else {
                            viewModel.startProcessing(projectId) 
                        }
                    }
                ) {
                    val btnText = "Generate Captions"
                    Text(btnText, fontSize = 16.sp, maxLines = 1)
                }
            }

            // ── Extracting audio ─────────────────────────────────────────
            is ProcessingStep.ExtractingAudio -> {
                RoundedProgressBar(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                ProcessingStateHeader(title = "Extracting Audio", subtitle = "Pulling audio track from your video...")
                Spacer(modifier = Modifier.height(32.dp))
                AppOutlinedButton(
                    onClick = { viewModel.cancel() }
                ) { Text("Cancel", maxLines = 1) }
            }

            // ── Loading Model ────────────────────────────────────────────
            is ProcessingStep.LoadingModel -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Loading AI Model", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Loading Whisper model into memory...",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            // ── Transcribing ─────────────────────────────────────────────
            is ProcessingStep.Transcribing -> {
                val rawProgress = current.progress
                val animatedProgress by animateFloatAsState(
                    targetValue    = rawProgress,
                    animationSpec  = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    label          = "transcriptionProgress"
                )
                RoundedProgressBar(modifier = Modifier.fillMaxWidth(), progress = animatedProgress)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Transcribing", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (current.progress > 0f) {
                        val percent = (current.progress * 100).toInt()
                        val etaPart = current.estimatedSecondsRemaining?.let { secs ->
                            if (secs >= 60) " · ~${secs / 60} min remaining"
                            else " · ~$secs sec remaining"
                        } ?: ""
                        "$percent%$etaPart"
                    } else {
                        "AI is generating captions with word timestamps...\nThis may take a moment."
                    },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                AppOutlinedButton(
                    onClick = { viewModel.cancel() }
                ) { Text("Cancel", maxLines = 1) }
            }

            // ── Saving ───────────────────────────────────────────────────
            is ProcessingStep.Saving -> {
                RoundedProgressBar(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                ProcessingStateHeader(title = "Saving Captions")
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

                var showBottomSheet by remember { mutableStateOf(true) }
                if (showBottomSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("What next?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { 
                                    showBottomSheet = false
                                    onNavigateToStyleEditor() 
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Choose a Style (Recommended)")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { 
                                    showBottomSheet = false
                                    onNavigateToCaptionEditor() 
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Review & Edit Captions")
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showBottomSheet = true }) {
                        Text("Continue")
                    }
                }
            }

            // ── Cancelling ───────────────────────────────────────────────
            is ProcessingStep.Cancelling -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                ProcessingStateHeader(title = "Cancelling", subtitle = "Stopping AI processing...")
            }

            // ── Cancelled ────────────────────────────────────────────────
            is ProcessingStep.Cancelled -> {
                ProcessingStateHeader(title = "Cancelled", subtitle = "Transcription was stopped.")
                Spacer(modifier = Modifier.height(24.dp))
                AppPrimaryButton(
                    onClick = { viewModel.startProcessing(projectId) }
                ) { Text("Try Again", maxLines = 1) }
                Spacer(modifier = Modifier.height(12.dp))
                AppOutlinedButton(
                    onClick = onCancel
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
                ProcessingStateHeader(title = "Processing Failed", subtitle = current.message)
                Spacer(modifier = Modifier.height(24.dp))
                AppPrimaryButton(
                    onClick = { viewModel.startProcessing(projectId) }
                ) { Text("Retry", maxLines = 1) }
                Spacer(modifier = Modifier.height(12.dp))
                AppOutlinedButton(
                    onClick = onCancel
                ) { Text("Go Back", maxLines = 1) }
            }
        }
    }
}
}

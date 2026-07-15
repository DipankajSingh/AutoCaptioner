package com.dipdev.aiautocaptioner.ui.processing

import com.skydoves.cloudy.cloudy
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.X
import compose.icons.feathericons.Edit2
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton
import com.dipdev.aiautocaptioner.ui.processing.components.CancelProcessDialog
import com.dipdev.aiautocaptioner.ui.processing.components.CancelledView
import com.dipdev.aiautocaptioner.ui.processing.components.CancellingView
import com.dipdev.aiautocaptioner.ui.processing.components.ErrorView
import com.dipdev.aiautocaptioner.ui.processing.components.ModelPickerCard
import com.dipdev.aiautocaptioner.ui.processing.components.SafetyCheckDialogs
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    projectId: String,
    forceModelPicker: Boolean = false,
    isRegenerating: Boolean = false,
    onNavigateToCaptionEditor: () -> Unit,
    onNavigateToVideoEditor: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val step = uiState.step
    val streamedSegments = uiState.streamedSegments
    val safetyCheck = uiState.safetyCheck

    LaunchedEffect(projectId, forceModelPicker, isRegenerating) {
        viewModel.setEvent(ProcessingUiEvent.PrepareForProject(projectId, forceModelPicker, isRegenerating))
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is ProcessingUiEffect.NavigateToVideoEditor -> onNavigateToVideoEditor()
                is ProcessingUiEffect.NavigateToCaptionEditor -> onNavigateToCaptionEditor()
            }
        }
    }

    val isProcessing = step is ProcessingStep.DownloadingModel ||
                       step is ProcessingStep.ExtractingAudio ||
                       step is ProcessingStep.LoadingModel ||
                       step is ProcessingStep.Transcribing ||
                       step is ProcessingStep.Saving

    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isProcessing) {
        showCancelDialog = true
    }

    if (showCancelDialog) {
        CancelProcessDialog(
            onDismiss = { showCancelDialog = false },
            onConfirm = {
                showCancelDialog = false
                viewModel.setEvent(ProcessingUiEvent.Cancel)
                onCancel()
            }
        )
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. Immersive Background ---
        val videoUri = uiState.workingVideoPath
        if (videoUri != null) {
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(videoUri)
                    .decoderFactory(coil3.video.VideoFrameDecoder.Factory())
                    .build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .cloudy(radius = 25)
            )
            // Darken overlay for better text readability
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
            )
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // --- 2. Main Content Stack ---
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (isProcessing) showCancelDialog = true else onCancel() },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(imageVector = FeatherIcons.X, contentDescription = "Back", tint = Color.White)
                }
                
                if (!isProcessing) {
                    IconButton(
                        onClick = onNavigateToVideoEditor,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(imageVector = FeatherIcons.Edit2, contentDescription = "Edit the video", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // --- 3. Dynamic Center Content ---
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.95f))
                        .togetherWith(fadeOut(tween(300)))
                },
                contentKey = { it::class.simpleName },
                label = "processing_step",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            ) { currentStep ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (currentStep) {
                        is ProcessingStep.SetupAI -> {
                            var selectedModelId by remember { mutableStateOf(currentStep.recommendedModelId) }
                            
                            Text(
                                text = "Choose AI Accuracy",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Downloaded once, runs fully offline.",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 32.dp)
                            )

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.heightIn(max = 300.dp)
                            ) {
                                items(currentStep.models) { model ->
                                    ModelPickerCard(
                                        model = model,
                                        isRecommended = model.id == currentStep.recommendedModelId,
                                        isSelected = model.id == selectedModelId,
                                        onClick = { selectedModelId = model.id }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            val selectedModel = currentStep.models.find { it.id == selectedModelId }
                            val isDownloaded = selectedModel?.isDownloaded == true

                            GradientPrimaryButton(
                                text = if (isDownloaded) "Generate Captions" else "Download & Generate",
                                onClick = { selectedModelId?.let { viewModel.setEvent(ProcessingUiEvent.DownloadAndProcess(it, projectId)) } },
                                enabled = selectedModelId != null,
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            )
                        }

                        is ProcessingStep.DownloadingModel -> {
                            com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation(progress = currentStep.progress / 100f, modifier = Modifier.size(120.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Downloading AI Model...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${currentStep.progress}%", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                        }

                        is ProcessingStep.ExtractingAudio -> {
                            com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation(progress = 0f, modifier = Modifier.size(120.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Preparing your video...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("This may take a moment", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                        }

                        is ProcessingStep.LoadingModel -> {
                            com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation(progress = 0.1f, modifier = Modifier.size(120.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Warming up the AI...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        is ProcessingStep.Transcribing -> {
                            // Fake progress interpolation
                            val rawProgress = currentStep.progress
                            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = rawProgress + 0.05f, // Slightly ahead
                                animationSpec = tween(durationMillis = 30000, easing = androidx.compose.animation.core.LinearOutSlowInEasing), // Slow 30s crawl
                                label = "transcriptionProgress"
                            )
                            
                            com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation(progress = animatedProgress, modifier = Modifier.size(120.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Listening & typing...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            if (currentStep.estimatedSecondsRemaining != null) {
                                val secs = currentStep.estimatedSecondsRemaining
                                val timeText = if (secs >= 60) "~${secs / 60}m left" else "~${secs}s left"
                                Text(timeText, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                            }
                        }

                        is ProcessingStep.Saving -> {
                            com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation(progress = 1f, modifier = Modifier.size(120.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Finalizing captions...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        is ProcessingStep.Cancelling -> {
                            CancellingView()
                        }
                        is ProcessingStep.Cancelled -> {
                            CancelledView(
                                onRetry = { viewModel.setEvent(ProcessingUiEvent.StartProcessing(projectId)) },
                                onGoBack = onCancel
                            )
                        }
                        is ProcessingStep.Error -> {
                            ErrorView(
                                message = currentStep.message,
                                onRetry = { viewModel.setEvent(ProcessingUiEvent.StartProcessing(projectId)) },
                                onGoBack = onCancel
                            )
                        }
                        else -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1.5f))
        }
    }

    SafetyCheckDialogs(
        safetyCheck = safetyCheck,
        onDismiss = { viewModel.setEvent(ProcessingUiEvent.ResetSafetyCheck) },
        onProceed = { modelId -> viewModel.setEvent(ProcessingUiEvent.ConfirmCellularDownload(modelId)) }
    )
}

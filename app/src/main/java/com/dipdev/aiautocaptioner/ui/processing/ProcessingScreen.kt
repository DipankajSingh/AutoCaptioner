package com.dipdev.aiautocaptioner.ui.processing

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
import com.dipdev.aiautocaptioner.ui.processing.components.DownloadingStateView
import com.dipdev.aiautocaptioner.ui.processing.components.ErrorView
import com.dipdev.aiautocaptioner.ui.processing.components.ExtractingAudioView
import com.dipdev.aiautocaptioner.ui.processing.components.LoadingModelView
import com.dipdev.aiautocaptioner.ui.processing.components.ModelPickerCard
import com.dipdev.aiautocaptioner.ui.processing.components.SafetyCheckDialogs
import com.dipdev.aiautocaptioner.ui.processing.components.SavingView
import com.dipdev.aiautocaptioner.ui.processing.components.TranscribingStateView

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    projectId: String,
    forceModelPicker: Boolean = false,
    onNavigateToStyleEditor: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    onNavigateToVideoEditor: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val step = uiState.step
    val streamedSegments = uiState.streamedSegments
    val safetyCheck = uiState.safetyCheck

    LaunchedEffect(projectId, forceModelPicker) {
        viewModel.setEvent(ProcessingUiEvent.PrepareForProject(projectId, forceModelPicker))
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect {
            onNavigateToStyleEditor()
        }
    }

    val isProcessing = step is ProcessingStep.DownloadingModel ||
                       step is ProcessingStep.ExtractingAudio ||
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

    Column(modifier = Modifier.fillMaxSize()) {
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
                modifier = Modifier.background(LocalAccentColor.current.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(imageVector = FeatherIcons.X, contentDescription = "Back to Home", tint = LocalAccentColor.current)
            }
            
            if (!isProcessing) {
                IconButton(
                    onClick = onNavigateToVideoEditor,
                    modifier = Modifier.background(LocalAccentColor.current.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(imageVector = FeatherIcons.Edit2, contentDescription = "Edit the video", tint = LocalAccentColor.current)
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(300)))
            },
            contentKey = { it::class.simpleName },
            label = "processing_step",
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) { currentStep ->
            when (currentStep) {
                // Idle / Loading — show nothing (splash screen holds)
                is ProcessingStep.Idle -> {}

                // SetupAI — model picker bottom sheet shown inline below
                is ProcessingStep.SetupAI,
                is ProcessingStep.Ready -> {
                    // Empty body; SetupAI sheet is shown as a bottom sheet overlay
                }
                is ProcessingStep.DownloadingModel -> {
                    DownloadingStateView(step = currentStep)
                }
                is ProcessingStep.ExtractingAudio -> {
                    ExtractingAudioView(onCancel = { viewModel.setEvent(ProcessingUiEvent.Cancel) })
                }
                is ProcessingStep.LoadingModel -> {
                    LoadingModelView()
                }
                is ProcessingStep.Transcribing -> {
                    TranscribingStateView(
                        step = currentStep,
                        streamedSegments = streamedSegments,
                        onCancel = { viewModel.setEvent(ProcessingUiEvent.Cancel) }
                    )
                }
                is ProcessingStep.Saving -> {
                    SavingView()
                }
                is ProcessingStep.Done -> {
                    // Navigation handled by LaunchedEffect collecting uiEffect
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
            }
        }
    }
    // end Column

    // SetupAI bottom sheet — shown when no model is downloaded yet
    if (step is ProcessingStep.SetupAI) {
        var selectedModelId by remember { mutableStateOf(step.recommendedModelId) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = onCancel,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.75f)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Choose your AI model",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Required for transcription. Downloaded once, runs offline.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(step.models) { model ->
                        ModelPickerCard(
                            model = model,
                            isRecommended = model.id == step.recommendedModelId,
                            isSelected = model.id == selectedModelId,
                            onClick = { selectedModelId = model.id }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val selectedModel = step.models.find { it.id == selectedModelId }
                val isDownloaded = selectedModel?.isDownloaded == true

                GradientPrimaryButton(
                    text = if (isDownloaded) "Generate Captions" else "Download & Generate",
                    onClick = { selectedModelId?.let { viewModel.setEvent(ProcessingUiEvent.DownloadAndProcess(it, projectId)) } },
                    enabled = selectedModelId != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )
            }
        }
    }

    SafetyCheckDialogs(
        safetyCheck = safetyCheck,
        onDismiss = { viewModel.setEvent(ProcessingUiEvent.ResetSafetyCheck) },
        onProceed = { modelId -> viewModel.setEvent(ProcessingUiEvent.ConfirmCellularDownload(modelId)) }
    )
}

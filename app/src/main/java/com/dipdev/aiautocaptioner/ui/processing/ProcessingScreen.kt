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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.ui.processing.components.*

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    projectId: String,
    onNavigateToStyleEditor: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    onNavigateToVideoEditor: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val step = uiState.step
    val selectedLanguage = uiState.selectedLanguage
    val translateToEnglish = uiState.translateToEnglish
    val activeModel = uiState.activeModel
    val workingVideoPath = uiState.workingVideoPath
    val streamedSegments = uiState.streamedSegments
    val safetyCheck = uiState.safetyCheck
    val segmentCount = uiState.segmentCount

    LaunchedEffect(projectId) {
        viewModel.setEvent(ProcessingUiEvent.PrepareForProject(projectId))
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

    CompositionLocalProvider(LocalAccentColor provides AccentCyan) {
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
                modifier = Modifier.background(AccentCyan.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = "Back to Home", tint = AccentCyan)
            }
            
            if (!isProcessing) {
                IconButton(
                    onClick = onNavigateToVideoEditor,
                    modifier = Modifier.background(AccentCyan.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Edit the video", tint = AccentCyan)
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
            contentKey = { 
                if (it is ProcessingStep.Idle || it is ProcessingStep.Ready || it is ProcessingStep.SetupAI) "ready" else it::class.simpleName 
            },
            label = "processing_step",
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) { currentStep ->
            when (currentStep) {
                is ProcessingStep.Idle,
                is ProcessingStep.Ready,
                is ProcessingStep.SetupAI -> {
                    ReadyStateView(
                        step = currentStep,
                        workingVideoPath = workingVideoPath,
                        selectedLanguage = selectedLanguage,
                        translateToEnglish = translateToEnglish,
                        activeModel = activeModel,
                        onLanguageSelected = { viewModel.setEvent(ProcessingUiEvent.SelectLanguage(it)) },
                        onToggleTranslation = { viewModel.setEvent(ProcessingUiEvent.ToggleTranslation(it)) },
                        onShowModelPicker = { viewModel.setEvent(ProcessingUiEvent.ShowModelPicker) },
                        onShowModelSetup = { viewModel.setEvent(ProcessingUiEvent.ShowModelSetup) },
                        onStartProcessing = { viewModel.setEvent(ProcessingUiEvent.StartProcessing(projectId)) },
                        onCancelModelSetup = { viewModel.setEvent(ProcessingUiEvent.CancelModelSetup) },
                        onDownloadAndProcess = { viewModel.setEvent(ProcessingUiEvent.DownloadAndProcess(it, projectId)) }
                    )
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
                    DoneStateView(
                        segmentCount = segmentCount,
                        onNavigateToStyleEditor = onNavigateToStyleEditor,
                        onNavigateToCaptionEditor = onNavigateToCaptionEditor,
                        onRegenerate = { viewModel.setEvent(ProcessingUiEvent.PrepareForProject(projectId)) }
                    )
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
    } // end CompositionLocalProvider

    SafetyCheckDialogs(
        safetyCheck = safetyCheck,
        onDismiss = { viewModel.setEvent(ProcessingUiEvent.ResetSafetyCheck) },
        onProceed = { modelId -> viewModel.setEvent(ProcessingUiEvent.ConfirmCellularDownload(modelId)) }
    )
}

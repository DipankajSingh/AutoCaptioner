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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val step by viewModel.step.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val workingVideoPath by viewModel.workingVideoPath.collectAsStateWithLifecycle()
    val streamedSegments by viewModel.streamedSegments.collectAsStateWithLifecycle()
    val safetyCheck by viewModel.safetyCheck.collectAsStateWithLifecycle()
    val segmentCount by viewModel.segmentCount.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) {
        viewModel.prepareForProject(projectId)
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToStyleEditor.collect {
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
                viewModel.cancel()
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
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = "Back to Home")
            }
            
            if (!isProcessing) {
                IconButton(
                    onClick = onNavigateToVideoEditor,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = "Edit the video")
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
                        activeModel = activeModel,
                        onLanguageSelected = { viewModel.selectLanguage(it) },
                        onShowModelPicker = { viewModel.showModelPicker() },
                        onShowModelSetup = { viewModel.showModelSetup() },
                        onStartProcessing = { viewModel.startProcessing(projectId) },
                        onCancelModelSetup = { viewModel.cancelModelSetup() },
                        onDownloadAndProcess = { viewModel.downloadAndProcess(it, projectId) }
                    )
                }
                is ProcessingStep.DownloadingModel -> {
                    DownloadingStateView(step = currentStep)
                }
                is ProcessingStep.ExtractingAudio -> {
                    ExtractingAudioView(onCancel = { viewModel.cancel() })
                }
                is ProcessingStep.LoadingModel -> {
                    LoadingModelView()
                }
                is ProcessingStep.Transcribing -> {
                    TranscribingStateView(
                        step = currentStep,
                        streamedSegments = streamedSegments,
                        onCancel = { viewModel.cancel() }
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
                        onRegenerate = { viewModel.prepareForProject(projectId) }
                    )
                }
                is ProcessingStep.Cancelling -> {
                    CancellingView()
                }
                is ProcessingStep.Cancelled -> {
                    CancelledView(
                        onRetry = { viewModel.startProcessing(projectId) },
                        onGoBack = onCancel
                    )
                }
                is ProcessingStep.Error -> {
                    ErrorView(
                        message = currentStep.message,
                        onRetry = { viewModel.startProcessing(projectId) },
                        onGoBack = onCancel
                    )
                }
            }
        }
    }

    SafetyCheckDialogs(
        safetyCheck = safetyCheck,
        onDismiss = { viewModel.resetSafetyCheck() },
        onProceed = { modelId -> viewModel.confirmCellularDownload(modelId) }
    )
}

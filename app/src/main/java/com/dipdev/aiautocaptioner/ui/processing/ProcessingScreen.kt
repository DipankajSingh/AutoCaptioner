package com.dipdev.aiautocaptioner.ui.processing


import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.AudioWaveformAnimation
import com.dipdev.aiautocaptioner.ui.components.LanguageDropdown
import com.dipdev.aiautocaptioner.ui.components.ProcessingStateHeader
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import com.dipdev.aiautocaptioner.ui.processing.components.ModelPickerCard

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

    LaunchedEffect(projectId) {
        viewModel.prepareForProject(projectId)
    }

    val isProcessing = step is ProcessingStep.DownloadingModel ||
                       step is ProcessingStep.ExtractingAudio ||
                       step is ProcessingStep.Transcribing ||
                       step is ProcessingStep.Saving

    var showCancelDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Cancel the active job when system back is pressed during processing
    BackHandler(enabled = isProcessing) {
        showCancelDialog = true
    }

    if (showCancelDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Process?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to stop? Your progress will be lost.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showCancelDialog = false
                    viewModel.cancel()
                    onCancel() // Exit back to home
                }) {
                    Text("Stop & Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Processing")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isProcessing) {
                        showCancelDialog = true
                    } else {
                        onCancel()
                    }
                },
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
                // Empty spacer to maintain the Arrangement.SpaceBetween layout
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Main content with animated transitions
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

                // ── Ready & Setup AI ─────────────────────────────────────
                is ProcessingStep.Idle,
                is ProcessingStep.Ready,
                is ProcessingStep.SetupAI -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        workingVideoPath?.let { path ->
                            com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                VideoPlayerCard(
                                    path = path,
                                    showControls = false,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LanguageDropdown(
                                    selectedLanguage = selectedLanguage,
                                    onLanguageSelected = { viewModel.selectLanguage(it) },
                                    isMultilingual = activeModel?.isMultilingual ?: true
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton(
                                    text = "Generate Captions",
                                    onClick = {
                                        if (activeModel == null) {
                                            viewModel.showModelSetup()
                                        } else {
                                            viewModel.startProcessing(projectId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                )
                            }
                        }
                    }

                    // Conditionally show the bottom sheet on top of the Ready UI
                    if (currentStep is ProcessingStep.SetupAI) {
                        var selectedModelId by remember { mutableStateOf(currentStep.recommendedModelId) }
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        
                        ModalBottomSheet(
                            onDismissRequest = {
                                viewModel.cancelModelSetup() // Go back to Ready without wiping state
                            },
                            sheetState = sheetState,
                            dragHandle = { BottomSheetDefaults.DragHandle() }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.7f) // Max 70% height
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 32.dp)
                            ) {
                                Text(
                                    text = "Setup AI Model",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = "Choose a model to power your captions. This is a one-time download.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(bottom = 20.dp)
                                )

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f, fill = false)
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

                                Spacer(modifier = Modifier.height(20.dp))

                                com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton(
                                    text = "Download & Generate",
                                    onClick = {
                                        selectedModelId?.let {
                                            viewModel.downloadAndProcess(it, projectId)
                                        }
                                    },
                                    enabled = selectedModelId != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                )
                            }
                        }
                    }
                }

                // ── Downloading Model ────────────────────────────────────
                is ProcessingStep.DownloadingModel -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = currentStep.progress / 100f,
                        animationSpec = tween(300),
                        label = "download_progress"
                    )

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Glowing download ring
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(120.dp),
                                strokeWidth = 6.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                color = MaterialTheme.colorScheme.primary,
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                text = "${currentStep.progress}%",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "Downloading ${currentStep.modelName}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (currentStep.totalBytes > 0) {
                            Text(
                                text = "${formatBytes(currentStep.downloadedBytes)} / ${formatBytes(
                                    currentStep.totalBytes)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "One-time download · Keep app open",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                // ── Extracting Audio ─────────────────────────────────────
                is ProcessingStep.ExtractingAudio -> {
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
                        AppOutlinedButton(
                            onClick = { viewModel.cancel() }
                        ) { Text("Cancel", maxLines = 1) }
                    }
                }

                // ── Loading Model ────────────────────────────────────────
                is ProcessingStep.LoadingModel -> {
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

                // ── Transcribing (with live subtitle preview) ────────────
                is ProcessingStep.Transcribing -> {
                    val rawProgress = currentStep.progress
                    val animatedProgress by animateFloatAsState(
                        targetValue = rawProgress,
                        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                        label = "transcriptionProgress"
                    )

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Video preview with live captions overlaid
                        workingVideoPath?.let { path ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(bottom = 16.dp)
                            ) {
                                // Implement the "seek-on-segment" live playback
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val player = remember(path) {
                                    androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                                        setMediaItem(androidx.media3.common.MediaItem.fromUri(path))
                                        volume = 0f // Play silently in background
                                        prepare()
                                    }
                                }
                                
                                DisposableEffect(player) {
                                    onDispose { player.release() }
                                }

                                val latestSegment = streamedSegments.lastOrNull()
                                
                                // Seek the video whenever a new segment arrives
                                LaunchedEffect(latestSegment) {
                                    if (latestSegment != null) {
                                        player.seekTo(latestSegment.startMs)
                                        player.play()
                                    }
                                }

                                VideoPlayerCard(
                                    player = player,
                                    showControls = false,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Live caption overlay
                                if (latestSegment != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(16.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = latestSegment.text,
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } ?: run {
                            // Fallback if no video path — show waveform
                            AudioWaveformAnimation(modifier = Modifier
                                .size(120.dp)
                                .padding(bottom = 16.dp))
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Progress ring + percentage
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(80.dp),
                                strokeWidth = 5.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                color = MaterialTheme.colorScheme.primary,
                                strokeCap = StrokeCap.Round
                            )
                            val percent = (rawProgress * 100).toInt()
                            Text(
                                text = "$percent%",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Transcribing",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (currentStep.estimatedSecondsRemaining != null) {
                            val secs = currentStep.estimatedSecondsRemaining
                            Text(
                                text = if (secs >= 60) "~${secs / 60} min remaining"
                                else "~$secs sec remaining",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        AppOutlinedButton(
                            onClick = { viewModel.cancel() }
                        ) { Text("Cancel", maxLines = 1) }
                    }
                }

                // ── Saving ───────────────────────────────────────────────
                is ProcessingStep.Saving -> {
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

                // ── Done (Lottie success animation) ──────────────────────
                is ProcessingStep.Done -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Lottie success checkmark
                        val composition by rememberLottieComposition(
                            LottieCompositionSpec.RawRes(R.raw.loading_checklist)
                        )
                        val lottieProgress by animateLottieCompositionAsState(
                            composition = composition,
                            iterations = 1
                        )
                        LottieAnimation(
                            composition = composition,
                            progress = { lottieProgress },
                            modifier = Modifier.size(120.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Captions Ready!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

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
                                    Text(
                                        text = "What next?",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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
                }

                // ── Cancelling ───────────────────────────────────────────
                is ProcessingStep.Cancelling -> {
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
                            title = "Cancelling",
                            subtitle = "Stopping AI processing..."
                        )
                    }
                }

                // ── Cancelled ────────────────────────────────────────────
                is ProcessingStep.Cancelled -> {
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
                        AppPrimaryButton(
                            onClick = { viewModel.startProcessing(projectId) }
                        ) { Text("Try Again", maxLines = 1) }
                        Spacer(modifier = Modifier.height(12.dp))
                        AppOutlinedButton(
                            onClick = onCancel
                        ) { Text("Go Back", maxLines = 1) }
                    }
                }

                // ── Error ────────────────────────────────────────────────
                is ProcessingStep.Error -> {
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
                            subtitle = currentStep.message
                        )
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

    // Safety check dialogs
    when (val check = safetyCheck) {
        is ModelSafetyCheck.StorageError -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetSafetyCheck() },
                title = { Text("Not enough storage") },
                text = { Text("You need at least ${check.requiredMb} MB of free space. Please free up storage and try again.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetSafetyCheck() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ModelSafetyCheck.CellularWarning -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetSafetyCheck() },
                title = { Text("Cellular Data Warning") },
                text = { Text("You're on mobile data. This download is around ${check.sizeMb} MB. Continue on mobile data?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.confirmCellularDownload(check.modelId)
                    }) {
                        Text("Continue Anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.resetSafetyCheck() }) {
                        Text("Wait for Wi-Fi")
                    }
                }
            )
        }
        is ModelSafetyCheck.Passed,
        is ModelSafetyCheck.Idle -> { /* no dialog */ }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// Utility
// ════════════════════════════════════════════════════════════════════════════════
private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "${"%.1f".format(bytes / 1_000_000_000f)} GB"
        bytes >= 1_000_000 -> "${"%.0f".format(bytes / 1_000_000f)} MB"
        bytes >= 1_000 -> "${"%.0f".format(bytes / 1_000f)} KB"
        else -> "$bytes B"
    }
}


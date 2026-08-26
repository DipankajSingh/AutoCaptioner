package com.dipdev.aiautocaptioner.ui.recorder.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.AppLinks
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.DialogType
import com.dipdev.aiautocaptioner.ui.components.UniversalDialog
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.recorder.components.StudioBottomArea
import com.dipdev.aiautocaptioner.ui.recorder.components.StudioRightSidebar
import com.dipdev.aiautocaptioner.ui.recorder.TeleprompterOverlay
import com.dipdev.aiautocaptioner.ui.recorder.components.TopHeaderBar
import com.dipdev.aiautocaptioner.ui.recorder.gesture.GestureDetectorHelper
import com.dipdev.aiautocaptioner.ui.recorder.model.RecorderEvent
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingMode
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingState
import com.dipdev.aiautocaptioner.ui.recorder.permission.PermissionGate
import com.dipdev.aiautocaptioner.ui.recorder.PermissionRequestScreen
import com.dipdev.aiautocaptioner.ui.recorder.ui.effects.FilterBadgeOverlay
import com.dipdev.aiautocaptioner.ui.recorder.ui.overlay.AnimatedCountdown
import com.dipdev.aiautocaptioner.ui.recorder.ui.overlay.AspectRatioMaskOverlay
import com.dipdev.aiautocaptioner.ui.recorder.ui.overlay.GridOverlay
import com.dipdev.aiautocaptioner.ui.recorder.ui.picker.BackgroundPickerSheet
import com.dipdev.aiautocaptioner.ui.recorder.ui.preview.CameraPreview
import com.dipdev.aiautocaptioner.ui.recorder.ui.preview.FacelessPreview
import com.dipdev.aiautocaptioner.ui.recorder.camera.ActiveRecording
import compose.icons.FeatherIcons
import compose.icons.feathericons.X
import compose.icons.feathericons.Zap
import compose.icons.feathericons.ZapOff
import androidx.media3.ui.AspectRatioFrameLayout

@Composable
fun RecorderRoute(
    onNavigateBack: () -> Unit,
    onVideoReady: (String) -> Unit,
    viewModel: RecorderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val permissions = listOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )

    PermissionGate(
        requiredPermissions = permissions,
        onAllGranted = {
            RecorderContent(
                onNavigateBack = onNavigateBack,
                onVideoReady = onVideoReady,
                viewModel = viewModel
            )
        },
        onBlocked = { cameraGranted, micGranted, cameraPermDenied, micPermDenied, onRequest, onOpenSettings ->
            PermissionRequestScreen(
                cameraGranted = cameraGranted,
                micGranted = micGranted,
                cameraPermanentlyDenied = cameraPermDenied,
                micPermanentlyDenied = micPermDenied,
                onRequestPermissions = onRequest,
                onOpenSettings = onOpenSettings,
                onDismiss = onNavigateBack,
                onPrivacyPolicy = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.PRIVACY_POLICY))
                    context.startActivity(intent)
                }
            )
        }
    )
}

@Composable
private fun RecorderContent(
    onNavigateBack: () -> Unit,
    onVideoReady: (String) -> Unit,
    viewModel: RecorderViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mode = uiState.recordingMode
    val recordingState = uiState.recordingState
    val aspectRatio = uiState.aspectRatio
    val showGrid = uiState.showGrid
    val showTeleprompter = uiState.showTeleprompter
    val teleprompterText = uiState.teleprompterText
    val isCountdownActive = uiState.isCountdownActive
    val countdownRemaining = uiState.countdownRemaining
    val isGestureDetectionEnabled = uiState.isGestureDetectionEnabled
    val selectedBackground = uiState.selectedBackground
    val finishedProjectId = uiState.finishedProjectId
    val finishedVideoFile = uiState.finishedVideoFile
    val elapsedSeconds = uiState.elapsedSeconds
    val countdownTimer = uiState.countdownTimer
    val showExitDialog = uiState.showExitDialog
    val recordingQuality = uiState.recordingQuality
    val isAudioMuted = uiState.isAudioMuted

    var showBgPicker by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }

    var flipRotation by remember { mutableFloatStateOf(0f) }
    val animateFlip: Float by animateFloatAsState(
        targetValue = flipRotation,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "FlipAnimation"
    )

    val engine = viewModel.cameraEngineRef
    var activeRecording by remember { mutableStateOf<ActiveRecording?>(null) }
    var cameraOpened by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.shouldStopCameraRecording) {
        if (uiState.shouldStopCameraRecording) {
            activeRecording?.stop()
            activeRecording = null
            viewModel.clearShouldStopCameraRecording()
        }
    }

    LaunchedEffect(uiState.shouldStartCameraRecording) {
        if (uiState.shouldStartCameraRecording) {
            viewModel.clearShouldStartCameraRecording()
            if (activeRecording != null) {
                return@LaunchedEffect
            }
            viewModel.prepareCameraRecordingFile { file ->
                val quality = uiState.recordingQuality
                val ratio = uiState.aspectRatio
                activeRecording = engine.startRecording(
                    file = file,
                    videoWidth = ratio.width,
                    videoHeight = ratio.height,
                    videoBitrate = quality.videoBitrate,
                    listener = object : com.dipdev.aiautocaptioner.ui.recorder.camera.RecordingListener {
                        override fun onRecordingStarted() {
                            viewModel.onCameraRecordingStarted()
                        }
                        override fun onRecordingFinished(file: java.io.File) {
                            viewModel.onCameraRecordingStopped()
                            activeRecording = null
                        }
                        override fun onRecordingError(error: Throwable) {
                            viewModel.onCameraRecordingError()
                            activeRecording = null
                        }
                    }
                )
            }
        }
    }

    LaunchedEffect(mode, recordingState) {
        if ((mode == RecordingMode.FACELESS || recordingState is RecordingState.Finalized) && flashEnabled) {
            flashEnabled = false
            engine.setTorch(false)
        }
    }

    val startRecordingAction: (forceCountdown: Int) -> Unit = { _ ->
        if (recordingState is RecordingState.Idle) {
            viewModel.setEvent(RecorderEvent.StartRecording)
        } else {
            if (mode == RecordingMode.FACELESS) {
                if (recordingState is RecordingState.Paused) {
                    viewModel.setEvent(RecorderEvent.ResumeRecording)
                }
                viewModel.setEvent(RecorderEvent.StopRecording)
            } else {
                if (recordingState is RecordingState.Paused) {
                    activeRecording?.resume()
                }
                activeRecording?.stop()
            }
        }
    }

    val currentStartAction by rememberUpdatedState(startRecordingAction)
    val currentRecordingState by rememberUpdatedState(recordingState)
    val currentIsCountdownActive by rememberUpdatedState(isCountdownActive)

    val shouldBindCamera = mode == RecordingMode.CAMERA && recordingState !is RecordingState.Finalized
    LaunchedEffect(shouldBindCamera, mode) {
        if (shouldBindCamera) {
            if (!cameraOpened) {
                engine.setAspectAndPreview(0, aspectRatio.width, aspectRatio.height)
                engine.open()
                cameraOpened = true
            }
        } else if (!shouldBindCamera) {
            engine.close()
            cameraOpened = false
        }
    }

    LaunchedEffect(aspectRatio) {
        if (cameraOpened) {
            engine.setAspectAndPreview(0, aspectRatio.width, aspectRatio.height)
        }
    }

    val gestureHelper = remember { mutableStateOf<GestureDetectorHelper?>(null) }
    val gestureListener = remember {
        object : GestureDetectorHelper.GestureListener {
            override fun onPalmDetected() {
                if (currentRecordingState is RecordingState.Idle && !currentIsCountdownActive) {
                    currentStartAction(3)
                }
            }
            override fun onError(error: String) {}
        }
    }

    LaunchedEffect(isGestureDetectionEnabled, mode) {
        if (isGestureDetectionEnabled && mode == RecordingMode.CAMERA) {
            val helper = GestureDetectorHelper(context, viewModel.crashReporter, gestureListener)
            gestureHelper.value = helper
            engine.setFrameAnalyzer(helper)
        } else {
            engine.setFrameAnalyzer(null)
            gestureHelper.value?.close()
            gestureHelper.value = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            engine.setFrameAnalyzer(null)
            gestureHelper.value?.close()
            gestureHelper.value = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val state = uiState.recordingState
                val m = uiState.recordingMode
                if (state is RecordingState.Recording || state is RecordingState.Paused) {
                    if (m == RecordingMode.FACELESS) {
                        viewModel.setEvent(RecorderEvent.StopRecording)
                    } else {
                        if (state is RecordingState.Paused) {
                            activeRecording?.resume()
                        }
                        activeRecording?.stop()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (recordingState is RecordingState.Finalized && finishedVideoFile != null && finishedVideoFile.exists()) {
            val targetRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(targetRatio)
                ) {
                    VideoPlayerCard(
                        path = finishedVideoFile.absolutePath,
                        modifier = Modifier.fillMaxSize(),
                        loop = true,
                        autoPlay = true,
                        showControls = false,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    )
                }
            }
        } else {
            if (mode == RecordingMode.CAMERA) {
                CameraPreview(textureView = engine.textureView, modifier = Modifier.fillMaxSize())
            } else {
                AnimatedVisibility(
                    visible = mode == RecordingMode.FACELESS,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(250)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    FacelessPreview(selectedBackground = selectedBackground, modifier = Modifier.fillMaxSize())
                }
            }
        }

        if (recordingState !is RecordingState.Finalized) {
            AspectRatioMaskOverlay(aspectRatio = aspectRatio)
        }
        if (mode == RecordingMode.CAMERA && showGrid) {
            GridOverlay(aspectRatio = aspectRatio)
        }

        if (showTeleprompter) {
            TeleprompterOverlay(
                text = teleprompterText,
                onTextChanged = { viewModel.setEvent(RecorderEvent.UpdateTeleprompterText(it)) },
                onDismiss = { viewModel.setEvent(RecorderEvent.ToggleTeleprompter) }
            )
        }

        if (isCountdownActive) {
            AnimatedCountdown(value = countdownRemaining)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    when (recordingState) {
                        is RecordingState.Idle, is RecordingState.Finalized -> onNavigateBack()
                        else -> viewModel.setEvent(RecorderEvent.RequestExitRecording)
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(FeatherIcons.X, contentDescription = "Close", tint = Color.White, modifier = Modifier.scale(1.25f))
            }

            if (recordingState !is RecordingState.Finalized && mode != RecordingMode.FACELESS) {
                IconButton(
                    onClick = {
                        flashEnabled = !flashEnabled
                        engine.setTorch(flashEnabled)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (flashEnabled) FeatherIcons.Zap else FeatherIcons.ZapOff,
                        contentDescription = "Flash",
                        tint = Color.White,
                        modifier = Modifier.scale(1.25f)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            if (recordingState is RecordingState.Idle) {
                TopHeaderBar(
                    aspectRatio = aspectRatio,
                    recordingQuality = recordingQuality,
                    onAspectRatioClick = { viewModel.setEvent(RecorderEvent.CycleAspectRatio(aspectRatio)) },
                    onQualityClick = { viewModel.setEvent(RecorderEvent.CycleRecordingQuality(recordingQuality)) }
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        FilterBadgeOverlay(
            activeFilter = uiState.activeFilter,
            modifier = Modifier.align(Alignment.Center).padding(bottom = 60.dp)
        )

        if (recordingState is RecordingState.Idle) {
            StudioRightSidebar(
                mode = mode,
                uiState = uiState,
                isGestureDetectionEnabled = isGestureDetectionEnabled,
                countdownTimer = countdownTimer,
                showGrid = showGrid,
                showTeleprompter = showTeleprompter,
                onToggleGrid = { viewModel.setEvent(RecorderEvent.ToggleGrid) },
                onToggleTeleprompter = { viewModel.setEvent(RecorderEvent.ToggleTeleprompter) },
                onToggleSmoothness = { viewModel.setEvent(RecorderEvent.ToggleSmoothnessSlider) },
                onToggleGesture = { viewModel.setEvent(RecorderEvent.ToggleGestureDetection) },
                onOpenCanvasPicker = { showBgPicker = true },
                onCycleTimer = {
                    val next = when (countdownTimer) { 3 -> 10; else -> 3 }
                    viewModel.setEvent(RecorderEvent.SetCountdownTimer(next))
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
            )
        }

        StudioBottomArea(
            uiState = uiState,
            recordingState = recordingState,
            mode = mode,
            isPermissionBlocked = false,
            elapsedSeconds = elapsedSeconds,
            animateFlip = animateFlip,
            onFilterSelected = { filter -> viewModel.setEvent(RecorderEvent.SelectFilter(filter)) },
            onSmoothnessChanged = { intensity -> viewModel.setEvent(RecorderEvent.UpdateSmoothness(intensity)) },
            onDismissSubControls = { viewModel.setEvent(RecorderEvent.DismissSubControls) },
            onModeSelected = { m ->
                viewModel.setEvent(RecorderEvent.SetRecordingMode(
                    if (m == "CAMERA") RecordingMode.CAMERA else RecordingMode.FACELESS
                ))
            },
            onStartRecording = { startRecordingAction(0) },
            onFlipCamera = {
                flipRotation += 180f
                viewModel.setEvent(RecorderEvent.FlipCamera)
            },
            onPauseRecording = { viewModel.setEvent(RecorderEvent.PauseRecording) },
            onResumeRecording = { viewModel.setEvent(RecorderEvent.ResumeRecording) },
            onStopRecording = { viewModel.setEvent(RecorderEvent.StopRecording) },
            onRetake = { viewModel.setEvent(RecorderEvent.ResetState) },
            onEdit = { finishedProjectId?.let { pId -> onVideoReady(pId) } },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showBgPicker) {
        BackgroundPickerSheet(
            currentBackground = selectedBackground,
            onDismissRequest = { showBgPicker = false },
            onBackgroundSelected = { bg -> viewModel.setEvent(RecorderEvent.SetSelectedBackground(bg)) }
        )
    }

    if (showExitDialog) {
        UniversalDialog(
            type = DialogType.WARNING,
            title = stringResource(R.string.recorder_exit_title),
            body = stringResource(R.string.recorder_exit_message),
            confirmText = stringResource(R.string.recorder_save_and_exit),
            onConfirm = { viewModel.setEvent(RecorderEvent.SaveAndExit) },
            dismissText = stringResource(R.string.recorder_discard),
            onDismiss = { viewModel.setEvent(RecorderEvent.DiscardRecording); onNavigateBack() },
            onDismissRequest = { viewModel.setEvent(RecorderEvent.DismissExitDialog) }
        )
    }
}

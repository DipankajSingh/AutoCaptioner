package com.dipdev.aiautocaptioner.ui.recorder

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.AppLinks
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.recorder.components.AspectRatioButton
import com.dipdev.aiautocaptioner.ui.recorder.components.ExitRecordingDialog
import com.dipdev.aiautocaptioner.ui.recorder.components.PauseResumeControls
import com.dipdev.aiautocaptioner.ui.recorder.components.QualityButton
import com.dipdev.aiautocaptioner.ui.recorder.components.QuickShareBar
import com.dipdev.aiautocaptioner.ui.recorder.components.SegmentBadge
import com.dipdev.aiautocaptioner.ui.recorder.components.StorageIndicator
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import compose.icons.FeatherIcons
import compose.icons.feathericons.Clock
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Grid
import compose.icons.feathericons.Image
import compose.icons.feathericons.RefreshCcw
import compose.icons.feathericons.X
import compose.icons.feathericons.Zap
import compose.icons.feathericons.ZapOff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

@Composable
fun SmartRecorderScreen(
    onNavigateBack: () -> Unit,
    onVideoReady: (String) -> Unit,
    viewModel: SmartRecorderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var cameraPermanentlyDenied by remember { mutableStateOf(false) }
    var micPermanentlyDenied by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (!granted) {
            val activity = context as? android.app.Activity
            cameraPermanentlyDenied = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.CAMERA)
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (!granted) {
            val activity = context as? android.app.Activity
            micPermanentlyDenied = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.RECORD_AUDIO)
        }
    }

    SmartRecorderContent(
        onNavigateBack = onNavigateBack,
        onVideoReady = onVideoReady,
        viewModel = viewModel,
        cameraGranted = cameraGranted,
        micGranted = micGranted,
        cameraPermanentlyDenied = cameraPermanentlyDenied,
        micPermanentlyDenied = micPermanentlyDenied,
        onRequestCamera = { cameraLauncher.launch(android.Manifest.permission.CAMERA) },
        onRequestMic = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
        onOpenSettings = {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            )
            context.startActivity(intent)
        }
    )
}

@SuppressLint("MissingPermission", "DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartRecorderContent(
    onNavigateBack: () -> Unit,
    onVideoReady: (String) -> Unit,
    viewModel: SmartRecorderViewModel,
    cameraGranted: Boolean,
    micGranted: Boolean,
    cameraPermanentlyDenied: Boolean,
    micPermanentlyDenied: Boolean,
    onRequestCamera: () -> Unit,
    onRequestMic: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mode = uiState.recordingMode
    val recordingState = uiState.recordingState
    val elapsedSeconds = uiState.elapsedSeconds
    val selectedBackground = uiState.selectedBackground
    val finishedProjectId = uiState.finishedProjectId
    val isAudioMuted = uiState.isAudioMuted
    val showGrid = uiState.showGrid
    val countdownTimer = uiState.countdownTimer
    val showTeleprompter = uiState.showTeleprompter
    val teleprompterText = uiState.teleprompterText
    val audioAmplitude = uiState.audioAmplitude
    val isCountdownActive = uiState.isCountdownActive
    val countdownRemaining = uiState.countdownRemaining
    val isGestureDetectionEnabled = uiState.isGestureDetectionEnabled
    val showRecorderOnboarding = uiState.showRecorderOnboarding
    val aspectRatio = uiState.aspectRatio
    val recordingQuality = uiState.recordingQuality
    val showExitDialog = uiState.showExitDialog
    val segments = uiState.segments
    val currentSegmentStartMs = uiState.currentSegmentStartMs

    var showBgPicker by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }

    var activeRecording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }

    val hasRequiredPermission = when (mode) {
        RecordingMode.CAMERA -> cameraGranted
        RecordingMode.FACELESS -> micGranted
    }
    val needsCameraForMode = mode == RecordingMode.CAMERA && !cameraGranted
    val needsMicForFaceless = mode == RecordingMode.FACELESS && !micGranted
    val isPermissionBlocked = needsCameraForMode || needsMicForFaceless

    val startRecordingAction: () -> Unit = {
        if (recordingState == RecordingState.IDLE) {
            when (mode) {
                RecordingMode.FACELESS if !micGranted -> onRequestMic()
                RecordingMode.CAMERA if !cameraGranted -> onRequestCamera()
                RecordingMode.CAMERA if !isAudioMuted && !micGranted -> onRequestMic()
                else -> {
                    viewModel.requestStartRecording {
                        viewModel.prepareCameraRecordingFile { file ->
                            val outputOptions = FileOutputOptions.Builder(file).build()
                            val executor = ContextCompat.getMainExecutor(context)

                            val listener = Consumer<VideoRecordEvent> { event ->
                                if (event is VideoRecordEvent.Start) {
                                    viewModel.onCameraRecordingStarted()
                                } else if (event is VideoRecordEvent.Finalize) {
                                    if (!event.hasError()) {
                                        viewModel.onCameraRecordingStopped()
                                    } else {
                                        viewModel.onCameraRecordingError()
                                    }
                                    activeRecording = null
                                }
                            }

                            activeRecording = if (isAudioMuted) {
                                cameraController.startRecording(
                                    outputOptions,
                                    AudioConfig.AUDIO_DISABLED,
                                    executor,
                                    listener
                                )
                            } else {
                                cameraController.startRecording(
                                    outputOptions,
                                    AudioConfig.create(true),
                                    executor,
                                    listener
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (mode == RecordingMode.FACELESS) {
                if (recordingState == RecordingState.PAUSED) {
                    viewModel.resumeRecording()
                    Thread.sleep(50)
                }
                viewModel.stopFacelessRecording()
            } else {
                if (recordingState == RecordingState.PAUSED) {
                    activeRecording?.resume()
                    Thread.sleep(50)
                }
                activeRecording?.stop()
            }
        }
    }

    val currentStartAction by rememberUpdatedState(startRecordingAction)
    val currentRecordingState by rememberUpdatedState(recordingState)
    val currentIsCountdownActive by rememberUpdatedState(isCountdownActive)

    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val gestureListener = remember {
        object : GestureDetectorHelper.GestureListener {
            override fun onPalmDetected() {
                mainExecutor.execute {
                    if (currentRecordingState == RecordingState.IDLE && !currentIsCountdownActive) {
                        currentStartAction()
                    }
                }
            }
            override fun onError(error: String) {}
        }
    }

    val backgroundExecutor = remember { Dispatchers.Default.asExecutor() }
    var gestureHelper by remember { mutableStateOf<GestureDetectorHelper?>(null) }

    LaunchedEffect(isGestureDetectionEnabled, mode, cameraController) {
        if (isGestureDetectionEnabled && mode == RecordingMode.CAMERA) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val helper = GestureDetectorHelper(context, gestureListener)
                gestureHelper = helper
                cameraController.setImageAnalysisAnalyzer(backgroundExecutor, helper)
            }
        } else {
            cameraController.clearImageAnalysisAnalyzer()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                gestureHelper?.clearGestureRecognizer()
                gestureHelper = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            val helperToClean = gestureHelper
            backgroundExecutor.execute {
                helperToClean?.clearGestureRecognizer()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
                    if (mode == RecordingMode.FACELESS) {
                        viewModel.stopFacelessRecording()
                    } else {
                        if (recordingState == RecordingState.PAUSED) {
                            activeRecording?.resume()
                            Thread.sleep(50)
                        }
                        activeRecording?.stop()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Navigation is handled by QuickShareBar's Edit button, not auto-navigation

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- 1. Background / Preview Area ---
        if (mode == RecordingMode.CAMERA) {
            if (cameraGranted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            controller = cameraController
                            cameraController.bindToLifecycle(lifecycleOwner)
                        }
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        } else {
            SmartRecorderFacelessPreview(
                selectedBackground = selectedBackground,
                micGranted = micGranted,
                isRecording = recordingState == RecordingState.RECORDING,
                onRequestMic = onRequestMic,
                onOpenSettings = onOpenSettings,
                onTransformUpdate = { scale, offsetX, offsetY ->
                    viewModel.updateImageTransform(scale, offsetX, offsetY)
                }
            )
        }

        // --- 2. Overlays ---
        if (mode == RecordingMode.CAMERA && showGrid) {
            GridOverlay()
        }

        if (showTeleprompter) {
            if (mode == RecordingMode.FACELESS) {
                FacelessTeleprompterOverlay(
                    text = teleprompterText,
                    onTextChanged = { viewModel.updateTeleprompterText(it) },
                    onDismiss = { viewModel.toggleTeleprompter() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                TeleprompterOverlay(
                    text = teleprompterText,
                    onTextChanged = { viewModel.updateTeleprompterText(it) },
                    onDismiss = { viewModel.toggleTeleprompter() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isCountdownActive) {
            AnimatedCountdown(value = countdownRemaining)
        }

        // --- 3. Recording / Paused Indicators ---
        if (recordingState == RecordingState.RECORDING) {
            RecordingIndicator()
        }
        if (recordingState == RecordingState.PAUSED) {
            PausedIndicator()
        }

        // Close button
        IconButton(
            onClick = {
                if (recordingState == RecordingState.IDLE) {
                    onNavigateBack()
                } else if (recordingState == RecordingState.DONE) {
                    viewModel.resetState()
                    onNavigateBack()
                } else {
                    viewModel.requestExitRecording()
                }
            },
            modifier = Modifier.padding(top = 48.dp, start = 16.dp).align(Alignment.TopStart)
        ) {
            Icon(FeatherIcons.X, contentDescription = "Close", tint = Color.White)
        }

        // Top Center: Timer
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(
                                if (recordingState == RecordingState.PAUSED) Color.White
                                else AccentRose
                            )
                    )
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                }
            }
        }

        // Storage indicator (top center, idle state)
        if (recordingState == RecordingState.IDLE && !isPermissionBlocked) {
            StorageIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
            )
        }

        // Segment badge (top center, recording/paused)
        if (segments.isNotEmpty() && (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED)) {
            val currentDuration = if (recordingState == RecordingState.RECORDING) {
                System.currentTimeMillis() - currentSegmentStartMs
            } else 0L
            val totalDuration = segments.sumOf { it.durationMs } + currentDuration
            SegmentBadge(
                segmentCount = segments.size + if (recordingState == RecordingState.RECORDING) 1 else 0,
                currentSegmentDurationMs = totalDuration,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) 96.dp else 56.dp)
            )
        }

        // Advanced controls — only visible when permission is granted
        if (!isPermissionBlocked) {
            // Left Sidebar
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SidebarButton(
                    icon = FeatherIcons.FileText,
                    text = stringResource(R.string.recorder_script),
                    isActive = showTeleprompter,
                    onClick = { viewModel.toggleTeleprompter() }
                )
                if (mode == RecordingMode.FACELESS && recordingState == RecordingState.IDLE) {
                    SidebarButton(
                        icon = FeatherIcons.Image,
                        text = stringResource(R.string.recorder_canvas),
                        isActive = false,
                        onClick = { showBgPicker = true }
                    )
                }
                if (recordingState == RecordingState.IDLE) {
                    AspectRatioButton(
                        currentRatio = aspectRatio,
                        onClick = { viewModel.cycleAspectRatio() }
                    )
                }
            }

            // Right Sidebar
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (mode == RecordingMode.CAMERA && recordingState == RecordingState.IDLE) {
                    SidebarButton(
                        icon = FeatherIcons.RefreshCcw,
                        text = stringResource(R.string.recorder_flip),
                        onClick = {
                            val current = cameraController.cameraSelector
                            cameraController.cameraSelector = if (current == CameraSelector.DEFAULT_BACK_CAMERA) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }
                        }
                    )
                    SidebarButton(
                        icon = if (flashEnabled) FeatherIcons.Zap else FeatherIcons.ZapOff,
                        text = stringResource(R.string.recorder_flash),
                        isActive = flashEnabled,
                        onClick = {
                            flashEnabled = !flashEnabled
                            cameraController.enableTorch(flashEnabled)
                        }
                    )
                    SidebarButton(
                        icon = FeatherIcons.Grid,
                        text = stringResource(R.string.recorder_grid),
                        isActive = showGrid,
                        onClick = { viewModel.toggleGrid() }
                    )
                    SidebarButton(
                        icon = Icons.Rounded.PanTool,
                        text = stringResource(R.string.recorder_palm),
                        isActive = isGestureDetectionEnabled,
                        onClick = { viewModel.toggleGestureDetection() }
                    )
                }
                if (recordingState == RecordingState.IDLE) {
                    QualityButton(
                        currentQuality = recordingQuality,
                        onClick = { viewModel.cycleRecordingQuality() }
                    )
                    val timerText = if (countdownTimer == 0) stringResource(R.string.recorder_timer) else "${countdownTimer}s"
                    SidebarButton(
                        icon = FeatherIcons.Clock,
                        text = timerText,
                        isActive = countdownTimer > 0,
                        onClick = {
                            val next = when (countdownTimer) {
                                0 -> 3
                                3 -> 10
                                else -> 0
                            }
                            viewModel.setCountdownTimer(next)
                        }
                    )
                }
            }
        }

        // Bottom Area
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode selector — only visible when idle
            if (recordingState == RecordingState.IDLE) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth().padding(horizontal = 32.dp)) {
                    SegmentedButton(
                        selected = mode == RecordingMode.CAMERA,
                        onClick = { viewModel.setRecordingMode(RecordingMode.CAMERA) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {}
                    ) {
                        Text(stringResource(R.string.recorder_mode_camera), fontSize = 14.sp, maxLines = 1)
                    }
                    SegmentedButton(
                        selected = mode == RecordingMode.FACELESS,
                        onClick = { viewModel.setRecordingMode(RecordingMode.FACELESS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {}
                    ) {
                        Text(stringResource(R.string.recorder_mode_faceless), fontSize = 14.sp, maxLines = 1)
                    }
                }
            }

            // Inline permission card
            if (isPermissionBlocked && recordingState == RecordingState.IDLE) {
                val permIcon = if (needsCameraForMode) Icons.Rounded.Videocam else Icons.Rounded.Mic
                val permMessage = if (needsCameraForMode) {
                    stringResource(R.string.recorder_permission_camera)
                } else {
                    stringResource(R.string.recorder_permission_microphone)
                }
                PermissionRequestCard(
                    icon = permIcon,
                    message = permMessage,
                    permanentlyDenied = if (needsCameraForMode) cameraPermanentlyDenied else micPermanentlyDenied,
                    onRequest = if (needsCameraForMode) onRequestCamera else onRequestMic,
                    onOpenSettings = onOpenSettings,
                    onPrivacyPolicy = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            AppLinks.PRIVACY_POLICY.toUri()
                        )
                        context.startActivity(intent)
                    }
                )
            }

            // Audio visualizer for faceless recording
            if (mode == RecordingMode.FACELESS && recordingState == RecordingState.RECORDING) {
                Box(modifier = Modifier.padding(bottom = 24.dp).height(32.dp).width(100.dp)) {
                    AudioVisualizerOverlay(amplitude = audioAmplitude)
                }
            }

            // Recording controls
            when (recordingState) {
                RecordingState.IDLE -> {
                    if (!isPermissionBlocked) {
                        RecordButton(
                            isRecording = false,
                            onClick = startRecordingAction
                        )
                    }
                }
                RecordingState.RECORDING -> {
                    if (mode == RecordingMode.CAMERA) {
                        RecordButton(
                            isRecording = true,
                            onClick = {
                                activeRecording?.stop()
                            }
                        )
                    } else {
                        PauseResumeControls(
                            isPaused = false,
                            onPause = { viewModel.pauseRecording() },
                            onResume = { viewModel.resumeRecording() },
                            onStop = { viewModel.stopFacelessRecording() }
                        )
                    }
                }
                RecordingState.PAUSED -> {
                    if (mode == RecordingMode.CAMERA) {
                        PauseResumeControls(
                            isPaused = true,
                            onPause = {},
                            onResume = { activeRecording?.resume(); viewModel.resumeRecording() },
                            onStop = {
                                activeRecording?.resume()
                                Thread.sleep(50)
                                activeRecording?.stop()
                            }
                        )
                    } else {
                        PauseResumeControls(
                            isPaused = true,
                            onPause = {},
                            onResume = { viewModel.resumeRecording() },
                            onStop = {
                                viewModel.resumeRecording()
                                Thread.sleep(50)
                                viewModel.stopFacelessRecording()
                            }
                        )
                    }
                }
                RecordingState.DONE -> {
                    QuickShareBar(
                        onRetake = {
                            viewModel.resetState()
                        },
                        onEdit = {
                            finishedProjectId?.let { pId ->
                                viewModel.resetState()
                                onVideoReady(pId)
                            }
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }

    // Recorder onboarding sheet
    if (showRecorderOnboarding) {
        RecorderOnboardingSheet(onDismiss = { viewModel.dismissRecorderOnboarding() })
    }

    if (showBgPicker) {
        BackgroundPickerSheet(
            onDismissRequest = { showBgPicker = false },
            onBackgroundSelected = { viewModel.setSelectedBackground(it) }
        )
    }

    // Exit recording dialog
    if (showExitDialog) {
        ExitRecordingDialog(
            onSaveAndExit = { viewModel.saveAndExit() },
            onDiscard = { viewModel.discardRecording() },
            onDismiss = { viewModel.dismissExitDialog() }
        )
    }
}

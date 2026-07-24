package com.dipdev.aiautocaptioner.ui.recorder

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.X
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Image
import compose.icons.feathericons.RefreshCcw
import compose.icons.feathericons.Zap
import compose.icons.feathericons.ZapOff
import compose.icons.feathericons.Grid
import compose.icons.feathericons.Clock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.Executor

@Composable
fun SmartRecorderScreen(
    onNavigateBack: () -> Unit,
    onVideoReady: (String) -> Unit,
    viewModel: SmartRecorderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
    }

    SmartRecorderContent(
        onNavigateBack = onNavigateBack,
        onVideoReady = onVideoReady,
        viewModel = viewModel,
        cameraGranted = cameraGranted,
        micGranted = micGranted,
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

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartRecorderContent(
    onNavigateBack: () -> Unit,
    onVideoReady: (String) -> Unit,
    viewModel: SmartRecorderViewModel,
    cameraGranted: Boolean,
    micGranted: Boolean,
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
    val needsMicForCameraRecording = mode == RecordingMode.CAMERA && !isAudioMuted && !micGranted
    val isPermissionBlocked = needsCameraForMode || needsMicForFaceless

    val startRecordingAction: () -> Unit = {
        if (recordingState == RecordingState.IDLE) {
            if (mode == RecordingMode.FACELESS && !micGranted) {
                onRequestMic()
            } else if (mode == RecordingMode.CAMERA && !cameraGranted) {
                onRequestCamera()
            } else if (mode == RecordingMode.CAMERA && !isAudioMuted && !micGranted) {
                onRequestMic()
            } else {
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
                            cameraController.startRecording(outputOptions, AudioConfig.AUDIO_DISABLED, executor, listener)
                        } else {
                            cameraController.startRecording(outputOptions, AudioConfig.create(true), executor, listener)
                        }
                    }
                }
            }
        } else {
            if (mode == RecordingMode.FACELESS) {
                viewModel.stopFacelessRecording()
            } else {
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
                if (recordingState == RecordingState.RECORDING) {
                    if (mode == RecordingMode.FACELESS) {
                        viewModel.stopFacelessRecording()
                    } else {
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

    LaunchedEffect(finishedProjectId) {
        finishedProjectId?.let { pId ->
            viewModel.resetState()
            onVideoReady(pId)
        }
    }

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

        // --- 2. Overlays (Grid, Visualizer, Teleprompter, Countdown) ---
        if (mode == RecordingMode.CAMERA && showGrid) {
            GridOverlay()
        }

        if (showTeleprompter) {
            if (mode == RecordingMode.FACELESS) {
                FacelessTeleprompterOverlay(
                    text = teleprompterText,
                    onTextChanged = { viewModel.updateTeleprompterText(it) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                TeleprompterOverlay(
                    text = teleprompterText,
                    onTextChanged = { viewModel.updateTeleprompterText(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isCountdownActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdownRemaining.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp),
                    color = Color.White
                )
            }
        }

        // --- 3. UI Controls ---
        // Close button — always visible
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.padding(top = 48.dp, start = 16.dp).align(Alignment.TopStart)
        ) {
            Icon(FeatherIcons.X, contentDescription = "Close", tint = Color.White)
        }

        // Top Center: Timer (only during recording)
        if (recordingState == RecordingState.RECORDING) {
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
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccentRose))
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                }
            }
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

        // Bottom Area — always visible: mode selector + permission card or record button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode selector — always visible
            if (recordingState == RecordingState.IDLE) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(bottom = 24.dp).width(300.dp)) {
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

            // Inline permission card when permission is blocked
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
                    onRequest = if (needsCameraForMode) onRequestCamera else onRequestMic,
                    onOpenSettings = onOpenSettings
                )
            }

            // Audio visualizer for faceless recording
            if (mode == RecordingMode.FACELESS && recordingState == RecordingState.RECORDING) {
                Box(modifier = Modifier.padding(bottom = 24.dp).height(32.dp).width(100.dp)) {
                    AudioVisualizerOverlay(amplitude = audioAmplitude)
                }
            }

            // Record button — visible when permission is granted (or during active recording)
            if (!isPermissionBlocked || recordingState == RecordingState.RECORDING) {
                RecordButton(
                    isRecording = recordingState == RecordingState.RECORDING,
                    onClick = startRecordingAction
                )
            }
        }
    }

    // Recorder onboarding sheet — shown once for first-time users
    if (showRecorderOnboarding) {
        RecorderOnboardingSheet(onDismiss = { viewModel.dismissRecorderOnboarding() })
    }

    if (showBgPicker) {
        BackgroundPickerSheet(
            onDismissRequest = { showBgPicker = false },
            onBackgroundSelected = { viewModel.setSelectedBackground(it) }
        )
    }
}

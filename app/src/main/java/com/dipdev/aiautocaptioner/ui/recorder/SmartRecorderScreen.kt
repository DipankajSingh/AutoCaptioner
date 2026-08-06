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
import androidx.camera.view.attachToComposeSurface
import androidx.camera.view.video.AudioConfig
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.dipdev.aiautocaptioner.ui.recorder.model.AspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
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
import com.dipdev.aiautocaptioner.ui.recorder.components.TopHeaderBar
import com.dipdev.aiautocaptioner.ui.components.UniversalDialog
import com.dipdev.aiautocaptioner.ui.components.DialogType
import com.dipdev.aiautocaptioner.ui.recorder.components.PauseResumeControls
import com.dipdev.aiautocaptioner.ui.recorder.components.QuickShareBar
import com.dipdev.aiautocaptioner.ui.recorder.components.SegmentBadge
import com.dipdev.aiautocaptioner.ui.recorder.components.StorageIndicator
import com.dipdev.aiautocaptioner.ui.recorder.components.SmoothnessSlider
import com.dipdev.aiautocaptioner.ui.recorder.components.FloatingFilterBadge
import com.dipdev.aiautocaptioner.ui.recorder.components.StudioRightSidebar
import com.dipdev.aiautocaptioner.ui.recorder.components.StudioBottomArea
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import compose.icons.FeatherIcons
import compose.icons.feathericons.Aperture
import compose.icons.feathericons.Clock
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Grid
import compose.icons.feathericons.Image
import compose.icons.feathericons.RefreshCcw
import compose.icons.feathericons.Smile
import compose.icons.feathericons.X
import compose.icons.feathericons.Zap
import compose.icons.feathericons.ZapOff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext

@Composable
fun SmartRecorderScreen(
    onNavigateBack: () -> Unit,
    onVideoReady: (String) -> Unit,
    viewModel: SmartRecorderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(checkPermission(context, android.Manifest.permission.CAMERA)) }
    var micGranted by remember { mutableStateOf(checkPermission(context, android.Manifest.permission.RECORD_AUDIO)) }
    var cameraPermanentlyDenied by remember { mutableStateOf(false) }
    var micPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraResult = grants[android.Manifest.permission.CAMERA]
        val micResult = grants[android.Manifest.permission.RECORD_AUDIO]

        if (cameraResult != null) {
            cameraGranted = cameraResult
            if (!cameraResult) {
                val activity = context as? android.app.Activity
                cameraPermanentlyDenied = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.CAMERA)
            }
        }
        if (micResult != null) {
            micGranted = micResult
            if (!micResult) {
                val activity = context as? android.app.Activity
                micPermanentlyDenied = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Refresh permission state whenever the screen resumes (e.g. returning from Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = checkPermission(context, android.Manifest.permission.CAMERA)
                micGranted = checkPermission(context, android.Manifest.permission.RECORD_AUDIO)
                cameraPermanentlyDenied = false
                micPermanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestPermissions: () -> Unit = {
        val needed = buildList {
            if (!cameraGranted) add(android.Manifest.permission.CAMERA)
            if (!micGranted) add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
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
        onRequestPermissions = requestPermissions,
        onOpenSettings = {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            )
            context.startActivity(intent)
        }
    )
}

private fun checkPermission(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

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
    onRequestPermissions: () -> Unit,
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
    val finishedVideoFile = uiState.finishedVideoFile
    val isAudioMuted = uiState.isAudioMuted
    val showGrid = uiState.showGrid
    val countdownTimer = uiState.countdownTimer
    val showTeleprompter = uiState.showTeleprompter
    val teleprompterText = uiState.teleprompterText
    val audioAmplitude = uiState.audioAmplitude
    val isCountdownActive = uiState.isCountdownActive
    val countdownRemaining = uiState.countdownRemaining
    val isGestureDetectionEnabled = uiState.isGestureDetectionEnabled
    val aspectRatio = uiState.aspectRatio
    val recordingQuality = uiState.recordingQuality
    val showExitDialog = uiState.showExitDialog
    val segments = uiState.segments
    val currentSegmentStartMs = uiState.currentSegmentStartMs

    var showBgPicker by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var flipRotation by remember { mutableFloatStateOf(0f) }
    val animateFlip: Float by animateFloatAsState(
        targetValue = flipRotation,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "flip_y"
    )
    val isFlipping = kotlin.math.abs(animateFlip - flipRotation) > 1f

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE or CameraController.IMAGE_ANALYSIS)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }

    var activeRecording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }

    val isPermissionBlocked = !cameraGranted || !micGranted

    val startRecordingAction: (forceCountdown: Int) -> Unit = { forceCountdown ->
        if (recordingState == RecordingState.IDLE) {
            if (isPermissionBlocked) {
                onRequestPermissions()
            } else {
                viewModel.requestStartRecording(forceCountdown = forceCountdown) {
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
    val currentMode by rememberUpdatedState(mode)
    val currentActiveRecording by rememberUpdatedState(activeRecording)

    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val gestureListener = remember {
        object : GestureDetectorHelper.GestureListener {
            override fun onPalmDetected() {
                mainExecutor.execute {
                    if (currentRecordingState == RecordingState.IDLE && !currentIsCountdownActive) {
                        currentStartAction(3)
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
            val helper = withContext(kotlinx.coroutines.Dispatchers.IO) {
                GestureDetectorHelper(context, gestureListener)
            }
            gestureHelper = helper
            cameraController.setImageAnalysisAnalyzer(backgroundExecutor, helper)
        } else {
            cameraController.clearImageAnalysisAnalyzer()
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                gestureHelper?.close()
            }
            gestureHelper = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            cameraController.clearImageAnalysisAnalyzer()
            gestureHelper?.close()
            gestureHelper = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val state = currentRecordingState
                val m = currentMode
                val rec = currentActiveRecording
                if (state == RecordingState.RECORDING || state == RecordingState.PAUSED) {
                    if (m == RecordingMode.FACELESS) {
                        viewModel.stopFacelessRecording()
                    } else {
                        if (state == RecordingState.PAUSED) {
                            rec?.resume()
                            Thread.sleep(50)
                        }
                        rec?.stop()
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
        // --- 1. Constant High-Def Background / Preview Area (Never reallocates surface texture) ---
        if (recordingState == RecordingState.DONE && finishedVideoFile != null && finishedVideoFile.exists()) {
            com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard(
                path = finishedVideoFile.absolutePath,
                modifier = Modifier.fillMaxSize(),
                loop = true,
                autoPlay = true,
                showControls = false
            )
        } else {
            // Intelligent Sensor Lifecycle: Unbind sensor immediately in Faceless mode to save 100% camera GPU & battery resources
            if (cameraGranted) {
                val shouldBindCamera = mode == RecordingMode.CAMERA && recordingState != RecordingState.DONE
                LaunchedEffect(shouldBindCamera, cameraController, lifecycleOwner) {
                    if (shouldBindCamera) {
                        cameraController.bindToLifecycle(lifecycleOwner)
                    } else {
                        cameraController.unbind()
                    }
                }
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101010))) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                this.controller = cameraController
                                this.scaleType = PreviewView.ScaleType.FILL_CENTER
                                this.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                        },
                        modifier = Modifier.fillMaxSize().alpha(if (mode == RecordingMode.CAMERA) 1f else 0f)
                    )
                    if (mode == RecordingMode.CAMERA && isFlipping) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                    }
                }
            } else if (mode == RecordingMode.CAMERA) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101010)))
            }

            // Smoothly transition Faceless preview over dark backdrop with zero camera hardware active underneath
            AnimatedVisibility(
                visible = mode == RecordingMode.FACELESS,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250)),
                modifier = Modifier.fillMaxSize()
            ) {
                SmartRecorderFacelessPreview(
                    selectedBackground = selectedBackground,
                    isRecording = recordingState == RecordingState.RECORDING,
                    onTransformUpdate = { scale, offsetX, offsetY ->
                        viewModel.updateImageTransform(scale, offsetX, offsetY)
                    }
                )
            }
        }

        // --- 2. Zero-Flicker Instant Aspect Ratio Masking & Grid Overlays ---
        if (recordingState != RecordingState.DONE) {
            AspectRatioMaskOverlay(aspectRatio = aspectRatio)
        }
        if (mode == RecordingMode.CAMERA && showGrid) {
            GridOverlay(aspectRatio = aspectRatio)
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

        // --- 3. Instagram / TikTok Creator UI Overlay ---

        // Top Control Bar (Clean, uncluttered, no overlapping)
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
            // Far Left: Close Button in a subtle dark circular glass badge
            IconButton(
                onClick = {
                    if (recordingState == RecordingState.IDLE) {
                        onNavigateBack()
                    } else if (recordingState == RecordingState.DONE) {
                        onNavigateBack()
                    } else {
                        viewModel.requestExitRecording()
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(FeatherIcons.X, contentDescription = "Close", tint = Color.White)
            }

            // Center: Flash Button
            IconButton(
                onClick = {
                    flashEnabled = !flashEnabled
                    cameraController.enableTorch(flashEnabled)
                },
                modifier = Modifier.size(40.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (flashEnabled) compose.icons.FeatherIcons.Zap else compose.icons.FeatherIcons.ZapOff,
                    contentDescription = "Flash",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }

            // Far Right: Top Header Bar (Aspect Ratio & Quality pills) when IDLE
            if (recordingState == RecordingState.IDLE && !isPermissionBlocked) {
                TopHeaderBar(
                    aspectRatio = aspectRatio,
                    recordingQuality = recordingQuality,
                    onAspectRatioClick = { viewModel.cycleAspectRatio() },
                    onQualityClick = { viewModel.cycleRecordingQuality() }
                )
            } else {
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.size(40.dp))
            }
        }

        // Floating filter badge on selection change
        if (!isPermissionBlocked) {
            FloatingFilterBadge(
                activeFilter = uiState.activeFilter,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp)
            )
        }

        // --- Vertical Studio Sidebars ---
        if (!isPermissionBlocked && recordingState == RecordingState.IDLE) {
            StudioRightSidebar(
                mode = mode,
                uiState = uiState,
                isGestureDetectionEnabled = isGestureDetectionEnabled,
                countdownTimer = countdownTimer,
                showGrid = showGrid,
                showTeleprompter = showTeleprompter,
                onToggleGrid = { viewModel.toggleGrid() },
                onToggleTeleprompter = { viewModel.toggleTeleprompter() },
                onToggleSmoothness = { viewModel.toggleSmoothnessSlider() },
                onToggleGesture = { viewModel.toggleGestureDetection() },
                onOpenCanvasPicker = { showBgPicker = true },
                onCycleTimer = {
                    val next = when (countdownTimer) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                    viewModel.setCountdownTimer(next)
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
            )
        }

        // --- Bottom Studio & Recording Area ---
        StudioBottomArea(
            uiState = uiState,
            recordingState = recordingState,
            mode = mode,
            isPermissionBlocked = isPermissionBlocked,
            audioAmplitude = audioAmplitude,
            elapsedSeconds = elapsedSeconds,
            animateFlip = animateFlip,
            onFilterSelected = { filter -> viewModel.selectFilter(filter) },
            onSmoothnessChanged = { intensity -> viewModel.updateSmoothness(intensity) },
            onDismissSubControls = { viewModel.dismissSubControls() },
            onModeSelected = { m ->
                viewModel.setRecordingMode(if (m == "CAMERA") RecordingMode.CAMERA else RecordingMode.FACELESS)
            },
            onStartRecording = { startRecordingAction(0) },
            onFlipCamera = {
                flipRotation += 180f
                val current = cameraController.cameraSelector
                cameraController.cameraSelector = if (current == CameraSelector.DEFAULT_BACK_CAMERA) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
            },
            onPauseRecording = {
                if (mode == RecordingMode.CAMERA) {
                    activeRecording?.pause()
                    viewModel.pauseRecording()
                } else {
                    viewModel.pauseRecording()
                }
            },
            onResumeRecording = {
                if (mode == RecordingMode.CAMERA) {
                    activeRecording?.resume()
                    viewModel.resumeRecording()
                } else {
                    viewModel.resumeRecording()
                }
            },
            onStopRecording = {
                if (mode == RecordingMode.CAMERA) {
                    if (recordingState == RecordingState.PAUSED) {
                        activeRecording?.resume()
                        Thread.sleep(50)
                    }
                    activeRecording?.stop()
                } else {
                    if (recordingState == RecordingState.PAUSED) {
                        viewModel.resumeRecording()
                        Thread.sleep(50)
                    }
                    viewModel.stopFacelessRecording()
                }
            },
            onRetake = { viewModel.resetState() },
            onEdit = {
                finishedProjectId?.let { pId -> onVideoReady(pId) }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showBgPicker) {
        BackgroundPickerSheet(
            onDismissRequest = { showBgPicker = false },
            onBackgroundSelected = { viewModel.setSelectedBackground(it) }
        )
    }

    // Exit recording dialog
    if (showExitDialog) {
        UniversalDialog(
            type = DialogType.WARNING,
            title = stringResource(R.string.recorder_exit_title),
            body = stringResource(R.string.recorder_exit_message),
            confirmText = stringResource(R.string.recorder_save_and_exit),
            onConfirm = { viewModel.saveAndExit() },
            dismissText = stringResource(R.string.recorder_discard),
            onDismiss = { viewModel.discardRecording(); onNavigateBack() },
            onDismissRequest = { viewModel.dismissExitDialog() }
        )
    }

    // Full-screen camera & microphone permission gate (shown while either is missing)
    if (isPermissionBlocked) {
        PermissionRequestScreen(
            cameraGranted = cameraGranted,
            micGranted = micGranted,
            cameraPermanentlyDenied = cameraPermanentlyDenied,
            micPermanentlyDenied = micPermanentlyDenied,
            onRequestPermissions = onRequestPermissions,
            onOpenSettings = onOpenSettings,
            onDismiss = onNavigateBack,
            onPrivacyPolicy = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    AppLinks.PRIVACY_POLICY.toUri()
                )
                context.startActivity(intent)
            }
        )
    }
}

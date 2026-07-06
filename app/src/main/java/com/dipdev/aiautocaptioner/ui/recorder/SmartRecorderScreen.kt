package com.dipdev.aiautocaptioner.ui.recorder

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import kotlin.random.Random

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SmartRecorderScreen(
    onNavigateBack: () -> Unit,
    onVideoReady: (String) -> Unit,
    viewModel: SmartRecorderViewModel = hiltViewModel()
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val micPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val context = LocalContext.current

    SmartRecorderContent(
        onNavigateBack = onNavigateBack,
        onVideoReady = onVideoReady,
        viewModel = viewModel,
        cameraGranted = cameraPermissionState.status.isGranted,
        micGranted = micPermissionState.status.isGranted,
        onRequestCamera = { cameraPermissionState.launchPermissionRequest() },
        onRequestMic = { micPermissionState.launchPermissionRequest() },
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

    var showBgPicker by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
        }
    }
    
    var activeRecording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }

    // Lifecycle handling for backgrounding app during recording
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
        if (finishedProjectId != null) {
            val pId = finishedProjectId!!
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
                PermissionOverlay(
                    message = "Camera access is required for Camera Mode.",
                    onRequest = onRequestCamera,
                    onOpenSettings = onOpenSettings
                )
            }
        } else {
            val bgModifier = Modifier.fillMaxSize()
            when (val bg = selectedBackground) {
                is BackgroundState.SolidColor -> Box(modifier = bgModifier.background(bg.color))
                is BackgroundState.Gradient -> Box(modifier = bgModifier.background(Brush.linearGradient(bg.colors)))
                is BackgroundState.ImageBitmap -> {
                    androidx.compose.foundation.Image(
                        bitmap = bg.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = bgModifier,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }

            if (!micGranted) {
                PermissionOverlay(
                    message = "Microphone access is required for Faceless Mode.",
                    onRequest = onRequestMic,
                    onOpenSettings = onOpenSettings
                )
            }
        }

        // --- 2. Overlays (Grid, Visualizer, Teleprompter, Countdown) ---
        if (mode == RecordingMode.CAMERA && showGrid) {
            GridOverlay()
        }

        if (mode == RecordingMode.FACELESS && recordingState == RecordingState.RECORDING) {
            AudioVisualizerOverlay(amplitude = audioAmplitude)
        }

        if (showTeleprompter) {
            TeleprompterOverlay(
                text = teleprompterText,
                onTextChanged = { viewModel.updateTeleprompterText(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .padding(horizontal = 32.dp, vertical = 80.dp) // Leave room for top bar
                    .align(Alignment.TopCenter)
            )
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
        // Top Left: Close
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.padding(top = 48.dp, start = 16.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        // Top Center: Timer
        if (recordingState == RecordingState.RECORDING) {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            Surface(
                color = AccentRose.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
            ) {
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Left Sidebar
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SidebarButton(
                icon = Icons.AutoMirrored.Filled.Subject,
                text = "Script",
                isActive = showTeleprompter,
                onClick = { viewModel.toggleTeleprompter() }
            )
            if (mode == RecordingMode.FACELESS && recordingState == RecordingState.IDLE) {
                SidebarButton(
                    icon = Icons.Default.Wallpaper,
                    text = "Canvas",
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
                    icon = Icons.Default.Cameraswitch,
                    text = "Flip",
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
                    icon = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    text = "Flash",
                    isActive = flashEnabled,
                    onClick = {
                        flashEnabled = !flashEnabled
                        cameraController.enableTorch(flashEnabled)
                    }
                )
                SidebarButton(
                    icon = Icons.Default.Grid3x3,
                    text = "Grid",
                    isActive = showGrid,
                    onClick = { viewModel.toggleGrid() }
                )
            }
            if (recordingState == RecordingState.IDLE) {
                val timerText = if (countdownTimer == 0) "Timer" else "${countdownTimer}s"
                SidebarButton(
                    icon = Icons.Default.Timer,
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

        // Bottom Area
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (recordingState == RecordingState.IDLE) {
                // Mode Selector
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(bottom = 24.dp)) {
                    SegmentedButton(
                        selected = mode == RecordingMode.CAMERA,
                        onClick = { viewModel.setRecordingMode(RecordingMode.CAMERA) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {} // Disable default checkmark to prevent text clipping
                    ) {
                        Text("📷 Camera")
                    }
                    SegmentedButton(
                        selected = mode == RecordingMode.FACELESS,
                        onClick = { viewModel.setRecordingMode(RecordingMode.FACELESS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {} // Disable default checkmark to prevent text clipping
                    ) {
                        Text("🎭 Faceless")
                    }
                }
            }

            // Record Button
            RecordButton(
                isRecording = recordingState == RecordingState.RECORDING,
                onClick = {
                    if (recordingState == RecordingState.IDLE) {
                        if (mode == RecordingMode.FACELESS && !micGranted) {
                            onRequestMic()
                            return@RecordButton
                        }
                        if (mode == RecordingMode.CAMERA && !cameraGranted) {
                            onRequestCamera()
                            return@RecordButton
                        }
                        if (mode == RecordingMode.CAMERA && !isAudioMuted && !micGranted) {
                            onRequestMic()
                            return@RecordButton
                        }
                        
                        viewModel.requestStartRecording {
                            // Start CameraX
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
                    } else {
                        if (mode == RecordingMode.FACELESS) {
                            viewModel.stopFacelessRecording()
                        } else {
                            activeRecording?.stop()
                        }
                    }
                }
            )
        }
    }

    if (showBgPicker) {
        BackgroundPickerSheet(
            onDismissRequest = { showBgPicker = false },
            onBackgroundSelected = { viewModel.setSelectedBackground(it) }
        )
    }
}

@Composable
fun SidebarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isActive) AccentCyan else Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Vertical lines
        drawLine(Color.White.copy(alpha = 0.5f), Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 2f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), strokeWidth = 2f)
        // Horizontal lines
        drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = 2f)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), strokeWidth = 2f)
    }
}

@Composable
fun TeleprompterOverlay(
    text: String,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        TextField(
            value = text,
            onValueChange = onTextChanged,
            placeholder = { Text("Paste your script here...", color = Color.White.copy(alpha = 0.5f)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AccentCyan,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun AudioVisualizerOverlay(amplitude: Float) {
    // 5 bars that scale based on amplitude + some random jitter
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 5
        for (i in 0 until barCount) {
            val randomFactor = remember { mutableStateOf(1f) }
            LaunchedEffect(amplitude) {
                randomFactor.value = Random.nextFloat() * 0.5f + 0.5f
            }
            // Base height + amplitude * scaling * random
            val safeAmplitude = if (amplitude.isNaN()) 0f else amplitude.coerceIn(0f, 1f)
            val addedHeight = (safeAmplitude * 200f * randomFactor.value).dp
            val height = 40.dp + addedHeight
            
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .width(16.dp)
                    .height(height)
                    .clip(CircleShape)
                    .background(AccentCyan)
            )
        }
    }
}

@Composable
fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = AccentCyan.copy(alpha = if (isRecording) 0.8f else 0.4f),
                radius = size.width / 2 * scale,
                style = Stroke(width = 8f)
            )
        }
        
        // Inner circle
        Box(
            modifier = Modifier
                .size(if (isRecording) 40.dp else 64.dp)
                .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                .background(AccentRose)
        )
    }
}

@Composable
fun PermissionOverlay(
    message: String,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.nothing))
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(150.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequest) {
                Text("Grant Permission")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings", color = Color.White)
            }
        }
    }
}

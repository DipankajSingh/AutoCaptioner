package com.dipdev.aiautocaptioner.ui.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

@Composable
fun SmartRecorderScreen(
    onNavigateBack: () -> Unit,
    onVideoReady: (String) -> Unit,
    viewModel: CameraRecorderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] == true &&
                results[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    if (!hasCameraPermission) {
        PermissionRationaleContent(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            },
            onNavigateBack = onNavigateBack
        )
        return
    }

    // Active CameraX Video Recording instance
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    // Lifecycle observer to stop/cleanup active recording on ON_PAUSE or ON_STOP
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                if (uiState.isRecording) {
                    viewModel.onRecordingStopping()
                    activeRecording?.stop()
                    activeRecording = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (uiState.isRecording) {
                viewModel.onRecordingStopping()
                activeRecording?.stop()
                activeRecording = null
            }
        }
    }

    // Auto-stop recording when 3-minute cap is reached
    LaunchedEffect(uiState.recordedDurationMs) {
        if (uiState.recordedDurationMs >= 180_000L && uiState.isRecording) {
            viewModel.onRecordingStopping()
            activeRecording?.stop()
            activeRecording = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (uiState.reviewVideoFile != null) {
            // Review Screen Overlay
            ReviewScreenOverlay(
                videoFile = uiState.reviewVideoFile!!,
                isImporting = uiState.isImporting,
                onRetake = {
                    viewModel.retakeRecording()
                },
                onUseVideo = {
                    viewModel.confirmVideoAndImport { projectId ->
                        onVideoReady(projectId)
                    }
                }
            )
        } else {
            // Live CameraX Preview & Recorder UI
            CameraPreviewAndControls(
                lifecycleOwner = lifecycleOwner,
                uiState = uiState,
                onCameraBound = { camera -> activeCamera = camera },
                onStartRecording = { videoCapture ->
                    val file = viewModel.createTempOutputFile()
                    val outputOptions = FileOutputOptions.Builder(file).build()
                    val mainExecutor = ContextCompat.getMainExecutor(context)

                    var recordingBuilder = videoCapture.output
                        .prepareRecording(context, outputOptions)

                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        recordingBuilder = recordingBuilder.withAudioEnabled()
                    }

                    activeRecording = recordingBuilder.start(mainExecutor) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                viewModel.onRecordingStarted()
                            }
                            is VideoRecordEvent.Pause -> {
                                viewModel.onRecordingPaused()
                            }
                            is VideoRecordEvent.Resume -> {
                                viewModel.onRecordingResumed()
                            }
                            is VideoRecordEvent.Finalize -> {
                                activeRecording = null
                                if (event.hasError() && (!file.exists() || file.length() == 0L)) {
                                    viewModel.onRecordingFailed(event.error)
                                } else {
                                    viewModel.onRecordingStopped(file)
                                }
                            }
                        }
                    }
                },
                onPauseRecording = {
                    activeRecording?.pause()
                },
                onResumeRecording = {
                    activeRecording?.resume()
                },
                onStopRecording = {
                    viewModel.onRecordingStopping()
                    activeRecording?.stop()
                    activeRecording = null
                },
                onToggleCamera = {
                    viewModel.toggleCameraFacing()
                },
                onZoomChanged = { zoom ->
                    viewModel.setZoomRatio(zoom)
                    activeCamera?.cameraControl?.setZoomRatio(zoom)
                },
                onCameraError = viewModel::onCameraError,
                onNavigateBack = onNavigateBack
            )
        }

        // Display Error Message Banner if error exists
        AnimatedVisibility(
            visible = uiState.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 24.dp, end = 24.dp)
        ) {
            uiState.errorMessage?.let { errorMsg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentRose.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearErrorMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Dismiss",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewAndControls(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    uiState: CameraRecorderState,
    onCameraBound: (Camera) -> Unit,
    onStartRecording: (VideoCapture<Recorder>) -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleCamera: () -> Unit,
    onZoomChanged: (Float) -> Unit,
    onCameraError: (Throwable) -> Unit,
    onNavigateBack: () -> Unit
) {
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var currentZoom by remember { mutableFloatStateOf(uiState.zoomRatio) }

    val cameraSelector = if (uiState.isFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    // Dynamic CameraX binding/rebinding on previewView, cameraSelector, or lifecycleOwner changes
    LaunchedEffect(previewView, cameraSelector, lifecycleOwner) {
        val pView = previewView ?: return@LaunchedEffect
        val ctx = pView.context
        val cameraProvider = ctx.getCameraProvider()

        val rotation = pView.display?.rotation ?: Surface.ROTATION_0

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()
            .also {
                it.surfaceProvider = pView.surfaceProvider
            }

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HIGHEST,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
            .build()

        val capture = VideoCapture.Builder(recorder)
            .setTargetRotation(rotation)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
        videoCapture = capture

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
            onCameraBound(camera)
        } catch (e: Exception) {
            android.util.Log.e("SmartRecorder", "Camera bind failed", e)
            onCameraError(e)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(uiState.isRecording) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!uiState.isRecording) {
                            onToggleCamera()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    currentZoom = (currentZoom * zoom).coerceIn(1.0f, 10.0f)
                    onZoomChanged(currentZoom)
                }
            }
    ) {
        // CameraX AndroidView Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            update = { view ->
                previewView = view
            }
        )

        // Top Control Bar: Back button, Timer, Near Limit warning
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Visible Timer
            val elapsedSec = uiState.recordedDurationMs / 1000L
            val totalSec = 180L
            val elapsedFormatted = String.format(Locale.US, "%02d:%02d", elapsedSec / 60, elapsedSec % 60)
            val maxFormatted = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f),
                border = if (uiState.isNearLimit) androidx.compose.foundation.BorderStroke(1.5.dp, AccentRose) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isRecording) {
                        val infiniteTransition = rememberInfiniteTransition(label = "recordingDot")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(AccentRose.copy(alpha = if (uiState.isPaused) 0.3f else alpha), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = "$elapsedFormatted / $maxFormatted",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isNearLimit) AccentRose else Color.White
                    )
                }
            }

            // Empty balancing container on top-right (camera switch button moved to bottom controls & double-tap gesture)
            Box(modifier = Modifier.size(44.dp))
        }

        // Near-Limit Warning Badge
        AnimatedVisibility(
            visible = uiState.isNearLimit && uiState.isRecording,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
        ) {
            val remainingSec = uiState.remainingDurationMs / 1000L
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentRose.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Near 3-min limit (${remainingSec}s remaining)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Bottom Controls Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (uiState.isRecording) {
                    // Pause / Resume Toggle Button
                    IconButton(
                        onClick = {
                            if (uiState.isPaused) onResumeRecording() else onPauseRecording()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (uiState.isPaused) "Resume" else "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // Big Stop Recording Button
                    IconButton(
                        onClick = onStopRecording,
                        modifier = Modifier
                            .size(80.dp)
                            .background(AccentRose, CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Recording",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    // Spacer on left to balance the Switch Camera button on right
                    Box(modifier = Modifier.size(56.dp))

                    Spacer(modifier = Modifier.width(32.dp))

                    // Big Start Recording Button
                    IconButton(
                        onClick = {
                            videoCapture?.let { onStartRecording(it) }
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .background(AccentRose, CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // Switch Camera Lens Button next to Start Recording
                    IconButton(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.25f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewScreenOverlay(
    videoFile: File,
    isImporting: Boolean,
    onRetake: () -> Unit,
    onUseVideo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Preview Card
        VideoPlayerCard(
            path = videoFile.absolutePath,
            modifier = Modifier.fillMaxSize()
        )

        // Loading Overlay while importing project
        if (isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentAmber)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Preparing project...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        } else {
            // Review Action Buttons (Retake vs Use Video)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRetake,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retake",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Retake",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = onUseVideo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentAmber,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Use Video",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Use Video",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRationaleContent(
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Permission Required",
                tint = AccentAmber,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera & Microphone Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AutoCaptioner needs access to your camera and microphone to record videos.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber, contentColor = Color.Black),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Grant Permissions", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
            ) {
                Text("Go Back")
            }
        }
    }
}

private suspend fun android.content.Context.getCameraProvider(): ProcessCameraProvider =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                if (continuation.isActive) {
                    continuation.resume(cameraProviderFuture.get())
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }


package com.dipdev.aiautocaptioner.ui.recorder
import androidx.compose.foundation.shape.RoundedCornerShape

import android.annotation.SuppressLint
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SmartRecorderScreen(
    onNavigateBack: () -> Unit,
    onVideoReady: (Uri) -> Unit,
    viewModel: SmartRecorderViewModel = hiltViewModel()
) {
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    )

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    if (permissionState.allPermissionsGranted) {
        SmartRecorderContent(onNavigateBack, onVideoReady, viewModel)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Permissions required to record video.")
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartRecorderContent(
    onNavigateBack: () -> Unit,
    onVideoReady: (Uri) -> Unit,
    viewModel: SmartRecorderViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mode by viewModel.recordingMode.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val selectedBackground by viewModel.selectedBackground.collectAsState()
    val outputUri by viewModel.outputUri.collectAsState()

    var showBgPicker by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
        }
    }
    
    var activeRecording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }

    LaunchedEffect(outputUri) {
        if (outputUri != null) {
            showSaveDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Preview Area
        if (mode == RecordingMode.CAMERA) {
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
            // Faceless background
            val bgModifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (recordingState == RecordingState.IDLE) {
                        showBgPicker = true
                    }
                }
            
            when (val bg = selectedBackground) {
                is BackgroundState.SolidColor -> {
                    Box(modifier = bgModifier.background(bg.color))
                }
                is BackgroundState.Gradient -> {
                    Box(modifier = bgModifier.background(Brush.linearGradient(bg.colors)))
                }
                is BackgroundState.ImageBitmap -> {
                    // For simplicity, just rendering an Image with the bitmap
                    // Assuming compose ui graphics ImageBitmap conversion:
                    androidx.compose.foundation.Image(
                        bitmap = bg.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = bgModifier,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }
        }

        // Top UI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            if (recordingState == RecordingState.IDLE) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = mode == RecordingMode.CAMERA,
                        onClick = { viewModel.setRecordingMode(RecordingMode.CAMERA) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("📷 Camera")
                    }
                    SegmentedButton(
                        selected = mode == RecordingMode.FACELESS,
                        onClick = { viewModel.setRecordingMode(RecordingMode.FACELESS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("🎭 Faceless")
                    }
                }
            } else {
                // Timer
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flip Camera (Hidden in Faceless or Recording)
            if (mode == RecordingMode.CAMERA && recordingState == RecordingState.IDLE) {
                IconButton(
                    onClick = {
                        val current = cameraController.cameraSelector
                        cameraController.cameraSelector = if (current == CameraSelector.DEFAULT_BACK_CAMERA) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                    }
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Flip", tint = Color.White)
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Record / Stop Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (recordingState == RecordingState.RECORDING) Color.Red else Color.White)
                    .clickable {
                        if (recordingState == RecordingState.IDLE) {
                            if (mode == RecordingMode.FACELESS) {
                                viewModel.startFacelessRecording(context)
                            } else {
                                val outputFile = File(context.cacheDir, "camera_video_${System.currentTimeMillis()}.mp4")
                                val outputOptions = FileOutputOptions.Builder(outputFile).build()
                                val executor = ContextCompat.getMainExecutor(context)
                                activeRecording = cameraController.startRecording(
                                    outputOptions,
                                    AudioConfig.create(true),
                                    executor
                                ) { event ->
                                    if (event is VideoRecordEvent.Start) {
                                        viewModel.onCameraRecordingStarted()
                                    } else if (event is VideoRecordEvent.Finalize) {
                                        if (!event.hasError()) {
                                            viewModel.onCameraRecordingStopped(Uri.fromFile(outputFile))
                                        } else {
                                            viewModel.onCameraRecordingError()
                                        }
                                        activeRecording = null
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
                    },
                contentAlignment = Alignment.Center
            ) {
                if (recordingState == RecordingState.RECORDING) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(40.dp))
                } else {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.Red))
                }
            }

            // Flash Toggle (Hidden in Faceless or Recording)
            if (mode == RecordingMode.CAMERA && recordingState == RecordingState.IDLE) {
                IconButton(
                    onClick = {
                        flashEnabled = !flashEnabled
                        cameraController.enableTorch(flashEnabled)
                    }
                ) {
                    Icon(
                        if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }

    if (showBgPicker) {
        BackgroundPickerSheet(
            onDismissRequest = { showBgPicker = false },
            onBackgroundSelected = { viewModel.setSelectedBackground(it) }
        )
    }

    if (showSaveDialog && outputUri != null) {
        AlertDialog(
            onDismissRequest = { 
                showSaveDialog = false
                viewModel.resetState() 
            },
            title = { Text("Save Video") },
            text = { Text("Where would you like to save the recorded video?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    val uri = outputUri!!
                    viewModel.resetState()
                    onVideoReady(uri)
                }) {
                    Text("App Only")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    val uri = outputUri!!
                    viewModel.resetState()
                    // Assuming for now it behaves same, parent will handle saving to camera roll if needed
                    onVideoReady(uri)
                }) {
                    Text("Camera Roll Too")
                }
            }
        )
    }
}

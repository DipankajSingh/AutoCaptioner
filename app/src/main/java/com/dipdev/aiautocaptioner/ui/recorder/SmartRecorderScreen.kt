package com.dipdev.aiautocaptioner.ui.recorder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.recorder.components.CameraPreviewView
import com.dipdev.aiautocaptioner.ui.recorder.components.QualitySelector
import com.dipdev.aiautocaptioner.ui.recorder.components.RatioSelector
import com.dipdev.aiautocaptioner.ui.recorder.components.RecordingControlsBar
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.GlassSurface
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import com.dipdev.aiautocaptioner.ui.theme.TrueBlackBackground

/**
 * The real in-app recorder screen — the Stage 3 nav destination.
 *
 * Replaces [PreviewHarnessScreen] (deleted). Handles permissions inline using
 * [PermissionRequestScreen], then shows the live camera preview + recording controls.
 *
 * Signature is fixed per the nav-graph contract (do not add parameters):
 *   - [onNavigateBack] → called on back press or when recording is impossible
 *   - [onVideoReady] → called with the new project ID after recording is finalized
 */
@Composable
fun SmartRecorderScreen(
    onNavigateBack: () -> Unit,
    onVideoReady: (projectId: String) -> Unit,
    viewModel: RecorderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Permission launcher — requests CAMERA + RECORD_AUDIO together
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val cameraGranted = results[Manifest.permission.CAMERA] == true
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true

        // Determine permanent denial by checking if we can show rationale
        // (cannot call shouldShowRequestPermissionRationale from Composable directly — use
        // the inverse: if the result is false and the activity has already asked, it's permanent)
        viewModel.setEvent(
            RecorderEvent.PermissionsResult(
                cameraGranted = cameraGranted,
                micGranted = micGranted,
                cameraPermanentlyDenied = !cameraGranted,
                micPermanentlyDenied = !micGranted
            )
        )
    }

    // Consume one-shot effects
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is RecorderEffect.NavigateToEditor -> onVideoReady(effect.projectId)
                is RecorderEffect.NavigateBack -> onNavigateBack()
                is RecorderEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is RecorderEffect.LaunchPermissionRequest -> {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                }
            }
        }
    }

    // On first composition, trigger initial permission check
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    ScreenThemeProvider(accentColor = AccentRose) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TrueBlackBackground)
        ) {
            when (val phase = state.permissionPhase) {
                is PermissionPhase.Checking -> {
                    // Blank while permission result is pending — avoids a flash of the wrong UI
                }

                is PermissionPhase.Denied -> {
                    PermissionRequestScreen(
                        cameraGranted = !phase.cameraPermanent,
                        micGranted = !phase.micPermanent,
                        cameraPermanentlyDenied = phase.cameraPermanent,
                        micPermanentlyDenied = phase.micPermanent,
                        onRequestPermissions = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        },
                        onDismiss = onNavigateBack,
                        onPrivacyPolicy = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://autocaptioner.app/privacy"))
                                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            )
                        }
                    )
                }

                is PermissionPhase.Granted -> {
                    RecorderContent(
                        state = state,
                        rendererThread = viewModel.rendererThread,
                        onNavigateBack = { viewModel.setEvent(RecorderEvent.NavigateBack) },
                        onSwitchCamera = { viewModel.setEvent(RecorderEvent.SwitchCamera) },
                        onToggleTorch = { viewModel.setEvent(RecorderEvent.ToggleTorch) },
                        onSelectQuality = { viewModel.setEvent(RecorderEvent.SelectQuality(it)) },
                        onSelectRatio = { viewModel.setEvent(RecorderEvent.SelectAspectRatio(it)) },
                        onZoomChange = { viewModel.setEvent(RecorderEvent.SetZoom(it)) },
                        onTapToFocus = { x, y -> viewModel.setEvent(RecorderEvent.TapToFocus(x, y)) },
                        onStart = { viewModel.setEvent(RecorderEvent.StartRecording) },
                        onPause = { viewModel.setEvent(RecorderEvent.PauseRecording) },
                        onResume = { viewModel.setEvent(RecorderEvent.ResumeRecording) },
                        onStop = { viewModel.setEvent(RecorderEvent.StopRecording) }
                    )
                }
            }

            // Snackbar for errors
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 120.dp)
            ) { data ->
                Snackbar(snackbarData = data)
            }
        }
    }
}

@Composable
private fun RecorderContent(
    state: RecorderState,
    rendererThread: com.dipdev.aiautocaptioner.core.gl.RendererThread,
    onNavigateBack: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleTorch: () -> Unit,
    onSelectQuality: (com.dipdev.aiautocaptioner.core.camera.RecordingQuality) -> Unit,
    onSelectRatio: (com.dipdev.aiautocaptioner.core.camera.RecordingAspectRatio) -> Unit,
    onZoomChange: (Float) -> Unit,
    onTapToFocus: (Float, Float) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val isRecordingActive = state.recordingPhase is RecordingPhase.Recording
            || state.recordingPhase is RecordingPhase.Paused

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Camera preview — aspect-ratio locked (letterboxed, same as TikTok/Reels) ──
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val aspectRatioValue = state.aspectRatio.widthRatio.toFloat() /
                    state.aspectRatio.heightRatio.toFloat()
            CameraPreviewView(
                rendererThread = rendererThread,
                onZoomChange = onZoomChange,
                onTapToFocus = onTapToFocus,
                modifier = Modifier.aspectRatio(aspectRatioValue)
            )
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            // Row 1: Back button only
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GlassSurface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Quality + Ratio selectors centered, with enough room for both pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QualitySelector(
                    qualities = state.availableQualities,
                    selected = state.quality,
                    enabled = !isRecordingActive,
                    onSelect = onSelectQuality
                )
                Spacer(modifier = Modifier.size(8.dp))
                RatioSelector(
                    selected = state.aspectRatio,
                    enabled = !isRecordingActive,
                    onSelect = onSelectRatio
                )
            }
        }

        // ── Side buttons (torch + camera switch) — right side, center ────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Torch (only for back camera)
            if (state.cameraFacing == com.dipdev.aiautocaptioner.core.camera.CameraFacing.BACK) {
                SideIconButton(
                    icon = {
                        Icon(
                            imageVector = if (state.torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = stringResource(R.string.recorder_torch),
                            tint = if (state.torchOn) com.dipdev.aiautocaptioner.ui.theme.AccentAmber
                                   else Color.White
                        )
                    },
                    onClick = onToggleTorch
                )
            }

            // Camera switch
            SideIconButton(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = stringResource(R.string.recorder_switch_camera),
                        tint = Color.White
                    )
                },
                onClick = onSwitchCamera,
                enabled = !isRecordingActive
            )
        }

        // ── Controls bar (bottom) ─────────────────────────────────────────────
        RecordingControlsBar(
            phase = state.recordingPhase,
            elapsedMs = state.elapsedMs,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun SideIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(GlassSurface)
    ) {
        icon()
    }
}

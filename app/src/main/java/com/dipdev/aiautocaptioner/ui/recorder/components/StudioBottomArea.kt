package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import com.dipdev.aiautocaptioner.ui.recorder.ModeToggle
import com.dipdev.aiautocaptioner.ui.recorder.RecordButton
import com.dipdev.aiautocaptioner.ui.recorder.RecordingMode
import com.dipdev.aiautocaptioner.ui.recorder.RecordingState
import com.dipdev.aiautocaptioner.ui.recorder.SmartRecorderState
import compose.icons.FeatherIcons
import compose.icons.feathericons.RefreshCcw
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch

@Composable
fun StudioBottomArea(
    uiState: SmartRecorderState,
    recordingState: RecordingState,
    mode: RecordingMode,
    isPermissionBlocked: Boolean,
    audioAmplitude: Float,
    elapsedSeconds: Int,
    animateFlip: Float,
    onFilterSelected: (CreatorFilter) -> Unit,
    onSmoothnessChanged: (Float) -> Unit,
    onDismissSubControls: () -> Unit,
    onModeSelected: (String) -> Unit,
    onStartRecording: () -> Unit,
    onFlipCamera: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRetake: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .displayCutoutPadding()
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Studio Overlays (Filters / Smoothness Sliders) sliding cleanly above shutter
        AnimatedVisibility(
            visible = uiState.isSmoothnessSliderVisible && recordingState == RecordingState.IDLE,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(200)),
            modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
        ) {
            SmoothnessSlider(
                currentSmoothness = uiState.smoothnessIntensity,
                onSmoothnessChanged = onSmoothnessChanged,
                onDismiss = onDismissSubControls,
                modifier = Modifier.fillMaxWidth()
            )
        }



        // Recording Duration Timer Pill located cleanly above bottom controls during recording
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(
                                if (recordingState == RecordingState.PAUSED) Color.White
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                    Text(
                        text = String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                }
            }
        }

        // Central Shutter / Record Button & Controls
        when (recordingState) {
            RecordingState.IDLE -> {
                if (!isPermissionBlocked) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (mode == RecordingMode.CAMERA) {
                                IntegratedFilterShutter(
                                    activeFilter = uiState.activeFilter,
                                    onFilterSelected = onFilterSelected,
                                    isRecording = false,
                                    onRecordClick = onStartRecording
                                )
                            } else {
                                HollowShutterRing(
                                    isRecording = false,
                                    onClick = onStartRecording
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ModeToggle(
                                currentMode = mode.name,
                                onModeSelected = onModeSelected
                            )

                            // Right Camera Flip Button (Lightning-fast thumb reach in Camera Mode)
                            if (mode == RecordingMode.CAMERA) {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (isPressed) 0.85f else 1f,
                                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f),
                                    label = "flipScale"
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(44.dp)
                                        .graphicsLayer {
                                            this.rotationY = animateFlip
                                            cameraDistance = 8 * density
                                        }
                                        .scale(scale)
                                        .clip(CircleShape)
                                        .clickable(
                                            indication = null,
                                            interactionSource = interactionSource,
                                            onClick = onFlipCamera
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cameraswitch,
                                        contentDescription = stringResource(R.string.recorder_flip),
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            RecordingState.RECORDING -> {
                PauseResumeControls(
                    isPaused = false,
                    onPause = onPauseRecording,
                    onResume = onResumeRecording,
                    onStop = onStopRecording
                )
            }
            RecordingState.PAUSED -> {
                PauseResumeControls(
                    isPaused = true,
                    onPause = onPauseRecording,
                    onResume = onResumeRecording,
                    onStop = onStopRecording
                )
            }
            RecordingState.DONE -> {
                QuickShareBar(
                    onRetake = onRetake,
                    onEdit = onEdit,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

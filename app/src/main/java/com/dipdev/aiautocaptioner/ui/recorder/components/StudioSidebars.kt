package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import com.dipdev.aiautocaptioner.ui.recorder.RecordingMode
import com.dipdev.aiautocaptioner.ui.recorder.SidebarButton
import com.dipdev.aiautocaptioner.ui.recorder.SmartRecorderState
import compose.icons.FeatherIcons
import compose.icons.feathericons.Aperture
import compose.icons.feathericons.Clock
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Grid
import compose.icons.feathericons.Image
import compose.icons.feathericons.Smile
import compose.icons.feathericons.Zap
import compose.icons.feathericons.ZapOff

@Composable
fun StudioLeftSidebar(
    mode: RecordingMode,
    flashEnabled: Boolean,
    showGrid: Boolean,
    showTeleprompter: Boolean,
    onToggleFlash: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleTeleprompter: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(tween(250)),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (mode == RecordingMode.CAMERA) {
                SidebarButton(
                    icon = if (flashEnabled) FeatherIcons.Zap else FeatherIcons.ZapOff,
                    text = stringResource(R.string.recorder_flash),
                    isActive = flashEnabled,
                    pulseRing = flashEnabled,
                    onClick = onToggleFlash
                )
                SidebarButton(
                    icon = FeatherIcons.Grid,
                    text = stringResource(R.string.recorder_grid),
                    isActive = showGrid,
                    onClick = onToggleGrid
                )
            }
            SidebarButton(
                icon = FeatherIcons.FileText,
                text = stringResource(R.string.recorder_script),
                isActive = showTeleprompter,
                onClick = onToggleTeleprompter
            )
        }
    }
}

@Composable
fun StudioRightSidebar(
    mode: RecordingMode,
    uiState: SmartRecorderState,
    isGestureDetectionEnabled: Boolean,
    countdownTimer: Int,
    onToggleSmoothness: () -> Unit,
    onToggleFilters: () -> Unit,
    onToggleGesture: () -> Unit,
    onOpenCanvasPicker: () -> Unit,
    onCycleTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(250)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (mode == RecordingMode.CAMERA) {
                SidebarButton(
                    icon = FeatherIcons.Smile,
                    text = "Retouch",
                    isActive = uiState.isSmoothnessSliderVisible || uiState.smoothnessIntensity > 0f,
                    breathingGlow = uiState.smoothnessIntensity > 0f,
                    onClick = onToggleSmoothness
                )
                SidebarButton(
                    icon = FeatherIcons.Aperture,
                    text = "Filters",
                    isActive = uiState.isFilterCarouselVisible || uiState.activeFilter != CreatorFilter.NATURAL,
                    onClick = onToggleFilters
                )
                SidebarButton(
                    icon = Icons.Rounded.PanTool,
                    text = stringResource(R.string.recorder_palm),
                    isActive = isGestureDetectionEnabled,
                    pulseRing = isGestureDetectionEnabled,
                    onClick = onToggleGesture
                )
            }
            if (mode == RecordingMode.FACELESS) {
                SidebarButton(
                    icon = FeatherIcons.Image,
                    text = stringResource(R.string.recorder_canvas),
                    isActive = false,
                    onClick = onOpenCanvasPicker
                )
            }
            val timerText = if (countdownTimer == 0) stringResource(R.string.recorder_timer) else "${countdownTimer}s"
            SidebarButton(
                icon = FeatherIcons.Clock,
                text = timerText,
                isActive = countdownTimer > 0,
                onClick = onCycleTimer
            )
        }
    }
}

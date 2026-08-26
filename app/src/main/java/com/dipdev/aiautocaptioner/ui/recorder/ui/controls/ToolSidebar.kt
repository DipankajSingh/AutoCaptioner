package com.dipdev.aiautocaptioner.ui.recorder.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Grid
import compose.icons.feathericons.Smile
import compose.icons.feathericons.Type
import compose.icons.feathericons.Sliders
import compose.icons.feathericons.Clock
import compose.icons.feathericons.Image
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingMode

@Composable
fun ToolSidebar(
    mode: RecordingMode,
    isGestureDetectionEnabled: Boolean,
    countdownTimer: Int,
    showGrid: Boolean,
    showTeleprompter: Boolean,
    onToggleGrid: () -> Unit,
    onToggleTeleprompter: () -> Unit,
    onToggleSmoothness: () -> Unit,
    onToggleGesture: () -> Unit,
    onOpenCanvasPicker: () -> Unit,
    onCycleTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SidebarButton(
            icon = FeatherIcons.Grid,
            text = "Grid",
            isActive = showGrid,
            onClick = onToggleGrid
        )
        SidebarButton(
            icon = FeatherIcons.Type,
            text = "Teleprompter",
            isActive = showTeleprompter,
            onClick = onToggleTeleprompter
        )
        SidebarButton(
            icon = FeatherIcons.Sliders,
            text = "Smoothness",
            onClick = onToggleSmoothness
        )
        SidebarButton(
            icon = Icons.Rounded.PanTool,
            text = "Gesture",
            isActive = isGestureDetectionEnabled,
            onClick = onToggleGesture
        )
        if (mode == RecordingMode.FACELESS) {
            SidebarButton(
                icon = FeatherIcons.Image,
                text = "Canvas",
                onClick = onOpenCanvasPicker
            )
        }
        SidebarButton(
            icon = FeatherIcons.Clock,
            text = "Timer",
            isActive = countdownTimer > 0,
            badgeText = if (countdownTimer > 0) "${countdownTimer}s" else null,
            onClick = onCycleTimer
        )
    }
}

package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor
import compose.icons.FeatherIcons
import compose.icons.feathericons.CornerUpLeft
import compose.icons.feathericons.CornerUpRight
import compose.icons.feathericons.Download
import compose.icons.feathericons.LogOut

@Composable
fun EditorTopOverlay(
    canUndo: Boolean,
    canRedo: Boolean,
    onNavigateBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onNavigateToExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        SideControlButton(
            icon = FeatherIcons.LogOut,
            contentDescription = stringResource(R.string.side_exit_editor),
            onClick = onNavigateBack,
            tint = AccentRose,
            containerColor = AccentRose.copy(alpha = 0.15f)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SideControlButton(
                icon = FeatherIcons.Download,
                contentDescription = stringResource(R.string.side_export),
                onClick = onNavigateToExport,
                tint = LocalAccentColor.current,
                containerColor = LocalAccentColor.current.copy(alpha = 0.15f)
            )
            SideControlButton(
                icon = FeatherIcons.CornerUpLeft,
                contentDescription = stringResource(R.string.side_undo),
                onClick = onUndo,
                enabled = canUndo
            )
            SideControlButton(
                icon = FeatherIcons.CornerUpRight,
                contentDescription = stringResource(R.string.side_redo),
                onClick = onRedo,
                enabled = canRedo
            )
        }
    }
}

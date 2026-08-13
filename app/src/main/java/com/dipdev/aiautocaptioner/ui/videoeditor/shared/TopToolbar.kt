package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor

@Composable
fun EditorTopOverlay(
    canUndo: Boolean,
    canRedo: Boolean,
    onNavigateBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onNavigateToExport: () -> Unit,
    modifier: Modifier = Modifier,
    leftContent: @Composable () -> Unit = {},
    rightContent: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.side_exit_editor),
                tint = AccentRose,
                modifier = Modifier
                    .shadow(4.dp, CircleShape)
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onNavigateBack() }
                    .padding(2.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            BottomUpFlowColumn(
                modifier = Modifier.weight(1f),
                verticalSpacing = 20.dp,
                horizontalSpacing = 12.dp,
                wrapDirection = WrapDirection.Right
            ) {
                leftContent()
            }
        }

        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val accent = LocalAccentColor.current
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.side_export),
                    tint = accent,
                    modifier = Modifier
                        .shadow(4.dp, CircleShape)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onNavigateToExport() }
                        .padding(2.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.side_undo),
                    tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.38f),
                    modifier = Modifier
                        .shadow(4.dp, CircleShape)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canUndo) { onUndo() }
                        .padding(2.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = stringResource(R.string.side_redo),
                    tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.38f),
                    modifier = Modifier
                        .shadow(4.dp, CircleShape)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canRedo) { onRedo() }
                        .padding(2.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            BottomUpFlowColumn(
                modifier = Modifier.weight(1f),
                verticalSpacing = 20.dp,
                horizontalSpacing = 12.dp,
                wrapDirection = WrapDirection.Left
            ) {
                rightContent()
            }
        }
    }
}

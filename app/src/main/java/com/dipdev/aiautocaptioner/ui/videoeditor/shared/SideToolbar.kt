package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.CornerUpLeft
import compose.icons.feathericons.CornerUpRight
import compose.icons.feathericons.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun SideToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Undo Button
        SideControlButton(
            icon = FeatherIcons.CornerUpLeft,
            contentDescription = "Undo",
            onClick = onUndo,
            enabled = canUndo
        )

        // Redo Button
        SideControlButton(
            icon = FeatherIcons.CornerUpRight,
            contentDescription = "Redo",
            onClick = onRedo,
            enabled = canRedo
        )

        // Add Image Button
        SideControlButton(
            icon = FeatherIcons.Image,
            contentDescription = "Add Image",
            onClick = onAddImage,
            enabled = true
        )
    }
}

@Composable
fun SideControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color? = null,
    containerColor: androidx.compose.ui.graphics.Color? = null
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(containerColor ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp)
        )
    }
}

package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OverlaySideToolbar(
    selectedOverlayId: String?,
    isTextOverlay: Boolean,
    onEdit: () -> Unit = {},
    onColor: () -> Unit = {},
    onFont: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onCrop: () -> Unit = {},
    onFilters: () -> Unit = {},
    onOpacity: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = selectedOverlayId != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (isTextOverlay) {
                ToolbarIcon(icon = Icons.Rounded.Edit, contentDescription = "Edit", onClick = onEdit)
                ToolbarIcon(icon = Icons.Rounded.ColorLens, contentDescription = "Color", onClick = onColor)
                ToolbarIcon(icon = Icons.Rounded.FontDownload, contentDescription = "Font", onClick = onFont)
            } else {
                ToolbarIcon(icon = Icons.Rounded.ContentCopy, contentDescription = "Duplicate", onClick = onDuplicate)
                ToolbarIcon(icon = Icons.Rounded.Crop, contentDescription = "Crop", onClick = onCrop, enabled = false)
                ToolbarIcon(icon = Icons.Rounded.AutoFixHigh, contentDescription = "Filters", onClick = onFilters)
                ToolbarIcon(icon = Icons.Rounded.Opacity, contentDescription = "Opacity", onClick = onOpacity)
            }
            ToolbarIcon(
                icon = Icons.Rounded.Delete,
                contentDescription = "Delete",
                onClick = onDelete,
                tint = Color(0xFFE84855)
            )
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    enabled: Boolean = true
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (enabled) tint else tint.copy(alpha = 0.5f),
        modifier = Modifier
            .shadow(4.dp, CircleShape)
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(2.dp)
    )
}

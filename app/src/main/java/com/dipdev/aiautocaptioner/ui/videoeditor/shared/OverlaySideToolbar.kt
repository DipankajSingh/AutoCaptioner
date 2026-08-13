package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OverlaySideToolbar(
    selectedOverlayId: String?,
    isTextOverlay: Boolean,
    onFontSize: () -> Unit = {},
    onEdit: () -> Unit = {},
    onColorMenuClicked: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onCrop: () -> Unit = {},
    onFilters: () -> Unit = {},
    onOpacity: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val visible = selectedOverlayId != null
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isTextOverlay) {
            ToolbarIcon(icon = Icons.Rounded.FormatSize, contentDescription = "Text size", onClick = onFontSize, visible = visible)
            ToolbarIcon(icon = Icons.Rounded.Edit, contentDescription = "Edit", onClick = onEdit, visible = visible)
            ToolbarIcon(icon = Icons.Rounded.ColorLens, contentDescription = "Color", onClick = onColorMenuClicked, visible = visible)
        } else {
            ToolbarIcon(icon = Icons.Rounded.ContentCopy, contentDescription = "Duplicate", onClick = onDuplicate, visible = visible)
            ToolbarIcon(icon = Icons.Rounded.Crop, contentDescription = "Crop", onClick = onCrop, visible = visible)
            ToolbarIcon(icon = Icons.Rounded.AutoFixHigh, contentDescription = "Filters", onClick = onFilters, visible = visible)
            ToolbarIcon(icon = Icons.Rounded.Opacity, contentDescription = "Opacity", onClick = onOpacity, visible = visible)
        }
        ToolbarIcon(
            icon = Icons.Rounded.Delete,
            contentDescription = "Delete",
            onClick = onDelete,
            tint = Color(0xFFE84855),
            visible = visible
        )
    }
}

@Composable
private fun ToolbarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    enabled: Boolean = true,
    visible: Boolean = true
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
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
}

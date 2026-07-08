package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor

@Composable
fun VideoEditorToolbar(
    selectedClipId: String?,
    zoomLevel: Float,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    val hasSelection = selectedClipId != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Group 1: Edit actions
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                Icons.Outlined.ContentCut, 
                "Split", 
                tint = LocalAccentColor.current,
                modifier = Modifier.size(24.dp).clickable { onSplit() }
            )
            Icon(
                Icons.Outlined.ContentCopy, 
                "Duplicate", 
                tint = if (hasSelection) LocalAccentColor.current else LocalAccentColor.current.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp).clickable(enabled = hasSelection) { onDuplicate() }
            )
            Icon(
                Icons.Outlined.Delete, 
                "Delete", 
                tint = if (hasSelection) AccentRose else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp).clickable(enabled = hasSelection) { onDelete() }
            )
        }

        // Group 3: Zoom
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Remove, 
                "Zoom Out", 
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp).clickable { onZoomOut() }
            )
            Text(
                text = "${(zoomLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Icon(
                Icons.Outlined.Add, 
                "Zoom In", 
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp).clickable { onZoomIn() }
            )
        }
    }
}


@Composable
fun AudioToolbar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Audio tools coming soon...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = LocalAccentColor.current
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else tint.copy(alpha = 0.38f)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun CompactTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) LocalAccentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

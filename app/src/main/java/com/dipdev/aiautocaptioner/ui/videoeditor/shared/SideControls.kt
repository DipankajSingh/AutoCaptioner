package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor

@Composable
fun SideControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isActive: Boolean = false,
    tint: Color? = null,
    containerColor: Color? = null
) {
    val accent = LocalAccentColor.current
    val finalContainerColor = if (isActive) accent else (containerColor ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
    val finalTint = if (isActive) MaterialTheme.colorScheme.onPrimary else (tint ?: if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(finalContainerColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = finalTint,
            modifier = Modifier.size(24.dp)
        )
    }
}
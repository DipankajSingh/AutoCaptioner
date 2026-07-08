package com.dipdev.aiautocaptioner.ui.videoeditor.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowDown
import compose.icons.feathericons.ArrowUp
import compose.icons.feathericons.Trash2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity

@Composable
fun OverlayActionMenu(
    selectedOverlay: ImageOverlayEntity,
    onFullVideo: () -> Unit,
    onSendToBack: () -> Unit,
    onBringToFront: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onFullVideo,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text("Full Video", color = MaterialTheme.colorScheme.onSurface)
        }
        
        IconButton(
            onClick = onSendToBack
        ) {
            Icon(FeatherIcons.ArrowDown, contentDescription = "Send to Back", tint = MaterialTheme.colorScheme.onSurface)
        }
        
        IconButton(
            onClick = onBringToFront
        ) {
            Icon(FeatherIcons.ArrowUp, contentDescription = "Bring to Front", tint = MaterialTheme.colorScheme.onSurface)
        }
        
        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Icon(FeatherIcons.Trash2, contentDescription = "Delete Overlay", tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(4.dp))
            Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

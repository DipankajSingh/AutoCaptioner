package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Image
import compose.icons.feathericons.Type
import com.dipdev.aiautocaptioner.R

@Composable
fun GlobalActionButtons(
    onAddImage: () -> Unit,
    onAddText: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 16.dp, top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = FeatherIcons.Type,
            contentDescription = "Add Text",
            tint = Color.White,
            modifier = Modifier
                .size(28.dp)
                .clickable { onAddText() }
        )
        
        Icon(
            imageVector = FeatherIcons.Image,
            contentDescription = stringResource(R.string.timeline_add_image),
            tint = Color.White,
            modifier = Modifier
                .size(28.dp)
                .clickable { onAddImage() }
        )
    }
}

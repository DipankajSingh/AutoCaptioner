package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.recorder.SidebarButton
import com.dipdev.aiautocaptioner.ui.recorder.model.AspectRatio
import compose.icons.FeatherIcons
import compose.icons.feathericons.Maximize

@Composable
fun AspectRatioButton(
    currentRatio: AspectRatio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        SidebarButton(
            icon = FeatherIcons.Maximize,
            text = stringResource(R.string.recorder_ratio),
            isActive = currentRatio != AspectRatio.PORTRAIT_9_16,
            onClick = onClick
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = currentRatio.displayLabel,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

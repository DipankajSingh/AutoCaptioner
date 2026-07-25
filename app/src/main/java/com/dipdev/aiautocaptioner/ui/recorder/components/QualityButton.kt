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
import com.dipdev.aiautocaptioner.ui.recorder.model.RecordingQuality
import compose.icons.FeatherIcons
import compose.icons.feathericons.Monitor

@Composable
fun QualityButton(
    currentQuality: RecordingQuality,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        SidebarButton(
            icon = FeatherIcons.Monitor,
            text = stringResource(R.string.recorder_quality),
            isActive = currentQuality != RecordingQuality.MEDIUM,
            onClick = onClick
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = currentQuality.label,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

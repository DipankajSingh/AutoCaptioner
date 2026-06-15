package com.dipdev.aiautocaptioner.ui.processing.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.processing.ProcessingStep

@SuppressLint("DefaultLocale")
@Composable
fun DownloadingStateView(step: ProcessingStep.DownloadingModel) {
    val animatedProgress by animateFloatAsState(
        targetValue = step.progress / 100f,
        animationSpec = tween(300),
        label = "download_progress"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(120.dp),
                strokeWidth = 6.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Round
            )
            Text(
                text = "${step.progress}%",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Downloading ${step.modelName}",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (step.totalBytes > 0) {
            Text(
                text = "${formatBytes(step.downloadedBytes)} / ${formatBytes(step.totalBytes)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "One-time download · Keep app open",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@SuppressLint("DefaultLocale")
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
    return String.format("%.1f %sB", bytes.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
}

package com.dipdev.aiautocaptioner.ui.recorder.components

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun StorageIndicator(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val storageInfo = remember {
        val filesDir = context.filesDir
        val stat = StatFs(filesDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val availableGB = availableBytes / (1024.0 * 1024.0 * 1024.0)
        val estimatedMinutes = (availableBytes / (4_000_000.0 / 8.0 * 60.0)).toInt()
        Pair(availableGB, estimatedMinutes)
    }

    val availableGB = storageInfo.first
    val estimatedMinutes = storageInfo.second
    val isLow = availableGB < 2.0

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, if (isLow) Color(0xFFFF6B6B).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = String.format(Locale.US, "%.1f GB free · ~%d min", availableGB, estimatedMinutes),
            color = if (isLow) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

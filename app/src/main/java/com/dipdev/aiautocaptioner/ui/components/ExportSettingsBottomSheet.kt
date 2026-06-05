package com.dipdev.aiautocaptioner.ui.components

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSettingsBottomSheet(
    videoPath: String?,
    onDismissRequest: () -> Unit,
    onExportClicked: (bitrate: Int?, fps: Int?, height: Int?) -> Unit
) {
    val context = LocalContext.current
    
    // Original Video Metadata
    var originalWidth by remember { mutableIntStateOf(1080) }
    var originalHeight by remember { mutableIntStateOf(1920) }
    var originalBitrate by remember { mutableIntStateOf(5_000_000) }
    var originalDurationMs by remember { mutableLongStateOf(0L) }
    var originalFps by remember { mutableIntStateOf(30) }

    // User Selections
    var selectedHeight by remember { mutableIntStateOf(-1) } // -1 means Original
    var selectedFps by remember { mutableIntStateOf(-1) }
    var selectedQuality by remember { mutableIntStateOf(1) } // 0: Low, 1: Recommended, 2: High

    LaunchedEffect(videoPath) {
        if (videoPath != null) {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, android.net.Uri.parse(videoPath))
                    
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    
                    if (rotation == 90 || rotation == 270) {
                        originalWidth = h
                        originalHeight = w
                    } else {
                        originalWidth = w
                        originalHeight = h
                    }

                    originalBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 5_000_000
                    originalDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    
                    // Note: MediaMetadataRetriever doesn't reliably give FPS on all devices, but we can try frame count / duration
                    // For simplicity, we'll assume 30fps default if we can't get it.
                    originalFps = 30
                    
                    retriever.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Calculate effective targets
    val targetHeight = if (selectedHeight == -1) originalHeight else selectedHeight
    val targetFps = if (selectedFps == -1) originalFps else selectedFps
    
    val targetBitrate = when (selectedQuality) {
        0 -> (originalBitrate * 0.6).toInt() // Low Quality
        1 -> originalBitrate                 // Recommended (Match Original)
        else -> (originalBitrate * 1.5).toInt() // High Quality
    }

    // File Size Estimation: Bitrate (bits per second) * Duration (seconds) / 8 (bytes)
    val estimatedSizeBytes = (targetBitrate.toLong() * (originalDurationMs / 1000.0)) / 8.0
    val estimatedSizeMB = estimatedSizeBytes / (1024 * 1024)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(com.dipdev.aiautocaptioner.R.string.export_settings),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Resolution
            Text(stringResource(com.dipdev.aiautocaptioner.R.string.export_resolution), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedHeight == -1,
                    onClick = { selectedHeight = -1 },
                    label = { Text(stringResource(com.dipdev.aiautocaptioner.R.string.export_original)) }
                )
                FilterChip(
                    selected = selectedHeight == 1920, // 1080p height usually 1920
                    onClick = { selectedHeight = 1920 },
                    label = { Text("1080p") }
                )
                FilterChip(
                    selected = selectedHeight == 1280, // 720p height usually 1280
                    onClick = { selectedHeight = 1280 },
                    label = { Text("720p") }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Frame Rate
            Text(stringResource(com.dipdev.aiautocaptioner.R.string.export_frame_rate), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFps == -1,
                    onClick = { selectedFps = -1 },
                    label = { Text(stringResource(com.dipdev.aiautocaptioner.R.string.export_original)) }
                )
                FilterChip(
                    selected = selectedFps == 30,
                    onClick = { selectedFps = 30 },
                    label = { Text("30 fps") }
                )
                FilterChip(
                    selected = selectedFps == 60,
                    onClick = { selectedFps = 60 },
                    label = { Text("60 fps") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quality
            Text(stringResource(com.dipdev.aiautocaptioner.R.string.export_quality), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedQuality == 0,
                    onClick = { selectedQuality = 0 },
                    label = { Text(stringResource(com.dipdev.aiautocaptioner.R.string.export_quality_low)) }
                )
                FilterChip(
                    selected = selectedQuality == 1,
                    onClick = { selectedQuality = 1 },
                    label = { Text(stringResource(com.dipdev.aiautocaptioner.R.string.export_quality_recommended)) }
                )
                FilterChip(
                    selected = selectedQuality == 2,
                    onClick = { selectedQuality = 2 },
                    label = { Text(stringResource(com.dipdev.aiautocaptioner.R.string.export_quality_high)) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Estimated Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(com.dipdev.aiautocaptioner.R.string.export_estimated_size),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("~%.1f MB", estimatedSizeMB),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onExportClicked(
                        targetBitrate, 
                        if (selectedFps == -1) null else selectedFps, 
                        if (selectedHeight == -1) null else selectedHeight
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(com.dipdev.aiautocaptioner.R.string.export_button), fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

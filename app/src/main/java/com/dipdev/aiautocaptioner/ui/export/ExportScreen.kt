package com.dipdev.aiautocaptioner.ui.export

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState = uiState.exportState
    val progress = uiState.progress
    val outputPath = uiState.outputPath
    val workingVideoPath = uiState.workingVideoPath

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

    LaunchedEffect(workingVideoPath) {
        if (workingVideoPath != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, android.net.Uri.parse(workingVideoPath))
                    
                    val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
                    val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
                    val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    
                    if (rotation == 90 || rotation == 270) {
                        originalWidth = h
                        originalHeight = w
                    } else {
                        originalWidth = w
                        originalHeight = h
                    }

                    originalBitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 5_000_000
                    originalDurationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    originalFps = 30
                    retriever.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Calculate effective targets
    val computedTargetHeight = if (selectedHeight == -1) originalHeight else selectedHeight
    val computedTargetFps = if (selectedFps == -1) originalFps else selectedFps
    
    val computedTargetBitrate = when (selectedQuality) {
        0 -> (originalBitrate * 0.6).toInt() // Low Quality
        1 -> originalBitrate                 // Recommended (Match Original)
        else -> (originalBitrate * 1.5).toInt() // High Quality
    }

    // File Size Estimation: Bitrate (bits per second) * Duration (seconds) / 8 (bytes)
    val estimatedSizeBytes = (computedTargetBitrate.toLong() * (originalDurationMs / 1000.0)) / 8.0
    val estimatedSizeMB = estimatedSizeBytes / (1024 * 1024)

    LaunchedEffect(Unit) {
        viewModel.setEvent(ExportUiEvent.PrepareExport(projectId))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors= TopAppBarDefaults.topAppBarColors(Color.Transparent),
                title = { Text("Export Video", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = exportState) {

                // ── Success / Already Exported ────────────────────────────
                is ExportState.AlreadyExported,
                is ExportState.Success,
                is ExportState.SavedToGallery -> {
                    val path = outputPath

                    if (state is ExportState.Success || state is ExportState.SavedToGallery) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = when (state) {
                            is ExportState.Success        -> "Export Complete!"
                            is ExportState.SavedToGallery -> "Saved to Gallery!"
                            else                          -> "Previously Exported"
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Video preview
                    if (path != null) {
                        VideoPlayerCard(
                            path     = path,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Video file not found", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {


                        // Save to Gallery
                        Button(
                            onClick = {
                                if (path != null) viewModel.setEvent(ExportUiEvent.SaveToGallery(path))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gallery", fontSize = 13.sp , softWrap = false)
                        }

                        // Share
                        Button(
                            onClick = {
                                if (path != null) {
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            viewModel.shareVideo(path), "Share Video"
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 13.sp,  softWrap = false)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ){
                        // Re-export
                        OutlinedButton(
                            onClick = { viewModel.setEvent(ExportUiEvent.ResetForReExport) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-export", fontSize = 13.sp, softWrap = false )
                        }

                        TextButton(onClick = onNavigateBack, modifier = Modifier.weight(1f)) {
                            Text("Done")
                        }
                    }


                }

                // ── Idle / Ready ─────────────────────────────────────────
                is ExportState.Idle,
                is ExportState.Ready -> {
                    Text("Export Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))

                    // Resolution
                    SegmentedSelector(
                        title = "Resolution",
                        options = listOf(-1 to "Original", 1920 to "1080p", 1280 to "720p"),
                        selected = selectedHeight,
                        onSelect = { selectedHeight = it }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Frame Rate
                    SegmentedSelector(
                        title = "Frame Rate",
                        options = listOf(-1 to "Original", 30 to "30 fps", 60 to "60 fps"),
                        selected = selectedFps,
                        onSelect = { selectedFps = it }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Quality
                    SegmentedSelector(
                        title = "Quality",
                        options = listOf(0 to "Low", 1 to "Recommended", 2 to "High"),
                        selected = selectedQuality,
                        onSelect = { selectedQuality = it }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Estimated Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated File Size:",
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

                    Spacer(modifier = Modifier.weight(1f))
                    
                    Button(
                        onClick = { viewModel.setEvent(ExportUiEvent.StartExport(projectId, computedTargetBitrate, if (selectedFps == -1) null else selectedFps, if (selectedHeight == -1) null else selectedHeight)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Start Export", fontSize = 16.sp, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("Cancel", maxLines = 1) }
                }

                // ── Running ──────────────────────────────────────────────
                is ExportState.Running -> {
                    Spacer(modifier = Modifier.height(16.dp))

                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.export))

                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier
                            .aspectRatio(1f)
                    )

                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(300),
                        label = "export_progress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Rendering Video...", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    OutlinedButton(
                        onClick = { viewModel.setEvent(ExportUiEvent.CancelExport) },
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("Cancel", maxLines = 1) }
                }

                // ── Cancelled ────────────────────────────────────────────
                is ExportState.Cancelled -> {

                    Icon(
                        imageVector = (Icons.Filled.Cancel),
                        contentDescription = "Cancelled",
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(50.dp))
                    Text("Export Cancelled", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The export was stopped before completion.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.setEvent(ExportUiEvent.StartExport(projectId, computedTargetBitrate, if (selectedFps == -1) null else selectedFps, if (selectedHeight == -1) null else selectedHeight)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("Try Again", maxLines = 1) }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("Go Back", maxLines = 1) }
                }

                // ── Error ────────────────────────────────────────────────
                is ExportState.Error -> {
                    Text("Export Failed", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.setEvent(ExportUiEvent.StartExport(projectId, computedTargetBitrate, if (selectedFps == -1) null else selectedFps, if (selectedHeight == -1) null else selectedHeight)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("Retry", maxLines = 1) }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("Go Back", maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun <T> SegmentedSelector(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Text(
        title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            androidx.compose.material3.FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}

package com.dipdev.aiautocaptioner.ui.export

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.theme.Dimens
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.Download
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Share2
import compose.icons.feathericons.XCircle
import androidx.core.net.toUri

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState = uiState.exportState
    val progress = uiState.progress
    val outputPath = uiState.outputPath
    val workingVideoPath = uiState.workingVideoPath
    val hasCaptions = uiState.hasCaptions
    var showNoCaptionsDialog by remember { mutableStateOf(false) }

    var originalWidth by remember { mutableIntStateOf(1080) }
    var originalHeight by remember { mutableIntStateOf(1920) }
    var originalBitrate by remember { mutableIntStateOf(5_000_000) }
    var originalDurationMs by remember { mutableLongStateOf(0L) }

    var selectedHeight by remember(uiState.savedResolution) { mutableIntStateOf(uiState.savedResolution) }
    var selectedFps by remember(uiState.savedFps) { mutableIntStateOf(uiState.savedFps) }
    var selectedQuality by remember(uiState.savedQuality) { mutableIntStateOf(uiState.savedQuality) }

    LaunchedEffect(workingVideoPath) {
        if (workingVideoPath != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, workingVideoPath.toUri())
                    val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
                    val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
                    val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rotation == 90 || rotation == 270) { originalWidth = h; originalHeight = w }
                    else { originalWidth = w; originalHeight = h }
                    originalBitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 5_000_000
                    originalDurationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (e: Exception) { e.printStackTrace() }
                finally { try { retriever.release() } catch (_: Exception) {} }
            }
        }
    }

    val computedTargetBitrate = remember(selectedQuality, originalBitrate) {
        when (selectedQuality) {
            0 -> (originalBitrate * 0.6).toInt()
            1 -> originalBitrate
            else -> (originalBitrate * 1.5).toInt()
        }
    }
    val estimatedSizeMB = remember(computedTargetBitrate, originalDurationMs) {
        (computedTargetBitrate.toLong() * (originalDurationMs / 1000.0)) / 8.0 / (1024 * 1024)
    }

    LaunchedEffect(Unit) { viewModel.setEvent(ExportUiEvent.PrepareExport(projectId)) }

    val accent = LocalAccentColor.current

    ScreenThemeProvider(accentColor = accent) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                    title = { Text("Export Video", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(FeatherIcons.ArrowLeft, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showNoCaptionsDialog) {
                    AlertDialog(
                        onDismissRequest = { showNoCaptionsDialog = false },
                        title = { Text("No Captions") },
                        text = { Text("No captions generated for this video. Export without captions?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showNoCaptionsDialog = false
                                viewModel.saveSettings(selectedHeight, selectedFps, selectedQuality)
                                viewModel.setEvent(ExportUiEvent.StartExport(projectId, computedTargetBitrate, if (selectedFps == -1) null else selectedFps, if (selectedHeight == -1) null else selectedHeight))
                            }) { Text("Export Anyway") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNoCaptionsDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                when (exportState) {

                    // ── Idle / Ready ─────────────────────────────────────────
                    is ExportState.Idle,
                    is ExportState.Ready -> {
                        if (workingVideoPath != null) {
                            VideoPlayerCard(
                                path = workingVideoPath,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(originalWidth.toFloat() / originalHeight.toFloat().coerceAtLeast(1f))
                                    .clip(RoundedCornerShape(Dimens.Radius.large))
                            )
                            Spacer(Modifier.height(Dimens.Padding.large))
                        }

                        Text("Export Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(Dimens.Padding.large))

                        SegmentedSelector(
                            title = "Resolution",
                            options = listOf(-1 to "Original", 1920 to "1080p", 1280 to "720p"),
                            selected = selectedHeight,
                            onSelect = { selectedHeight = it }
                        )
                        Spacer(Modifier.height(Dimens.Padding.medium))

                        SegmentedSelector(
                            title = "Frame Rate",
                            options = listOf(-1 to "Original", 30 to "30 fps", 60 to "60 fps"),
                            selected = selectedFps,
                            onSelect = { selectedFps = it }
                        )
                        Spacer(Modifier.height(Dimens.Padding.medium))

                        SegmentedSelector(
                            title = "Quality",
                            options = listOf(0 to "Low", 1 to "Recommended", 2 to "High"),
                            selected = selectedQuality,
                            onSelect = { selectedQuality = it }
                        )
                        Spacer(Modifier.height(Dimens.Padding.large))

                        Surface(
                            shape = RoundedCornerShape(Dimens.Radius.medium),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Est. File Size", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "~%.1f MB".format(estimatedSizeMB),
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent
                                )
                            }
                        }

                        Spacer(Modifier.height(Dimens.Padding.large))

                        Button(
                            onClick = {
                                if (!hasCaptions) showNoCaptionsDialog = true
                                else {
                                    viewModel.saveSettings(selectedHeight, selectedFps, selectedQuality)
                                    viewModel.setEvent(ExportUiEvent.StartExport(projectId, computedTargetBitrate, if (selectedFps == -1) null else selectedFps, if (selectedHeight == -1) null else selectedHeight))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(FeatherIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start Export", fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))
                        AppOutlinedButton(onClick = onNavigateBack) {
                            Text("Cancel")
                        }
                    }

                    // ── Running ──────────────────────────────────────────────
                    is ExportState.Running -> {
                        Spacer(Modifier.height(Dimens.Padding.extraLarge))

                        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.export))
                        LottieAnimation(
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                            modifier = Modifier.size(140.dp)
                        )
                        Spacer(Modifier.height(Dimens.Padding.large))

                        Text("Rendering Video", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(Dimens.Padding.small))

                        val animatedProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(300),
                            label = "export_progress"
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                ) {
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier.fillMaxSize(),
                                        color = accent,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${(progress * 100).toInt()}%",
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent
                                    )
                                    Text(
                                        "~%.1f MB".format(estimatedSizeMB),
                                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(Dimens.Padding.extraLarge))
                        AppOutlinedButton(onClick = { viewModel.setEvent(ExportUiEvent.CancelExport) }) {
                            Text("Cancel")
                        }
                    }

                    // ── Success / Already Exported ────────────────────────────
                    is ExportState.AlreadyExported,
                    is ExportState.Success,
                    is ExportState.SavedToGallery -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(accent.copy(alpha = 0.15f), accent.copy(alpha = 0.03f))
                                    )
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(FeatherIcons.CheckCircle, null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when (exportState) {
                                        is ExportState.Success -> "Export Complete"
                                        is ExportState.SavedToGallery -> "Saved to Gallery"
                                        else -> "Previously Exported"
                                    },
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent
                                )
                            }
                        }
                        Spacer(Modifier.height(Dimens.Padding.medium))

                        if (outputPath != null) {
                            VideoPlayerCard(
                                path = outputPath,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(originalWidth.toFloat() / originalHeight.toFloat().coerceAtLeast(1f))
                                    .clip(RoundedCornerShape(Dimens.Radius.large))
                            )
                        } else {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(9f / 16f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Video file not found", color = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(Modifier.height(Dimens.Padding.large))

                        Button(
                            onClick = {
                                if (outputPath != null) viewModel.setEvent(ExportUiEvent.SaveToGallery(outputPath))
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(FeatherIcons.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save to Gallery")
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))

                        Button(
                            onClick = { if (outputPath != null) viewModel.shareVideo(outputPath) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(FeatherIcons.Share2, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share")
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppOutlinedButton(
                                onClick = { viewModel.setEvent(ExportUiEvent.ResetForReExport) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(FeatherIcons.RefreshCw, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Re-export")
                            }
                            AppPrimaryButton(
                                onClick = onNavigateToHome,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Done")
                            }
                        }
                    }

                    // ── Cancelled ────────────────────────────────────────────
                    is ExportState.Cancelled -> {
                        Spacer(Modifier.height(Dimens.Padding.extraLarge))
                        Icon(
                            FeatherIcons.XCircle, contentDescription = "Cancelled",
                            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(Dimens.Padding.large))
                        Text("Export Cancelled", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(Dimens.Padding.small))
                        Text(
                            "The export was stopped before completion.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(Dimens.Padding.extraLarge))

                        Button(
                            onClick = {
                                if (!hasCaptions) showNoCaptionsDialog = true
                                else {
                                    viewModel.saveSettings(selectedHeight, selectedFps, selectedQuality)
                                    viewModel.setEvent(ExportUiEvent.StartExport(projectId, computedTargetBitrate, if (selectedFps == -1) null else selectedFps, if (selectedHeight == -1) null else selectedHeight))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(FeatherIcons.RefreshCw, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Try Again")
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))
                        AppOutlinedButton(onClick = onNavigateBack) { Text("Go Back") }
                    }

                    // ── Error ────────────────────────────────────────────────
                    is ExportState.Error -> {
                        Spacer(Modifier.height(Dimens.Padding.extraLarge))
                        Icon(
                            FeatherIcons.XCircle, contentDescription = "Error",
                            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(Dimens.Padding.large))
                        Text("Export Failed", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(Dimens.Padding.small))
                        Text(
                            exportState.message,
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Dimens.Padding.extraLarge))

                        Button(
                            onClick = {
                                if (!hasCaptions) showNoCaptionsDialog = true
                                else {
                                    viewModel.saveSettings(selectedHeight, selectedFps, selectedQuality)
                                    viewModel.setEvent(ExportUiEvent.StartExport(projectId, computedTargetBitrate, if (selectedFps == -1) null else selectedFps, if (selectedHeight == -1) null else selectedHeight))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(FeatherIcons.RefreshCw, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))
                        AppOutlinedButton(onClick = onNavigateBack) { Text("Go Back") }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> SegmentedSelector(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            val accent = LocalAccentColor.current
            Surface(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSelected) BorderStroke(1.5.dp, accent) else null
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

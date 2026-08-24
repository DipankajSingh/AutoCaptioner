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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.MascotMode
import com.dipdev.aiautocaptioner.ui.components.MascotRobot
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

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState  = uiState.exportState
    val accent       = LocalAccentColor.current

    // ── Derived / memoised values ─────────────────────────────────────────────
    val selectedHeight  = remember(uiState.savedResolution) { uiState.savedResolution }
    val selectedFps     = remember(uiState.savedFps)        { uiState.savedFps }
    val selectedQuality = remember(uiState.savedQuality)    { uiState.savedQuality }

    // Note: these are remembered on their inputs so they recompute only when
    // source metadata or user selection changes — not on every recomposition.
    val computedTargetBitrate = remember(selectedQuality, uiState.originalBitrate) {
        when (selectedQuality) {
            0    -> (uiState.originalBitrate * 0.6).toInt()
            2    -> (uiState.originalBitrate * 1.5).toInt()
            else -> uiState.originalBitrate
        }
    }
    val estimatedSizeMB = remember(
        computedTargetBitrate, uiState.originalDurationMs,
        selectedHeight, uiState.originalHeight,
        selectedFps, uiState.originalFps
    ) {
        val resScale = if (selectedHeight <= 0 || uiState.originalHeight <= 0) 1.0
                       else selectedHeight.toDouble() / uiState.originalHeight
        val fpsScale = if (selectedFps <= 0) 1.0
                       else selectedFps.toDouble() / uiState.originalFps.toDouble()
        (computedTargetBitrate * resScale * fpsScale * (uiState.originalDurationMs / 1000.0)) / 8.0 / (1024 * 1024)
    }

    // Convenience lambda — built once, reused in Ready and Cancelled states
    // to avoid duplicating the StartExport call with its four args.
    val onStartExport: () -> Unit = remember(projectId, selectedHeight, selectedFps, selectedQuality, computedTargetBitrate) {
        {
            viewModel.saveSettings(selectedHeight, selectedFps, selectedQuality)
            viewModel.setEvent(
                ExportUiEvent.StartExport(
                    projectId     = projectId,
                    targetBitrate = computedTargetBitrate,
                    targetFps     = selectedFps.takeIf  { it > 0 },
                    targetHeight  = selectedHeight.takeIf { it > 0 }
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setEvent(ExportUiEvent.PrepareExport(projectId))
    }

    ScreenThemeProvider(accentColor = accent) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                    title  = {
                        Text(
                            stringResource(R.string.export_video_title),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.cd_go_back))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (exportState) {

                    // ── Idle / Ready ──────────────────────────────────────────
                    is ExportState.Idle,
                    is ExportState.Ready -> {
                        ExportSettingsContent(
                            modifier          = Modifier.weight(1f),
                            uiState           = uiState,
                            accent            = accent,
                            estimatedSizeMB   = estimatedSizeMB,
                            onResolutionSelect = { viewModel.saveSettings(it, selectedFps, selectedQuality) },
                            onFpsSelect        = { viewModel.saveSettings(selectedHeight, it, selectedQuality) },
                            onQualitySelect    = { viewModel.saveSettings(selectedHeight, selectedFps, it) }
                        )

                        Button(
                            onClick  = onStartExport,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(FeatherIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.export_start_button), fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))
                        AppOutlinedButton(onClick = onNavigateBack) {
                            Text(stringResource(R.string.export_go_back))
                        }
                    }

                    // ── Running ───────────────────────────────────────────────
                    is ExportState.Running -> {
                        ExportProgressContent(
                            modifier        = Modifier.weight(1f),
                            progress        = uiState.progress,
                            etaMs           = uiState.etaMs,
                            estimatedSizeMB = estimatedSizeMB,
                            accent          = accent
                        )
                        AppOutlinedButton(onClick = {
                            viewModel.setEvent(ExportUiEvent.CancelExport)
                        }) {
                            Text(stringResource(R.string.processing_cancel))
                        }
                    }

                    // ── Success / Already Exported / Saved ────────────────────
                    is ExportState.AlreadyExported,
                    is ExportState.Success,
                    is ExportState.SavedToGallery -> {
                        ExportSuccessContent(
                            modifier      = Modifier.weight(1f),
                            exportState   = exportState,
                            outputPath    = uiState.outputPath,
                            accent        = accent
                        )

                        Button(
                            onClick = {
                                uiState.outputPath?.let {
                                    viewModel.setEvent(ExportUiEvent.SaveToGallery(it))
                                }
                            },
                            enabled  = uiState.outputPath != null,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor   = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor     = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(FeatherIcons.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.export_save_to_gallery))
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppOutlinedButton(
                                onClick  = {
                                    uiState.outputPath?.let { viewModel.shareVideo(it) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(FeatherIcons.Share2, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.export_share))
                            }
                            AppOutlinedButton(
                                onClick  = { viewModel.setEvent(ExportUiEvent.ResetForReExport) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(FeatherIcons.RefreshCw, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.export_reexport))
                            }
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))
                        TextButton(onClick = onNavigateBack) {
                            Text(stringResource(R.string.export_done))
                        }
                    }

                    // ── Cancelled ─────────────────────────────────────────────
                    is ExportState.Cancelled -> {
                        TerminalStateContent(
                            modifier    = Modifier.weight(1f),
                            icon        = FeatherIcons.XCircle,
                            iconTint    = MaterialTheme.colorScheme.error,
                            title       = stringResource(R.string.export_cancelled_title),
                            description = stringResource(R.string.export_cancelled_desc)
                        )
                        Button(
                            onClick  = onStartExport,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(FeatherIcons.RefreshCw, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.export_try_again))
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))
                        AppOutlinedButton(onClick = onNavigateBack) {
                            Text(stringResource(R.string.export_go_back))
                        }
                    }

                    // ── Error ─────────────────────────────────────────────────
                    is ExportState.Error -> {
                        TerminalStateContent(
                            modifier    = Modifier.weight(1f),
                            icon        = FeatherIcons.XCircle,
                            iconTint    = MaterialTheme.colorScheme.error,
                            title       = stringResource(R.string.export_failed),
                            description = exportState.message,
                            descriptionColor = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick  = onStartExport,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(FeatherIcons.RefreshCw, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.export_retry))
                        }
                        Spacer(Modifier.height(Dimens.Padding.small))
                        AppOutlinedButton(onClick = onNavigateBack) {
                            Text(stringResource(R.string.export_go_back))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables — each state gets its own private function to keep
// ExportScreen's `when` block scannable at a glance.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExportSettingsContent(
    modifier: Modifier,
    uiState: ExportUiState,
    accent: Color,
    estimatedSizeMB: Double,
    onResolutionSelect: (Int) -> Unit,
    onFpsSelect: (Int) -> Unit,
    onQualitySelect: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Dimens.Padding.large))

        SegmentedSelector(
            title    = stringResource(R.string.export_resolution_label),
            options  = listOf(
                -1   to stringResource(R.string.export_original),
                1080 to stringResource(R.string.export_resolution_1080p),
                720  to stringResource(R.string.export_resolution_720p)
            ),
            selected = uiState.savedResolution,
            onSelect = onResolutionSelect
        )
        Spacer(Modifier.height(Dimens.Padding.medium))

        SegmentedSelector(
            title    = stringResource(R.string.export_frame_rate_label),
            options  = listOf(
                -1 to stringResource(R.string.export_original),
                30 to stringResource(R.string.export_frame_rate_30),
                60 to stringResource(R.string.export_frame_rate_60)
            ),
            selected = uiState.savedFps,
            onSelect = onFpsSelect
        )
        Spacer(Modifier.height(Dimens.Padding.medium))

        SegmentedSelector(
            title    = stringResource(R.string.export_quality),
            options  = listOf(
                0 to stringResource(R.string.export_quality_low),
                1 to stringResource(R.string.export_quality_recommended),
                2 to stringResource(R.string.export_quality_high)
            ),
            selected = uiState.savedQuality,
            onSelect = onQualitySelect
        )
        Spacer(Modifier.height(Dimens.Padding.large))

        EstimatedSizeChip(sizeMB = estimatedSizeMB, accent = accent)
    }
}

@Composable
private fun ExportProgressContent(
    modifier: Modifier,
    progress: Float,
    etaMs: Long?,
    estimatedSizeMB: Double,
    accent: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "export_progress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(Dimens.Padding.extraLarge))

        MascotRobot(mode = MascotMode.Exporting, modifier = Modifier.size(140.dp))
        Spacer(Modifier.height(Dimens.Padding.large))

        Text(
            stringResource(R.string.export_rendering),
            fontSize = 18.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(Dimens.Padding.small))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            LinearProgressIndicator(
                progress    = { animatedProgress },
                modifier    = Modifier.fillMaxSize(),
                color       = accent,
                trackColor  = MaterialTheme.colorScheme.surfaceVariant
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (etaMs == null) {
                stringResource(R.string.export_eta_estimating)
            } else {
                stringResource(R.string.export_eta_remaining, formatEta(etaMs))
            },
            fontSize  = 12.sp,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier  = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ExportSuccessContent(
    modifier: Modifier,
    exportState: ExportState,
    outputPath: String?,
    accent: Color
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                    text = when (exportState) {
                        is ExportState.Success        -> stringResource(R.string.export_complete)
                        is ExportState.SavedToGallery -> stringResource(R.string.export_saved_to_gallery)
                        else                          -> stringResource(R.string.export_previously_exported)
                    },
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent
                )
            }
        }

        if (outputPath != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                VideoPlayerCard(path = outputPath, modifier = Modifier.fillMaxSize())
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.export_file_not_found),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Reusable terminal state layout shared by [ExportState.Cancelled] and
 * [ExportState.Error] — avoids duplicating the icon + title + description
 * column structure.
 */
@Composable
private fun TerminalStateContent(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(Dimens.Padding.extraLarge))
        Icon(
            icon, contentDescription = title,
            modifier = Modifier.size(48.dp), tint = iconTint
        )
        Spacer(Modifier.height(Dimens.Padding.large))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = iconTint)
        Spacer(Modifier.height(Dimens.Padding.small))
        Text(
            description,
            color     = descriptionColor,
            textAlign = TextAlign.Center,
            fontSize  = 14.sp
        )
    }
}

@Composable
private fun EstimatedSizeChip(sizeMB: Double, accent: Color) {
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
            Text(
                stringResource(R.string.export_est_file_size),
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "~%.1f MB".format(sizeMB),
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = accent
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Segmented selector — unchanged from original
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun <T> SegmentedSelector(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            val accent     = LocalAccentColor.current
            Surface(
                onClick  = { onSelect(value) },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(10.dp),
                color    = if (isSelected) accent.copy(alpha = 0.15f)
                           else MaterialTheme.colorScheme.surfaceVariant,
                border   = if (isSelected) BorderStroke(1.5.dp, accent) else null
            ) {
                Text(
                    text       = label,
                    modifier   = Modifier
                        .padding(vertical = 10.dp)
                        .fillMaxWidth(),
                    textAlign  = TextAlign.Center,
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color      = if (isSelected) accent
                                 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

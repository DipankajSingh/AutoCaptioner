package com.dipdev.aiautocaptioner.ui.styleeditor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.FlatAlertDialog
import com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard
import com.dipdev.aiautocaptioner.ui.paywall.PaywallDialog
import com.dipdev.aiautocaptioner.ui.styleeditor.tabs.*
import kotlinx.coroutines.launch

@Composable
fun StyleEditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    onNavigateToExport: () -> Unit,
    viewModel: StyleEditorViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val styles by viewModel.styles.collectAsStateWithLifecycle()
    val activeStyle by viewModel.activeStyle.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    
    var showExportWarning by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<CaptionStyleEntity?>(null) }
    var showPaywall by remember { mutableStateOf(false) }
    var pendingPremiumTab by remember { mutableStateOf<StyleTab?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.exoPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.exoPlayer?.pause()
        }
    }

    BackHandler {
        viewModel.saveAndApply(projectId)
        onNavigateBack()
    }

    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val wordsMap by viewModel.wordsMap.collectAsStateWithLifecycle()
    val videoDurationMs by viewModel.videoDurationMs.collectAsStateWithLifecycle()

    LaunchedEffect(project?.workingVideoPath) {
        project?.workingVideoPath?.let { viewModel.initPlayer(it) }
    }

    LaunchedEffect(projectId) {
        viewModel.loadStyles(projectId)
    }

    if (showPaywall) {
        PaywallDialog(
            onDismiss = { showPaywall = false; pendingPremiumTab = null },
            onPurchase = {
                viewModel.unlockPremiumMock()
                showPaywall = false
                pendingPremiumTab?.let { viewModel.selectTab(it) }
                pendingPremiumTab = null
            }
        )
    }

    if (showExportWarning) {
        FlatAlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text("Review Recommended") },
            text = { Text("You haven't checked the accuracy of your AI captions. Please review them before exporting.") },
            confirmButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    onNavigateToCaptionEditor()
                }) {
                    Text("Review Captions")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    viewModel.saveAndApply(projectId)
                    onNavigateToExport()
                }) {
                    Text("Export Anyway")
                }
            }
        )
    }

    if (showPresetDialog) {
        var presetName by remember { mutableStateOf("My Preset") }
        FlatAlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (presetName.isNotBlank()) {
                        viewModel.saveAsNewPreset(presetName)
                    }
                    showPresetDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) { Text("Cancel") }
            }
        )
    }

    presetToDelete?.let { style ->
        FlatAlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete Preset") },
            text = { Text("Are you sure you want to delete '${style.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePreset(style)
                    presetToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // MAIN LAYOUT
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        
        // 1. TOP APP BAR
        Surface(
            color = Color(0xFF1E1E1E),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .windowInsetsPadding(WindowInsets.statusBars),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    viewModel.saveAndApply(projectId)
                    onNavigateBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (canUndo) Color.White else Color.Gray)
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (canRedo) Color.White else Color.Gray)
                    }
                    IconButton(onClick = { showPresetDialog = true }) {
                        Icon(Icons.Default.Save, "Save Preset", tint = Color.White)
                    }
                    IconButton(onClick = onNavigateToCaptionEditor) {
                        Icon(Icons.Default.ClosedCaption, "Edit Captions", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            if (project?.hasVisitedCaptionEditor == false) {
                                showExportWarning = true
                            } else {
                                viewModel.saveAndApply(projectId)
                                onNavigateToExport()
                            }
                        }
                    ) {
                        Icon(Icons.Default.FileDownload, "Export Video", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // 2. VIDEO VIEWPORT
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Takes all remaining space dynamically
                .background(Color.Black), // Blends with video
            contentAlignment = Alignment.Center
        ) {
            activeStyle?.let { style ->
                val isPortrait = (project?.videoRotation == 90 || project?.videoRotation == 270)
                val outWidth = if (isPortrait) project?.videoHeight ?: 1080 else project?.videoWidth ?: 1080
                val outHeight = if (isPortrait) project?.videoWidth ?: 1920 else project?.videoHeight ?: 1920

                VideoPreview(
                    style = style,
                    videoPath = project?.workingVideoPath,
                    videoWidth = outWidth,
                    videoHeight = outHeight,
                    segments = segments,
                    wordsMap = wordsMap,
                    durationMs = videoDurationMs,
                    exoPlayer = viewModel.exoPlayer,
                    onPositionYChange = viewModel::updatePositionY,
                    onSeek = viewModel::seekTo
                )
            }
        }

        // 3. BOTTOM PANEL
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E1E1E),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 8.dp)
            ) {
                // Tab Navigation
                StyleEditorBottomBar(
                    selectedTab = selectedTab,
                    isPremium = isPremium,
                    onTabSelected = { tab ->
                        if (tab != StyleTab.PRESETS && !isPremium) {
                            pendingPremiumTab = tab
                            showPaywall = true
                        } else {
                            viewModel.selectTab(tab)
                        }
                    }
                )

                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    activeStyle?.let { style ->
                        when (selectedTab) {
                            StyleTab.PRESETS -> {
                                PresetsTab(
                                    styles = styles,
                                    activeStyle = activeStyle,
                                    onPresetSelected = { viewModel.selectPreset(it) },
                                    onPresetLongClicked = { if (!it.isDefault) presetToDelete = it }
                                )
                            }
                            StyleTab.TEXT -> {
                                TextTab(
                                    style = style,
                                    onFontSizeChange = viewModel::updateFontSize,
                                    onFontWeightChange = viewModel::updateFontWeight,
                                    onMaxWordsChange = viewModel::updateMaxWordsPerLine,
                                    onMaxLinesChange = viewModel::updateMaxLines,
                                    onRemovePunctuationChange = viewModel::updateRemovePunctuation,
                                    onAlignmentChange = viewModel::updateAlignment,
                                    onLetterSpacingChange = viewModel::updateLetterSpacing,
                                    onIsItalicChange = viewModel::updateIsItalic
                                )
                            }
                            StyleTab.COLOR -> {
                                ColorTab(
                                    style = style,
                                    onTextColorChange = viewModel::updateTextColor,
                                    onHighlightColorChange = viewModel::updateHighlightColor,
                                    onOutlineColorChange = viewModel::updateOutlineColor,
                                    onOutlineWidthChange = viewModel::updateOutlineWidth,
                                    onBackgroundTypeChange = viewModel::updateBackgroundType,
                                    onBackgroundColorChange = viewModel::updateBackgroundColor,
                                    onBackgroundOpacityChange = viewModel::updateBackgroundOpacity,
                                    onBackgroundPaddingHChange = viewModel::updateBackgroundPaddingH,
                                    onBackgroundPaddingVChange = viewModel::updateBackgroundPaddingV,
                                    onBackgroundCornerRadiusChange = viewModel::updateBackgroundCornerRadius
                                )
                            }
                            StyleTab.ANIMATION -> {
                                AnimationTab(
                                    style = style,
                                    onDisplayModeChange = viewModel::updateDisplayMode,
                                    onWordEnterChange = viewModel::updateWordEnterAnimation,
                                    onKaraokeHighlightChange = viewModel::updateKaraokeHighlightMode,
                                    onAnimationDurationChange = viewModel::updateAnimationDurationMs
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

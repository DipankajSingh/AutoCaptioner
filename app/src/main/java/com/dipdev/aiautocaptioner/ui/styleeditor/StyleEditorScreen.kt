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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val project = uiState.project
    val styles = uiState.styles
    val activeStyle = uiState.activeStyle
    val selectedTab = uiState.selectedTab
    val canUndo = uiState.canUndo
    val canRedo = uiState.canRedo
    val isPremium = uiState.isPremium
    
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
        viewModel.setEvent(StyleEditorUiEvent.SaveAndApply(projectId))
        onNavigateBack()
    }

    val segments = uiState.segments
    val wordsMap = uiState.wordsMap
    val videoDurationMs = uiState.videoDurationMs

    LaunchedEffect(project?.workingVideoPath) {
        project?.workingVideoPath?.let { viewModel.setEvent(StyleEditorUiEvent.InitPlayer(it)) }
    }

    LaunchedEffect(projectId) {
        viewModel.setEvent(StyleEditorUiEvent.LoadStyles(projectId))
    }

    if (showPaywall) {
        PaywallDialog(
            onDismiss = { showPaywall = false; pendingPremiumTab = null },
            onPurchase = {
                viewModel.setEvent(StyleEditorUiEvent.UnlockPremiumMock)
                showPaywall = false
                pendingPremiumTab?.let { viewModel.setEvent(StyleEditorUiEvent.SelectTab(it)) }
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
                    viewModel.setEvent(StyleEditorUiEvent.SaveAndApply(projectId))
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
                        viewModel.setEvent(StyleEditorUiEvent.SaveAsNewPreset(presetName))
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
                    viewModel.setEvent(StyleEditorUiEvent.DeletePreset(style))
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
                    viewModel.setEvent(StyleEditorUiEvent.SaveAndApply(projectId))
                    onNavigateBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.setEvent(StyleEditorUiEvent.Undo) },
                        enabled = canUndo
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (canUndo) Color.White else Color.Gray)
                    }
                    IconButton(
                        onClick = { viewModel.setEvent(StyleEditorUiEvent.Redo) },
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
                                viewModel.setEvent(StyleEditorUiEvent.SaveAndApply(projectId))
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
                    onPositionYChange = { viewModel.setEvent(StyleEditorUiEvent.UpdatePositionY(it)) },
                    onSeek = { viewModel.setEvent(StyleEditorUiEvent.SeekTo(it)) }
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
                            viewModel.setEvent(StyleEditorUiEvent.SelectTab(tab))
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
                                    onPresetSelected = { viewModel.setEvent(StyleEditorUiEvent.SelectPreset(it)) },
                                    onPresetLongClicked = { if (!it.isDefault) presetToDelete = it }
                                )
                            }
                            StyleTab.TEXT -> {
                                TextTab(
                                    style = style,
                                    onFontSizeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateFontSize(it)) },
                                    onFontWeightChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateFontWeight(it)) },
                                    onMaxWordsChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateMaxWordsPerLine(it)) },
                                    onMaxLinesChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateMaxLines(it)) },
                                    onRemovePunctuationChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateRemovePunctuation(it)) },
                                    onAlignmentChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateAlignment(it)) },
                                    onLetterSpacingChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateLetterSpacing(it)) },
                                    onIsItalicChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateIsItalic(it)) }
                                )
                            }
                            StyleTab.COLOR -> {
                                ColorTab(
                                    style = style,
                                    onTextColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateTextColor(it)) },
                                    onHighlightColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateHighlightColor(it)) },
                                    onOutlineColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateOutlineColor(it)) },
                                    onOutlineWidthChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateOutlineWidth(it)) },
                                    onBackgroundTypeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundType(it)) },
                                    onBackgroundColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundColor(it)) },
                                    onBackgroundOpacityChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundOpacity(it)) },
                                    onBackgroundPaddingHChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundPaddingH(it)) },
                                    onBackgroundPaddingVChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundPaddingV(it)) },
                                    onBackgroundCornerRadiusChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundCornerRadius(it)) }
                                )
                            }
                            StyleTab.ANIMATION -> {
                                AnimationTab(
                                    style = style,
                                    onDisplayModeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateDisplayMode(it)) },
                                    onWordEnterChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateWordEnterAnimation(it)) },
                                    onKaraokeHighlightChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateKaraokeHighlightMode(it)) },
                                    onAnimationDurationChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateAnimationDurationMs(it)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

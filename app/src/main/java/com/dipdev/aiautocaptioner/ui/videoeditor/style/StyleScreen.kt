package com.dipdev.aiautocaptioner.ui.videoeditor.style

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.CornerUpRight
import compose.icons.feathericons.CornerUpLeft
import compose.icons.feathericons.MessageSquare
import compose.icons.feathericons.Download
import compose.icons.feathericons.MoreVertical
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.ui.components.FlatAlertDialog
import com.dipdev.aiautocaptioner.ui.components.InAppToast
import com.dipdev.aiautocaptioner.ui.paywall.CustomPaywallDialog
import com.dipdev.aiautocaptioner.ui.videoeditor.core.player.SharedPlayerViewModel
import com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs.AnimationTab
import com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs.ColorTab
import com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs.TextTab
import com.dipdev.aiautocaptioner.ui.theme.AccentViolet
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider

@Composable
fun StyleScreen(
    projectId: String,
    fromProcessing: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToProcessing: () -> Unit = {},
    // Fix A: shared player from NavGraph (navigation-graph-scoped)
    sharedPlayerViewModel: SharedPlayerViewModel,
    viewModel: StyleViewModel = hiltViewModel()
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
    var showOverflowMenu by remember { mutableStateOf(false) }
    // Show toast once on first entry from processing
    var toastTriggered by remember { mutableStateOf(fromProcessing) }

    // Fix A: collect shared player (owned by SharedPlayerViewModel)
    val player by sharedPlayerViewModel.player.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                sharedPlayerViewModel.pauseForBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sharedPlayerViewModel.pauseForBackground()
        }
    }

    BackHandler {
        viewModel.setEvent(StyleEditorUiEvent.SaveAndApply(projectId))
        onNavigateBack()
    }

    val segments = uiState.segments
    val wordsMap = uiState.wordsMap
    val videoDurationMs = uiState.videoDurationMs

    LaunchedEffect(projectId) {
        viewModel.setEvent(StyleEditorUiEvent.LoadStyles(projectId))
    }

    LaunchedEffect(isPremium) {
        if (isPremium && pendingPremiumTab != null) {
            viewModel.setEvent(StyleEditorUiEvent.SelectTab(pendingPremiumTab!!))
            pendingPremiumTab = null
            showPaywall = false
        }
    }

    val context = LocalContext.current

    if (showPaywall) {
        CustomPaywallDialog(
            isLoading = uiState.isPurchaseLoading,
            onPurchaseClick = {
                (context as? Activity)?.let { activity ->
                    viewModel.setEvent(StyleEditorUiEvent.PurchaseLifetime(activity))
                }
            },
            onRestoreClick = {
                viewModel.setEvent(StyleEditorUiEvent.RestorePurchases)
            },
            onDismissRequest = {
                showPaywall = false
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
                    // Fix 9: mark as visited so the warning won't reappear on the next export attempt
                    viewModel.setEvent(StyleEditorUiEvent.MarkCaptionEditorVisited(projectId))
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
    ScreenThemeProvider(accentColor = AccentViolet) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // 1. TOP APP BAR
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                IconButton(onClick = {
                    viewModel.setEvent(StyleEditorUiEvent.SaveAndApply(projectId))
                    onNavigateBack()
                }) {
                    Icon(FeatherIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(Modifier.weight(1f))

                // Undo
                IconButton(
                    onClick = { viewModel.setEvent(StyleEditorUiEvent.Undo) },
                    enabled = canUndo
                ) {
                    Icon(
                        FeatherIcons.CornerUpLeft, "Undo",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                // Redo
                IconButton(
                    onClick = { viewModel.setEvent(StyleEditorUiEvent.Redo) },
                    enabled = canRedo
                ) {
                    Icon(
                        FeatherIcons.CornerUpRight, "Redo",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                // Save preset
                IconButton(onClick = { showPresetDialog = true }) {
                    Icon(FeatherIcons.Save, "Save Preset", tint = MaterialTheme.colorScheme.onSurface)
                }
                // Caption editor
                IconButton(onClick = onNavigateToCaptionEditor) {
                    Icon(FeatherIcons.MessageSquare, "Edit Captions", tint = MaterialTheme.colorScheme.onSurface)
                }
                // Overflow menu (Re-transcribe)
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(FeatherIcons.MoreVertical, "More options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Re-transcribe") },
                            onClick = {
                                showOverflowMenu = false
                                onNavigateToProcessing()
                            },
                            leadingIcon = {
                                Icon(FeatherIcons.RefreshCw, contentDescription = null)
                            }
                        )
                    }
                }
                // Export — prominent labeled button
                Button(
                    onClick = {
                        if (project?.hasVisitedCaptionEditor == false) showExportWarning = true
                        else {
                            viewModel.setEvent(StyleEditorUiEvent.SaveAndApply(projectId))
                            onNavigateToExport()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(FeatherIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 2. VIDEO VIEWPORT
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Takes all remaining space dynamically
                .background(Color(0xFF000000)), // Intentional black for video letterboxing
            contentAlignment = Alignment.Center
        ) {
            activeStyle?.let { style ->
                val isPortrait = (project?.videoRotation == 90 || project?.videoRotation == 270)
                val outWidth = if (isPortrait) project.videoHeight else project?.videoWidth ?: 1080
                val outHeight = project?.videoHeight ?: 1920

                StylePreview(
                    style = style,
                    videoPath = project?.workingVideoPath,
                    segments = segments,
                    wordsMap = wordsMap,
                    durationMs = videoDurationMs,
                    // Fix A: `player` is collected from sharedPlayerViewModel.player StateFlow
                    exoPlayer = player,
                    onPositionYChange = { viewModel.setEvent(StyleEditorUiEvent.UpdatePositionY(it)) },
                    onSeek = { ms -> player?.seekTo(ms) }
                )
            }
        }

        // 3. BOTTOM PANEL
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
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
                                    onPresetLongClicked = { if (!it.isDefault) presetToDelete = it },
                                    onAddPreset = { showPresetDialog = true }
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
    } // ScreenThemeProvider

    // In-app toast — shown once when navigated from processing pipeline
    InAppToast(
        message = "Captions ready! Tap Export when you're done styling.",
        visible = toastTriggered,
        onDismiss = { toastTriggered = false }
    )
}

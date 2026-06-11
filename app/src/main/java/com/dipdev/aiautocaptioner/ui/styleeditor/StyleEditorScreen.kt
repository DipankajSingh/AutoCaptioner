package com.dipdev.aiautocaptioner.ui.styleeditor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.ui.components.FlatAlertDialog
import com.dipdev.aiautocaptioner.ui.styleeditor.tabs.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Palette
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleEditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    onNavigateToExport: () -> Unit,
    onSaved: () -> Unit,
    viewModel: StyleEditorViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val styles by viewModel.styles.collectAsStateWithLifecycle()
    val activeStyle by viewModel.activeStyle.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isCustomizing by viewModel.isCustomizing.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    var showExportWarning by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<CaptionStyleEntity?>(null) }

    BackHandler {
        viewModel.saveAndApply(projectId)
        onNavigateBack()
    }

    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val wordsMap by viewModel.wordsMap.collectAsStateWithLifecycle()
    val videoDurationMs by viewModel.videoDurationMs.collectAsStateWithLifecycle()

    // Init player once we have the video path — idempotent inside ViewModel
    LaunchedEffect(project?.workingVideoPath) {
        project?.workingVideoPath?.let { viewModel.initPlayer(it) }
    }

    LaunchedEffect(projectId) {
        viewModel.loadStyles(projectId)
    }

    Scaffold(

        topBar = {

            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                title = { Text("Editor", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveAndApply(projectId)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "Redo")
                    }
                    IconButton(onClick = { showPresetDialog = true }) {
                        Icon(Icons.Default.Save, "Save Preset")
                    }
                    IconButton(onClick = onNavigateToCaptionEditor) {
                        Icon(Icons.Default.ClosedCaption, "Edit Captions")
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
                        Icon(Icons.Default.FileDownload, "Export Video")
                    }
                }
            )
        }
    ) { padding ->

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            com.dipdev.aiautocaptioner.ui.components.PipelineProgressBar(
                currentStage = com.dipdev.aiautocaptioner.ui.components.PipelineStage.STYLE,
                onNavigateToStage = { stage ->
                    when (stage) {
                        com.dipdev.aiautocaptioner.ui.components.PipelineStage.REVIEW -> {
                            viewModel.saveAndApply(projectId)
                            onNavigateToCaptionEditor()
                        }
                        com.dipdev.aiautocaptioner.ui.components.PipelineStage.IMPORT,
                        com.dipdev.aiautocaptioner.ui.components.PipelineStage.AI_CAPTIONS -> {
                            viewModel.saveAndApply(projectId)
                            onNavigateBack() // Or a more specific route, but BackHandler does this too
                        }
                        else -> {}
                    }
                }
            )

            // Caption live preview taking available space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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

            // Contextual Tab content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                activeStyle?.let { style ->
                    if (!isCustomizing) {
                        // STATE A: Presets First
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PresetsTab(
                                styles = styles,
                                activeStyle = activeStyle,
                                onPresetSelected = { viewModel.selectPreset(it) },
                                onPresetLongClicked = { if (!it.isDefault) presetToDelete = it }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.setCustomizing(true) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Customize Style", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // STATE B: Detailed Customization
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // The editor controls
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                            ) {
                                when (selectedTab) {
                                    StyleTab.TEXT -> TextTab(
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
                                    StyleTab.COLOR -> ColorTab(
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
                                        onBackgroundCornerRadiusChange = viewModel::updateBackgroundCornerRadius,
                                        onShadowRadiusChange = viewModel::updateShadowRadius,
                                        onShadowColorChange = viewModel::updateShadowColor
                                    )
                                    StyleTab.ANIMATION -> AnimationTab(
                                        style = style,
                                        onDisplayModeChange = viewModel::updateDisplayMode,
                                        onWordEnterChange = viewModel::updateWordEnterAnimation,
                                        onWordExitChange = viewModel::updateWordExitAnimation,
                                        onKaraokeHighlightChange = viewModel::updateKaraokeHighlightMode,
                                        onAnimationDurationChange = viewModel::updateAnimationDurationMs
                                    )
                                    StyleTab.PRESETS -> { /* Hidden when customizing */ }
                                }
                            }
                            
                            // Bottom navigation for customization + Done button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    StyleEditorBottomBar(
                                        selectedTab = selectedTab,
                                        onTabSelected = { viewModel.selectTab(it) },
                                        isCustomizing = true
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.setCustomizing(false) },
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

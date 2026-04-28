package com.dipdev.autocaptioner.ui.styleeditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dipdev.autocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.autocaptioner.ui.styleeditor.tabs.*

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
    val project by viewModel.project.collectAsState()
    val styles by viewModel.styles.collectAsState()
    val activeStyle by viewModel.activeStyle.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    var showExportWarning by remember { mutableStateOf(false) }

    val segments by viewModel.segments.collectAsState()
    val wordsMap by viewModel.wordsMap.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val videoDurationMs by viewModel.videoDurationMs.collectAsState()

    // Init player once we have the video path — idempotent inside ViewModel
    LaunchedEffect(project?.workingVideoPath) {
        project?.workingVideoPath?.let { viewModel.initPlayer(it) }
    }

    var showPresets by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        viewModel.loadStyles(projectId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Style Editor", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
            AlertDialog(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

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
                        currentPositionMs = currentPositionMs,
                        durationMs = videoDurationMs,
                        exoPlayer = viewModel.exoPlayer,
                        onPositionChanged = { ms -> viewModel.updatePlaybackPosition(ms) },
                        onPositionYChange = viewModel::updatePositionY,
                        onSeek = viewModel::seekTo
                    )
                }
            }

            // Contextual Tab content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp) // Fixed height for options
            ) {
                activeStyle?.let { style ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (selectedTab) {
                            StyleTab.TEXT -> TextTab(
                                style = style,
                                onFontSizeChange = viewModel::updateFontSize,
                                onFontWeightChange = viewModel::updateFontWeight,
                                onMaxWordsChange = viewModel::updateMaxWordsPerLine,
                                onMaxLinesChange = viewModel::updateMaxLines,
                                onRemovePunctuationChange = viewModel::updateRemovePunctuation,
                                onAlignmentChange = viewModel::updateAlignment
                            )
                            StyleTab.COLOR -> ColorTab(
                                style = style,
                                onTextColorChange = viewModel::updateTextColor,
                                onHighlightColorChange = viewModel::updateHighlightColor,
                                onOutlineColorChange = viewModel::updateOutlineColor,
                                onOutlineWidthChange = viewModel::updateOutlineWidth,
                                onBackgroundTypeChange = viewModel::updateBackgroundType,
                                onBackgroundColorChange = viewModel::updateBackgroundColor,
                                onBackgroundOpacityChange = viewModel::updateBackgroundOpacity
                            )
                            StyleTab.ANIMATION -> AnimationTab(
                                style = style,
                                onDisplayModeChange = viewModel::updateDisplayMode,
                                onWordEnterChange = viewModel::updateWordEnterAnimation,
                                onWordExitChange = viewModel::updateWordExitAnimation,
                                onKaraokeHighlightChange = viewModel::updateKaraokeHighlightMode
                            )
                            StyleTab.PRESETS -> PresetsTab(
                                styles = styles,
                                activeStyle = activeStyle,
                                onPresetSelected = { viewModel.selectPreset(it) }
                            )
                        }
                    }
                }
            }



            // Icon-based bottom bar
            StyleEditorBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        }
    }
}

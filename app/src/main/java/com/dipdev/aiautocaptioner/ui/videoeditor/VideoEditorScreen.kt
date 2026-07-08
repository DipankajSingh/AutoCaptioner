package com.dipdev.aiautocaptioner.ui.videoeditor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor.StyleEditorUiEvent
import com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor.StyleEditorViewModel
import com.dipdev.aiautocaptioner.ui.videoeditor.components.BottomWorkspacePanel
import com.dipdev.aiautocaptioner.ui.videoeditor.components.CompactTabItem
import com.dipdev.aiautocaptioner.ui.videoeditor.components.LeftSideControlsPanel
import com.dipdev.aiautocaptioner.ui.videoeditor.components.MiniScrubber
import com.dipdev.aiautocaptioner.ui.videoeditor.components.RightSideControlsPanel
import com.dipdev.aiautocaptioner.ui.videoeditor.components.TimerPill
import com.dipdev.aiautocaptioner.ui.videoeditor.components.VideoPreviewSection
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun VideoEditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToProcessing: () -> Unit,
    viewModel: VideoEditorViewModel = hiltViewModel(),
    styleViewModel: StyleEditorViewModel = hiltViewModel()
) {
    ScreenThemeProvider(accentColor = AccentCyan) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val overlays by viewModel.overlays.collectAsStateWithLifecycle()
        val selectedOverlayId by viewModel.selectedOverlayId.collectAsStateWithLifecycle()
        
        val step = uiState.step
        val clips = uiState.clips
        val hasEdits = uiState.hasEdits
        val canUndo = uiState.canUndo
        val canRedo = uiState.canRedo
        val clipThumbnails = uiState.clipThumbnails
        val selectedLanguage = uiState.selectedLanguage
        val translateToEnglish = uiState.translateToEnglish

        var currentMode by remember { mutableStateOf(EditorMode.VIDEO) }
        var selectedClipId by remember { mutableStateOf<String?>(null) }
        var timelineHeight by remember { mutableStateOf(300.dp) }
        var zoomLevel by remember { mutableStateOf(1f) }
        var showBackDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { viewModel.setEvent(VideoEditorUiEvent.AddOverlay(it.toString())) }
        }

        BackHandler {
            if (hasEdits) showBackDialog = true else onNavigateBack()
        }

        LaunchedEffect(Unit) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is VideoEditorUiEffect.ProjectDeleted -> onNavigateBack()
                }
            }
        }

        LaunchedEffect(projectId) {
            viewModel.setEvent(VideoEditorUiEvent.LoadProject(projectId))
            styleViewModel.setEvent(StyleEditorUiEvent.LoadStyles(projectId))
        }

        LaunchedEffect(step) {
            if (step is VideoEditorUiStep.Success) {
                onNavigateToProcessing()
            }
        }

        // State Holder for ExoPlayer
        val originalVideoPath = (step as? VideoEditorUiStep.Ready)?.originalPath ?: ""
        val editorState = rememberVideoEditorState(
            clips = clips,
            originalVideoPath = originalVideoPath,
            onDurationUpdated = { duration ->
                viewModel.setEvent(VideoEditorUiEvent.UpdateDurationFromPlayer(duration))
            }
        )

        Scaffold(
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactTabItem(
                            icon = Icons.Outlined.Movie,
                            label = "Video",
                            selected = currentMode == EditorMode.VIDEO,
                            onClick = { currentMode = EditorMode.VIDEO }
                        )
                        CompactTabItem(
                            icon = Icons.Outlined.Subtitles,
                            label = "Captions",
                            selected = currentMode == EditorMode.CAPTIONS,
                            onClick = { currentMode = EditorMode.CAPTIONS }
                        )
                        CompactTabItem(
                            icon = Icons.Outlined.MusicNote,
                            label = "Audio",
                            selected = currentMode == EditorMode.AUDIO,
                            onClick = { currentMode = EditorMode.AUDIO }
                        )
                    }
                }
            }
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val maxTimelineHeight = maxHeight * 0.5f

                when (step) {
                    is VideoEditorUiStep.Idle, is VideoEditorUiStep.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is VideoEditorUiStep.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Error: ${step.message}", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            AppPrimaryButton(onClick = { viewModel.setEvent(VideoEditorUiEvent.LoadProject(projectId)) }) {
                                Text("Retry")
                            }
                        }
                    }
                    is VideoEditorUiStep.Processing -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (step.progress > 0) {
                                LinearProgressIndicator(progress = { step.progress / 100f })
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("${step.progress}%")
                            } else {
                                CircularProgressIndicator()
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Applying edits...", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            AppOutlinedButton(onClick = { viewModel.setEvent(VideoEditorUiEvent.Cancel) }) {
                                Text("Cancel")
                            }
                        }
                    }
                    is VideoEditorUiStep.Success -> {
                        // Handled by LaunchedEffect
                    }
                    is VideoEditorUiStep.Ready -> {
                        val totalEditedMs = clips.sumOf { it.endTrimMs - it.startTrimMs }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Video Player and Overlays
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                VideoPreviewSection(
                                    player = editorState.player,
                                    overlays = overlays,
                                    currentTimelineMs = { editorState.currentTimelineMs },
                                    selectedOverlayId = selectedOverlayId,
                                    onUpdateOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                    onSelectOverlay = { viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(it)) },
                                    modifier = Modifier.fillMaxSize()
                                )

                                LeftSideControlsPanel(
                                    hasEdits = hasEdits,
                                    onNavigateBack = onNavigateBack,
                                    onShowBackDialog = { showBackDialog = true },
                                    onNavigateToExport = onNavigateToExport,
                                    onDeleteProject = { viewModel.setEvent(VideoEditorUiEvent.DeleteProject) },
                                    onShowDeleteDialog = { showDeleteDialog = true },
                                    onApplyEdits = { viewModel.setEvent(VideoEditorUiEvent.ApplyEdits) },
                                    onNavigateToProcessing = onNavigateToProcessing,
                                    modifier = Modifier.align(Alignment.TopStart)
                                )

                                RightSideControlsPanel(
                                    canUndo = canUndo,
                                    canRedo = canRedo,
                                    onUndo = { viewModel.setEvent(VideoEditorUiEvent.Undo) },
                                    onRedo = { viewModel.setEvent(VideoEditorUiEvent.Redo) },
                                    onAddImage = { imagePickerLauncher.launch("image/*") },
                                    selectedLanguage = selectedLanguage,
                                    translateToEnglish = translateToEnglish,
                                    onLanguageSelected = { lang, trans ->
                                        viewModel.setEvent(VideoEditorUiEvent.SaveLanguage(lang, trans))
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Timer Pill
                            TimerPill(
                                currentTimelineMs = { editorState.currentTimelineMs },
                                totalEditedMs = totalEditedMs,
                                formatTime = ::formatTime
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Mini scrubber
                            MiniScrubber(
                                currentTimelineMs = { editorState.currentTimelineMs },
                                totalEditedMs = totalEditedMs,
                                clips = clips,
                                player = editorState.player
                            )

                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            BottomWorkspacePanel(
                                timelineHeight = timelineHeight,
                                maxTimelineHeight = maxTimelineHeight,
                                onTimelineHeightChanged = { timelineHeight = it },
                                currentMode = currentMode,
                                clips = clips,
                                clipThumbnails = clipThumbnails,
                                selectedClipId = selectedClipId,
                                onClipSelected = { selectedClipId = it },
                                onMoveClip = { from, to -> viewModel.setEvent(VideoEditorUiEvent.MoveClip(from, to)) },
                                overlays = overlays,
                                selectedOverlayId = selectedOverlayId,
                                onOverlaySelected = { viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(it)) },
                                onUpdateOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                onCaptionTap = { currentMode = EditorMode.CAPTIONS },
                                onDragStateChange = { editorState.isDragging = it },
                                zoomLevel = zoomLevel,
                                onZoomIn = { zoomLevel = (zoomLevel * 1.5f).coerceAtMost(5f) },
                                onZoomOut = { zoomLevel = (zoomLevel / 1.5f).coerceAtLeast(0.2f) },
                                player = editorState.player,
                                currentTimelineMs = { editorState.currentTimelineMs },
                                onSplitClip = { viewModel.setEvent(VideoEditorUiEvent.SplitClipAtAbsoluteTime(editorState.currentTimelineMs)) },
                                onDuplicateClip = { viewModel.setEvent(VideoEditorUiEvent.DuplicateClip(it)) },
                                onDeleteClip = { viewModel.setEvent(VideoEditorUiEvent.DeleteClip(it)) },
                                onMoveOverlayZ = { id, bringToFront -> viewModel.setEvent(VideoEditorUiEvent.MoveOverlayZ(id, bringToFront)) },
                                onDeleteOverlay = { viewModel.setEvent(VideoEditorUiEvent.DeleteOverlay(it)) },
                                styleViewModel = styleViewModel,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        if (showBackDialog) {
            AlertDialog(
                onDismissRequest = { showBackDialog = false },
                title = { Text("Save changes?") },
                text = { Text("You have unsaved edits. Do you want to apply them before leaving?") },
                confirmButton = {
                    TextButton(onClick = {
                        showBackDialog = false
                        viewModel.setEvent(VideoEditorUiEvent.ApplyEdits)
                    }) {
                        Text("Save & Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showBackDialog = false
                        onNavigateBack()
                    }) {
                        Text("Discard")
                    }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Project?") },
                text = { Text("Are you sure you want to delete this project? Your edits will be lost.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        viewModel.setEvent(VideoEditorUiEvent.DeleteProject)
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    val millis = ms % 1000 / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
}

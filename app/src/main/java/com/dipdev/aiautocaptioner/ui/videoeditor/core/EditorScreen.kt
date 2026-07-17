package com.dipdev.aiautocaptioner.ui.videoeditor.core

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.theme.AccentViolet
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StyleEditorUiEvent
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StyleViewModel
import com.dipdev.aiautocaptioner.ui.videoeditor.shared.EditorBottomDock
import com.dipdev.aiautocaptioner.ui.videoeditor.shared.LeftSideControls
import com.dipdev.aiautocaptioner.ui.videoeditor.player.MiniScrubber
import com.dipdev.aiautocaptioner.ui.videoeditor.shared.RightSideControls
import com.dipdev.aiautocaptioner.ui.videoeditor.player.TimerPill
import com.dipdev.aiautocaptioner.ui.videoeditor.player.PreviewSection
import com.dipdev.aiautocaptioner.ui.videoeditor.core.player.SharedPlayerViewModel
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import compose.icons.FeatherIcons
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.X
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun EditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToProcessing: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    // Fix A: received from NavGraph — navigation-graph-scoped shared player
    sharedPlayerViewModel: SharedPlayerViewModel,
    viewModel: EditorViewModel = hiltViewModel(),
    styleViewModel: StyleViewModel = hiltViewModel(),
    processingViewModel: com.dipdev.aiautocaptioner.ui.processing.ProcessingViewModel = hiltViewModel()
) {
    ScreenThemeProvider(accentColor = AccentAmber) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val thumbnails by viewModel.thumbnailManager.thumbnails.collectAsStateWithLifecycle()
        val overlays by viewModel.overlays.collectAsStateWithLifecycle()
        val selectedOverlayId by viewModel.selectedOverlayId.collectAsStateWithLifecycle()
        
        val step = uiState.step
        val processingUiState by processingViewModel.uiState.collectAsStateWithLifecycle()
        val processingStep = processingUiState.step

        // Fix 12: Direct reads from uiState — derivedStateOf{} wrappers that merely
        // re-expose fields add overhead without any recomposition benefit.
        val clips = uiState.clips
        val hasEdits = uiState.hasEdits
        val canUndo = uiState.canUndo
        val canRedo = uiState.canRedo
        val originalDurationMs = uiState.originalDurationMs
        val selectedLanguage = uiState.selectedLanguage
        val translateToEnglish = uiState.translateToEnglish

        val styleUiState by styleViewModel.uiState.collectAsStateWithLifecycle()

        // Fix A: collect the shared player
        val player by sharedPlayerViewModel.player.collectAsStateWithLifecycle()

        var selectedClipId by remember { mutableStateOf<String?>(null) }
        var zoomLevel by remember { mutableFloatStateOf(1f) }
        var showBackDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var selectedCaptionSegment by remember { mutableStateOf<CaptionSegmentEntity?>(null) }
        var inlineEditText by remember { mutableStateOf("") }
        
        var showTranscriptionBottomSheet by remember { mutableStateOf(false) }
        var pendingTranscriptionParams by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { viewModel.setEvent(VideoEditorUiEvent.AddOverlay(it.toString())) }
        }

        // Fix A: pause when app goes to background — shared player, shared responsibility
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
            }
        }

        BackHandler {
            if (hasEdits) showBackDialog = true else onNavigateBack()
        }

        LaunchedEffect(Unit) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is VideoEditorUiEffect.ProjectDeleted -> onNavigateBack()
                    is VideoEditorUiEffect.NavigateToProcessing -> {
                        if (pendingTranscriptionParams != null) {
                            val (modelId, lang, translate) = pendingTranscriptionParams!!
                            pendingTranscriptionParams = null
                            processingViewModel.setEvent(
                                com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.StartTranscriptionExplicit(
                                    projectId = projectId,
                                    modelId = modelId,
                                    language = lang,
                                    translateToEnglish = translate
                                )
                            )
                        } else {
                            onNavigateToProcessing()
                        }
                    }
                    is VideoEditorUiEffect.NavigateToExport -> onNavigateToExport()
                }
            }
        }
        
        LaunchedEffect(processingStep) {
            if (processingStep is com.dipdev.aiautocaptioner.ui.processing.ProcessingStep.Done) {
                // Transcription finished! Reset state to hide overlay.
                processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.ResetToIdle)
                // We reload styles to immediately show the new captions on screen
                styleViewModel.setEvent(StyleEditorUiEvent.LoadStyles(projectId))
            }
        }

        LaunchedEffect(projectId) {
            viewModel.setEvent(VideoEditorUiEvent.LoadProject(projectId))
            styleViewModel.setEvent(StyleEditorUiEvent.LoadStyles(projectId))
        }


        // Fix A: initialise the shared player once the video path is known
        val originalVideoPath = (step as? VideoEditorUiStep.Ready)?.originalPath ?: ""
        LaunchedEffect(originalVideoPath) {
            if (originalVideoPath.isNotEmpty()) {
                sharedPlayerViewModel.initPlayer(originalVideoPath)
            }
        }

        Scaffold { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                val maxH = maxHeight

                when (step) {
                    is VideoEditorUiStep.Idle, is VideoEditorUiStep.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is VideoEditorUiStep.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(R.string.editor_error_prefix, step.message), color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            AppPrimaryButton(onClick = { viewModel.setEvent(VideoEditorUiEvent.LoadProject(projectId)) }) {
                                Text(stringResource(R.string.editor_retry))
                            }
                        }
                    }
                    is VideoEditorUiStep.Processing -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(32.dp)
                            ) {
                                AiProcessingAnimation(
                                    progress = if (step.progress > 0) step.progress / 100f else 0f, 
                                    modifier = Modifier.size(120.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.editor_applying_edits),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (step.progress > 0) {
                                    Text(
                                        text = "${step.progress}%",
                                        fontSize = 16.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }

                            TextButton(
                                onClick = { viewModel.setEvent(VideoEditorUiEvent.Cancel) },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 64.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_cancel),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    is VideoEditorUiStep.Ready -> {
                        // Wait for shared player to be initialised
                        val currentPlayer = player
                        if (currentPlayer == null) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            return@BoxWithConstraints
                        }

                        val totalEditedMs = clips.sumOf { it.endTrimMs - it.startTrimMs }

                        // Fix A: pass injected player into EditorState (no longer creates its own)
                        val editorState = rememberEditorState(
                            player = currentPlayer,
                            clips = clips,
                            originalVideoPath = originalVideoPath,
                            onDurationUpdated = { duration ->
                                viewModel.setEvent(VideoEditorUiEvent.UpdateDurationFromPlayer(duration))
                            }
                        )

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Video Player and Overlays
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                PreviewSection(
                                    player = editorState.player,
                                    overlays = overlays,
                                    currentTimelineMs = { editorState.currentTimelineMs },
                                    currentSourceMs = { editorState.currentSourceMs },
                                    selectedOverlayId = selectedOverlayId,
                                    onUpdateOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                    onSelectOverlay = { viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(it)) },
                                    activeStyle = styleUiState.activeStyle,
                                    segments = styleUiState.segments,
                                    wordsMap = styleUiState.wordsMap,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Status bar shadow
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                                            )
                                        )
                                        .align(Alignment.TopCenter)
                                )

                                LeftSideControls(
                                    hasEdits = hasEdits,
                                    onNavigateBack = onNavigateBack,
                                    onShowBackDialog = { showBackDialog = true },
                                    onNavigateToExport = { viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = true)) },
                                    onDeleteProject = { viewModel.setEvent(VideoEditorUiEvent.DeleteProject) },
                                    onShowDeleteDialog = { showDeleteDialog = true },
                                    onNavigateToProcessing = { showTranscriptionBottomSheet = true },
                                    hasCaptions = styleUiState.segments.isNotEmpty(),
                                    onNavigateToCaptionEditor = onNavigateToCaptionEditor,
                                    selectedLanguage = selectedLanguage,
                                    translateToEnglish = translateToEnglish,
                                    onLanguageSelected = { lang, trans ->
                                        viewModel.setEvent(VideoEditorUiEvent.SaveLanguage(lang, trans))
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
                                )

                                RightSideControls(
                                    canUndo = canUndo,
                                    canRedo = canRedo,
                                    onUndo = { viewModel.setEvent(VideoEditorUiEvent.Undo) },
                                    onRedo = { viewModel.setEvent(VideoEditorUiEvent.Redo) },
                                    onAddImage = { imagePickerLauncher.launch("image/*") },
                                    onNavigateToExport = { viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = true)) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
                                )

                                // Fix 11: Inline caption editor extracted to CaptionInlineEditor composable
                                // Fix 8: imePadding is applied inside CaptionInlineEditor
                                CaptionInlineEditor(
                                    segment = selectedCaptionSegment,
                                    editText = inlineEditText,
                                    onEditTextChange = { inlineEditText = it },
                                    onSave = { segId, text ->
                                        styleViewModel.setEvent(
                                            StyleEditorUiEvent.UpdateSegmentText(segId, text)
                                        )
                                        selectedCaptionSegment = null
                                    },
                                    onDismiss = { selectedCaptionSegment = null },
                                    onOpenFullEditor = {
                                        selectedCaptionSegment = null
                                        onNavigateToCaptionEditor()
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            } // end preview Box

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
                                clips = editorState.mergedClips,
                                player = editorState.player
                            )

                            EditorBottomDock(
                                maxHeight = maxH,
                                clips = clips,
                                thumbnails = thumbnails,
                                onRequestThumbnails = { viewModel.thumbnailManager.requestThumbnails(it) },
                                originalDurationMs = originalDurationMs,
                                selectedClipId = selectedClipId,
                                onClipSelected = { 
                                    selectedClipId = it 
                                    if (it != null) viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(null))
                                },
                                onMoveClip = { from, to -> viewModel.setEvent(VideoEditorUiEvent.MoveClip(from, to)) },
                                overlays = overlays,
                                selectedOverlayId = selectedOverlayId,
                                onOverlaySelected = { 
                                    viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(it)) 
                                    if (it != null) selectedClipId = null
                                },
                                onUpdateOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                onDragStateChange = { 
                                    if (!editorState.isDragging && it) {
                                        viewModel.setEvent(VideoEditorUiEvent.SaveState)
                                    }
                                    editorState.isDragging = it 
                                },
                                zoomLevel = zoomLevel,
                                player = editorState.player,
                                currentTimelineMs = { editorState.currentTimelineMs },
                                onTrimClip = { id, start, end -> viewModel.setEvent(VideoEditorUiEvent.TrimClip(id, start, end)) },
                                onMoveOverlayZ = { id, bringToFront -> viewModel.setEvent(VideoEditorUiEvent.MoveOverlayZ(id, bringToFront)) },
                                onDeleteOverlay = { viewModel.setEvent(VideoEditorUiEvent.DeleteOverlay(it)) },
                                styleViewModel = styleViewModel,
                                onSplit = { viewModel.setEvent(VideoEditorUiEvent.SplitClipAtAbsoluteTime(editorState.currentTimelineMs)) },
                                onDuplicate = { viewModel.setEvent(VideoEditorUiEvent.DuplicateClip(it)) },
                                onDuplicateOverlay = { viewModel.setEvent(VideoEditorUiEvent.DuplicateOverlay(it)) },
                                onDelete = { 
                                    viewModel.setEvent(VideoEditorUiEvent.DeleteClip(it))
                                    selectedClipId = null
                                },
                                onZoomIn = { zoomLevel = (zoomLevel * 1.5f).coerceAtMost(5f) },
                                onZoomOut = { zoomLevel = (zoomLevel / 1.5f).coerceAtLeast(0.2f) },
                                onPinchZoom = { scale ->
                                    // Fix 6: Pinch-to-zoom from timeline passed through here
                                    zoomLevel = (zoomLevel * scale).coerceIn(0.2f, 5f)
                                },
                                segments = styleUiState.segments,
                                selectedCaptionSegmentId = selectedCaptionSegment?.id,
                                onCaptionSegmentTap = { seg ->
                                    selectedCaptionSegment = seg
                                    inlineEditText = seg.text
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Fix 10: Extracted dialog composables
        if (showBackDialog) {
            UnsavedEditsDialog(
                onSaveAndContinue = {
                    showBackDialog = false
                    viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = false))
                },
                onDiscard = {
                    showBackDialog = false
                    onNavigateBack()
                },
                onDismiss = { showBackDialog = false }
            )
        }

        if (showDeleteDialog) {
            DeleteProjectDialog(
                onConfirm = {
                    showDeleteDialog = false
                    viewModel.setEvent(VideoEditorUiEvent.DeleteProject)
                },
                onDismiss = { showDeleteDialog = false }
            )
        }

        if (showTranscriptionBottomSheet) {
            // First time they click it, fetch models if needed
            LaunchedEffect(Unit) {
                processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.PrepareForProject(projectId, forceModelPicker = true))
            }
            
            com.dipdev.aiautocaptioner.ui.processing.components.TranscriptionBottomSheet(
                onDismiss = { showTranscriptionBottomSheet = false },
                availableModels = processingUiState.availableModels,
                initialModelId = processingUiState.activeModel?.id,
                initialLanguage = processingUiState.selectedLanguage,
                initialTranslate = processingUiState.translateToEnglish,
                onStart = { modelId, lang, translate ->
                    showTranscriptionBottomSheet = false
                    pendingTranscriptionParams = Triple(modelId, lang, translate)
                    viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = false))
                }
            )
        }

        com.dipdev.aiautocaptioner.ui.processing.components.TranscriptionOverlay(
            step = processingStep,
            onCancel = { processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.Cancel) }
        )
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    val millis = ms % 1000 / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
}

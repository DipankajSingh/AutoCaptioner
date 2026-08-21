package com.dipdev.aiautocaptioner.ui.videoeditor.core

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import com.dipdev.aiautocaptioner.ui.theme.TextPrimary
import com.dipdev.aiautocaptioner.ui.videoeditor.core.player.SharedPlayerViewModel
import com.dipdev.aiautocaptioner.ui.videoeditor.player.MiniScrubber
import com.dipdev.aiautocaptioner.ui.videoeditor.player.PreviewSection
import com.dipdev.aiautocaptioner.ui.videoeditor.player.TimerPill
import com.dipdev.aiautocaptioner.ui.videoeditor.shared.EditorBottomDock
import com.dipdev.aiautocaptioner.ui.videoeditor.shared.EditorTopOverlay
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StyleEditorUiEvent
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StyleViewModel
import com.dipdev.aiautocaptioner.ui.videoeditor.style.VerticalPremiumSlider
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.concurrent.TimeUnit


private val CaptionSegmentSaver = Saver<CaptionSegmentEntity?, Any>(
    save = { segment ->
        segment?.let {
            listOf(it.id, it.projectId, it.index, it.startTimeMs, it.endTimeMs, it.text, it.isEdited)
        }
    },
    restore = { value ->
        (value as? List<*>)?.let { list ->
            if (list.size == 7) {
                CaptionSegmentEntity(
                    id = list[0]?.toString() ?: return@let null,
                    projectId = list[1]?.toString() ?: return@let null,
                    index = (list[2] as? Int) ?: (list[2] as? Long)?.toInt() ?: 0,
                    startTimeMs = (list[3] as? Long) ?: (list[3] as? Int)?.toLong() ?: 0L,
                    endTimeMs = (list[4] as? Long) ?: (list[4] as? Int)?.toLong() ?: 0L,
                    text = list[5]?.toString() ?: "",
                    isEdited = list[6] as? Boolean ?: false
                )
            } else null
        }
    }
)

@Composable
fun EditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToProcessing: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    sharedPlayerViewModel: SharedPlayerViewModel,
    viewModel: EditorViewModel = hiltViewModel(),
    styleViewModel: StyleViewModel = hiltViewModel(),
    processingViewModel: com.dipdev.aiautocaptioner.ui.processing.ProcessingViewModel = hiltViewModel()
) {
    ScreenThemeProvider(accentColor = AccentAmber) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val thumbnails by viewModel.thumbnailManager.thumbnails.collectAsStateWithLifecycle()
        val selectedOverlayId by viewModel.selectedOverlayId.collectAsStateWithLifecycle()
        
        val overlays = uiState.imageOverlays
        val textOverlays = uiState.textOverlays
        
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
        val videoWidth = uiState.videoWidth
        val videoHeight = uiState.videoHeight

        val activeStyle by remember(styleViewModel) {
            styleViewModel.uiState.map { it.activeStyle }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = null)

        val segments by remember(styleViewModel) {
            styleViewModel.uiState.map { it.segments }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = persistentListOf())

        val wordsMap by remember(styleViewModel) {
            styleViewModel.uiState.map { it.wordsMap }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = persistentMapOf())

        val player by sharedPlayerViewModel.player.collectAsStateWithLifecycle()

        var selectedClipId by rememberSaveable { mutableStateOf<String?>(null) }
        var zoomLevel by rememberSaveable { mutableFloatStateOf(1f) }
        var currentMode by rememberSaveable { mutableStateOf(EditorMode.VIDEO) }

        var showDiscardDialog by remember { mutableStateOf(false) }
        var selectedCaptionSegment by rememberSaveable(stateSaver = CaptionSegmentSaver) { mutableStateOf(null) }
        var inlineEditText by rememberSaveable { mutableStateOf("") }
        var showTextColorMenu by remember { mutableStateOf(false) }
        var showTextSizeSlider by remember { mutableStateOf(false) }
        var cropOverlay by remember { mutableStateOf<com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity?>(null) }
        
        var showTranscriptionBottomSheet by remember { mutableStateOf(false) }
        var pendingTranscriptionParams by remember { mutableStateOf<PendingTranscriptionParams?>(null) }
        var showExportWarning by remember { mutableStateOf(false) }

        var pendingImagePlayheadMs by remember { mutableLongStateOf(0L) }
        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { viewModel.setEvent(VideoEditorUiEvent.AddOverlay(it.toString(), pendingImagePlayheadMs)) }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    sharedPlayerViewModel.pauseForBackground()
                } else if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                    sharedPlayerViewModel.resumePlayerFromExport()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        BackHandler(enabled = showTextColorMenu) {
            showTextColorMenu = false
        }

        BackHandler(enabled = hasEdits && !showDiscardDialog && !showTextColorMenu) {
            showDiscardDialog = true
        }

        // While a text overlay is being edited, Back should commit the edit
        // (dismissing the keyboard once via the IME) before any destructive back press.
        BackHandler(enabled = uiState.editingTextOverlayId != null && !showTextColorMenu) {
            viewModel.setEvent(VideoEditorUiEvent.StopEditingText)
        }

        LaunchedEffect(Unit) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is VideoEditorUiEffect.ProjectDeleted -> onNavigateBack()
                    is VideoEditorUiEffect.NavigateToProcessing -> {
                        if (pendingTranscriptionParams != null) {
                            val params = pendingTranscriptionParams!!
                            pendingTranscriptionParams = null
                            processingViewModel.setEvent(
                                com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.StartTranscriptionExplicit(
                                    projectId = projectId,
                                    modelId = params.modelId,
                                    language = params.language,
                                    translateToEnglish = params.translate,
                                    initialPrompt = params.prompt
                                )
                            )
                        } else {
                            onNavigateToProcessing()
                        }
                    }
                    is VideoEditorUiEffect.NavigateToExport -> {
                        sharedPlayerViewModel.suspendPlayerForExport()
                        onNavigateToExport()
                    }
                    is VideoEditorUiEffect.ShowExportWithoutCaptionsWarning -> {
                        showExportWarning = true
                    }
                }
            }
        }
        
        LaunchedEffect(processingStep) {
            if (processingStep is com.dipdev.aiautocaptioner.ui.processing.ProcessingStep.Done) {
                processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.ResetToIdle)
                styleViewModel.setEvent(StyleEditorUiEvent.LoadStyles(projectId))
            }
        }

        LaunchedEffect(projectId) {
            viewModel.setEvent(VideoEditorUiEvent.LoadProject(projectId))
            styleViewModel.setEvent(StyleEditorUiEvent.LoadStyles(projectId))
        }

        LaunchedEffect(selectedOverlayId) {
            if (selectedOverlayId == null) {
                showTextSizeSlider = false
            }
        }


        LaunchedEffect(uiState.editingTextOverlayId) {
            if (uiState.editingTextOverlayId != null) {
                player?.pause()
            }
        }

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
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
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
                                    color = TextPrimary
                                )
                                if (step.progress > 0) {
                                    Text(
                                        text = "${step.progress}%",
                                        fontSize = 16.sp,
                                        color = TextPrimary.copy(alpha = 0.7f),
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
                                    color = TextPrimary.copy(alpha = 0.6f),
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

                        val totalEditedMs = remember(clips) { clips.sumOf { it.endTrimMs - it.startTrimMs } }
                        // This editor draws edge-to-edge, so imePadding alone is
                        // laid out behind the IME on some devices. Offset the tray
                        // by the real keyboard inset, excluding the nav-bar inset.


                        // Fix A: pass injected player into EditorState (no longer creates its own)
                        val editorState = rememberEditorState(
                            player = currentPlayer,
                            clips = clips,
                            originalVideoPath = originalVideoPath,
                            onDurationUpdated = { duration ->
                                viewModel.setEvent(VideoEditorUiEvent.UpdateDurationFromPlayer(duration))
                            }
                        )

                        Box(modifier = Modifier.fillMaxSize()) {
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
                                    modifier = Modifier.fillMaxSize(),
                                    videoWidth = videoWidth,
                                    videoHeight = videoHeight,
                                    activeStyle = activeStyle,
                                    segments = segments,
                                    wordsMap = wordsMap,
                                    textOverlays = textOverlays,
                                    onUpdateTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateTextOverlay(it)) },
                                    editingTextOverlayId = uiState.editingTextOverlayId,
                                    onStopEditingTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.StopEditingText) }
                                )

                                // Status bar shadow
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), Color.Transparent)
                                            )
                                        )
                                        .align(Alignment.TopCenter)
                                )

                                EditorTopOverlay(
                                    canUndo = canUndo,
                                    canRedo = canRedo,
                                    onNavigateBack = onNavigateBack,
                                    onUndo = { viewModel.setEvent(VideoEditorUiEvent.Undo) },
                                    onRedo = { viewModel.setEvent(VideoEditorUiEvent.Redo) },
                                    onNavigateToExport = { 
                                        viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = true, hasCaptions = segments.isNotEmpty())) 
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp),
                                    leftContent = {
                                        var showOpacitySheet by remember { mutableStateOf(false) }
                                        var showFiltersSheet by remember { mutableStateOf(false) }
                                        val currentImageOverlay = overlays.find { it.id == selectedOverlayId }

                                        com.dipdev.aiautocaptioner.ui.videoeditor.shared.OverlaySideToolbar(
                                            selectedOverlayId = selectedOverlayId,
                                            isTextOverlay = textOverlays.any { it.id == selectedOverlayId } || uiState.editingTextOverlayId == selectedOverlayId,
                                            onFontSize = {
                                                if (selectedOverlayId != null && textOverlays.any { it.id == selectedOverlayId }) {
                                                    showTextSizeSlider = !showTextSizeSlider
                                                }
                                            },
                                            onEdit = { viewModel.setEvent(VideoEditorUiEvent.StartEditingText) },
                                            onColorMenuClicked = { showTextColorMenu = true },
                                            onDuplicate = { 
                                                if (textOverlays.any { it.id == selectedOverlayId }) {
                                                    viewModel.setEvent(VideoEditorUiEvent.DuplicateTextOverlay(selectedOverlayId!!))
                                                } else if (selectedOverlayId != null) {
                                                    viewModel.setEvent(VideoEditorUiEvent.DuplicateOverlay(selectedOverlayId!!))
                                                }
                                            },
                                            onCrop = { cropOverlay = currentImageOverlay },
                                            onFilters = { showFiltersSheet = true },
                                            onOpacity = { showOpacitySheet = true },
                                            onDelete = {
                                                if (textOverlays.any { it.id == selectedOverlayId }) {
                                                    viewModel.setEvent(VideoEditorUiEvent.DeleteTextOverlay(selectedOverlayId!!))
                                                } else if (selectedOverlayId != null) {
                                                    viewModel.setEvent(VideoEditorUiEvent.DeleteOverlay(selectedOverlayId!!))
                                                }
                                                viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(null))
                                            }
                                        )

                                        if (showTextSizeSlider && selectedOverlayId != null) {
                                            val currentTextOverlay = textOverlays.find { it.id == selectedOverlayId }
                                            if (currentTextOverlay != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .padding(horizontal = 12.dp, vertical = 16.dp)
                                                ) {
                                                    VerticalPremiumSlider(
                                                        value = currentTextOverlay.fontSize,
                                                        valueRange = 24f..120f,
                                                        onValueChange = { newSize: Float ->
                                                            viewModel.setEvent(
                                                                VideoEditorUiEvent.UpdateTextOverlay(
                                                                    currentTextOverlay.copy(fontSize = newSize)
                                                                )
                                                            )
                                                        },
                                                        modifier = Modifier
                                                            .height(220.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (showOpacitySheet && currentImageOverlay != null) {
                                            com.dipdev.aiautocaptioner.ui.videoeditor.image.components.OpacityControlSheet(
                                                overlay = currentImageOverlay,
                                                onUpdateOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                                onDismiss = { showOpacitySheet = false }
                                            )
                                        }

                                        if (showFiltersSheet && currentImageOverlay != null) {
                                            com.dipdev.aiautocaptioner.ui.videoeditor.image.components.FilterControlSheet(
                                                overlay = currentImageOverlay,
                                                onUpdateOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                                onDismiss = { showFiltersSheet = false }
                                            )
                                        }
                                    },
                                    rightContent = {
                                        val isVisible = currentMode == EditorMode.VIDEO
                                        
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = isVisible,
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Title,
                                                contentDescription = "Add Text",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .shadow(4.dp, CircleShape)
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .clickable { viewModel.setEvent(VideoEditorUiEvent.StartAddingText(editorState.currentTimelineMs)) }
                                                    .padding(2.dp)
                                            )
                                        }
                                        
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = isVisible,
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Image,
                                                contentDescription = stringResource(R.string.timeline_add_image),
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .shadow(4.dp, CircleShape)
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .clickable { 
                                                        pendingImagePlayheadMs = editorState.currentTimelineMs
                                                        imagePickerLauncher.launch("image/*") 
                                                    }
                                                    .padding(2.dp)
                                            )
                                        }
                                    }
                                )

                            if (selectedCaptionSegment != null) {
                                CaptionInlineEditor(
                                    segment = selectedCaptionSegment!!,
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
                            }
                            } // end preview Box

                            cropOverlay?.let { overlayToCrop ->
                                com.dipdev.aiautocaptioner.ui.videoeditor.image.components.ImageCropOverlay(
                                    overlay = overlayToCrop,
                                    onApply = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                    onDismiss = { cropOverlay = null }
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Timer Pill
                            TimerPill(
                                currentTimelineMs = { editorState.currentTimelineMs },
                                totalEditedMs = totalEditedMs,
                                formatTime = ::formatTime
                            )

                            Spacer(modifier = Modifier.height(2.dp))

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
                                onMoveClip = { from, to -> viewModel.setEvent(VideoEditorUiEvent.MoveClip(from, to, !editorState.isDragging)) },
                                overlays = overlays,
                                selectedOverlayId = selectedOverlayId,
                                onOverlaySelected = { 
                                    viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(it)) 
                                    if (it != null) selectedClipId = null
                                },
                                onUpdateOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                textOverlays = textOverlays,
                                onUpdateTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateTextOverlay(it)) },
                                onDragStateChange = { 
                                    if (!editorState.isDragging && it) {
                                        viewModel.setEvent(VideoEditorUiEvent.SaveState)
                                    }
                                    editorState.isDragging = it 
                                },
                                zoomLevel = zoomLevel,
                                player = editorState.player,
                                currentTimelineMs = { editorState.currentTimelineMs },
                                onTrimClip = { id, start, end -> viewModel.setEvent(VideoEditorUiEvent.TrimClip(id, start, end, !editorState.isDragging)) },
                                onMoveOverlayZ = { id, bringToFront -> viewModel.setEvent(VideoEditorUiEvent.MoveOverlayZ(id, bringToFront)) },
                                onDeleteOverlay = { viewModel.setEvent(VideoEditorUiEvent.DeleteOverlay(it)) },
                                onDeleteTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.DeleteTextOverlay(it)) },
                                styleViewModel = styleViewModel,
                                onSplit = { viewModel.setEvent(VideoEditorUiEvent.SplitClipAtAbsoluteTime(editorState.currentTimelineMs)) },
                                onDuplicate = { viewModel.setEvent(VideoEditorUiEvent.DuplicateClip(it)) },
                                onDuplicateOverlay = { viewModel.setEvent(VideoEditorUiEvent.DuplicateOverlay(it)) },
                                onDuplicateTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.DuplicateTextOverlay(it)) },
                                onDelete = { 
                                    viewModel.setEvent(VideoEditorUiEvent.DeleteClip(it))
                                    selectedClipId = null
                                },
                                onZoomIn = { zoomLevel = (zoomLevel * 1.5f).coerceAtMost(5f) },
                                onZoomOut = { zoomLevel = (zoomLevel / 1.5f).coerceAtLeast(0.2f) },
                                onPinchZoom = { scale ->
                                    zoomLevel = (zoomLevel * scale).coerceIn(0.2f, 5f)
                                },
                                segments = segments,
                                selectedCaptionSegmentId = selectedCaptionSegment?.id,
                                onCaptionSegmentTap = { seg ->
                                    selectedCaptionSegment = seg
                                    inlineEditText = seg.text
                                },
                                onGenerateCaptions = { showTranscriptionBottomSheet = true },
                                onAddImage = { 
                                    pendingImagePlayheadMs = editorState.currentTimelineMs
                                    imagePickerLauncher.launch("image/*") 
                                },
                                currentMode = currentMode,
                                onModeChange = { currentMode = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            }

                            // Keep text controls in the screen layer, rather than the
                            // video canvas. This makes the tray span the editor and
                            // places it immediately above the real IME.
                            textOverlays.find { it.id == uiState.editingTextOverlayId }?.let { editingOverlay ->
                                com.dipdev.aiautocaptioner.ui.videoeditor.text.FontStyleCarousel(
                                    selectedAssetPath = editingOverlay.fontAssetPath,
                                    onFontChange = { fontAssetPath ->
                                        viewModel.setEvent(
                                            VideoEditorUiEvent.UpdateTextOverlay(
                                                editingOverlay.copy(fontAssetPath = fontAssetPath)
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 16.dp, bottom = 16.dp)
                                        .imePadding()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fix 10: Extracted dialog composables
        if (showDiscardDialog) {
            DiscardEditsDialog(
                onConfirm = {
                    showDiscardDialog = false
                    onNavigateBack()
                },
                onDismiss = { showDiscardDialog = false }
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
                initialPrompt = processingUiState.initialPrompt,
                skipUi = segments.isEmpty(),
                onStart = { modelId, lang, translate, prompt ->
                    showTranscriptionBottomSheet = false
                    pendingTranscriptionParams = PendingTranscriptionParams(modelId, lang, translate, prompt)
                    viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = false, hasCaptions = segments.isNotEmpty()))
                }
            )
        }

        com.dipdev.aiautocaptioner.ui.processing.components.TranscriptionOverlay(
            step = processingStep,
            streamedSegments = processingUiState.streamedSegments,
            onCancel = { processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.Cancel) }
        )

        if (showTextColorMenu && selectedOverlayId != null) {
            val overlay = textOverlays.find { it.id == selectedOverlayId }
            if (overlay != null) {
                com.dipdev.aiautocaptioner.ui.videoeditor.text.TextOverlayColorPickerPopup(
                    textColorArgb = overlay.textColorArgb,
                    backgroundColorArgb = overlay.backgroundColorArgb,
                    onColorChanged = { field, color ->
                        val updated = when (field) {
                            com.dipdev.aiautocaptioner.ui.videoeditor.text.TextOverlayColorField.TEXT -> overlay.copy(textColorArgb = color)
                            com.dipdev.aiautocaptioner.ui.videoeditor.text.TextOverlayColorField.BACKGROUND -> overlay.copy(backgroundColorArgb = color, backgroundOpacity = 1f, backgroundStyle = "SOLID")
                        }
                        viewModel.setEvent(VideoEditorUiEvent.UpdateTextOverlay(updated))
                    },
                    onDismissRequest = { showTextColorMenu = false }
                )
            }
        }

        if (showExportWarning) {
            com.dipdev.aiautocaptioner.ui.components.UniversalDialog(
                type = com.dipdev.aiautocaptioner.ui.components.DialogType.WARNING,
                title = androidx.compose.ui.res.stringResource(id = R.string.export_no_captions_title),
                body = androidx.compose.ui.res.stringResource(id = R.string.export_no_captions_body),
                confirmText = androidx.compose.ui.res.stringResource(id = R.string.export_anyway),
                onConfirm = {
                    showExportWarning = false
                    viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = true, hasCaptions = false, forceExport = true))
                },
                dismissText = androidx.compose.ui.res.stringResource(id = android.R.string.cancel),
                onDismissRequest = { showExportWarning = false }
            )
        }
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private data class PendingTranscriptionParams(
    val modelId: String,
    val language: String,
    val translate: Boolean,
    val prompt: String
)

private fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    val millis = ms % 1000 / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
}

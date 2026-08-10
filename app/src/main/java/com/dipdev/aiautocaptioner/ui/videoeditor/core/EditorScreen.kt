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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.theme.AccentViolet
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StyleEditorUiEvent
import com.dipdev.aiautocaptioner.ui.videoeditor.style.StyleViewModel
import com.dipdev.aiautocaptioner.ui.videoeditor.shared.EditorBottomDock
import com.dipdev.aiautocaptioner.ui.videoeditor.shared.EditorTopOverlay
import com.dipdev.aiautocaptioner.ui.videoeditor.player.MiniScrubber
import com.dipdev.aiautocaptioner.ui.videoeditor.player.TimerPill
import com.dipdev.aiautocaptioner.ui.videoeditor.player.PreviewSection
import com.dipdev.aiautocaptioner.ui.videoeditor.core.player.SharedPlayerViewModel
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.TextPrimary
import compose.icons.FeatherIcons
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.X
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
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
    // Fix A: received from NavGraph — navigation-graph-scoped shared player
    sharedPlayerViewModel: SharedPlayerViewModel,
    viewModel: EditorViewModel = hiltViewModel(),
    styleViewModel: StyleViewModel = hiltViewModel(),
    processingViewModel: com.dipdev.aiautocaptioner.ui.processing.ProcessingViewModel = hiltViewModel()
) {
    ScreenThemeProvider(accentColor = AccentAmber) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val thumbnails by viewModel.thumbnailManager.thumbnails.collectAsStateWithLifecycle()
        val selectedOverlayId by viewModel.selectedOverlayId.collectAsStateWithLifecycle()
        val selectedTextOverlayId by viewModel.selectedTextOverlayId.collectAsStateWithLifecycle()
        
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
        val selectedLanguage = uiState.selectedLanguage
        val translateToEnglish = uiState.translateToEnglish
        val videoWidth = uiState.videoWidth
        val videoHeight = uiState.videoHeight
        val allowedLanguages = processingUiState.activeModel?.languages ?: listOf("multilingual")

        val activeStyle by remember(styleViewModel) {
            styleViewModel.uiState.map { it.activeStyle }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = null)

        val segments by remember(styleViewModel) {
            styleViewModel.uiState.map { it.segments }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = persistentListOf())

        val wordsMap by remember(styleViewModel) {
            styleViewModel.uiState.map { it.wordsMap }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = persistentMapOf())

        // Fix A: collect the shared player
        val player by sharedPlayerViewModel.player.collectAsStateWithLifecycle()

        var selectedClipId by rememberSaveable { mutableStateOf<String?>(null) }
        var zoomLevel by rememberSaveable { mutableFloatStateOf(1f) }
        var currentMode by rememberSaveable { mutableStateOf(com.dipdev.aiautocaptioner.ui.videoeditor.core.EditorMode.VIDEO) }

        var showDiscardDialog by remember { mutableStateOf(false) }
        var selectedCaptionSegment by rememberSaveable(stateSaver = CaptionSegmentSaver) { mutableStateOf<CaptionSegmentEntity?>(null) }
        var inlineEditText by rememberSaveable { mutableStateOf("") }
        
        var showTranscriptionBottomSheet by remember { mutableStateOf(false) }
        var pendingTranscriptionParams by remember { mutableStateOf<PendingTranscriptionParams?>(null) }

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
                } else if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                    sharedPlayerViewModel.resumePlayerFromExport()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        BackHandler(enabled = hasEdits && !showDiscardDialog) {
            showDiscardDialog = true
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
                                    videoWidth = videoWidth,
                                    videoHeight = videoHeight,
                                    activeStyle = activeStyle,
                                    segments = segments,
                                    wordsMap = wordsMap,
                                    textOverlays = textOverlays,
                                    selectedTextOverlayId = selectedTextOverlayId,
                                    onUpdateTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateTextOverlay(it)) },
                                    onSelectTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.SelectTextOverlay(it)) },
                                    modifier = Modifier.fillMaxSize()
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
                                    onNavigateToExport = { viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = true)) },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp),
                                    leftContent = {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = selectedTextOverlayId != null || selectedOverlayId != null,
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                                if (selectedTextOverlayId != null) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Edit,
                                                        contentDescription = "Edit",
                                                        tint = Color.White,
                                                        modifier = Modifier
                                                            .shadow(4.dp, CircleShape)
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .clickable {
                                                                viewModel.setEvent(VideoEditorUiEvent.StartEditingText)
                                                            }
                                                            .padding(2.dp)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Rounded.ColorLens,
                                                        contentDescription = "Color",
                                                        tint = Color.White,
                                                        modifier = Modifier
                                                            .shadow(4.dp, CircleShape)
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .clickable { /* TODO: Open Color Picker */ }
                                                            .padding(2.dp)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Rounded.FontDownload,
                                                        contentDescription = "Font",
                                                        tint = Color.White,
                                                        modifier = Modifier
                                                            .shadow(4.dp, CircleShape)
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .clickable { /* TODO: Open Font Picker */ }
                                                            .padding(2.dp)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color(0xFFE84855),
                                                    modifier = Modifier
                                                        .shadow(4.dp, CircleShape)
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            if (selectedTextOverlayId != null) {
                                                                viewModel.setEvent(VideoEditorUiEvent.DeleteTextOverlay(selectedTextOverlayId!!))
                                                            } else if (selectedOverlayId != null) {
                                                                viewModel.setEvent(VideoEditorUiEvent.DeleteOverlay(selectedOverlayId!!))
                                                            }
                                                        }
                                                        .padding(2.dp)
                                                )
                                            }
                                        }
                                    },
                                    rightContent = {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = currentMode == com.dipdev.aiautocaptioner.ui.videoeditor.core.EditorMode.VIDEO,
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Title,
                                                    contentDescription = "Add Text",
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .shadow(4.dp, CircleShape)
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .clickable { viewModel.setEvent(VideoEditorUiEvent.StartAddingText) }
                                                        .padding(2.dp)
                                                )
                                                
                                                Icon(
                                                    imageVector = Icons.Rounded.Image,
                                                    contentDescription = stringResource(R.string.timeline_add_image),
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .shadow(4.dp, CircleShape)
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .clickable { imagePickerLauncher.launch("image/*") }
                                                        .padding(2.dp)
                                                )
                                            }
                                        }
                                    }
                                )

                                // Fix 11: Inline caption editor extracted to CaptionInlineEditor composable
                                // Fix 8: imePadding is applied inside CaptionInlineEditor
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
                            
                            // Snapchat-style Text Inline Editor
                            if (uiState.isAddingText || uiState.editingTextOverlayId != null) {
                                val initialText = if (uiState.editingTextOverlayId != null) {
                                    textOverlays.find { it.id == uiState.editingTextOverlayId }?.text ?: ""
                                } else {
                                    ""
                                }
                                
                                com.dipdev.aiautocaptioner.ui.videoeditor.text.TextInlineEditor(
                                    initialText = initialText,
                                    onDismiss = {
                                        viewModel.setEvent(VideoEditorUiEvent.CancelAddingText)
                                    },
                                    onSave = { newText ->
                                        if (uiState.editingTextOverlayId != null) {
                                            val overlay = textOverlays.find { it.id == uiState.editingTextOverlayId }
                                            if (overlay != null) {
                                                viewModel.setEvent(VideoEditorUiEvent.UpdateTextOverlay(overlay.copy(text = newText)))
                                            }
                                        } else {
                                            viewModel.setEvent(VideoEditorUiEvent.AddTextOverlay(newText, editorState.currentTimelineMs))
                                        }
                                        viewModel.setEvent(VideoEditorUiEvent.CancelAddingText)
                                    },
                                    modifier = Modifier.align(Alignment.Center).zIndex(10f)
                                )
                            }
                                                   // Old GlobalActionButtons deleted because they are now inside rightContent slot of EditorTopOverlay     }
                            } // end preview Box

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
                                selectedTextOverlayId = selectedTextOverlayId,
                                onTextOverlaySelected = { 
                                    viewModel.setEvent(VideoEditorUiEvent.SelectTextOverlay(it)) 
                                    if (it != null) selectedClipId = null
                                },
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
                                    // Fix 6: Pinch-to-zoom from timeline passed through here
                                    zoomLevel = (zoomLevel * scale).coerceIn(0.2f, 5f)
                                },
                                segments = segments,
                                selectedCaptionSegmentId = selectedCaptionSegment?.id,
                                onCaptionSegmentTap = { seg ->
                                    selectedCaptionSegment = seg
                                    inlineEditText = seg.text
                                },
                                onGenerateCaptions = { showTranscriptionBottomSheet = true },
                                onAddImage = { imagePickerLauncher.launch("image/*") },
                                currentMode = currentMode,
                                onModeChange = { currentMode = it },
                                selectedLanguage = selectedLanguage,
                                translateToEnglish = translateToEnglish,
                                onLanguageSelected = { lang, trans ->
                                    viewModel.setEvent(VideoEditorUiEvent.SaveLanguage(lang, trans))
                                },
                                allowedLanguages = allowedLanguages,
                                modifier = Modifier.fillMaxWidth()
                            )
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
                onStart = { modelId, lang, translate, prompt ->
                    showTranscriptionBottomSheet = false
                    pendingTranscriptionParams = PendingTranscriptionParams(modelId, lang, translate, prompt)
                    viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = false))
                }
            )
        }

        com.dipdev.aiautocaptioner.ui.processing.components.TranscriptionOverlay(
            step = processingStep,
            streamedSegments = processingUiState.streamedSegments,
            onCancel = { processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.Cancel) }
        )
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

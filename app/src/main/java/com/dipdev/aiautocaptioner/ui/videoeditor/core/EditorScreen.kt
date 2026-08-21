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
        val isTextOverlaySelected by viewModel.isTextOverlaySelected.collectAsStateWithLifecycle()
        val isImageOverlaySelected by viewModel.isImageOverlaySelected.collectAsStateWithLifecycle()
        
        val overlays = uiState.imageOverlays
        val textOverlays = uiState.textOverlays
        
        val step = uiState.step
        val processingUiState by processingViewModel.uiState.collectAsStateWithLifecycle()
        val processingStep = processingUiState.step


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

        val screenState = rememberEditorScreenState()
        
        with(screenState) {
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

        BackHandler(enabled = uiState.editingTextOverlayId != null && !showTextColorMenu) {
            viewModel.setEvent(VideoEditorUiEvent.StopEditingText)
        }

        LaunchedEffect(Unit) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is VideoEditorUiEffect.ProjectDeleted -> onNavigateBack()
                    is VideoEditorUiEffect.NavigateToProcessing -> {
                        if (effect.params != null) {
                            processingViewModel.setEvent(
                                com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.StartTranscriptionExplicit(
                                    projectId = projectId,
                                    modelId = effect.params.modelId,
                                    language = effect.params.language,
                                    translateToEnglish = effect.params.translate,
                                    initialPrompt = effect.params.prompt
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
                         val currentPlayer = player
                        if (currentPlayer == null) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            return@BoxWithConstraints
                        }

                        val totalEditedMs = remember(clips) { clips.sumOf { it.endTrimMs - it.startTrimMs } }

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
                                        val currentImageOverlay = overlays.find { it.id == selectedOverlayId }
                                        val currentTextOverlay = textOverlays.find { it.id == selectedOverlayId }

                                        EditorSideControls(
                                            selectedOverlayId = selectedOverlayId,
                                            isTextOverlaySelected = isTextOverlaySelected,
                                            isEditingTextOverlay = uiState.editingTextOverlayId == selectedOverlayId,
                                            currentImageOverlay = currentImageOverlay,
                                            currentTextOverlay = currentTextOverlay,
                                            showTextSizeSlider = showTextSizeSlider,
                                            onToggleTextSizeSlider = { showTextSizeSlider = !showTextSizeSlider },
                                            onStartEditingText = { viewModel.setEvent(VideoEditorUiEvent.StartEditingText) },
                                            onShowTextColorMenu = { showTextColorMenu = true },
                                            onDuplicateTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.DuplicateTextOverlay(it)) },
                                            onDuplicateImageOverlay = { viewModel.setEvent(VideoEditorUiEvent.DuplicateOverlay(it)) },
                                            onDeleteTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.DeleteTextOverlay(it)) },
                                            onDeleteImageOverlay = { viewModel.setEvent(VideoEditorUiEvent.DeleteOverlay(it)) },
                                            onDeselectOverlay = { viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(null)) },
                                            onUpdateTextOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateTextOverlay(it)) },
                                            onUpdateImageOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                            onCropImage = { cropOverlay = it }
                                        )
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

                            val timelineData = remember(
                                clips, overlays, textOverlays, segments, thumbnails, originalDurationMs, zoomLevel, editorState.player, editorState
                            ) {
                                com.dipdev.aiautocaptioner.ui.videoeditor.timeline.TimelineData(
                                    clips = clips,
                                    overlays = overlays,
                                    textOverlays = textOverlays,
                                    segments = segments,
                                    thumbnails = thumbnails,
                                    originalDurationMs = originalDurationMs,
                                    zoomLevel = zoomLevel,
                                    player = editorState.player,
                                    currentTimelineMs = { editorState.currentTimelineMs }
                                )
                            }
                            
                            val timelineSelection = remember(
                                selectedClipId, selectedOverlayId, isTextOverlaySelected, selectedCaptionSegment?.id
                            ) {
                                com.dipdev.aiautocaptioner.ui.videoeditor.timeline.TimelineSelection(
                                    selectedClipId = selectedClipId,
                                    selectedOverlayId = selectedOverlayId,
                                    isTextOverlaySelected = isTextOverlaySelected,
                                    selectedCaptionSegmentId = selectedCaptionSegment?.id
                                )
                            }
                            
                            val timelineCallbacks = remember(viewModel, editorState) {
                                object : com.dipdev.aiautocaptioner.ui.videoeditor.timeline.TimelineCallbacks {
                                    override fun onClipSelected(id: String?) {
                                        selectedClipId = id
                                        if (id != null) viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(null))
                                    }
                                    override fun onMoveClip(from: Int, to: Int) {
                                        viewModel.setEvent(VideoEditorUiEvent.MoveClip(from, to, !editorState.isDragging))
                                    }
                                    override fun onTrimClip(id: String, startMs: Long, endMs: Long) {
                                        viewModel.setEvent(VideoEditorUiEvent.TrimClip(id, startMs, endMs, !editorState.isDragging))
                                    }
                                    override fun onOverlaySelected(id: String?) {
                                        viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(id))
                                        if (id != null) selectedClipId = null
                                    }
                                    override fun onUpdateImageOverlay(overlay: com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity) {
                                        viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(overlay))
                                    }
                                    override fun onUpdateTextOverlay(overlay: com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity) {
                                        viewModel.setEvent(VideoEditorUiEvent.UpdateTextOverlay(overlay))
                                    }
                                    override fun onMoveOverlayZ(id: String, bringToFront: Boolean) {
                                        viewModel.setEvent(VideoEditorUiEvent.MoveOverlayZ(id, bringToFront))
                                    }
                                    override fun onDeleteImageOverlay(id: String) {
                                        viewModel.setEvent(VideoEditorUiEvent.DeleteOverlay(id))
                                    }
                                    override fun onDeleteTextOverlay(id: String) {
                                        viewModel.setEvent(VideoEditorUiEvent.DeleteTextOverlay(id))
                                    }
                                    override fun onCaptionSegmentTap(segment: com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity) {
                                        selectedCaptionSegment = segment
                                        inlineEditText = segment.text
                                    }
                                    override fun onRequestThumbnails(timestamps: List<Long>) {
                                        viewModel.thumbnailManager.requestThumbnails(timestamps)
                                    }
                                    override fun onDragStateChange(isDragging: Boolean) {
                                        if (!editorState.isDragging && isDragging) {
                                            viewModel.setEvent(VideoEditorUiEvent.SaveState)
                                        }
                                        editorState.isDragging = isDragging
                                    }
                                    override fun onSplit() {
                                        viewModel.setEvent(VideoEditorUiEvent.SplitClipAtAbsoluteTime(editorState.currentTimelineMs))
                                    }
                                    override fun onDelete(id: String) {
                                        viewModel.setEvent(VideoEditorUiEvent.DeleteClip(id))
                                        selectedClipId = null
                                    }
                                    override fun onDuplicateClip(id: String) {
                                        viewModel.setEvent(VideoEditorUiEvent.DuplicateClip(id))
                                    }
                                    override fun onDuplicateImageOverlay(id: String) {
                                        viewModel.setEvent(VideoEditorUiEvent.DuplicateOverlay(id))
                                    }
                                    override fun onDuplicateTextOverlay(id: String) {
                                        viewModel.setEvent(VideoEditorUiEvent.DuplicateTextOverlay(id))
                                    }
                                    override fun onZoomIn() {
                                        zoomLevel = (zoomLevel * 1.5f).coerceAtMost(5f)
                                    }
                                    override fun onZoomOut() {
                                        zoomLevel = (zoomLevel / 1.5f).coerceAtLeast(0.2f)
                                    }
                                    override fun onPinchZoom(scale: Float) {
                                        zoomLevel = (zoomLevel * scale).coerceIn(0.2f, 5f)
                                    }
                                }
                            }

                            EditorBottomDock(
                                maxHeight = maxH,
                                data = timelineData,
                                selection = timelineSelection,
                                callbacks = timelineCallbacks,
                                styleViewModel = styleViewModel,
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

                        }
                    }
                }
            }
        }

        EditorDialogs(
            showDiscardDialog = showDiscardDialog,
            showTranscriptionBottomSheet = showTranscriptionBottomSheet,
            showTextColorMenu = showTextColorMenu,
            showExportWarning = showExportWarning,
            projectId = projectId,
            selectedTextOverlay = if (selectedOverlayId != null) textOverlays.find { it.id == selectedOverlayId } else null,
            processingStep = processingStep,
            availableModels = processingUiState.availableModels,
            initialModelId = processingUiState.activeModel?.id,
            initialLanguage = processingUiState.selectedLanguage,
            initialTranslate = processingUiState.translateToEnglish,
            initialPrompt = processingUiState.initialPrompt,
            segmentsEmpty = segments.isEmpty(),
            streamedSegments = processingUiState.streamedSegments,
            onDismissDiscard = { showDiscardDialog = false },
            onConfirmDiscard = {
                showDiscardDialog = false
                onNavigateBack()
            },
            onDismissTranscription = { showTranscriptionBottomSheet = false },
            onStartTranscription = { modelId, lang, translate, prompt ->
                showTranscriptionBottomSheet = false
                val params = PendingTranscriptionParams(modelId, lang, translate, prompt)
                viewModel.setEvent(VideoEditorUiEvent.StartTranscription(params))
            },
            onPrepareTranscription = { id ->
                processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.PrepareForProject(id, forceModelPicker = true))
            },
            onCancelProcessing = {
                processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.Cancel)
            },
            onDismissTextColorMenu = { showTextColorMenu = false },
            onUpdateTextOverlay = {
                viewModel.setEvent(VideoEditorUiEvent.UpdateTextOverlay(it))
            },
            onDismissExportWarning = { showExportWarning = false },
            onConfirmExportWarning = {
                showExportWarning = false
                viewModel.setEvent(VideoEditorUiEvent.ApplyEdits(navigateToExport = true, hasCaptions = false, forceExport = true))
            }
        )
        }
    }
}




private fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    val millis = ms % 1000 / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
}

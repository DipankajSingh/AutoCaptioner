package com.dipdev.aiautocaptioner.ui.videoeditor


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.LanguageDropdown
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor
import com.dipdev.aiautocaptioner.ui.videoeditor.components.AudioToolbar
import com.dipdev.aiautocaptioner.ui.videoeditor.components.CompactTabItem
import com.dipdev.aiautocaptioner.ui.videoeditor.components.MiniScrubber
import com.dipdev.aiautocaptioner.ui.videoeditor.components.OverlayActionMenu
import com.dipdev.aiautocaptioner.ui.videoeditor.components.PlayPauseTapOverlay
import com.dipdev.aiautocaptioner.ui.videoeditor.components.TimerPill
import com.dipdev.aiautocaptioner.ui.videoeditor.components.VideoEditorToolbar
import com.dipdev.aiautocaptioner.ui.videoeditor.components.VideoOverlayRenderer
import com.dipdev.aiautocaptioner.ui.videoeditor.components.VideoSideControls
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import com.dipdev.aiautocaptioner.ui.styleeditor.StyleEditorViewModel
import com.dipdev.aiautocaptioner.ui.styleeditor.StyleEditorUiEvent
import com.dipdev.aiautocaptioner.ui.styleeditor.StyleTab
import com.dipdev.aiautocaptioner.ui.styleeditor.StyleEditorBottomBar
import com.dipdev.aiautocaptioner.ui.styleeditor.PresetsTab
import com.dipdev.aiautocaptioner.ui.styleeditor.tabs.TextTab
import com.dipdev.aiautocaptioner.ui.styleeditor.tabs.ColorTab
import com.dipdev.aiautocaptioner.ui.styleeditor.tabs.AnimationTab
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

enum class EditorMode {
    VIDEO, CAPTIONS, AUDIO
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    projectId: String,
    onNavigateToProcessing: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    viewModel: VideoEditorViewModel = hiltViewModel(),
    styleViewModel: StyleEditorViewModel = hiltViewModel()
) {
    ScreenThemeProvider(accentColor = AccentCyan) {
        VideoEditorScreenContent(
            projectId = projectId,
            onNavigateToProcessing = onNavigateToProcessing,
            onNavigateBack = onNavigateBack,
            onNavigateToCaptionEditor = onNavigateToCaptionEditor,
            onNavigateToExport = onNavigateToExport,
            viewModel = viewModel,
            styleViewModel = styleViewModel
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoEditorScreenContent(
    projectId: String,
    onNavigateToProcessing: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    onNavigateToExport: () -> Unit,
    viewModel: VideoEditorViewModel,
    styleViewModel: StyleEditorViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val step = uiState.step
    val clips = uiState.clips
    val clipThumbnails = uiState.clipThumbnails
    val hasEdits = uiState.hasEdits
    val canUndo = uiState.canUndo
    val canRedo = uiState.canRedo
    val selectedLanguage = uiState.selectedLanguage
    val translateToEnglish = uiState.translateToEnglish
    val overlays by viewModel.overlays.collectAsStateWithLifecycle()
    val selectedOverlayId by viewModel.selectedOverlayId.collectAsStateWithLifecycle()

    var showBackDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var timelineHeight by remember { mutableStateOf(240.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    val styleUiState by styleViewModel.uiState.collectAsStateWithLifecycle()
    val styles = styleUiState.styles
    val activeStyle = styleUiState.activeStyle
    val selectedTab = styleUiState.selectedTab
    val isPremium = styleUiState.isPremium
    
    var currentMode by remember { mutableStateOf(EditorMode.VIDEO) }
    var showMenu by remember { mutableStateOf(false) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.setEvent(VideoEditorUiEvent.AddOverlay(it.toString()))
        }
    }

    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
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

    LaunchedEffect(uiState) {
        if (step is VideoEditorUiStep.Success) {
            onNavigateToProcessing()
        }
    }

    // Keep track of dragging state to prevent video stutter during drag
    var isDragging by remember { mutableStateOf(false) }

    // Use ViewModel state for playback
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentTimelineMs by viewModel.currentTimelineMs.collectAsStateWithLifecycle()

    LaunchedEffect(player) {
        viewModel.bindPlayer(player)
    }

    // Show/hide play-pause overlay icon
    var showPlayPauseIcon by remember { mutableStateOf(false) }

    // Sync ExoPlayer playlist with clips
    LaunchedEffect(clips, uiState, isDragging) {
        if (!isDragging && step is VideoEditorUiStep.Ready && clips.isNotEmpty()) {
            val stateReady = step as VideoEditorUiStep.Ready

            // Merge contiguous clips to prevent ExoPlayer decode lag
            val mergedClips = mutableListOf<com.dipdev.aiautocaptioner.data.model.Clip>()
            var currentMergedClip: com.dipdev.aiautocaptioner.data.model.Clip? = null
            for (clip in clips) {
                if (currentMergedClip == null) {
                    currentMergedClip = clip
                } else {
                    if (currentMergedClip.endTrimMs == clip.startTrimMs) {
                        currentMergedClip = currentMergedClip.copy(endTrimMs = clip.endTrimMs)
                    } else {
                        mergedClips.add(currentMergedClip)
                        currentMergedClip = clip
                    }
                }
            }
            if (currentMergedClip != null) {
                mergedClips.add(currentMergedClip)
            }

            val mediaItems = mergedClips.map { clip ->
                MediaItem.Builder()
                    .setUri(android.net.Uri.parse(stateReady.originalPath).toString())
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startTrimMs)
                            .setEndPositionMs(clip.endTrimMs)
                            .build()
                    )
                    .build()
            }

            // Try to preserve timeline position across playlist updates
            val oldWindowIndex = player.currentMediaItemIndex
            val oldPos = player.currentPosition

            val wasPlaying = player.playWhenReady
            player.setMediaItems(mediaItems)
            player.prepare()

            if (mediaItems.isNotEmpty()) {
                player.seekTo(oldWindowIndex.coerceIn(0, mediaItems.size - 1), oldPos)
            }
            player.playWhenReady = wasPlaying

            // Listen for duration changes
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val duration = player.duration
                        if (duration > 0) {
                            viewModel.setEvent(VideoEditorUiEvent.UpdateDurationFromPlayer(duration))
                        }
                    }
                }
            })
        }
    }

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
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val maxTimelineHeight = maxHeight * 0.5f
            when (val state = step) {
                is VideoEditorUiStep.Idle, is VideoEditorUiStep.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is VideoEditorUiStep.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
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
                        if (state.progress > 0) {
                            LinearProgressIndicator(progress = { state.progress / 100f })
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${state.progress}%")
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

                    // Recompute mergedClips locally for the ExoPlayer playlist
                    val mergedClips = remember(clips) {
                        val list = mutableListOf<com.dipdev.aiautocaptioner.data.model.Clip>()
                        var current: com.dipdev.aiautocaptioner.data.model.Clip? = null
                        for (c in clips) {
                            if (current == null) {
                                current = c
                            } else {
                                if (current.endTrimMs == c.startTrimMs) {
                                    current = current.copy(endTrimMs = c.endTrimMs)
                                } else {
                                    list.add(current)
                                    current = c
                                }
                            }
                        }
                        if (current != null) list.add(current)
                        list
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Video Player with play/pause overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            VideoPlayerCard(
                                player = player,
                                modifier = Modifier.fillMaxSize(),
                                showControls = false
                            )

                            // Image Overlays Rendering
                            VideoOverlayRenderer(
                                overlays = overlays,
                                currentTimelineMs = currentTimelineMs,
                                selectedOverlayId = selectedOverlayId,
                                onUpdateOverlay = { viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(it)) },
                                onSelectOverlay = { viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(it)) }
                            )

                            // Play/pause tap overlay
                            PlayPauseTapOverlay(player = player)
                            
                            // Left controls (Close, Menu, Generate Captions)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 16.dp, start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                com.dipdev.aiautocaptioner.ui.videoeditor.components.SideControlButton(
                                    icon = Icons.Outlined.Close,
                                    contentDescription = "Exit Editor",
                                    onClick = {
                                        if (hasEdits) showBackDialog = true else onNavigateBack()
                                    },
                                    enabled = true,
                                    tint = Color.Red,
                                    containerColor = Color.Red.copy(alpha = 0.15f)
                                )
                                
                                Box {
                                    com.dipdev.aiautocaptioner.ui.videoeditor.components.SideControlButton(
                                        icon = Icons.Default.Menu,
                                        contentDescription = "Menu",
                                        onClick = { showMenu = true },
                                        enabled = true
                                    )
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Export") },
                                            onClick = {
                                                showMenu = false
                                                onNavigateToExport()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Project") },
                                            onClick = {
                                                showMenu = false
                                                if (hasEdits) showDeleteDialog = true
                                                else viewModel.setEvent(VideoEditorUiEvent.DeleteProject)
                                            }
                                        )
                                    }
                                }

                                com.dipdev.aiautocaptioner.ui.videoeditor.components.SideControlButton(
                                    icon = Icons.Outlined.Subtitles,
                                    contentDescription = "Generate Captions",
                                    onClick = {
                                        if (hasEdits) viewModel.setEvent(VideoEditorUiEvent.ApplyEdits)
                                        else onNavigateToProcessing()
                                    },
                                    enabled = true
                                )
                            }
                            
                            // Right controls (Undo, Redo, Add Image, Language)
                            var langPanelExpanded by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 16.dp, end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                VideoSideControls(
                                    canUndo = canUndo,
                                    canRedo = canRedo,
                                    onUndo = { viewModel.setEvent(VideoEditorUiEvent.Undo) },
                                    onRedo = { viewModel.setEvent(VideoEditorUiEvent.Redo) },
                                    onAddImage = { imagePickerLauncher.launch("image/*") }
                                )
                                
                                androidx.compose.animation.AnimatedContent(
                                    targetState = langPanelExpanded,
                                    label = "langPanel"
                                ) { expanded ->
                                    if (expanded) {
                                        androidx.compose.material3.Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            shadowElevation = 8.dp,
                                            modifier = Modifier.width(220.dp)
                                        ) {
                                            androidx.compose.foundation.layout.Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "Language",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                    )
                                                    androidx.compose.material3.IconButton(
                                                        onClick = { langPanelExpanded = false },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Outlined.Close, null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                LanguageDropdown(
                                                    selectedLanguage = selectedLanguage,
                                                    onLanguageSelected = { lang ->
                                                        viewModel.setEvent(VideoEditorUiEvent.SaveLanguage(lang, if (lang == "en") false else translateToEnglish))
                                                    },
                                                    allowedLanguages = listOf("multilingual")
                                                )
                                                if (selectedLanguage != "en") {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Translate to EN", style = MaterialTheme.typography.labelSmall)
                                                        androidx.compose.material3.Switch(
                                                            checked = translateToEnglish,
                                                            onCheckedChange = { v ->
                                                                viewModel.setEvent(VideoEditorUiEvent.SaveLanguage(selectedLanguage, v))
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Collapsed pill showing current language
                                        androidx.compose.material3.Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            shadowElevation = 4.dp,
                                            onClick = { langPanelExpanded = true }
                                        ) {
                                            Text(
                                                text = selectedLanguage.uppercase(),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Timer Pill
                        TimerPill(
                            currentTimelineMs = currentTimelineMs,
                            totalEditedMs = totalEditedMs,
                            formatTime = ::formatTime
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Mini scrubber
                        MiniScrubber(
                            currentTimelineMs = currentTimelineMs,
                            totalEditedMs = totalEditedMs,
                            clips = clips,
                            player = player
                        )

                        // Horizontal Separator
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Unified Resizable Bottom Area
                        Box(modifier = Modifier.fillMaxWidth().height(timelineHeight)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Unified Drag handle at top
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                        .pointerInput(Unit) {
                                            detectVerticalDragGestures { _, dragAmount ->
                                                val dragAmountDp = with(density) { dragAmount.toDp() }
                                                timelineHeight = (timelineHeight - dragAmountDp).coerceIn(200.dp, maxTimelineHeight)
                                            }
                                        },
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .width(40.dp)
                                            .height(4.dp)
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                    )
                                }

                                if (currentMode != EditorMode.CAPTIONS) {
                                    // Timeline takes remaining space
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                        VideoTimelineView(
                                            clips = clips,
                                            clipThumbnails = clipThumbnails,
                                            selectedClipId = selectedClipId,
                                            onClipSelected = { 
                                                selectedClipId = it
                                                currentMode = EditorMode.VIDEO 
                                            },
                                            onMoveClip = { from, to -> viewModel.setEvent(VideoEditorUiEvent.MoveClip(from, to)) },
                                            overlays = overlays,
                                            selectedOverlayId = selectedOverlayId,
                                            onOverlaySelected = { viewModel.setEvent(VideoEditorUiEvent.SelectOverlay(it)) },
                                            onOverlayTimingChanged = { id, startMs, endMs ->
                                                val overlay = overlays.find { it.id == id } ?: return@VideoTimelineView
                                                viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(overlay.copy(startTimeMs = startMs, endTimeMs = endMs)))
                                            },
                                            onCaptionTap = { currentMode = EditorMode.CAPTIONS },
                                            onDragStateChange = { isDragging = it },
                                            zoomLevel = zoomLevel,
                                            player = player,
                                            currentTimelineMs = currentTimelineMs,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val selectedOverlay = overlays.find { it.id == selectedOverlayId }
                                    if (selectedOverlay != null) {
                                        OverlayActionMenu(
                                            selectedOverlay = selectedOverlay,
                                            onFullVideo = {
                                                viewModel.setEvent(VideoEditorUiEvent.UpdateOverlay(
                                                    selectedOverlay.copy(startTimeMs = 0L, endTimeMs = Long.MAX_VALUE)
                                                ))
                                            },
                                            onSendToBack = { viewModel.setEvent(VideoEditorUiEvent.MoveOverlayZ(selectedOverlay.id, bringToFront = false)) },
                                            onBringToFront = { viewModel.setEvent(VideoEditorUiEvent.MoveOverlayZ(selectedOverlay.id, bringToFront = true)) },
                                            onDelete = { viewModel.setEvent(VideoEditorUiEvent.DeleteOverlay(selectedOverlay.id)) }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    // Restored toolbar but much more compact
                                    when (currentMode) {
                                        EditorMode.VIDEO -> {
                                            VideoEditorToolbar(
                                                selectedClipId = selectedClipId,
                                                zoomLevel = zoomLevel,
                                                onSplit = { viewModel.setEvent(VideoEditorUiEvent.SplitClipAtAbsoluteTime(currentTimelineMs)) },
                                                onDuplicate = {
                                                    selectedClipId?.let { viewModel.setEvent(VideoEditorUiEvent.DuplicateClip(it)) }
                                                },
                                                onDelete = {
                                                    selectedClipId?.let {
                                                        viewModel.setEvent(VideoEditorUiEvent.DeleteClip(it))
                                                        selectedClipId = null
                                                    }
                                                },
                                                onZoomIn = { zoomLevel = (zoomLevel * 1.5f).coerceAtMost(5f) },
                                                onZoomOut = { zoomLevel = (zoomLevel / 1.5f).coerceAtLeast(0.2f) }
                                            )
                                        }
                                        EditorMode.AUDIO -> {
                                            AudioToolbar()
                                        }
                                        else -> {}
                                    }

                                } else {
                                    // Styling Tabs Area
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 16.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Style Editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                                            }
                                            
                                            StyleEditorBottomBar(
                                                selectedTab = selectedTab,
                                                isPremium = isPremium,
                                                onTabSelected = { tab ->
                                                    styleViewModel.setEvent(StyleEditorUiEvent.SelectTab(tab))
                                                }
                                            )
                                            
                                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                                activeStyle?.let { style ->
                                                    when (selectedTab) {
                                                        StyleTab.PRESETS -> {
                                                            PresetsTab(
                                                                styles = styles,
                                                                activeStyle = style,
                                                                onPresetSelected = { styleViewModel.setEvent(StyleEditorUiEvent.SelectPreset(it)) },
                                                                onPresetLongClicked = { },
                                                                onAddPreset = { }
                                                            )
                                                        }
                                                        StyleTab.TEXT -> {
                                                            TextTab(
                                                                style = style,
                                                                onFontSizeChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateFontSize(it)) },
                                                                onFontWeightChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateFontWeight(it)) },
                                                                onMaxWordsChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateMaxWordsPerLine(it)) },
                                                                onMaxLinesChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateMaxLines(it)) },
                                                                onRemovePunctuationChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateRemovePunctuation(it)) },
                                                                onAlignmentChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateAlignment(it)) },
                                                                onLetterSpacingChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateLetterSpacing(it)) },
                                                                onIsItalicChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateIsItalic(it)) }
                                                            )
                                                        }
                                                        StyleTab.COLOR -> {
                                                            ColorTab(
                                                                style = style,
                                                                onTextColorChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateTextColor(it)) },
                                                                onHighlightColorChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateHighlightColor(it)) },
                                                                onOutlineColorChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateOutlineColor(it)) },
                                                                onOutlineWidthChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateOutlineWidth(it)) },
                                                                onBackgroundTypeChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundType(it)) },
                                                                onBackgroundColorChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundColor(it)) },
                                                                onBackgroundOpacityChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundOpacity(it)) },
                                                                onBackgroundPaddingHChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundPaddingH(it)) },
                                                                onBackgroundPaddingVChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundPaddingV(it)) },
                                                                onBackgroundCornerRadiusChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundCornerRadius(it)) }
                                                            )
                                                        }
                                                        StyleTab.ANIMATION -> {
                                                            AnimationTab(
                                                                style = style,
                                                                onDisplayModeChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateDisplayMode(it)) },
                                                                onWordEnterChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateWordEnterAnimation(it)) },
                                                                onKaraokeHighlightChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateKaraokeHighlightMode(it)) },
                                                                onAnimationDurationChange = { styleViewModel.setEvent(StyleEditorUiEvent.UpdateAnimationDurationMs(it)) }
                                                            )
                                                        }
                                                    }
                                                } ?: run {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    val millis = ms % 1000 / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
}


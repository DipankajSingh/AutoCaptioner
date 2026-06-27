package com.dipdev.aiautocaptioner.ui.videoeditor


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.EmeraldPrimary
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    projectId: String,
    onNavigateToProcessing: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VideoEditorViewModel = hiltViewModel()
) {
    CompositionLocalProvider(LocalAccentColor provides AccentAmber) {
        VideoEditorScreenContent(
            projectId = projectId,
            onNavigateToProcessing = onNavigateToProcessing,
            onNavigateBack = onNavigateBack,
            viewModel = viewModel
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
    viewModel: VideoEditorViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val step = uiState.step
    val clips = uiState.clips
    val clipThumbnails = uiState.clipThumbnails
    val hasEdits = uiState.hasEdits
    val canUndo = uiState.canUndo
    val canRedo = uiState.canRedo

    var showBackDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }

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
    }

    LaunchedEffect(uiState) {
        if (step is VideoEditorUiStep.Success) {
            onNavigateToProcessing()
        }
    }

    // Keep track of dragging state to prevent video stutter during drag
    var isDragging by remember { mutableStateOf(false) }

    // Track play/pause state polled at ~60fps
    var isPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (isActive) {
            isPlaying = player.isPlaying
            delay(16.milliseconds)
        }
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
        topBar = {
            // Custom top bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Close
                    IconButton(onClick = {
                        if (hasEdits) showBackDialog = true else onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close"
                        )
                    }

                    // Center: Title
                    Text(
                        text = "Video Editor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Right: Trash + Generate pill
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (hasEdits) showDeleteDialog = true
                            else viewModel.setEvent(VideoEditorUiEvent.DeleteProject)
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete Project"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                if (hasEdits) viewModel.setEvent(VideoEditorUiEvent.ApplyEdits)
                                else onNavigateToProcessing()
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Generate Captions", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                    var currentTimelineMs by remember { mutableLongStateOf(0L) }

                    // Recompute mergedClips locally for the timer
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

                    // Sync timeline timer
                    LaunchedEffect(player, mergedClips) {
                        while (isActive) {
                            val windowIndex = player.currentMediaItemIndex
                            val posInWindow = player.currentPosition
                            var accumulated = 0L
                            for (i in 0 until windowIndex.coerceAtMost(mergedClips.size)) {
                                accumulated += (mergedClips[i].endTrimMs - mergedClips[i].startTrimMs)
                            }
                            currentTimelineMs = accumulated + posInWindow
                            kotlinx.coroutines.delay(16.milliseconds)
                        }
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

                            // Play/pause tap overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                    ) {
                                        if (player.isPlaying) player.pause() else player.play()
                                        showPlayPauseIcon = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Auto-hide the play/pause icon after 1.5s
                                LaunchedEffect(showPlayPauseIcon) {
                                    if (showPlayPauseIcon) {
                                        delay(1500)
                                        showPlayPauseIcon = false
                                    }
                                }
                                val iconAlpha by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (showPlayPauseIcon) 1f else 0f,
                                    animationSpec = androidx.compose.animation.core.tween(200),
                                    label = "playPauseAlpha"
                                )
                                if (iconAlpha > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f * iconAlpha),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = AccentAmber.copy(alpha = iconAlpha),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Timer Pill
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${formatTime(currentTimelineMs)} / ${formatTime(totalEditedMs)}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Mini scrubber
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .padding(horizontal = 16.dp)
                        ) {
                            val scrubFraction = if (totalEditedMs > 0L) {
                                (currentTimelineMs.toFloat() / totalEditedMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Slider(
                                value = scrubFraction,
                                onValueChange = { fraction ->
                                    val seekMs = (fraction * totalEditedMs).toLong()
                                    // Seek player to the appropriate clip/position
                                    var accumulated = 0L
                                    var targetWindowIndex = 0
                                    var targetPosInWindow = 0L
                                    for (i in clips.indices) {
                                        val clipDuration = clips[i].endTrimMs - clips[i].startTrimMs
                                        if (seekMs >= accumulated && seekMs < accumulated + clipDuration) {
                                            targetWindowIndex = i
                                            targetPosInWindow = seekMs - accumulated
                                            break
                                        }
                                        accumulated += clipDuration
                                        targetWindowIndex = i
                                        targetPosInWindow = clips[i].endTrimMs - clips[i].startTrimMs
                                    }
                                    player.seekTo(targetWindowIndex, targetPosInWindow)
                                },
                                modifier = Modifier.fillMaxSize(),
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentAmber,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = AccentAmber.copy(alpha = 0.3f)
                                )
                            )
                        }

                        // Horizontal Separator
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                            VideoTimelineView(
                                clips = clips,
                                mergedClips = mergedClips,
                                clipThumbnails = clipThumbnails,
                                selectedClipId = selectedClipId,
                                onClipSelected = { selectedClipId = it },
                                onMoveClip = { from, to -> viewModel.setEvent(VideoEditorUiEvent.MoveClip(from, to)) },
                                onDragStateChange = { isDragging = it },
                                zoomLevel = zoomLevel,
                                player = player,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // New toolbar
                        VideoEditorToolbar(
                            canUndo = canUndo,
                            canRedo = canRedo,
                            selectedClipId = selectedClipId,
                            zoomLevel = zoomLevel,
                            onUndo = { viewModel.setEvent(VideoEditorUiEvent.Undo) },
                            onRedo = { viewModel.setEvent(VideoEditorUiEvent.Redo) },
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

                        Spacer(modifier = Modifier.height(8.dp))
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

// ── Toolbar ───────────────────────────────────────────────────────────────────

@Composable
private fun VideoEditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    selectedClipId: String?,
    zoomLevel: Float,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    val hasSelection = selectedClipId != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Group 1: Undo / Redo
        Row {
            LabeledIconButton(
                icon = Icons.AutoMirrored.Outlined.Undo,
                label = "Undo",
                onClick = onUndo,
                enabled = canUndo
            )
            LabeledIconButton(
                icon = Icons.AutoMirrored.Outlined.Redo,
                label = "Redo",
                onClick = onRedo,
                enabled = canRedo
            )
        }

        // Group 2: Clip tools
        Row {
            LabeledIconButton(
                icon = Icons.Outlined.ContentCut,
                label = "Split",
                onClick = onSplit
            )
            LabeledIconButton(
                icon = Icons.Outlined.ContentCopy,
                label = "Duplicate",
                onClick = onDuplicate,
                enabled = hasSelection
            )
            LabeledIconButton(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                onClick = onDelete,
                enabled = hasSelection,
                tint = AccentRose
            )
        }

        // Group 3: Zoom
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onZoomOut) {
                Icon(
                    Icons.Outlined.Remove,
                    contentDescription = "Zoom Out",
                    tint = LocalAccentColor.current
                )
            }
            Text(
                text = "${(zoomLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onZoomIn) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "Zoom In",
                    tint = LocalAccentColor.current
                )
            }
        }
    }
}

@Composable
private fun LabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = LocalAccentColor.current
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else tint.copy(alpha = 0.38f)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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

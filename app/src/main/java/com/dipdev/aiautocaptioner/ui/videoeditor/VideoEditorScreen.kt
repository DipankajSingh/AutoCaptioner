package com.dipdev.aiautocaptioner.ui.videoeditor


import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val clipThumbnails by viewModel.clipThumbnails.collectAsStateWithLifecycle()
    val hasEdits by viewModel.hasEdits.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

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

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    LaunchedEffect(uiState) {
        if (uiState is VideoEditorUiState.Success) {
            onNavigateToProcessing()
        }
    }

    // Keep track of dragging state to prevent video stutter during drag
    var isDragging by androidx.compose.runtime.remember { mutableStateOf(false) }

    // Sync ExoPlayer playlist with clips
    LaunchedEffect(clips, uiState, isDragging) {
        if (!isDragging && uiState is VideoEditorUiState.Ready && clips.isNotEmpty()) {
            val stateReady = uiState as VideoEditorUiState.Ready
            
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
            // We can't perfectly recover absolute time here without storing the OLD mergedClips,
            // but for simplicity we rely on dragging dropping to handle seeking.
            // If the playlist size didn't change drastically, we just seek to 0, or attempt the best effort:
            
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
                            viewModel.updateDurationFromPlayer(duration)
                        }
                    }
                }
            })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Video") },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (hasEdits) {
                            showBackDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (hasEdits) {
                            showDeleteDialog = true
                        } else {
                            viewModel.deleteProject { onNavigateBack() }
                        }
                    }) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete Project")
                    }
                    IconButton(onClick = {
                        if (hasEdits) {
                            viewModel.applyEdits()
                        } else {
                            onNavigateToProcessing()
                        }
                    }) {
                        Icon(imageVector = Icons.Outlined.Done, contentDescription = "Continue")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is VideoEditorUiState.Idle, is VideoEditorUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is VideoEditorUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        AppPrimaryButton(onClick = { viewModel.loadProject(projectId) }) {
                            Text("Retry")
                        }
                    }
                }
                is VideoEditorUiState.Processing -> {
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
                        AppOutlinedButton(onClick = { viewModel.cancel() }) {
                            Text("Cancel")
                        }
                    }
                }
                is VideoEditorUiState.Success -> {
                    // Handled by LaunchedEffect
                }
                is VideoEditorUiState.Ready -> {
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
                            // Removed horizontal padding so video can be full width
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Video Player container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            VideoPlayerCard(
                                player = player,
                                modifier = Modifier
                                    .fillMaxSize(),
                                showControls = false
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Timer Pill & Divider
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${formatTime(currentTimelineMs)} / ${formatTime(totalEditedMs)}",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                        
                        // Horizontal Separator
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            thickness = 1.dp,
                            color = Color.DarkGray
                        )
                        
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                            VideoTimelineView(
                                clips = clips,
                                mergedClips = mergedClips,
                                clipThumbnails = clipThumbnails,
                                selectedClipId = selectedClipId,
                                onClipSelected = { selectedClipId = it },
                                onMoveClip = { from, to -> viewModel.moveClip(from, to) },
                                onDragStateChange = { isDragging = it },
                                zoomLevel = zoomLevel,
                                player = player,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons Row (Horizontally Scrollable)
                        val toolsScrollState = androidx.compose.foundation.rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(toolsScrollState)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Zoom controls
                            IconButton(onClick = { zoomLevel = (zoomLevel / 1.5f).coerceAtLeast(0.2f) }) {
                                Icon(Icons.Outlined.Remove, contentDescription = "Zoom Out")
                            }
                            IconButton(onClick = { zoomLevel = (zoomLevel * 1.5f).coerceAtMost(5f) }) {
                                Icon(Icons.Outlined.Add, contentDescription = "Zoom In")
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Undo / Redo
                            IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                            }
                            IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
                            }

                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Vertical Separator
                            Box(modifier = Modifier.height(24.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            
                            Spacer(modifier = Modifier.width(16.dp))

                            // Split based on absolute timeline ms
                            IconButton(onClick = {
                                viewModel.splitClipAtAbsoluteTime(currentTimelineMs)
                            }) {
                                Icon(Icons.Outlined.ContentCut, contentDescription = "Split")
                            }

                            // Contextual Action Buttons
                            if (selectedClipId != null) {
                                IconButton(onClick = { viewModel.duplicateClip(selectedClipId!!) }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate")
                                }
                                IconButton(onClick = { 
                                    viewModel.deleteClip(selectedClipId!!)
                                    selectedClipId = null 
                                }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (toolsScrollState.maxValue > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val trackColor = MaterialTheme.colorScheme.surfaceVariant
                            val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(2.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                drawRoundRect(
                                    color = trackColor,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                                )
                                val progress = toolsScrollState.value.toFloat() / toolsScrollState.maxValue
                                val thumbWidth = size.width * 0.4f
                                val thumbOffset = progress * (size.width - thumbWidth)
                                drawRoundRect(
                                    color = thumbColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(thumbOffset, 0f),
                                    size = androidx.compose.ui.geometry.Size(thumbWidth, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                                )
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
                    viewModel.applyEdits()
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
                    viewModel.deleteProject { onNavigateBack() }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    val millis = ms % 1000 / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
}

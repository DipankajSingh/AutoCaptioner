package com.dipdev.aiautocaptioner.ui.videoeditor


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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    val hasEdits by viewModel.hasEdits.collectAsStateWithLifecycle()

    var showBackDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    LaunchedEffect(uiState) {
        if (uiState is VideoEditorUiState.Success) {
            onNavigateToProcessing()
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Video Player
                        VideoPlayerCard(
                            path = state.originalPath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            showControls = true
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Timeline
                        Text("Timeline", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                            VideoTimelineView(
                                clips = clips,
                                selectedClipId = selectedClipId,
                                onClipSelected = { selectedClipId = it },
                                onMoveClip = { from, to -> viewModel.moveClip(from, to) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Playhead Marker
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(2.dp)
                                    .fillMaxSize()
                                    .background(Color.Red)
                            )
                        }

                        // Conditional Action Buttons
                        if (selectedClipId != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                AppOutlinedButton(onClick = {
                                    viewModel.splitClip(selectedClipId!!, (clips.find { it.id == selectedClipId }?.startTrimMs ?: 0L) + 1000L) // Dummy 1s split for now
                                }) { Text("Split") }
                                AppOutlinedButton(onClick = { viewModel.duplicateClip(selectedClipId!!) }) { Text("Duplicate") }
                                AppOutlinedButton(onClick = { 
                                    viewModel.deleteClip(selectedClipId!!)
                                    selectedClipId = null 
                                }) { Text("Delete") }
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

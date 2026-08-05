package com.dipdev.aiautocaptioner.ui.home

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.RoundedProgressBar
import com.dipdev.aiautocaptioner.ui.components.SpeedDialFab
import com.dipdev.aiautocaptioner.ui.components.SpeedDialItem
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import compose.icons.FeatherIcons
import compose.icons.feathericons.Scissors
import compose.icons.feathericons.Video
import compose.icons.feathericons.Zap
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigateToSmartRecorder: (String) -> Unit,
    onNavigateToVideoEditor: (String) -> Unit,
    onNavigateToProcessing: (String) -> Unit,
    onNavigateToCaptionEditor: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val projects = uiState.projects
    val importState = uiState.importState
    
    var previewVideoPath by remember { mutableStateOf<String?>(null) }
    var speedDialExpanded by remember { mutableStateOf(false) }

    // Advanced import picker → VideoEditor
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importVideo(it) }
    }

    // Quick Generate picker → Processing directly (no trimming)
    val quickPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importVideoQuick(it) }
    }

    BackHandler {
        val activity = context as? Activity
        activity?.moveTaskToBack(true)
    }

    // Navigate when import succeeds
    LaunchedEffect(importState) {
        when (importState) {
            is ImportState.Success -> {
                viewModel.resetImportState()
                onNavigateToVideoEditor(importState.projectId)
            }
            is ImportState.QuickSuccess -> {
                viewModel.resetImportState()
                onNavigateToProcessing(importState.projectId)
            }
            else -> {}
        }
    }

    ScreenThemeProvider(accentColor = AccentAmber) {
        Scaffold(
            topBar = {
                HomeTopBar(
                    onNavigateToSettings = onNavigateToSettings
                )
            },
            floatingActionButton = {
                if (projects?.isNotEmpty() == true) {
                    SpeedDialFab(
                        expanded = speedDialExpanded,
                        onExpandedChange = { speedDialExpanded = it },
                        items = listOf(
                            SpeedDialItem(
                                icon = FeatherIcons.Zap,
                                label = stringResource(R.string.home_1_tap_captions),
                                color = AccentAmber,
                                onColor = Color.White,
                                onClick = { quickPicker.launch("video/*") }
                            ),
                            SpeedDialItem(
                                icon = FeatherIcons.Video,
                                label = stringResource(R.string.home_record_video),
                                color = Color(0xFF232632),
                                onColor = Color(0xFFE2E7F0),
                                onClick = { onNavigateToSmartRecorder(uiState.lastRecordingMode) }
                            ),
                            SpeedDialItem(
                                icon = FeatherIcons.Scissors,
                                label = stringResource(R.string.home_advanced_studio),
                                color = Color(0xFF232632),
                                onColor = Color(0xFFE2E7F0),
                                onClick = { videoPicker.launch("video/*") }
                            )
                        )
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val announcement = uiState.announcementMessage
                    if (announcement.isNotBlank()) {
                        var dismissed by remember(announcement) { mutableStateOf(false) }
                        if (!dismissed) {
                            HomeAnnouncementBanner(
                                announcement = announcement,
                                onDismiss = { dismissed = true }
                            )
                        }
                    }
                    
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            projects == null -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            projects.isEmpty() -> {
                                EmptyProjectView(
                                    lastRecordingMode = uiState.lastRecordingMode,
                                    onNavigateToSmartRecorder = onNavigateToSmartRecorder,
                                    onQuickCaption = { quickPicker.launch("video/*") },
                                    onAdvancedStudio = { videoPicker.launch("video/*") }
                                )
                            }
                            else -> {
                                HomeProjectList(
                                    projects = projects,
                                    onNavigateToVideoEditor = onNavigateToVideoEditor,
                                    onNavigateToProcessing = onNavigateToProcessing,
                                    onNavigateToCaptionEditor = onNavigateToCaptionEditor,
                                    onNavigateToHistory = onNavigateToHistory,
                                    onDeleteProject = { viewModel.deleteProject(it) },
                                    onRenameProject = { id, title -> viewModel.renameProject(id, title) },
                                    onDuplicateProject = { viewModel.duplicateProject(it) },
                                    onPlayVideo = { previewVideoPath = it }
                                )
                            }
                        }
                    }
                }

                // Loading overlay while importing
                if (importState is ImportState.Loading) {
                    ImportProgressOverlay()
                }

                // Error snackbar
                if (importState is ImportState.Error) {
                    val message = importState.message
                    LaunchedEffect(importState) {
                        delay(3000.milliseconds)
                        viewModel.resetImportState()
                    }
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.home_import_failed).format(message))
                    }
                }
                
                // Video Preview Dialog
                val currentPreviewPath = previewVideoPath
                if (currentPreviewPath != null && File(currentPreviewPath).exists()) {
                    VideoPreviewDialog(
                        videoPath = currentPreviewPath,
                        onDismiss = { previewVideoPath = null }
                    )
                }

                // Scrim Overlay when SpeedDial is expanded
                AnimatedVisibility(
                    visible = speedDialExpanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { speedDialExpanded = false }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProgressOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundedProgressBar(
                modifier = Modifier.fillMaxWidth(0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.home_importing_video))
        }
    }
}

@Composable
private fun VideoPreviewDialog(
    videoPath: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            VideoPlayerCard(
                path = videoPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
            )
        }
    }
}

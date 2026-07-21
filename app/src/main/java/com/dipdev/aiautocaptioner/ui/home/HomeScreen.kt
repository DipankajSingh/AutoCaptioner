package com.dipdev.aiautocaptioner.ui.home


import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.Scissors
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Video
import compose.icons.feathericons.X
import compose.icons.feathericons.Zap
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.ui.components.RoundedProgressBar
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSmartRecorder: (String) -> Unit,
    onNavigateToVideoEditor: (String) -> Unit,
    onNavigateToProcessing: (String) -> Unit,
    onNavigateToCaptionEditor: (String) -> Unit,
    onNavigateToModelManager: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val projects = uiState.projects
    val activeModel = uiState.activeModel
    
    val importState = uiState.importState
    
    var previewVideoPath by remember { mutableStateOf<String?>(null) }

    // Request Notification Permission on Android 13+
    var showNotificationRationale by remember { mutableStateOf(false) }
    var hasRequestedNotification by remember { mutableStateOf(false) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }
        
        LaunchedEffect(isGranted) {
            if (!isGranted && !hasRequestedNotification) {
                showNotificationRationale = true
            }
        }

        if (showNotificationRationale) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { 
                    showNotificationRationale = false
                    hasRequestedNotification = true
                },
                title = { Text(stringResource(R.string.home_notification_title)) },
                text = { Text(stringResource(R.string.home_notification_desc)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showNotificationRationale = false
                            hasRequestedNotification = true
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    ) {
                        Text(stringResource(R.string.home_enable))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { 
                            showNotificationRationale = false
                            hasRequestedNotification = true
                        }
                    ) {
                        Text(stringResource(R.string.home_not_now))
                    }
                }
            )
        }
    }

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
        val activity = context as? android.app.Activity
        activity?.moveTaskToBack(true)
    }

    // Navigate when import succeeds
    LaunchedEffect(uiState.importState) {
        when (val importState = uiState.importState) {
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

    var speedDialExpanded by remember { mutableStateOf(false) }

    ScreenThemeProvider(accentColor = AccentAmber) {
        Scaffold(
            topBar = {
            HomeTopBar(
                activeModel = activeModel,
                onNavigateToModelManager = onNavigateToModelManager,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        floatingActionButton = {
            if (projects?.isNotEmpty() == true) {
                com.dipdev.aiautocaptioner.ui.components.SpeedDialFab(
                    expanded = speedDialExpanded,
                    onExpandedChange = { speedDialExpanded = it },
                    items = listOf(
                        com.dipdev.aiautocaptioner.ui.components.SpeedDialItem(
                            icon = FeatherIcons.Video,
                            label = "Record Video",
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            onColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { onNavigateToSmartRecorder(uiState.lastRecordingMode) }
                        ),
                        com.dipdev.aiautocaptioner.ui.components.SpeedDialItem(
                            icon = FeatherIcons.Zap,
                            label = "1-Tap Captions",
                            color = MaterialTheme.colorScheme.primary,
                            onColor = MaterialTheme.colorScheme.onPrimary,
                            onClick = { quickPicker.launch("video/*") }
                        ),
                        com.dipdev.aiautocaptioner.ui.components.SpeedDialItem(
                            icon = FeatherIcons.Scissors,
                            label = "Advanced Studio",
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Temporary fetch and display of Remote Config
                val announcement = uiState.announcementMessage

                if (announcement.isNotBlank()) {
                    var dismissed by remember(announcement) { mutableStateOf(false) }
                    if (!dismissed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left accent strip
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(56.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = announcement,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                            IconButton(onClick = { dismissed = true }) {
                                Icon(
                                    FeatherIcons.X,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (projects == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (projects.isEmpty()) {
                EmptyProjectView(
                    lastRecordingMode = uiState.lastRecordingMode,
                    onNavigateToSmartRecorder = onNavigateToSmartRecorder,
                    onQuickCaption = { quickPicker.launch("video/*") },
                    onAdvancedStudio = { videoPicker.launch("video/*") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 100.dp // space for FAB
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section header with project count badge
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "My Projects",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${projects.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    items(
                        items = projects,
                        key = { it.project.id }
                    ) { projectWithExports ->
                        ProjectCard(
                            projectWithExports = projectWithExports,
                            onClick = {
                                val project = projectWithExports.project
                                if (project.creationMode == com.dipdev.aiautocaptioner.data.db.entity.CreationMode.QUICK_CAPTION) {
                                    if (project.status == com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus.TRANSCRIBED || project.status == com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus.EXPORTED) {
                                        onNavigateToCaptionEditor(project.id)
                                    } else {
                                        onNavigateToProcessing(project.id)
                                    }
                                } else {
                                    onNavigateToVideoEditor(project.id)
                                }
                            },
                            onDelete = { viewModel.deleteProject(projectWithExports.project) },
                            onRename = { newTitle -> viewModel.renameProject(projectWithExports.project.id, newTitle) },
                            onDuplicate = { viewModel.duplicateProject(projectWithExports.project.id) },
                            onPlayVideo = { path -> previewVideoPath = path },
                            onShareVideo = { path -> 
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(android.content.Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", java.io.File(path)))
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video"))
                                } catch (_: android.content.ActivityNotFoundException) {
                                    // Handle missing activity gracefully
                                }
                            },
                            onNavigateToHistory = { onNavigateToHistory(projectWithExports.project.id) },
                            onRetranscribe = { onNavigateToProcessing(projectWithExports.project.id) }
                        )
                    }
                }
            }

            // Loading overlay while importing
            if (importState is ImportState.Loading) {
                Box(
                    modifier = Modifier
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

            // Error snackbar
            if (importState is ImportState.Error) {
                val message = importState.message
                LaunchedEffect(importState) {
                    kotlinx.coroutines.delay(3000.milliseconds)
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
            if (currentPreviewPath != null) {
                Dialog(
                    onDismissRequest = { previewVideoPath = null },
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
                            path = currentPreviewPath,
                            modifier = Modifier.fillMaxWidth().aspectRatio(9f/16f)
                        )
                    }
                }
            }
        } // end of Box(modifier = Modifier.weight(1f).fillMaxWidth())
        } // end of Column

        // Scrim Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = speedDialExpanded,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { speedDialExpanded = false }
                    )
            )
        }

        } // end of outer Box (padding)
    } // end of Scaffold content box
    } // end of ScreenThemeProvider
} // end of HomeScreen

@Composable
private fun HomeTopBar(
    activeModel: com.dipdev.aiautocaptioner.data.model.WhisperModel?,
    onNavigateToModelManager: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand: icon + wordmark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo_ui),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "AutoCaptioner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // Right actions: model chip + settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                val modelText = activeModel?.displayName?.split("—")?.first()?.trim()
                    ?.let { "Model: $it" } ?: "Select Model"
                val buttonColor = if (activeModel != null)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondaryContainer

                FilledTonalButton(
                    onClick = onNavigateToModelManager,
                    modifier = Modifier.padding(end = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = buttonColor),
                    contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 0.dp, bottom = 0.dp)
                ) {
                    Text(modelText, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(FeatherIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = FeatherIcons.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectView(
    lastRecordingMode: String,
    onNavigateToSmartRecorder: (String) -> Unit,
    onQuickCaption: () -> Unit,
    onAdvancedStudio: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.nothing))
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(160.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No projects yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose how you want to start your first video",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        androidx.compose.material3.Card(
            onClick = { onNavigateToSmartRecorder(lastRecordingMode) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Record Video" },
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(FeatherIcons.Video, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.home_record_video), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(stringResource(R.string.home_record_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        androidx.compose.material3.Card(
            onClick = onQuickCaption,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Start 1-Tap Captions" },
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(FeatherIcons.Zap, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.home_1_tap_captions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.home_1_tap_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        androidx.compose.material3.Card(
            onClick = onAdvancedStudio,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Start Advanced Studio" },
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(FeatherIcons.Scissors, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.home_advanced_studio), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(stringResource(R.string.home_advanced_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                }
            }
        }
    }
}

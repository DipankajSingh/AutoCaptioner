package com.dipdev.aiautocaptioner.ui.home


import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.graphics.toArgb
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.ui.components.RoundedProgressBar
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.theme.AccentBlue
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateToSmartRecorder: () -> Unit,
    onNavigateToVideoEditor: (String) -> Unit,
    onNavigateToProcessing: (String) -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToModelManager: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {

    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    
    var previewVideoPath by remember { mutableStateOf<String?>(null) }

    // Request Notification Permission on Android 13+
    var showNotificationRationale by remember { mutableStateOf(false) }
    var hasRequestedNotification by remember { mutableStateOf(false) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionState = rememberPermissionState(
            permission = android.Manifest.permission.POST_NOTIFICATIONS
        )
        
        LaunchedEffect(notificationPermissionState.status) {
            if (!notificationPermissionState.status.isGranted && !hasRequestedNotification) {
                showNotificationRationale = true
            }
        }

        if (showNotificationRationale) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { 
                    showNotificationRationale = false
                    hasRequestedNotification = true
                },
                title = { Text("Show Progress in Background?") },
                text = { Text("AutoCaptioner can show a progress bar in your notifications so you can track transcriptions even when the app is minimized. Would you like to enable this?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showNotificationRationale = false
                            hasRequestedNotification = true
                            notificationPermissionState.launchPermissionRequest()
                        }
                    ) {
                        Text("Enable")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { 
                            showNotificationRationale = false
                            hasRequestedNotification = true
                        }
                    ) {
                        Text("Not Now")
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

    // Navigate when import succeeds
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportState.Success -> {
                viewModel.resetImportState()
                onNavigateToVideoEditor(state.projectId)
            }
            is ImportState.QuickSuccess -> {
                viewModel.resetImportState()
                onNavigateToProcessing(state.projectId)
            }
            else -> {}
        }
    }

    ScreenThemeProvider(accentColor = AccentBlue) {
        Scaffold(
            topBar = {
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
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_img),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
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
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }

                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (projects.isNotEmpty()) {
                // Speed dial FAB
                var speedDialExpanded by remember { mutableStateOf(false) }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // Mini FABs — visible when expanded
                androidx.compose.animation.AnimatedVisibility(
                    visible = speedDialExpanded,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Record Video
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    "Record Video",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            androidx.compose.material3.SmallFloatingActionButton(
                                onClick = {
                                    speedDialExpanded = false
                                    onNavigateToSmartRecorder()
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = "Record Video", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        // Quick Generate
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    "Quick Caption",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            androidx.compose.material3.SmallFloatingActionButton(
                                onClick = {
                                    speedDialExpanded = false
                                    quickPicker.launch("video/*")
                                },
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = "Quick Caption")
                            }
                        }
                        // Advanced Import
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    "Import & Edit",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            androidx.compose.material3.SmallFloatingActionButton(
                                onClick = {
                                    speedDialExpanded = false
                                    videoPicker.launch("video/*")
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = "Import & Edit", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }

                // Main FAB
                ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { speedDialExpanded = !speedDialExpanded },
                    icon = {
                        androidx.compose.animation.AnimatedContent(
                            targetState = speedDialExpanded,
                            label = "fabIcon"
                        ) { expanded ->
                            if (expanded) Icon(Icons.Default.Close, null)
                            else Icon(Icons.Default.Add, null)
                        }
                    },
                    text = { Text(if (speedDialExpanded) "Close" else "+ Create", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 4.dp
                    )
                )
                }
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
                val announcement by viewModel.announcementMessage.collectAsStateWithLifecycle()

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
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (projects.isEmpty()) {
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
                        onClick = { quickPicker.launch("video/*") },
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "Start 1-Tap Captions"
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("1-Tap Captions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Let AI do the heavy lifting instantly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    androidx.compose.material3.Card(
                        onClick = { videoPicker.launch("video/*") },
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "Start Advanced Studio"
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Advanced Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("Trim video and configure setup manually.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 80.dp // space for FAB
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
                                when (projectWithExports.project.status) {
                                    ProjectStatus.IMPORTED -> onNavigateToVideoEditor(projectWithExports.project.id)
                                    ProjectStatus.READY_FOR_PROCESSING,
                                    ProjectStatus.EXTRACTING_AUDIO,
                                    ProjectStatus.TRANSCRIBING -> onNavigateToProcessing(projectWithExports.project.id)
                                    else -> onNavigateToEditor(projectWithExports.project.id)
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
                        Text("Importing video...")
                    }
                }
            }

            // Error snackbar
            if (importState is ImportState.Error) {
                val message = (importState as ImportState.Error).message
                LaunchedEffect(importState) {
                    kotlinx.coroutines.delay(3000.milliseconds)
                    viewModel.resetImportState()
                }
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text("Import failed: $message")
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
        } // end of outer Box (padding)
    } // end of Scaffold content box
    } // end of ScreenThemeProvider
} // end of HomeScreen


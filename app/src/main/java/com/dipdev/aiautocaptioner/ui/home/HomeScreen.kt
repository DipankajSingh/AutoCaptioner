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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
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

    // Video picker launcher
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importVideo(it) }
    }

    // Navigate when import succeeds
    LaunchedEffect(importState) {
        if (importState is ImportState.Success) {
            val projectId = (importState as ImportState.Success).projectId
            viewModel.resetImportState()
            onNavigateToVideoEditor(projectId)
        }
    }

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
            if(projects.isNotEmpty()){
                ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { videoPicker.launch("video/*") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Import New Video", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 4.dp
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
                        modifier = Modifier.size(200.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "No projects yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Import a video to auto-generate captions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { videoPicker.launch("video/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import a Video", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
                            onNavigateToHistory = { onNavigateToHistory(projectWithExports.project.id) }
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
                            .background(Color(0xFF000000))
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
} // end of HomeScreen


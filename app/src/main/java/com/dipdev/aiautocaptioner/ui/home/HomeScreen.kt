package com.dipdev.aiautocaptioner.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToProcessing: (String) -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToModelManager: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {

    val projects by viewModel.projects.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val importState by viewModel.importState.collectAsState()

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
            onNavigateToProcessing(projectId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                title = {

                    Row {
                        Text(
                            color = MaterialTheme.colorScheme.primary,
                            text = "AutoCaptioner",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Show active model name as a chip
                    activeModel?.let { model ->
                        FilledTonalButton(
                            onClick = onNavigateToModelManager,
                            modifier = Modifier.padding(end = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = model.displayName.split("—").first().trim(),
                                fontSize = 13.sp
                            )
                        }
                    } ?: run {
                        FilledTonalButton(
                            onClick = onNavigateToModelManager,
                            modifier = Modifier.padding(end = 8.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Select Engine", fontSize = 13.sp)
                        }
                    }
                    
                    androidx.compose.material3.IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
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
            if (projects.isEmpty()) {
                EmptyState(
                    onImport = { videoPicker.launch("video/*") }
                )
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
                    items(
                        items = projects,
                        key = { it.id }
                    ) { project ->
                        ProjectCard(
                            project = project,
                            onClick = {
                                when (project.status) {
                                    ProjectStatus.IMPORTED,
                                    ProjectStatus.EXTRACTING_AUDIO,
                                    ProjectStatus.TRANSCRIBING -> onNavigateToProcessing(project.id)
                                    else -> onNavigateToEditor(project.id)
                                }
                            },
                            onDelete = { viewModel.deleteProject(project) },
                            onRename = { newTitle -> viewModel.renameProject(project.id, newTitle) }
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
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(0.6f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
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
        }
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.nothing))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("No Projects Yet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Import a video to add karaoke captions",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onImport,
            shape = RoundedCornerShape(4.dp), // Flattened shape
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp) // Removed elevation
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Video", maxLines = 1)
        }
    }
}

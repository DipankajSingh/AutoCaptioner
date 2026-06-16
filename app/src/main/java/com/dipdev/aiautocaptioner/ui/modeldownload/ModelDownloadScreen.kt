package com.dipdev.aiautocaptioner.ui.modeldownload

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dipdev.aiautocaptioner.data.repository.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    modelId: String,
    onDownloadComplete: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ModelDownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadState = uiState.downloadState
    val modelName = uiState.modelName

    LaunchedEffect(modelId) {
        viewModel.setEvent(ModelDownloadUiEvent.StartDownload(modelId))
    }

    // Navigate when complete
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Complete) {
            onDownloadComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                title = {},
                navigationIcon = {
                    // Only show back if provided and we're not mid-download
                    onNavigateBack?.let { back ->
                        if (downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Starting) {
                            IconButton(onClick = back) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = downloadState) {

                is DownloadState.Starting -> {
                    // Manual progress bar to avoid LinearProgressIndicator constraint crash
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Preparing download...", fontSize = 16.sp)
                }

                is DownloadState.Downloading -> {
                    DownloadingContent(
                        modelName = modelName,
                        state = state
                    )
                }

                is DownloadState.Complete -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Model Ready!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$modelName is ready to use.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                is DownloadState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.setEvent(ModelDownloadUiEvent.Retry(modelId)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadingContent(
    modelName: String,
    state: DownloadState.Downloading
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress / 100f,
        animationSpec = tween(300),
        label = "download_progress"
    )

    Text(
        text = "Downloading",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = modelName,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(40.dp))

    // Manual progress bar to avoid LinearProgressIndicator constraint crash
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatBytes(state.downloadedBytes),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = "${state.progress}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = formatBytes(state.totalBytes),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = "This is a one-time download.\nKeep the app open until complete.",
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        lineHeight = 20.sp
    )
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(64.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Download Failed",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = message,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onRetry,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("Retry Download", maxLines = 1)
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "${"%.1f".format(bytes / 1_000_000_000f)} GB"
        bytes >= 1_000_000     -> "${"%.0f".format(bytes / 1_000_000f)} MB"
        bytes >= 1_000         -> "${"%.0f".format(bytes / 1_000f)} KB"
        else                   -> "$bytes B"
    }
}
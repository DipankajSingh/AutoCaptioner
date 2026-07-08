package com.dipdev.aiautocaptioner.ui.modelmanager

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Download
import compose.icons.feathericons.Star
import compose.icons.feathericons.Trash2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.ui.components.SimpleAppScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val models = uiState.availableModels
    val activeModel = uiState.activeModel
    val downloadStates = uiState.downloadStates

    SimpleAppScaffold(
        title = "AI Models",
        onNavigateBack = onNavigateBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(models, key = { it.id }) { model ->
                val isActive = model.id == activeModel?.id
                val dlState = downloadStates[model.id]
                
                ModelCard(
                    model = model,
                    isActive = isActive,
                    downloadState = dlState,
                    onDownload = { viewModel.setEvent(ModelManagerUiEvent.StartDownload(model.id)) },
                    onSetActive = { viewModel.setEvent(ModelManagerUiEvent.SetActiveModel(model)) },
                    onDelete = { viewModel.setEvent(ModelManagerUiEvent.DeleteModel(model.id)) }
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: WhisperModel,
    isActive: Boolean,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                      else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "color_anim"
    )

    com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = model.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${model.sizeMb} MB • Req RAM: ${model.minRamMb} MB",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(FeatherIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Active", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // Description
            Text(
                text = model.description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricItem(label = "Accuracy", value = model.accuracy)
                MetricItem(label = "Speed", value = model.speed)
                
                if (model.isMultilingual) {
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text("Multi-language", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action / State Layout
            when {
                isActive -> {
                    // Nothing to show if active (Header already has Active tag)
                }
                model.isDownloaded -> {
                    // Option to Activate or Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        
                        if (showDeleteConfirm) {
                            Text("Delete from storage?", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            TextButton(
                                onClick = {
                                    showDeleteConfirm = false
                                    onDelete()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Confirm") }
                        } else {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(FeatherIcons.Trash2, contentDescription = "Delete model", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(onClick = onSetActive) {
                                Text("Set Active", maxLines = 1)
                            }
                        }
                    }
                }
                downloadState is DownloadState.Downloading || downloadState is DownloadState.Starting -> {
                    // Native Inline download progress
                    val isStarting = downloadState is DownloadState.Starting
                    val progress = if (downloadState is DownloadState.Downloading) downloadState.progress / 100f else 0f
                    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(300), label = "dl_prog")
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = if (isStarting) "Preparing..." else "Downloading...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!isStarting && downloadState is DownloadState.Downloading) {
                                Text(
                                    text = "${downloadState.progress}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            if (isStarting) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
                downloadState is DownloadState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Download Failed", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(downloadState.message, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        Button(onClick = onDownload) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    // Not downloaded natively
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = onDownload) {
                            Icon(FeatherIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: Int) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Row {
            for (i in 1..5) {
                Icon(
                    imageVector = FeatherIcons.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (i <= value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            }
        }
    }
}

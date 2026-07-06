package com.dipdev.aiautocaptioner.ui.exporthistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.data.db.entity.ExportedFileEntity
import com.dipdev.aiautocaptioner.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.dipdev.aiautocaptioner.ui.components.SimpleAppScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportHistoryScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: ExportHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exports = uiState.exports

    LaunchedEffect(projectId) {
        viewModel.loadExports(projectId)
    }

    SimpleAppScaffold(
        title = "Export History",
        onNavigateBack = onNavigateBack
    ) {
        if (exports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                EmptyState(
                    title = "No exports yet.",
                    subtitle = "Your exported videos will appear here",
                    buttonText = "Go Back",
                    onAction = onNavigateBack
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(exports, key = { it.id }) { export ->
                    ExportHistoryItem(
                        export = export,
                        onSaveVideo = { viewModel.saveVideoToGallery(export) },
                        onShareVideo = { viewModel.shareVideo(export) },
                        onShareSrt = { viewModel.shareSrt(export) },
                        onSaveSrt = { viewModel.saveSrt(export) },
                        onDelete = { viewModel.deleteExport(export) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExportHistoryItem(
    export: ExportedFileEntity,
    onSaveVideo: () -> Unit,
    onShareVideo: () -> Unit,
    onShareSrt: () -> Unit,
    onSaveSrt: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Exported Video",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(export.exportedAt)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (export.quality != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Quality: ${export.quality}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Save Video to Gallery") },
                        onClick = { menuExpanded = false; onSaveVideo() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share Video") },
                        onClick = { menuExpanded = false; onShareVideo() }
                    )
                    DropdownMenuItem(
                        text = { Text("Save SRT") },
                        onClick = { menuExpanded = false; onSaveSrt() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share SRT") },
                        onClick = { menuExpanded = false; onShareSrt() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

package com.dipdev.aiautocaptioner.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.CreationMode
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.db.entity.ProjectWithExportedFiles
import java.io.File

@Composable
internal fun HomeProjectList(
    projects: List<ProjectWithExportedFiles>,
    onNavigateToVideoEditor: (String) -> Unit,
    onNavigateToProcessing: (String) -> Unit,
    onNavigateToCaptionEditor: (String) -> Unit,
    onNavigateToHistory: (String) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onPlayVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 100.dp // space for speed dial FAB
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
                    text = stringResource(R.string.home_my_projects),
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
                    if (project.creationMode == CreationMode.QUICK_CAPTION) {
                        if (project.status == ProjectStatus.TRANSCRIBED || project.status == ProjectStatus.EXPORTED) {
                            onNavigateToCaptionEditor(project.id)
                        } else {
                            onNavigateToProcessing(project.id)
                        }
                    } else {
                        onNavigateToVideoEditor(project.id)
                    }
                },
                onDelete = { onDeleteProject(projectWithExports.project) },
                onRename = { newTitle -> onRenameProject(projectWithExports.project.id, newTitle) },
                onDuplicate = { onDuplicateProject(projectWithExports.project.id) },
                onPlayVideo = { path -> onPlayVideo(path) },
                onShareVideo = { path -> shareVideoFile(context, path) },
                onNavigateToHistory = { onNavigateToHistory(projectWithExports.project.id) },
                onRetranscribe = { onNavigateToProcessing(projectWithExports.project.id) }
            )
        }
    }
}

private fun shareVideoFile(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
    } catch (_: Exception) {
        // Handle bad path or activity not found gracefully
    }
}

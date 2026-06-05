package com.dipdev.aiautocaptioner.ui.captioneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionEditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToStyleEditor: () -> Unit,
    onNavigateToProcessing: (String) -> Unit,
    viewModel: CaptionEditorViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val wordsMap by viewModel.wordsMap.collectAsStateWithLifecycle()
    val expandedSegmentId by viewModel.expandedSegmentId.collectAsStateWithLifecycle()
    val retranscribeRequested by viewModel.retranscribeRequested.collectAsStateWithLifecycle()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    LaunchedEffect(retranscribeRequested) {
        if (retranscribeRequested) {
            viewModel.retranscribeHandled()
            onNavigateToProcessing(projectId)
        }
    }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.srtContentToShare.collect { content ->
            val srtFile = java.io.File(context.cacheDir, "captions_$projectId.srt")
            srtFile.writeText(content)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", srtFile)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/x-subrip"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share SRT"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = project?.title ?: "Caption Editor",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.retranscribe(projectId) }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retranscribe",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToStyleEditor,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Style Presets", maxLines = 1)
                }
                Button(
                    onClick = { viewModel.shareSrt(projectId) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Export SRT", maxLines = 1)
                }
            }
        }
    ) { padding ->
        if (segments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(0.6f).height(10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "${segments.size} segments · tap to edit",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(segments, key = { it.id }) { segment ->
                    val words = wordsMap[segment.id] ?: emptyList()
                    val isExpanded = expandedSegmentId == segment.id

                    SegmentCard(
                        segment = segment,
                        words = words,
                        isExpanded = isExpanded,
                        onToggleExpand = { viewModel.toggleSegmentExpanded(segment.id) },
                        onSaveText = { newText ->
                            viewModel.saveSegmentText(segment, newText)
                        },
                        onWordLongPress = { word ->
                            viewModel.toggleWordEmphasis(word)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentCard(
    segment: CaptionSegmentEntity,
    words: List<CaptionWordEntity>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSaveText: (String) -> Unit,          // called only on focus-lost, not every keystroke
    onWordLongPress: (CaptionWordEntity) -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header row — timestamp + expand toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatMs(segment.startTimeMs)} → ${formatMs(segment.endTimeMs)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isExpanded) {
                // We use remember with segment.id AND segment.text to initialize it correctly if DB changes underneath us
                var text by remember(segment.id, segment.text) { mutableStateOf(segment.text) }

                BasicTextField(
                    value = text,
                    onValueChange = { text = it },   // local only — fast, no DB
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)) // Flattened shape
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp)
                        .onFocusChanged { state ->
                            // Commit to DB only when the field loses focus
                            if (!state.isFocused) onSaveText(text)
                        },
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Word chips with emphasis toggle
                if (words.isNotEmpty()) {
                    Text(
                        text = "Long press a word to mark emphasis:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    WordChips(
                        words = words,
                        onWordLongPress = onWordLongPress
                    )
                }
            } else {
                // Collapsed — just show text preview
                Text(
                    text = segment.text,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

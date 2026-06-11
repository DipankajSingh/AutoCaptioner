package com.dipdev.aiautocaptioner.ui.captioneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard
import com.dipdev.aiautocaptioner.ui.components.RoundedProgressBar
import kotlinx.coroutines.launch

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
    val filteredSegments by viewModel.filteredSegments.collectAsStateWithLifecycle()
    val wordsMap by viewModel.wordsMap.collectAsStateWithLifecycle()
    val expandedSegmentId by viewModel.expandedSegmentId.collectAsStateWithLifecycle()
    val retranscribeRequested by viewModel.retranscribeRequested.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpMinutes by remember { mutableStateOf("") }
    var jumpSeconds by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

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
                    TextButton(onClick = { showJumpDialog = true }) {
                        Text("Jump", color = MaterialTheme.colorScheme.primary)
                    }
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
                AppOutlinedButton(
                    onClick = onNavigateToStyleEditor,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Style Editor", maxLines = 1)
                }
                AppPrimaryButton(
                    onClick = { viewModel.shareSrt(projectId) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export SRT", maxLines = 1)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            com.dipdev.aiautocaptioner.ui.components.PipelineProgressBar(
                currentStage = com.dipdev.aiautocaptioner.ui.components.PipelineStage.REVIEW,
                onNavigateToStage = { stage ->
                    when (stage) {
                        com.dipdev.aiautocaptioner.ui.components.PipelineStage.STYLE -> onNavigateToStyleEditor()
                        else -> {}
                    }
                }
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search segments...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            if (segments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    RoundedProgressBar(modifier = Modifier.fillMaxWidth(0.6f))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "${filteredSegments.size} segments · tap to edit",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(filteredSegments, key = { it.id }) { segment ->
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

    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Time") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = jumpMinutes,
                        onValueChange = { if (it.length <= 3 && it.all { char -> char.isDigit() }) jumpMinutes = it },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true
                    )
                    Text(":")
                    OutlinedTextField(
                        value = jumpSeconds,
                        onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) jumpSeconds = it },
                        label = { Text("Sec") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val m = jumpMinutes.toLongOrNull() ?: 0L
                        val s = jumpSeconds.toLongOrNull() ?: 0L
                        val targetMs = (m * 60 + s) * 1000

                        val targetIndex = filteredSegments.indexOfFirst { it.startTimeMs >= targetMs }.let {
                            if (it == -1) filteredSegments.size - 1 else it
                        }
                        
                        if (targetIndex >= 0) {
                            coroutineScope.launch {
                                // +1 because the first item in LazyColumn is the header text
                                listState.animateScrollToItem(targetIndex + 1)
                            }
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("Jump")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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

package com.dipdev.autocaptioner.ui.captioneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dipdev.autocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.autocaptioner.data.db.entity.CaptionWordEntity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionEditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToStyleEditor: () -> Unit,
    viewModel: CaptionEditorViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsState()
    val segments by viewModel.segments.collectAsState()
    val wordsMap by viewModel.wordsMap.collectAsState()
    val expandedSegmentId by viewModel.expandedSegmentId.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
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
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Style")
                }
            }
        }
    ) { padding ->
        if (segments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                        onTextChange = { newText ->
                            viewModel.updateSegmentText(segment, newText)
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
    onTextChange: (String) -> Unit,
    onWordLongPress: (CaptionWordEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
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
                    imageVector = if (isExpanded)
                        Icons.Default.KeyboardArrowUp
                    else
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isExpanded) {
                // Editable text field
                var text by remember(segment.id) { mutableStateOf(segment.text) }

                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onTextChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
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
                // Collapsed — just show text
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

@Composable
private fun WordChips(
    words: List<CaptionWordEntity>,
    onWordLongPress: (CaptionWordEntity) -> Unit
) {
    // Flow layout using wrapping rows
    val rows = mutableListOf<MutableList<CaptionWordEntity>>()
    var currentRow = mutableListOf<CaptionWordEntity>()
    words.forEach { word ->
        currentRow.add(word)
        if (currentRow.size >= 5) {
            rows.add(currentRow)
            currentRow = mutableListOf()
        }
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { word ->
                    WordChip(word = word, onLongPress = { onWordLongPress(word) })
                }
            }
        }
    }
}

@Composable
private fun WordChip(
    word: CaptionWordEntity,
    onLongPress: () -> Unit
) {
    val bgColor = if (word.isEmphasized)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (word.isEmphasized)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = Modifier.combinedClickable(
            onLongClick = onLongPress,
            onClick = {}
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = word.word,
                fontSize = 13.sp,
                color = textColor,
                fontWeight = if (word.isEmphasized) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "${formatMs(word.startTimeMs)}",
                fontSize = 9.sp,
                color = textColor.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    val millis = (ms % 1000) / 10
    return "%d:%02d.%02d".format(minutes, secs, millis)
}
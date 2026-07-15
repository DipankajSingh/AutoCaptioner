package com.dipdev.aiautocaptioner.ui.captioneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronUp
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Search
import compose.icons.feathericons.X
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionEditorScreen(
    projectId: String,
    fromEditor: Boolean = false,
    sharedPlayerViewModel: com.dipdev.aiautocaptioner.ui.videoeditor.core.player.SharedPlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProcessing: (String) -> Unit,
    onNavigateToExport: (String) -> Unit,
    viewModel: CaptionEditorViewModel = hiltViewModel(),
    processingViewModel: com.dipdev.aiautocaptioner.ui.processing.ProcessingViewModel = hiltViewModel()
) {
    val processingUiState by processingViewModel.uiState.collectAsStateWithLifecycle()
    val processingStep = processingUiState.step
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val project = uiState.project
    val segments = uiState.segments
    val filteredSegments = uiState.filteredSegments
    val wordsMap = uiState.wordsMap
    val expandedSegmentId = uiState.expandedSegmentId
    val retranscribeRequested = uiState.retranscribeRequested
    val searchQuery = uiState.searchQuery

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpMinutes by remember { mutableStateOf("") }
    var jumpSeconds by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(projectId) {
        viewModel.setEvent(CaptionEditorUiEvent.LoadProject(projectId))
    }

    var showTranscriptionBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(retranscribeRequested) {
        if (retranscribeRequested) {
            viewModel.setEvent(CaptionEditorUiEvent.RetranscribeHandled)
            showTranscriptionBottomSheet = true
        }
    }
    
    LaunchedEffect(processingStep) {
        if (processingStep is com.dipdev.aiautocaptioner.ui.processing.ProcessingStep.Done) {
            processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.ResetToIdle)
        }
    }

    val context = LocalContext.current

    val videoPath = project?.workingVideoPath
    val player by sharedPlayerViewModel.player.collectAsStateWithLifecycle()
    
    LaunchedEffect(videoPath) {
        if (!videoPath.isNullOrEmpty()) {
            sharedPlayerViewModel.initPlayer(videoPath)
        }
    }

    var currentPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        val currentPlayer = player ?: return@LaunchedEffect
        while (true) {
            currentPositionMs = currentPlayer.currentPosition
            kotlinx.coroutines.delay(if (currentPlayer.isPlaying) 100L else 500L)
        }
    }

    val activeSegmentIndex by remember(filteredSegments) {
        androidx.compose.runtime.derivedStateOf {
            filteredSegments.indexOfFirst { segment ->
                currentPositionMs in segment.startTimeMs..segment.endTimeMs
            }
        }
    }

    val activeSegment = if (activeSegmentIndex >= 0) filteredSegments[activeSegmentIndex] else null

    LaunchedEffect(activeSegmentIndex) {
        if (activeSegmentIndex >= 0 && player?.isPlaying == true) {
            coroutineScope.launch {
                listState.animateScrollToItem(activeSegmentIndex + 1)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is CaptionEditorUiEffect.ShareSrt -> {
                    val srtFile = java.io.File(context.cacheDir, "captions_$projectId.srt")
                    srtFile.writeText(effect.content)
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", srtFile)
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/x-subrip"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share SRT"))
                }
            }
        }
    }

    CompositionLocalProvider(LocalAccentColor provides AccentAmber) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = project?.title ?: "Caption Editor",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showJumpDialog = true }) {
                        Text(stringResource(R.string.caption_jump), color = AccentAmber)
                    }
                    IconButton(
                        onClick = { viewModel.setEvent(CaptionEditorUiEvent.Retranscribe(projectId)) }
                    ) {
                        Icon(
                            FeatherIcons.RefreshCw,
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
                if (!fromEditor) {
                    AppPrimaryButton(
                        onClick = { onNavigateToExport(projectId) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmber,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Export Video", maxLines = 1)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (player != null) {
                com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard(
                    player = player,
                    showControls = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setEvent(CaptionEditorUiEvent.UpdateSearchQuery(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.caption_search_segments)) },
                leadingIcon = {
                    Icon(FeatherIcons.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setEvent(CaptionEditorUiEvent.UpdateSearchQuery("")) }) {
                            Icon(FeatherIcons.X, contentDescription = "Clear search")
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
                        val isActive = segment.id == activeSegment?.id

                        SegmentCard(
                            segment = segment,
                            words = words,
                            isExpanded = isExpanded,
                            isActive = isActive,
                            onToggleExpand = { viewModel.setEvent(CaptionEditorUiEvent.ToggleSegmentExpanded(segment.id)) },
                            onSaveText = { newText ->
                                viewModel.setEvent(CaptionEditorUiEvent.SaveSegmentText(segment, newText))
                            },
                            onWordLongPress = { word ->
                                viewModel.setEvent(CaptionEditorUiEvent.ToggleWordEmphasis(word))
                            }
                        )
                    }
                }
            }
        }
    }
    } // end CompositionLocalProvider

    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text(stringResource(R.string.caption_jump_time)) },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = jumpMinutes,
                        onValueChange = { if (it.length <= 3 && it.all { char -> char.isDigit() }) jumpMinutes = it },
                        label = { Text(stringResource(R.string.caption_min)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true
                    )
                    Text(":")
                    OutlinedTextField(
                        value = jumpSeconds,
                        onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) jumpSeconds = it },
                        label = { Text(stringResource(R.string.caption_sec)) },
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
                    Text(stringResource(R.string.caption_jump))
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text(stringResource(R.string.caption_cancel))
                }
            }
        )
    }

    if (showTranscriptionBottomSheet) {
        LaunchedEffect(Unit) {
            processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.PrepareForProject(projectId, forceModelPicker = true))
        }
        
        com.dipdev.aiautocaptioner.ui.processing.components.TranscriptionBottomSheet(
            onDismiss = { showTranscriptionBottomSheet = false },
            availableModels = processingUiState.availableModels,
            initialModelId = processingUiState.activeModel?.id,
            initialLanguage = processingUiState.selectedLanguage,
            initialTranslate = processingUiState.translateToEnglish,
            onStart = { modelId, lang, translate ->
                showTranscriptionBottomSheet = false
                processingViewModel.setEvent(
                    com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.StartTranscriptionExplicit(
                        projectId = projectId,
                        modelId = modelId,
                        language = lang,
                        translateToEnglish = translate
                    )
                )
            }
        )
    }

    com.dipdev.aiautocaptioner.ui.processing.components.TranscriptionOverlay(
        step = processingStep,
        onCancel = { processingViewModel.setEvent(com.dipdev.aiautocaptioner.ui.processing.ProcessingUiEvent.Cancel) }
    )
}

@Composable
private fun SegmentCard(
    segment: CaptionSegmentEntity,
    words: List<CaptionWordEntity>,
    isExpanded: Boolean,
    isActive: Boolean = false,
    onToggleExpand: () -> Unit,
    onSaveText: (String) -> Unit,          // called only on focus-lost, not every keystroke
    onWordLongPress: (CaptionWordEntity) -> Unit
) {
    val cardColor = if (isActive) {
        AccentAmber.copy(alpha = 0.12f)
    } else {
        androidx.compose.ui.graphics.Color.Unspecified
    }

    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = cardColor
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Blue left-border accent strip for the active / playing segment
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            AccentAmber,
                            RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                        )
                )
            }
        Column(modifier = Modifier.weight(1f).padding(12.dp)) {

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
                    color = AccentAmber,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (isExpanded) FeatherIcons.ChevronUp
                                  else FeatherIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isExpanded) {
                // Prioritize local text state while expanded to prevent typing wipeout from delayed DB updates
                var text by remember(segment.id, segment.text) { mutableStateOf(segment.text) }
                val currentText by androidx.compose.runtime.rememberUpdatedState(text)
                val currentSegmentText by androidx.compose.runtime.rememberUpdatedState(segment.text)
                val currentOnSaveText by androidx.compose.runtime.rememberUpdatedState(onSaveText)

                LaunchedEffect(text) {
                    kotlinx.coroutines.delay(500)
                    if (text != segment.text) {
                        onSaveText(text)
                    }
                }
                
                androidx.compose.runtime.DisposableEffect(segment.id) {
                    onDispose {
                        if (currentText != currentSegmentText) {
                            currentOnSaveText(currentText)
                        }
                    }
                }

                BasicTextField(
                    value = text,
                    onValueChange = { text = it },   // local only — fast, no DB
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)) // Flattened shape
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
                        text = stringResource(R.string.caption_word_emphasis_hint),
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
        } // end Row
    }
}

package com.dipdev.aiautocaptioner.ui.processing


import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.X
import compose.icons.feathericons.Edit2
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton
import com.dipdev.aiautocaptioner.ui.processing.components.CancelProcessDialog
import com.dipdev.aiautocaptioner.ui.processing.components.CancelledView
import com.dipdev.aiautocaptioner.ui.processing.components.CancellingView
import com.dipdev.aiautocaptioner.ui.processing.components.ErrorView
import com.dipdev.aiautocaptioner.ui.processing.components.ModelPickerCard
import com.dipdev.aiautocaptioner.ui.processing.components.SafetyCheckDialogs
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.dipdev.aiautocaptioner.R
@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    projectId: String,
    forceModelPicker: Boolean = false,
    isRegenerating: Boolean = false,
    onNavigateToCaptionEditor: () -> Unit,
    onNavigateToVideoEditor: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val step = uiState.step
    val streamedSegments = uiState.streamedSegments
    val safetyCheck = uiState.safetyCheck

    LaunchedEffect(projectId, forceModelPicker, isRegenerating) {
        viewModel.setEvent(ProcessingUiEvent.PrepareForProject(projectId, forceModelPicker, isRegenerating))
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is ProcessingUiEffect.NavigateToVideoEditor -> onNavigateToVideoEditor()
                is ProcessingUiEffect.NavigateToCaptionEditor -> onNavigateToCaptionEditor()
            }
        }
    }

    val isProcessing = step is ProcessingStep.DownloadingModel ||
                       step is ProcessingStep.ExtractingAudio ||
                       step is ProcessingStep.LoadingModel ||
                       step is ProcessingStep.Transcribing ||
                       step is ProcessingStep.Saving

    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isProcessing) {
        showCancelDialog = true
    }

    var pendingModelIdToDownload by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        pendingModelIdToDownload?.let { 
            viewModel.setEvent(ProcessingUiEvent.DownloadAndProcess(it, projectId))
            pendingModelIdToDownload = null
        }
    }

    if (showCancelDialog) {
        CancelProcessDialog(
            onDismiss = { showCancelDialog = false },
            onConfirm = {
                showCancelDialog = false
                viewModel.setEvent(ProcessingUiEvent.Cancel)
                onCancel()
            }
        )
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. Immersive Background ---
        val videoUri = uiState.workingVideoPath
        if (videoUri != null) {
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(videoUri)
                    .decoderFactory(coil3.video.VideoFrameDecoder.Factory())
                    .build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(25.dp)
            )
            // Darken overlay for better text readability
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
            )
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        // --- 2. Main Content Stack ---
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (isProcessing) showCancelDialog = true else onCancel() },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(imageVector = FeatherIcons.X, contentDescription = stringResource(R.string.cd_go_back), tint = Color.White)
                }
                
                if (!isProcessing) {
                    IconButton(
                        onClick = onNavigateToVideoEditor,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(imageVector = FeatherIcons.Edit2, contentDescription = stringResource(R.string.cd_edit_video), tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // --- 3. Dynamic Center Content ---
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.95f))
                        .togetherWith(fadeOut(tween(300)))
                },
                contentKey = { it::class.simpleName },
                label = "processing_step",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            ) { currentStep ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (currentStep) {
                        is ProcessingStep.SetupAI -> {
                            var selectedModelId by remember { mutableStateOf(currentStep.recommendedModelId) }
                            val context = LocalContext.current

                            LaunchedEffect(Unit) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                    if (hasPerm != PackageManager.PERMISSION_GRANTED) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                            
                            Text(
                                text = stringResource(com.dipdev.aiautocaptioner.R.string.model_picker_title),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = stringResource(com.dipdev.aiautocaptioner.R.string.model_picker_subtitle),
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Text(
                                text = stringResource(com.dipdev.aiautocaptioner.R.string.setupai_language_label),
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            val quickLanguages = listOf("auto", "en", "es", "fr", "de")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = if (currentStep.autoDetectMode) 16.dp else 32.dp)
                            ) {
                                items(quickLanguages) { lang ->
                                    val isSelected = lang == uiState.selectedLanguage
                                    val label = if (lang == "auto") stringResource(R.string.lang_auto_detect) else lang.uppercase()
                                    Surface(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = CircleShape,
                                        modifier = Modifier.clickable { viewModel.setEvent(ProcessingUiEvent.SelectLanguage(lang)) }
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            var promptText by remember(uiState.initialPrompt) { mutableStateOf(uiState.initialPrompt) }

                            Text(
                                text = stringResource(R.string.setupai_prompt_label),
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = promptText,
                                onValueChange = {
                                    promptText = it
                                    viewModel.setEvent(ProcessingUiEvent.SetInitialPrompt(it))
                                },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.setupai_prompt_placeholder),
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 14.sp
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (currentStep.autoDetectMode) 16.dp else 24.dp)
                            )

                            if (currentStep.autoDetectMode) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 24.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = stringResource(com.dipdev.aiautocaptioner.R.string.auto_detect_info_title),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = stringResource(com.dipdev.aiautocaptioner.R.string.auto_detect_info_body),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                lineHeight = 17.sp
                                            )
                                        }
                                    }
                                }
                            }

                            if (currentStep.autoDetectMode) {
                                val multilingualModels = currentStep.models.filter { it.isMultilingual }
                                val englishModels = currentStep.models.filter { !it.isMultilingual }

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.heightIn(max = 340.dp)
                                ) {
                                    if (multilingualModels.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = stringResource(com.dipdev.aiautocaptioner.R.string.auto_detect_tier_any_language),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                        items(multilingualModels) { model ->
                                            ModelPickerCard(
                                                model = model,
                                                isRecommended = model.id == currentStep.recommendedModelId,
                                                isSelected = model.id == selectedModelId,
                                                onClick = { selectedModelId = model.id },
                                                autoDetectMode = true,
                                                isMultilingual = true
                                            )
                                        }
                                    }
                                    if (englishModels.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(com.dipdev.aiautocaptioner.R.string.auto_detect_tier_english),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                        items(englishModels) { model ->
                                            ModelPickerCard(
                                                model = model,
                                                isRecommended = model.id == currentStep.recommendedModelId,
                                                isSelected = model.id == selectedModelId,
                                                onClick = { selectedModelId = model.id },
                                                autoDetectMode = true,
                                                isMultilingual = false
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.heightIn(max = 300.dp)
                                ) {
                                    items(currentStep.models) { model ->
                                        ModelPickerCard(
                                            model = model,
                                            isRecommended = model.id == currentStep.recommendedModelId,
                                            isSelected = model.id == selectedModelId,
                                            onClick = { selectedModelId = model.id }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            val selectedModel = currentStep.models.find { it.id == selectedModelId }
                            val isDownloaded = selectedModel?.isDownloaded == true

                            GradientPrimaryButton(
                                text = stringResource(
                                    if (isDownloaded) com.dipdev.aiautocaptioner.R.string.model_picker_generate_button
                                    else com.dipdev.aiautocaptioner.R.string.model_picker_download_button
                                ),
                                onClick = { 
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                        if (hasPerm == PackageManager.PERMISSION_GRANTED) {
                                            selectedModelId?.let { viewModel.setEvent(ProcessingUiEvent.DownloadAndProcess(it, projectId)) }
                                        } else {
                                            selectedModelId?.let { 
                                                pendingModelIdToDownload = it
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                    } else {
                                        selectedModelId?.let { viewModel.setEvent(ProcessingUiEvent.DownloadAndProcess(it, projectId)) }
                                    }
                                },
                                enabled = selectedModelId != null,
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            )
                            
                            Text(
                                text = stringResource(com.dipdev.aiautocaptioner.R.string.model_picker_battery_note),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                            )
                        }

                        is ProcessingStep.Cancelled -> {
                            CancelledView(
                                onRetry = { viewModel.setEvent(ProcessingUiEvent.StartProcessing(projectId)) },
                                onGoBack = onCancel
                            )
                        }
                        is ProcessingStep.Error -> {
                            ErrorView(
                                message = currentStep.message,
                                onRetry = { viewModel.setEvent(ProcessingUiEvent.StartProcessing(projectId)) },
                                onGoBack = onCancel
                            )
                        }
                        is ProcessingStep.DownloadingModel -> {
                            com.dipdev.aiautocaptioner.ui.processing.components.DownloadingStateView(step = currentStep)
                        }
                        else -> {
                            com.dipdev.aiautocaptioner.ui.processing.components.TranscriptionProgressView(
                                step = currentStep,
                                detectedLanguage = uiState.detectedLanguage,
                                streamedSegments = uiState.streamedSegments,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(if (isProcessing) 0.3f else 1.5f))
        }
    }

    SafetyCheckDialogs(
        safetyCheck = safetyCheck,
        onDismiss = { viewModel.setEvent(ProcessingUiEvent.ResetSafetyCheck) },
        onProceed = { modelId -> viewModel.setEvent(ProcessingUiEvent.ConfirmCellularDownload(modelId)) }
    )
}

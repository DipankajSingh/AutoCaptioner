package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation
import com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton
import com.dipdev.aiautocaptioner.ui.processing.ProcessingStep

private val languageDisplayNames = mapOf(
    "auto" to "Auto",
    "en" to "English",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "zh" to "Chinese",
    "ja" to "Japanese",
    "ko" to "Korean",
    "it" to "Italian",
    "nl" to "Dutch",
    "pt" to "Portuguese",
    "ru" to "Russian",
    "ar" to "Arabic"
)

private val quickLanguages = listOf("auto", "en", "es", "fr", "de")
private val allLanguages = listOf("auto", "en", "es", "fr", "de", "zh", "ja", "ko", "it", "nl", "pt", "ru", "ar")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionBottomSheet(
    onDismiss: () -> Unit,
    availableModels: List<WhisperModel>,
    initialModelId: String?,
    initialLanguage: String,
    initialTranslate: Boolean,
    initialPrompt: String = "",
    onStart: (modelId: String, language: String, translate: Boolean, prompt: String) -> Unit
) {
    var selectedModelId by remember { mutableStateOf(initialModelId ?: availableModels.firstOrNull()?.id ?: "") }
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var translateToEnglish by remember { mutableStateOf(initialTranslate) }
    var prompt by remember { mutableStateOf(initialPrompt) }
    var showAllLanguages by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(initialModelId) {
        if (initialModelId != null && initialModelId != selectedModelId) {
            selectedModelId = initialModelId
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.model_sheet_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Language Selection
            Text(
                text = stringResource(R.string.model_sheet_language_label),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val languagesToShow = if (showAllLanguages) allLanguages else quickLanguages
                items(languagesToShow) { lang ->
                    val isSelected = lang == selectedLanguage
                    val label = languageDisplayNames[lang] ?: lang.uppercase()

                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.clickable {
                            selectedLanguage = lang
                            if (lang == "en") translateToEnglish = false
                        }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
                if (!showAllLanguages) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.clickable { showAllLanguages = true }
                        ) {
                            Text(
                                text = stringResource(R.string.lang_more),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quality Selection
            Text(
                text = stringResource(R.string.model_sheet_quality_label),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val selectedModel = availableModels.find { it.id == selectedModelId }
            ExposedDropdownMenuBox(
                expanded = showModelDropdown,
                onExpandedChange = { showModelDropdown = it }
            ) {
                OutlinedTextField(
                    value = selectedModel?.description ?: stringResource(R.string.model_sheet_select_model),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModelDropdown) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = showModelDropdown,
                    onDismissRequest = { showModelDropdown = false }
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(model.description, fontWeight = FontWeight.Bold)
                                    val size = "${model.sizeMb} MB"
                                    val downloaded = if (model.isDownloaded) stringResource(R.string.model_sheet_downloaded) else stringResource(R.string.model_sheet_tap_to_download)
                                    Text("$size • $downloaded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingIcon = {
                                if (model.id == selectedModelId) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                selectedModelId = model.id
                                showModelDropdown = false
                            }
                        )
                    }
                }
            }

            if (selectedLanguage == "auto") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.model_sheet_auto_detect_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Translate Toggle
            if (selectedLanguage != "en") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.model_sheet_translate_label),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = translateToEnglish,
                        onCheckedChange = { translateToEnglish = it }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Special Words Hint
            Text(
                text = stringResource(R.string.sheet_prompt_label),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = { Text(stringResource(R.string.sheet_prompt_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = false,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))

            val isDownloaded = selectedModel?.isDownloaded == true
            val buttonText = if (isDownloaded) stringResource(R.string.sheet_start_button) else stringResource(R.string.sheet_download_button)

            GradientPrimaryButton(
                text = buttonText,
                onClick = {
                    if (selectedModelId.isNotEmpty()) {
                        onStart(selectedModelId, selectedLanguage, translateToEnglish, prompt)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .height(56.dp),
                enabled = selectedModelId.isNotEmpty()
            )
        }
    }
}

@Composable
fun TranscriptionOverlay(
    step: ProcessingStep,
    detectedLanguage: String? = null,
    streamedSegments: List<com.dipdev.aiautocaptioner.ui.processing.StreamedSegment> = emptyList(),
    onCancel: () -> Unit = {}
) {
    if (step is ProcessingStep.Idle || step is ProcessingStep.Ready || step is ProcessingStep.SetupAI || 
        step is ProcessingStep.Done || step is ProcessingStep.Cancelling || step is ProcessingStep.Cancelled) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}
    ) {
        TranscriptionProgressView(
            step = step,
            detectedLanguage = detectedLanguage,
            streamedSegments = streamedSegments,
            modifier = Modifier.align(Alignment.Center)
        )
        
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            Text(
                text = stringResource(R.string.processing_cancel),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun TranscriptionProgressView(
    step: ProcessingStep,
    detectedLanguage: String? = null,
    streamedSegments: List<com.dipdev.aiautocaptioner.ui.processing.StreamedSegment> = emptyList(),
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.95f))
                .togetherWith(fadeOut(tween(300)))
        },
        contentKey = { it::class.simpleName },
        label = "overlay_step",
        modifier = modifier
    ) { currentStep ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            when (currentStep) {
                is ProcessingStep.DownloadingModel -> {
                    AiProcessingAnimation(progress = currentStep.progress / 100f, modifier = Modifier.size(120.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.processing_downloading_model), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("${currentStep.progress}%", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                }
                is ProcessingStep.ExtractingAudio -> {
                    AiProcessingAnimation(progress = 0f, modifier = Modifier.size(120.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.processing_preparing_video), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.processing_tip_extracting), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                }
                is ProcessingStep.LoadingModel -> {
                    AiProcessingAnimation(progress = 0.1f, modifier = Modifier.size(120.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.processing_warming_up), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.processing_tip_loading), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                }
                is ProcessingStep.Transcribing -> {
                    val rawProgress = currentStep.progress
                    val animatedProgress by animateFloatAsState(
                        targetValue = rawProgress + 0.05f,
                        animationSpec = tween(durationMillis = 30000, easing = LinearOutSlowInEasing),
                        label = "overlay_progress"
                    )
                    AiProcessingAnimation(progress = animatedProgress, modifier = Modifier.size(120.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.processing_listening), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (detectedLanguage != null) {
                        Text(
                            text = stringResource(R.string.lang_detected_format, detectedLanguage),
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (currentStep.estimatedSecondsRemaining != null) {
                        val secs = currentStep.estimatedSecondsRemaining
                        val timeText = if (secs >= 60) stringResource(R.string.time_remaining_minutes, secs / 60) else stringResource(R.string.time_remaining_seconds, secs)
                        Text(timeText, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                    }
                    if (streamedSegments.isEmpty() && currentStep.estimatedSecondsRemaining == null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.processing_tip_transcribing), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    if (streamedSegments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            streamedSegments.takeLast(10).forEach { segment ->
                                Text(
                                    text = segment.text,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
                is ProcessingStep.Saving -> {
                    AiProcessingAnimation(progress = 1f, modifier = Modifier.size(120.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.processing_finalizing), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                is ProcessingStep.Cancelling -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.processing_cancelling), color = Color.White)
                }
                is ProcessingStep.Error -> {
                    Text(stringResource(R.string.processing_error_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(currentStep.message, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                }
                else -> {}
            }
        }
    }
}

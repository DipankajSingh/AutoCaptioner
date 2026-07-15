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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation
import com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton
import com.dipdev.aiautocaptioner.ui.processing.ProcessingStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionBottomSheet(
    onDismiss: () -> Unit,
    availableModels: List<WhisperModel>,
    initialModelId: String?,
    initialLanguage: String,
    initialTranslate: Boolean,
    onStart: (modelId: String, language: String, translate: Boolean) -> Unit
) {
    var selectedModelId by remember { mutableStateOf(initialModelId ?: availableModels.firstOrNull()?.id ?: "") }
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var translateToEnglish by remember { mutableStateOf(initialTranslate) }
    var showAllLanguages by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }

    val quickLanguages = listOf("auto", "en", "es", "fr", "de")
    val allLanguages = listOf("auto", "en", "es", "fr", "de", "zh", "ja", "ko", "it", "nl", "pt", "ru", "ar") // Add more as needed

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
                text = "Generate Captions",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Language Selection
            Text(
                text = "Language",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val languagesToShow = if (showAllLanguages) allLanguages else quickLanguages
                items(languagesToShow) { lang ->
                    val isSelected = lang == selectedLanguage
                    val label = if (lang == "auto") "Auto-Detect" else lang.uppercase()
                    
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                                text = "More...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Model Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Model",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            val selectedModel = availableModels.find { it.id == selectedModelId }
            ExposedDropdownMenuBox(
                expanded = showModelDropdown,
                onExpandedChange = { showModelDropdown = it }
            ) {
                OutlinedTextField(
                    value = selectedModel?.displayName ?: "Select Model",
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
                                    Text(model.displayName, fontWeight = FontWeight.Bold)
                                    val size = "${model.sizeMb}MB"
                                    val downloaded = if (model.isDownloaded) "Downloaded" else "Tap to download"
                                    Text("$size • $downloaded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingIcon = {
                                if (model.id == selectedModelId) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
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

            Spacer(modifier = Modifier.height(24.dp))

            // Translate Toggle
            if (selectedLanguage != "en") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Translate to English",
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

            val isDownloaded = selectedModel?.isDownloaded == true
            val buttonText = if (isDownloaded) "Start Transcription" else "Download & Start"

            GradientPrimaryButton(
                text = buttonText,
                onClick = {
                    if (selectedModelId.isNotEmpty()) {
                        onStart(selectedModelId, selectedLanguage, translateToEnglish)
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
    onCancel: () -> Unit = {}
) {
    if (step is ProcessingStep.Idle || step is ProcessingStep.Ready || step is ProcessingStep.SetupAI || 
        step is ProcessingStep.Done || step is ProcessingStep.Cancelling || step is ProcessingStep.Cancelled) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)) // Replaced cloudy with a solid dark overlay for performance
            .clickable(enabled = false) {} // Block touches
    ) {
        TranscriptionProgressView(
            step = step,
            modifier = Modifier.align(Alignment.Center)
        )
        
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            Text(
                text = "Cancel",
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
                    Text("Downloading AI Model...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("${currentStep.progress}%", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                }
                is ProcessingStep.ExtractingAudio -> {
                    AiProcessingAnimation(progress = 0f, modifier = Modifier.size(120.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Preparing video...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                is ProcessingStep.LoadingModel -> {
                    AiProcessingAnimation(progress = 0.1f, modifier = Modifier.size(120.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Warming up the AI...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    Text("Listening & typing...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (currentStep.estimatedSecondsRemaining != null) {
                        val secs = currentStep.estimatedSecondsRemaining
                        val timeText = if (secs >= 60) "~${secs / 60}m left" else "~${secs}s left"
                        Text(timeText, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                    }
                }
                is ProcessingStep.Saving -> {
                    AiProcessingAnimation(progress = 1f, modifier = Modifier.size(120.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Finalizing captions...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                is ProcessingStep.Cancelling -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Cancelling...", color = Color.White)
                }
                is ProcessingStep.Error -> {
                    Text("Error occurred", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(currentStep.message, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                }
                else -> {}
            }
        }
    }
}

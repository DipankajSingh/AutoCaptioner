package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard
import com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.LanguageDropdown
import com.dipdev.aiautocaptioner.ui.components.VideoPlayerCard
import com.dipdev.aiautocaptioner.ui.processing.ProcessingStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadyStateView(
    step: ProcessingStep,
    workingVideoPath: String?,
    selectedLanguage: String,
    translateToEnglish: Boolean,
    activeModel: WhisperModel?,
    onLanguageSelected: (String) -> Unit,
    onToggleTranslation: (Boolean) -> Unit,
    onShowModelPicker: () -> Unit,
    onShowModelSetup: () -> Unit,
    onStartProcessing: () -> Unit,
    onCancelModelSetup: () -> Unit,
    onDownloadAndProcess: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        workingVideoPath?.let { path ->
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                VideoPlayerCard(
                    path = path,
                    showControls = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        GlassmorphicCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LanguageDropdown(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = onLanguageSelected,
                    isMultilingual = activeModel?.isMultilingual ?: true
                )

                if (selectedLanguage != "en") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Translate to English",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = translateToEnglish,
                            onCheckedChange = onToggleTranslation,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                activeModel?.let { model ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model: ${model.displayName}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        TextButton(onClick = onShowModelPicker) {
                            Text(text = "Change", fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                GradientPrimaryButton(
                    text = "Generate Captions",
                    onClick = {
                        if (activeModel == null) onShowModelSetup() else onStartProcessing()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )
            }
        }
    }

    if (step is ProcessingStep.SetupAI) {
        var selectedModelId by remember { mutableStateOf(step.recommendedModelId) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = onCancelModelSetup,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.7f)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Setup AI Model",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Choose a model to power your captions.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(step.models) { model ->
                        ModelPickerCard(
                            model = model,
                            isRecommended = model.id == step.recommendedModelId,
                            isSelected = model.id == selectedModelId,
                            onClick = { selectedModelId = model.id }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val selectedModel = step.models.find { it.id == selectedModelId }
                val isSelectedModelDownloaded = selectedModel?.isDownloaded == true

                GradientPrimaryButton(
                    text = if (isSelectedModelDownloaded) "Generate" else "Download & Generate",
                    onClick = { selectedModelId?.let { onDownloadAndProcess(it) } },
                    enabled = selectedModelId != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )
            }
        }
    }
}

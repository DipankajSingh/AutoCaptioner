package com.dipdev.aiautocaptioner.ui.devicecheck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.ui.components.ModelStat

@Composable
fun DeviceCheckScreen(
    onModelSelected: (String) -> Unit,
    viewModel: DeviceCheckViewModel = hiltViewModel()
) {
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val recommendedModelId by viewModel.recommendedModelId.collectAsStateWithLifecycle()
    val safetyState by viewModel.safetyState.collectAsStateWithLifecycle()
    var selectedModelId by remember { mutableStateOf<String?>(null) }

    // Auto-select recommended model once it's known
    LaunchedEffect(recommendedModelId) {
        if (selectedModelId == null) {
            selectedModelId = recommendedModelId
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Choose Your AI Model",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Downloaded once, works forever offline.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        // Device specs card
        deviceInfo?.let { info ->
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your Device",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DeviceSpecRow("RAM", "${info.totalRamMb} MB")
                    DeviceSpecRow("Storage Available", "${"%.1f".format(info.availableStorageGb)} GB")
                    DeviceSpecRow("Android", "API ${info.androidVersion}")
                    DeviceSpecRow("CPU", info.cpuAbi)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Available Models",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Model list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(models) { model ->
                ModelCard(
                    model = model,
                    isRecommended = model.id == recommendedModelId,
                    isSelected = model.id == selectedModelId,
                    onClick = { selectedModelId = model.id }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val model = models.find { it.id == selectedModelId }
                if (model != null) {
                    viewModel.checkSafety(model.sizeMb.toLong())
                }
            },
            enabled = selectedModelId != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = "Download Selected Model",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    when (val state = safetyState) {
        is SafetyCheckState.StorageError -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetSafetyState() },
                title = { Text("Not enough storage") },
                text = { Text("Not enough storage. Please free up space and try again.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetSafetyState() }) {
                        Text("OK")
                    }
                }
            )
        }
        is SafetyCheckState.CellularWarning -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetSafetyState() },
                title = { Text("Cellular Data Warning") },
                text = { Text("You are on mobile data. This download is around ${state.sizeMb} MB. Continue on mobile data?") },
                confirmButton = {
                    TextButton(onClick = {
                        selectedModelId?.let { onModelSelected(it) }
                        viewModel.resetSafetyState()
                    }) {
                        Text("Continue Anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.resetSafetyState() }) {
                        Text("Wait for Wi-Fi")
                    }
                }
            )
        }
        is SafetyCheckState.Passed -> {
            LaunchedEffect(Unit) {
                selectedModelId?.let { onModelSelected(it) }
                viewModel.resetSafetyState()
            }
        }
        SafetyCheckState.Idle -> {}
    }
}

@Composable
private fun DeviceSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ModelCard(
    model: WhisperModel,
    isRecommended: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = model.displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Recommended badge
                    if (isRecommended) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Recommended",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    // Already downloaded badge
                    if (model.isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = model.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Speed + Accuracy + Size row
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModelStat(label = "Size", value = "${model.sizeMb} MB")
                    ModelStat(label = "Speed", value = "★".repeat(model.speed))
                    ModelStat(label = "Accuracy", value = "★".repeat(model.accuracy))
                }
            }

            // Selection indicator
            if (isSelected) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


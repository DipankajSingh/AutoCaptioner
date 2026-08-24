package com.dipdev.aiautocaptioner.ui.onboarding

import com.dipdev.aiautocaptioner.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dipdev.aiautocaptioner.core.device.ModelSafetyCheckState
import com.dipdev.aiautocaptioner.core.device.OnboardingModelTier
import com.dipdev.aiautocaptioner.core.whisper.ModelDownloadServiceManager
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton
import com.dipdev.aiautocaptioner.ui.onboarding.components.CellularWarningDialog
import com.dipdev.aiautocaptioner.ui.onboarding.components.StorageErrorDialog
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.X

import androidx.compose.runtime.LaunchedEffect

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SetupQualityScreen(
    onNavigateBack: () -> Unit,
    onDownloadComplete: () -> Unit,
    viewModel: SetupQualityViewModel = hiltViewModel()
) {
    val tiers by viewModel.tiers.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val safetyCheckState by viewModel.safetyCheckState.collectAsState()
    val downloadState by ModelDownloadServiceManager.downloadState.collectAsState()

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Complete) {
            viewModel.finalizeSetup()
            onDownloadComplete()
        }
    }

    when (val state = safetyCheckState) {
        is ModelSafetyCheckState.StorageError -> {
            StorageErrorDialog(
                requiredMb = state.requiredMb,
                onCheckAgain = { viewModel.checkStorageAgain() },
                onDismiss = { viewModel.clearSafetyCheckState() }
            )
        }
        is ModelSafetyCheckState.CellularWarning -> {
            CellularWarningDialog(
                sizeMb = state.sizeMb,
                onWaitForWifi = { viewModel.clearSafetyCheckState() },
                onDownloadAnyway = { viewModel.onCellularWarningAccepted() }
            )
        }
        else -> {}
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.navigation_go_back))
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            if (downloadState != null && downloadState !is DownloadState.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (downloadState) {
                                is DownloadState.Starting -> "Preparing download..."
                                is DownloadState.Downloading -> {
                                    val pct = (downloadState as DownloadState.Downloading).progress
                                    "Downloading Engine... $pct%"
                                }
                                else -> "Finishing up..."
                            },
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (downloadState is DownloadState.Starting || downloadState is DownloadState.Downloading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val progress = (downloadState as? DownloadState.Downloading)?.progress?.div(100f)
                                com.dipdev.aiautocaptioner.ui.components.RoundedProgressBar(
                                    modifier = Modifier.weight(1f),
                                    progress = progress
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                androidx.compose.material3.IconButton(onClick = { viewModel.cancelDownload() }) {
                                    androidx.compose.material3.Icon(
                                        imageVector = FeatherIcons.X,
                                        contentDescription = "Cancel",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            com.dipdev.aiautocaptioner.ui.components.RoundedProgressBar(
                                modifier = Modifier.fillMaxWidth(),
                                progress = null
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column {
                        if (downloadState is DownloadState.Error) {
                            Text(
                                text = (downloadState as DownloadState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        GradientPrimaryButton(
                            text = stringResource(R.string.onboarding_quality_download_btn),
                            onClick = { viewModel.onDownloadRequested() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = selectedModelId != null
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.onboarding_quality_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_quality_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            val isDownloading = downloadState != null && downloadState !is DownloadState.Error

            if (tiers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No compatible engine found for this language on your device.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else if (tiers.size == 1) {
                TierCard(
                    tier = tiers[0],
                    isSelected = true,
                    onClick = {}
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(tiers) { tier ->
                        TierCard(
                            tier = tier,
                            isSelected = selectedModelId == tier.model.id,
                            onClick = { 
                                if (!isDownloading) {
                                    viewModel.selectModel(tier.model.id) 
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TierCard(
    tier: OnboardingModelTier,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor.current

    com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard(
        color = if (isSelected) accentColor.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Unspecified,
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, accentColor, RoundedCornerShape(16.dp)) 
                else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tier.tierName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${tier.model.sizeMb} MB • ${tier.model.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (tier.isRecommended) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            accentColor.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    val reason = tier.recommendedReasonResId?.let { stringResource(it) }
                    val recText = if (reason != null) stringResource(R.string.onboarding_quality_recommended_reason, reason) else stringResource(R.string.onboarding_quality_recommended)
                    
                    Text(
                        text = recText,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

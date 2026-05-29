package com.dipdev.aiautocaptioner.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dipdev.aiautocaptioner.data.repository.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentTheme by viewModel.themeFlow.collectAsState()
    val isGlassmorphism by viewModel.glassmorphismFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Glassmorphism Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Glassmorphism UI",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Enable translucent backgrounds and blur effects",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isGlassmorphism,
                    onCheckedChange = { viewModel.setGlassmorphismEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Theme Selection
            Text(
                text = "Primary Color Theme",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            AppTheme.values().forEach { themeOption ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setTheme(themeOption) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (themeOption == currentTheme),
                        onClick = { viewModel.setTheme(themeOption) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = themeOption.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

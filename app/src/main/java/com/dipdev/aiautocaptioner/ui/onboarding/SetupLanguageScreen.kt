package com.dipdev.aiautocaptioner.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import android.os.Bundle
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.core.whisper.WhisperLanguages
import com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Search
import java.util.Locale
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SetupLanguageScreen(
    onNavigateBack: () -> Unit,
    onContinue: (languageCode: String) -> Unit,
    viewModel: SetupLanguageViewModel = hiltViewModel()
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val allLanguages = viewModel.orderedLanguages

    var showAll by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val displayLanguages = remember(allLanguages, showAll, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            allLanguages
        } else {
            val query = searchQuery.lowercase()
            allLanguages.filter { code ->
                WhisperLanguages.matchesSearchQuery(code, query)
            }
        }
        
        if (showAll || searchQuery.isNotBlank()) {
            filtered
        } else {
            filtered.take(6)
        }
    }

    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            },
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
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                GradientPrimaryButton(
                    text = stringResource(R.string.onboarding_continue_btn),
                    onClick = {
                        selectedLanguage?.let { onContinue(it) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = selectedLanguage != null
                )
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
                text = stringResource(R.string.onboarding_language_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_language_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { 
                    Text(
                        stringResource(R.string.onboarding_language_search_placeholder), 
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    ) 
                },
                leadingIcon = {
                    Icon(
                        FeatherIcons.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor.current,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            if (displayLanguages.isEmpty() && searchQuery.isNotBlank()) {
                val context = LocalContext.current
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_language_not_found, searchQuery),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            if (!com.dipdev.aiautocaptioner.BuildConfig.DEBUG) {
                                val bundle = Bundle().apply { putString("requested_text", searchQuery) }
                                Firebase.analytics.logEvent("language_requested", bundle)
                            }
                            Toast.makeText(context, context.getString(R.string.onboarding_language_request_sent), Toast.LENGTH_SHORT).show()
                            searchQuery = ""
                        }
                    ) {
                        Text(stringResource(R.string.onboarding_language_request_btn), color = com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor.current)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(displayLanguages) { code ->
                        LanguageCard(
                            code = code,
                            displayName = WhisperLanguages.getDisplayName(code),
                            isSelected = code == selectedLanguage,
                            onClick = { viewModel.selectLanguage(code) }
                        )
                    }

                    if (!showAll && allLanguages.size > 6) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            TextButton(
                                onClick = { showAll = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_language_show_all),
                                    color = com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor.current,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageCard(
    code: String,
    displayName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor.current
    
    val contentColor = if (isSelected) {
        accentColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard(
        color = if (isSelected) accentColor.copy(alpha = 0.15f) else Color.Unspecified,
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(
                if (isSelected) Modifier.border(2.dp, accentColor, RoundedCornerShape(16.dp)) 
                else Modifier
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
        ) {
            if (code == "auto") {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = displayName,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

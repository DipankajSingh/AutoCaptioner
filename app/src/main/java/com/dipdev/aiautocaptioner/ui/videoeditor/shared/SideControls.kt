package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.dipdev.aiautocaptioner.ui.components.LanguageDropdown
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

@Composable
fun LeftSideControls(
    hasEdits: Boolean,
    onNavigateBack: () -> Unit,
    onShowBackDialog: () -> Unit,
    onNavigateToExport: () -> Unit,
    onDeleteProject: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onNavigateToProcessing: () -> Unit,
    selectedLanguage: String,
    translateToEnglish: Boolean,
    onLanguageSelected: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showLanguagePanel by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(top = 16.dp, start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SideControlButton(
            icon = FeatherIcons.LogOut,
            contentDescription = "Exit Editor", // Replace with stringResource
            onClick = {
                if (hasEdits) onShowBackDialog() else onNavigateBack()
            },
            tint = Color.Red,
            containerColor = Color.Red.copy(alpha = 0.15f)
        )

        Box {
            SideControlButton(
                icon = FeatherIcons.Menu,
                contentDescription = "Menu", // Replace with stringResource
                onClick = { showMenu = !showMenu },
                isActive = showMenu
            )

            if (showMenu) {
                HamburgerPopup(
                    hasEdits = hasEdits,
                    onDismiss = { showMenu = false },
                    onNavigateToExport = onNavigateToExport,
                    onShowDeleteDialog = onShowDeleteDialog,
                    onDeleteProject = onDeleteProject
                )
            }
        }

        SideControlButton(
            icon = Icons.Rounded.ClosedCaption,
            contentDescription = "Generate Captions", // Replace with stringResource
            onClick = {
                onNavigateToProcessing()
            }
        )

        LanguageSelector(
            expanded = showLanguagePanel,
            onExpandedChange = { showLanguagePanel = it },
            selectedLanguage = selectedLanguage,
            translateToEnglish = translateToEnglish,
            onLanguageSelected = onLanguageSelected
        )
    }
}

@Composable
fun RightSideControls(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(top = 16.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SideToolbar(
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = onUndo,
            onRedo = onRedo,
            onAddImage = onAddImage
        )
    }
}

// ---------------------------------------------------------------------------
// REUSABLE COMPONENTS (The Fixes)
// ---------------------------------------------------------------------------

@Composable
fun AnimatedEditorPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Fixed: State is initialized entirely within the remember block
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                modifier = modifier.padding(top = 48.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SideControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isActive: Boolean = false,
    tint: Color? = null,
    containerColor: Color? = null
) {
    val accent = LocalAccentColor.current
    val finalContainerColor = if (isActive) accent else (containerColor ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
    val finalTint = if (isActive) MaterialTheme.colorScheme.onPrimary else (tint ?: if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape) // Fixed: Use native Compose CircleShape
            .background(finalContainerColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = finalTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// POPUP IMPLEMENTATIONS
// ---------------------------------------------------------------------------

@Composable
fun HamburgerPopup(
    hasEdits: Boolean,
    onDismiss: () -> Unit,
    onNavigateToExport: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onDeleteProject: () -> Unit
) {
    AnimatedEditorPopup(
        onDismiss = onDismiss,
        modifier = Modifier.width(180.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onNavigateToExport()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Export", style = MaterialTheme.typography.bodyMedium)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        if (hasEdits) onShowDeleteDialog() else onDeleteProject()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Delete Project", style = MaterialTheme.typography.bodyMedium, color = Color.Red)
            }
        }
    }
}

@Composable
fun LanguageSelector(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedLanguage: String,
    translateToEnglish: Boolean,
    onLanguageSelected: (String, Boolean) -> Unit
) {
    Box {
        SideControlButton(
            icon = Icons.Rounded.Language,
            contentDescription = "Select Language",
            onClick = { onExpandedChange(!expanded) },
            isActive = expanded
        )

        if (expanded) {
            LanguagePopup(
                selectedLanguage = selectedLanguage,
                translateToEnglish = translateToEnglish,
                onExpandedChange = onExpandedChange,
                onLanguageSelected = onLanguageSelected
            )
        }
    }
}

@Composable
fun LanguagePopup(
    selectedLanguage: String,
    translateToEnglish: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLanguageSelected: (String, Boolean) -> Unit
) {
    AnimatedEditorPopup(
        onDismiss = { onExpandedChange(false) },
        modifier = Modifier.width(220.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Language",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                IconButton(
                    onClick = { onExpandedChange(false) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(FeatherIcons.X, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }
            LanguageDropdown(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { lang ->
                    onLanguageSelected(lang, if (lang == "en") false else translateToEnglish)
                },
                allowedLanguages = listOf("multilingual")
            )
            if (selectedLanguage != "en") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Translate to EN", style = MaterialTheme.typography.labelSmall)
                    Switch(
                        checked = translateToEnglish,
                        onCheckedChange = { v ->
                            onLanguageSelected(selectedLanguage, v)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SideToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SideControlButton(
            icon = FeatherIcons.CornerUpLeft,
            contentDescription = "Undo",
            onClick = onUndo,
            enabled = canUndo
        )
        SideControlButton(
            icon = FeatherIcons.CornerUpRight,
            contentDescription = "Redo",
            onClick = onRedo,
            enabled = canRedo
        )
        SideControlButton(
            icon = FeatherIcons.Image,
            contentDescription = "Add Image",
            onClick = onAddImage,
            enabled = true
        )
    }
}
package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor

@Composable
fun LeftSideControls(
    hasEdits: Boolean,
    onNavigateBack: () -> Unit,
    onShowBackDialog: () -> Unit,
    onNavigateToExport: () -> Unit,
    onDeleteProject: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onApplyEdits: () -> Unit,
    onNavigateToProcessing: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier.padding(top = 16.dp, start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SideControlButton(
            icon = Icons.Outlined.Close,
            contentDescription = "Exit Editor",
            onClick = {
                if (hasEdits) onShowBackDialog() else onNavigateBack()
            },
            enabled = true,
            tint = Color.Red,
            containerColor = Color.Red.copy(alpha = 0.15f)
        )
        
        Box {
            SideControlButton(
                icon = Icons.Default.Menu,
                contentDescription = "Menu",
                onClick = { showMenu = true },
                enabled = true
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Export") },
                    onClick = {
                        showMenu = false
                        onNavigateToExport()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Project") },
                    onClick = {
                        showMenu = false
                        if (hasEdits) onShowDeleteDialog()
                        else onDeleteProject()
                    }
                )
            }
        }

        SideControlButton(
            icon = Icons.Outlined.Subtitles,
            contentDescription = "Generate Captions",
            onClick = {
                if (hasEdits) onApplyEdits()
                else onNavigateToProcessing()
            },
            enabled = true
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
    selectedLanguage: String,
    translateToEnglish: Boolean,
    onLanguageSelected: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var langPanelExpanded by remember { mutableStateOf(false) }
    
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
        
        
        LanguageSelector(
            expanded = langPanelExpanded,
            onExpandedChange = { langPanelExpanded = it },
            selectedLanguage = selectedLanguage,
            translateToEnglish = translateToEnglish,
            onLanguageSelected = onLanguageSelected
        )
    }
}

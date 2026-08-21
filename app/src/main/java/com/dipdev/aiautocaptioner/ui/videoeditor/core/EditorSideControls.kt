package com.dipdev.aiautocaptioner.ui.videoeditor.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.ui.videoeditor.style.VerticalPremiumSlider

@Composable
fun EditorSideControls(
    selectedOverlayId: String?,
    isTextOverlaySelected: Boolean,
    isEditingTextOverlay: Boolean,
    currentImageOverlay: ImageOverlayEntity?,
    currentTextOverlay: TextOverlayEntity?,
    
    showTextSizeSlider: Boolean,
    onToggleTextSizeSlider: () -> Unit,
    
    onStartEditingText: () -> Unit,
    onShowTextColorMenu: () -> Unit,
    
    onDuplicateTextOverlay: (String) -> Unit,
    onDuplicateImageOverlay: (String) -> Unit,
    
    onDeleteTextOverlay: (String) -> Unit,
    onDeleteImageOverlay: (String) -> Unit,
    
    onDeselectOverlay: () -> Unit,
    
    onUpdateTextOverlay: (TextOverlayEntity) -> Unit,
    onUpdateImageOverlay: (ImageOverlayEntity) -> Unit,
    
    onCropImage: (ImageOverlayEntity?) -> Unit
) {
    var showOpacitySheet by remember { mutableStateOf(false) }
    var showFiltersSheet by remember { mutableStateOf(false) }

    com.dipdev.aiautocaptioner.ui.videoeditor.shared.OverlaySideToolbar(
        selectedOverlayId = selectedOverlayId,
        isTextOverlay = isTextOverlaySelected || isEditingTextOverlay,
        onFontSize = {
            if (isTextOverlaySelected) {
                onToggleTextSizeSlider()
            }
        },
        onEdit = onStartEditingText,
        onColorMenuClicked = onShowTextColorMenu,
        onDuplicate = {
            if (isTextOverlaySelected && selectedOverlayId != null) {
                onDuplicateTextOverlay(selectedOverlayId)
            } else if (selectedOverlayId != null) {
                onDuplicateImageOverlay(selectedOverlayId)
            }
        },
        onCrop = { onCropImage(currentImageOverlay) },
        onFilters = { showFiltersSheet = true },
        onOpacity = { showOpacitySheet = true },
        onDelete = {
            if (isTextOverlaySelected && selectedOverlayId != null) {
                onDeleteTextOverlay(selectedOverlayId)
            } else if (selectedOverlayId != null) {
                onDeleteImageOverlay(selectedOverlayId)
            }
            onDeselectOverlay()
        }
    )

    if ((showTextSizeSlider || isEditingTextOverlay) && currentTextOverlay != null) {
        com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard(
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerticalPremiumSlider(
                    value = currentTextOverlay.fontSize,
                    valueRange = 24f..120f,
                    onValueChange = { newSize: Float ->
                        onUpdateTextOverlay(currentTextOverlay.copy(fontSize = newSize))
                    },
                    modifier = Modifier.height(220.dp)
                )

                com.dipdev.aiautocaptioner.ui.videoeditor.text.FontStyleCarousel(
                    selectedAssetPath = currentTextOverlay.fontAssetPath,
                    onFontChange = { fontAssetPath ->
                        onUpdateTextOverlay(currentTextOverlay.copy(fontAssetPath = fontAssetPath))
                    },
                    modifier = Modifier.height(220.dp)
                )
            }
        }
    }

    if (showOpacitySheet && currentImageOverlay != null) {
        com.dipdev.aiautocaptioner.ui.videoeditor.image.components.OpacityControlSheet(
            overlay = currentImageOverlay,
            onUpdateOverlay = onUpdateImageOverlay,
            onDismiss = { showOpacitySheet = false }
        )
    }

    if (showFiltersSheet && currentImageOverlay != null) {
        com.dipdev.aiautocaptioner.ui.videoeditor.image.components.FilterControlSheet(
            overlay = currentImageOverlay,
            onUpdateOverlay = onUpdateImageOverlay,
            onDismiss = { showFiltersSheet = false }
        )
    }
}

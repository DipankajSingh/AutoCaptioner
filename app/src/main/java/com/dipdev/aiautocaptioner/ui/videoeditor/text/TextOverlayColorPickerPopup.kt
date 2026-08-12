package com.dipdev.aiautocaptioner.ui.videoeditor.text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.videoeditor.style.AdvancedColorPicker

enum class TextOverlayColorField {
    TEXT,
    BACKGROUND
}

@Composable
fun TextOverlayColorPickerPopup(
    textColorArgb: Int,
    backgroundColorArgb: Int,
    onColorChanged: (TextOverlayColorField, Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            modifier = Modifier
                .padding(bottom = 60.dp) // Leave some space from the bottom edge
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.95f)
        ) {
            TextOverlayColorPickerContent(
                textColorArgb = textColorArgb,
                backgroundColorArgb = backgroundColorArgb,
                onColorChanged = onColorChanged
            )
        }
    }
}

@Composable
private fun TextOverlayColorPickerContent(
    textColorArgb: Int,
    backgroundColorArgb: Int,
    onColorChanged: (TextOverlayColorField, Int) -> Unit
) {
    var selectedField by remember { mutableStateOf(TextOverlayColorField.TEXT) }

    val currentColor = when (selectedField) {
        TextOverlayColorField.TEXT -> textColorArgb
        TextOverlayColorField.BACKGROUND -> backgroundColorArgb
    }

    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ColorFieldSwatch(
                label = stringResource(R.string.color_tab_text),
                color = textColorArgb,
                selected = selectedField == TextOverlayColorField.TEXT,
                onClick = { selectedField = TextOverlayColorField.TEXT },
                modifier = Modifier.weight(1f)
            )
            ColorFieldSwatch(
                label = stringResource(R.string.color_tab_bg_color),
                color = backgroundColorArgb,
                selected = selectedField == TextOverlayColorField.BACKGROUND,
                onClick = { selectedField = TextOverlayColorField.BACKGROUND },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        key(selectedField) {
            // AdvancedColorPicker expects Long for ARGB
            AdvancedColorPicker(
                initialColor = currentColor.toLong() and 0xFFFFFFFF,
                onColorChanged = { colorLong ->
                    onColorChanged(selectedField, colorLong.toInt())
                }
            )
        }
    }
}

@Composable
private fun ColorFieldSwatch(
    label: String,
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.6f),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        )
    }
}

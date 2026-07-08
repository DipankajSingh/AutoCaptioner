package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.Plus
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity

@Composable
fun PresetsTab(
    styles: List<CaptionStyleEntity>,
    activeStyle: CaptionStyleEntity?,
    onPresetSelected: (CaptionStyleEntity) -> Unit,
    onPresetLongClicked: (CaptionStyleEntity) -> Unit = {},
    onAddPreset: () -> Unit = {}
) {
    if (styles.isNotEmpty()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(styles) { style ->
                PresetChip(
                    style = style,
                    isSelected = activeStyle?.name == style.name,
                    onClick = { onPresetSelected(style) },
                    onLongClick = { onPresetLongClicked(style) }
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable { onAddPreset() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(FeatherIcons.Plus, contentDescription = "Save Preset", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

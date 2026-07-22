package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity

@Composable
fun PresetsTab(
    styles: List<CaptionStyleEntity>,
    activeStyle: CaptionStyleEntity?,
    onPresetSelected: (CaptionStyleEntity) -> Unit,
    onPresetLongClicked: (CaptionStyleEntity) -> Unit = {},
    onAddPreset: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeStyle?.id, styles.size) {
        val index = styles.indexOfFirst { it.id == activeStyle?.id }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Box(modifier = Modifier.height(110.dp)) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(styles, key = { it.id }) { style ->
                PresetChip(
                    style = style,
                    isSelected = activeStyle?.id == style.id,
                    onClick = { onPresetSelected(style) },
                    onLongClick = { onPresetLongClicked(style) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(16.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                    )
                )
                .align(Alignment.CenterStart)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(16.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                    )
                )
                .align(Alignment.CenterEnd)
        )
    }
}

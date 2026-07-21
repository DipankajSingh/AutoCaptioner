package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity

private val presetCategories = mapOf(
    "Basic" to "Clean",
    "Cinematic" to "Clean",
    "Elegant" to "Clean",
    "Hormozi" to "Bold",
    "Bold Pop" to "Bold",
    "Karaoke Pro" to "Bold",
    "Cyberpunk" to "Creative",
    "Neon Glow" to "Creative",
    "Retro Sign" to "Creative",
    "Typewriter" to "Motion",
    "Tech Terminal" to "Motion",
    "Story Time" to "Motion",
    "Smooth Gradient" to "Motion",
)

private val categories = listOf("All", "Clean", "Bold", "Creative", "Motion")

@Composable
fun PresetsTab(
    styles: List<CaptionStyleEntity>,
    activeStyle: CaptionStyleEntity?,
    onPresetSelected: (CaptionStyleEntity) -> Unit,
    onPresetLongClicked: (CaptionStyleEntity) -> Unit = {},
    onAddPreset: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val listState = rememberLazyListState()

    val filteredStyles = remember(styles, selectedCategory) {
        if (selectedCategory == "All") styles
        else styles.filter { presetCategories[it.name] == selectedCategory }
    }

    LaunchedEffect(activeStyle?.id, filteredStyles.size) {
        val index = filteredStyles.indexOfFirst { it.id == activeStyle?.id }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { category ->
                val isActive = category == selectedCategory
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { selectedCategory = category }
                ) {
                    Text(
                        text = category,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.height(110.dp)) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredStyles, key = { it.id }) { style ->
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
}

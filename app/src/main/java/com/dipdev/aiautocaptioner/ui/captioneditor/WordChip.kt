package com.dipdev.aiautocaptioner.ui.captioneditor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber

/**
 * A container for a flow-like layout of [WordChip]s.
 */
@Composable
fun WordChips(
    words: List<CaptionWordEntity>,
    onWordLongPress: (CaptionWordEntity) -> Unit
) {
    @OptIn(ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        words.forEach { word ->
            WordChip(word = word, onLongPress = { onWordLongPress(word) })
        }
    }
}

/**
 * A single word chip with its timestamp and emphasis state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordChip(
    word: CaptionWordEntity,
    onLongPress: () -> Unit
) {
    val bgColor = if (word.isEmphasized)
        AccentAmber.copy(alpha = 0.18f)
    else
        MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (word.isEmphasized)
        AccentAmber
    else
        MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(4.dp), // Flattened shape
        shadowElevation = 0.dp,
        color = bgColor,
        modifier = Modifier.combinedClickable(
            onLongClick = onLongPress,
            onClick = {}
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = word.word,
                fontSize = 13.sp,
                color = textColor,
                fontWeight = if (word.isEmphasized) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = formatMs(word.startTimeMs),
                fontSize = 9.sp,
                color = textColor.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Formats milliseconds into M:SS.CC (Minutes:Seconds.Centiseconds)
 */
internal fun formatMs(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    val millis = (ms % 1000) / 10
    return "%d:%02d.%02d".format(minutes, secs, millis)
}

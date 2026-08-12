package com.dipdev.aiautocaptioner.ui.videoeditor.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WrapDirection {
    Left, Right
}

@Composable
fun BottomUpFlowColumn(
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = 0.dp,
    horizontalSpacing: Dp = 0.dp,
    wrapDirection: WrapDirection = WrapDirection.Right,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val vSpacingPx = verticalSpacing.roundToPx()
        val hSpacingPx = horizontalSpacing.roundToPx()

        // Loose constraints for flow behavior
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }

        val columns = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentColumn = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentColumnHeight = 0
        var maxColumnWidth = 0

        val columnWidths = mutableListOf<Int>()

        // Pack items into columns (order is top-to-bottom logically)
        for (placeable in placeables) {
            val itemHeight = placeable.height + if (currentColumn.isNotEmpty()) vSpacingPx else 0
            if (currentColumnHeight + itemHeight > constraints.maxHeight && currentColumn.isNotEmpty()) {
                // Wrap to next column
                columns.add(currentColumn)
                columnWidths.add(maxColumnWidth)
                currentColumn = mutableListOf()
                currentColumnHeight = 0
                maxColumnWidth = 0
            }
            currentColumn.add(placeable)
            currentColumnHeight += if (currentColumn.size == 1) placeable.height else itemHeight
            maxColumnWidth = maxOf(maxColumnWidth, placeable.width)
        }
        if (currentColumn.isNotEmpty()) {
            columns.add(currentColumn)
            columnWidths.add(maxColumnWidth)
        }

        val totalWidth = columnWidths.sum() + maxOf(0, columns.size - 1) * hSpacingPx
        
        layout(totalWidth, constraints.maxHeight) {
            var xOffset = if (wrapDirection == WrapDirection.Right) 0 else totalWidth
            
            for ((colIndex, column) in columns.withIndex()) {
                val colWidth = columnWidths[colIndex]
                if (wrapDirection == WrapDirection.Left) {
                    xOffset -= colWidth
                }
                
                // Start from the bottom of the container
                var yOffset = constraints.maxHeight
                
                // We process the column logically from bottom to top, meaning the last item
                // in the `column` list gets placed at the absolute bottom.
                for (placeable in column.asReversed()) {
                    yOffset -= placeable.height
                    placeable.placeRelative(
                        x = xOffset + (colWidth - placeable.width) / 2, // Center horizontally
                        y = yOffset
                    )
                    yOffset -= vSpacingPx
                }
                
                if (wrapDirection == WrapDirection.Right) {
                    xOffset += colWidth + hSpacingPx
                } else {
                    xOffset -= hSpacingPx
                }
            }
        }
    }
}

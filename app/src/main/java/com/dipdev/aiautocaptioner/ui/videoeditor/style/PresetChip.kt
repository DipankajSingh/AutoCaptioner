package com.dipdev.aiautocaptioner.ui.videoeditor.style

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.engine.CaptionPaints
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetChip(
    style: CaptionStyleEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val typeface = remember(style.id, style.fontFamily, style.fontWeight, style.isItalic) {
        CaptionPaints.loadTypeface(context, style.fontFamily, style.fontWeight, style.isItalic)
    }

    val textSizePx = with(density) { 20.sp.toPx() }
    val outlineWidthPx = with(density) { (style.outlineWidth * 0.5f).dp.toPx() }

    val fillPaint = remember(style.id, typeface, style.textColor, style.outlineOnly) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = textSizePx
            color = style.textColor.toInt()
            this.style = if (style.outlineOnly) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = if (style.outlineOnly) outlineWidthPx else 0f
            strokeJoin = Paint.Join.ROUND
            textAlign = Paint.Align.CENTER
            textLocale = Locale.ROOT
            flags = flags or Paint.SUBPIXEL_TEXT_FLAG
        }
    }

    val outlinePaint = remember(style.id, typeface, style.outlineColor, style.outlineWidth) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = textSizePx
            color = style.outlineColor.toInt()
            this.style = Paint.Style.STROKE
            strokeWidth = outlineWidthPx
            strokeJoin = Paint.Join.ROUND
            textAlign = Paint.Align.CENTER
            textLocale = Locale.ROOT
            flags = flags or Paint.SUBPIXEL_TEXT_FLAG
        }
    }

    val highlightColor = remember(style.highlightColor) { Color(style.highlightColor) }
    val hasBackground = style.backgroundType != BackgroundType.NONE
    val bgColor = remember(style.backgroundColor, style.backgroundOpacity, style.backgroundType) {
        if (hasBackground) Color(style.backgroundColor).copy(alpha = style.backgroundOpacity)
        else Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = if (isSelected) 8.dp else 1.dp,
        modifier = Modifier
            .size(width = 80.dp, height = 96.dp)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A2E))
                ) {
                    if (hasBackground) {
                        drawRoundRect(
                            color = bgColor,
                            cornerRadius = CornerRadius(4.dp.toPx()),
                            topLeft = Offset(size.width * 0.1f, size.height * 0.2f),
                            size = Size(size.width * 0.8f, size.height * 0.6f)
                        )
                    }

                    drawIntoCanvas { canvas ->
                        val x = size.width / 2f
                        val y = size.height / 2f + textSizePx * 0.35f

                        if (style.outlineWidth > 0 && !style.outlineOnly) {
                            canvas.nativeCanvas.drawText("Aa", x, y, outlinePaint)
                        }
                        canvas.nativeCanvas.drawText("Aa", x, y, fillPaint)
                    }
                }

                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF1A1A2E),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = style.name,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(style.textColor))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(highlightColor)
                )
            }
        }
    }
}

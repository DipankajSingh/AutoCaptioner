package com.dipdev.aiautocaptioner.ui.styleeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetChip(
    style: CaptionStyleEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (isSelected) 4.dp else 1.dp,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = style.name,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun BottomTabItem(
    name: String, 
    icon: ImageVector, 
    selected: Boolean, 
    isPremiumLocked: Boolean = false,
    onClick: () -> Unit
) {
    val tint by androidx.compose.animation.animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    val scale by androidx.compose.animation.core.animateFloatAsState(if (selected) 1.1f else 1.0f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally, 
        modifier = Modifier
            .clickable(
                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
    ) {
        Box {
            Icon(icon, contentDescription = name, tint = tint)
            if (isPremiumLocked) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "PRO",
                    modifier = Modifier.size(10.dp).align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp),
                    tint = Color(0xFFF6A90A)
                )
            }
        }
        if (selected) {
            Text(name, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StyleEditorBottomBar(
    selectedTab: StyleTab,
    isPremium: Boolean,
    onTabSelected: (StyleTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color.Transparent),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomTabItem("Presets", Icons.Default.Style, selectedTab == StyleTab.PRESETS) { onTabSelected(StyleTab.PRESETS) }
        BottomTabItem("Text", Icons.Default.TextFields, selectedTab == StyleTab.TEXT, isPremiumLocked = !isPremium) { onTabSelected(StyleTab.TEXT) }
        BottomTabItem("Color", Icons.Default.Palette, selectedTab == StyleTab.COLOR, isPremiumLocked = !isPremium) { onTabSelected(StyleTab.COLOR) }
        BottomTabItem("Animate", Icons.Default.Animation, selectedTab == StyleTab.ANIMATION, isPremiumLocked = !isPremium) { onTabSelected(StyleTab.ANIMATION) }
    }
}

@Composable
fun PresetsTab(
    styles: List<CaptionStyleEntity>,
    activeStyle: CaptionStyleEntity?,
    onPresetSelected: (CaptionStyleEntity) -> Unit,
    onPresetLongClicked: (CaptionStyleEntity) -> Unit = {}
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
        }
    }
}


@Composable
fun PremiumSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    var internalRatio by remember { 
        mutableFloatStateOf(((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)) 
    }
    var isDragging by remember { mutableStateOf(false) }

    // Sync from ViewModel updates only if we aren't currently dragging
    LaunchedEffect(value) {
        if (!isDragging) {
            internalRatio = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        }
    }
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    internalRatio = (internalRatio + dragAmount / size.width).coerceIn(0f, 1f)
                    val realValue = valueRange.start + internalRatio * (valueRange.endInclusive - valueRange.start)
                    onValueChange(realValue)
                }
            }
            .pointerInput(valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        val newRatio = (offset.x / size.width).coerceIn(0f, 1f)
                        val realValue = valueRange.start + newRatio * (valueRange.endInclusive - valueRange.start)
                        onValueChange(realValue)
                    }
                )
            }
    ) {
        val trackHeight = 4.dp.toPx()
        val cornerRadius = CornerRadius(trackHeight / 2f)
        val cy = size.height / 2f

        // Track
        drawRoundRect(
            color = Color.DarkGray.copy(alpha = 0.5f),
            size = Size(width = size.width, height = trackHeight),
            topLeft = Offset(0f, cy - trackHeight / 2f),
            cornerRadius = cornerRadius
        )
        // Fill
        drawRoundRect(
            color = Color.White,
            size = Size(width = size.width * internalRatio, height = trackHeight),
            topLeft = Offset(0f, cy - trackHeight / 2f),
            cornerRadius = cornerRadius
        )
        // Thumb
        drawCircle(
            color = Color.White,
            radius = 12.dp.toPx(),
            center = Offset(size.width * internalRatio, cy)
        )
    }
}

@Composable
fun AdvancedColorPicker(
    initialColor: Long,
    onColorChanged: (Long) -> Unit
) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(initialColor.toInt(), hsv)
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 2D Saturation / Value Box
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(horizontal = 24.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                    value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onColorChanged(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong())
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    saturation = (offset.x / size.width).coerceIn(0f, 1f)
                    value = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    onColorChanged(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong())
                }
            }
        ) {
            val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            // Saturation Horizontal
            drawRoundRect(Brush.horizontalGradient(listOf(Color.White, hueColor)), cornerRadius = CornerRadius(16f))
            // Value Vertical
            drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), cornerRadius = CornerRadius(16f))
            
            drawCircle(
                color = Color.White,
                radius = 18.dp.toPx(),
                center = Offset(saturation * size.width, (1f - value) * size.height),
                style = Stroke(width = 4.dp.toPx())
            )
            // Adding a sharp contrast ring so it doesn't get lost on bright backgrounds
            drawCircle(
                color = Color.Black,
                radius = 20.dp.toPx(),
                center = Offset(saturation * size.width, (1f - value) * size.height),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Hue Ribbon Slider
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(28.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    hue = ((change.position.x / size.width) * 360f).coerceIn(0f, 360f)
                    onColorChanged(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong())
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    hue = ((offset.x / size.width) * 360f).coerceIn(0f, 360f)
                    onColorChanged(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong())
                }
            }
        ) {
            val rainbow = listOf(
                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
            drawRoundRect(Brush.horizontalGradient(rainbow), cornerRadius = CornerRadius(size.height / 2f))
            
            drawCircle(
                color = Color.White,
                radius = size.height / 2f + 6.dp.toPx(),
                center = Offset((hue / 360f) * size.width, size.height / 2f),
                style = Stroke(width = 6.dp.toPx())
            )
        }
    }
}

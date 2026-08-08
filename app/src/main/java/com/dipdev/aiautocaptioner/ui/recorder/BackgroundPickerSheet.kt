package com.dipdev.aiautocaptioner.ui.recorder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.net.Uri
import com.dipdev.aiautocaptioner.ui.videoeditor.style.AdvancedColorPicker
import compose.icons.FeatherIcons
import compose.icons.feathericons.Film
import compose.icons.feathericons.Image
import compose.icons.feathericons.Plus

// ---------------------------------------------------------------------------
// Curated creator colour palette — 16 studio-quality colours
// ---------------------------------------------------------------------------
private val CREATOR_COLORS = listOf(
    Color(0xFF0F172A), // Deep Space (default)
    Color(0xFF18181B), // Near Black
    Color(0xFFFFFFFF), // White
    Color(0xFF1E293B), // Slate 800
    Color(0xFF0EA5E9), // Sky Blue
    Color(0xFF6366F1), // Indigo
    Color(0xFF8B5CF6), // Violet
    Color(0xFFEC4899), // Pink
    Color(0xFFF43F5E), // Rose
    Color(0xFFEF4444), // Red
    Color(0xFFF97316), // Orange
    Color(0xFFF59E0B), // Amber
    Color(0xFF84CC16), // Lime
    Color(0xFF10B981), // Emerald
    Color(0xFF14B8A6), // Teal
    Color(0xFF3B82F6), // Blue
)

// ---------------------------------------------------------------------------
// Curated gradient presets — 5 carefully chosen presets
// ---------------------------------------------------------------------------
private data class GradientPreset(val name: String, val colors: List<Color>)

private val GRADIENT_PRESETS = listOf(
    GradientPreset("Dusk", listOf(Color(0xFF4158D0), Color(0xFFC850C0), Color(0xFFFFCC70))),
    GradientPreset("Sunset", listOf(Color(0xFFff9a9e), Color(0xFFfecfef))),
    GradientPreset("Ocean", listOf(Color(0xFF2193b0), Color(0xFF6dd5ed))),
    GradientPreset("Neon", listOf(Color(0xFFa18cd1), Color(0xFFfbc2eb))),
    GradientPreset("Forest", listOf(Color(0xFF134E5E), Color(0xFF71B280))),
)

private enum class BgTab { COLOR, GRADIENT, VIDEO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerSheet(
    currentBackground: BackgroundState? = null,
    onDismissRequest: () -> Unit,
    onBackgroundSelected: (BackgroundState) -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(BgTab.COLOR) }
    var showColorPicker by remember { mutableStateOf(false) }
    var customColorLong by remember {
        val initColor = (currentBackground as? BackgroundState.SolidColor)?.color ?: Color(0xFF0F172A)
        mutableLongStateOf(initColor.toArgb().toLong())
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                onBackgroundSelected(BackgroundState.ImageBitmap(bitmap))
                onDismissRequest()
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onBackgroundSelected(BackgroundState.VideoUri(uri))
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF18181B),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Sheet title
            Text(
                text = "Background",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Tab pills
            TabPillRow(activeTab = activeTab, onTabSelected = { activeTab = it })

            // Tab content
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "bgTabContent"
            ) { tab ->
                when (tab) {
                    BgTab.COLOR -> ColorTab(
                        currentBackground = currentBackground,
                        showColorPicker = showColorPicker,
                        customColorLong = customColorLong,
                        onShowColorPicker = { showColorPicker = !showColorPicker },
                        onCustomColorChanged = { long ->
                            customColorLong = long
                            onBackgroundSelected(BackgroundState.SolidColor(Color(long.toInt())))
                        },
                        onColorSelected = { color ->
                            onBackgroundSelected(BackgroundState.SolidColor(color))
                            onDismissRequest()
                        }
                    )
                    BgTab.GRADIENT -> GradientTab(
                        currentBackground = currentBackground,
                        onGradientSelected = { colors ->
                            onBackgroundSelected(BackgroundState.Gradient(colors))
                            onDismissRequest()
                        }
                    )
                    BgTab.VIDEO -> VideoTab(
                        onPickImage = { imagePickerLauncher.launch("image/*") },
                        onPickVideo = { videoPickerLauncher.launch("video/*") }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab pill selector
// ---------------------------------------------------------------------------

@Composable
private fun TabPillRow(activeTab: BgTab, onTabSelected: (BgTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BgTab.entries.forEach { tab ->
            val isActive = tab == activeTab
            val bg by animateColorAsState(
                targetValue = if (isActive) Color(0xFF6366F1) else Color.Transparent,
                animationSpec = tween(200),
                label = "tabBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                animationSpec = tween(200),
                label = "tabText"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (tab) {
                        BgTab.COLOR -> "Color"
                        BgTab.GRADIENT -> "Gradient"
                        BgTab.VIDEO -> "Video"
                    },
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Color tab — preset swatches + AdvancedColorPicker
// ---------------------------------------------------------------------------

@Composable
private fun ColorTab(
    currentBackground: BackgroundState?,
    showColorPicker: Boolean,
    customColorLong: Long,
    onShowColorPicker: () -> Unit,
    onCustomColorChanged: (Long) -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val activeColor = (currentBackground as? BackgroundState.SolidColor)?.color

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Grid of preset swatches
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp) // ~2 rows
        ) {
            items(CREATOR_COLORS) { color ->
                val isSelected = color == activeColor
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onColorSelected(color) }
                )
            }
            // Custom color tile
            item {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(Color(customColorLong.toInt()))
                        .border(
                            width = if (showColorPicker) 2.dp else 1.dp,
                            color = if (showColorPicker) Color(0xFF6366F1) else Color.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onShowColorPicker() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FeatherIcons.Plus,
                        contentDescription = "Custom color",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Expandable AdvancedColorPicker
        if (showColorPicker) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Custom Color",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                AdvancedColorPicker(
                    initialColor = customColorLong,
                    onColorChanged = { long -> onCustomColorChanged(long) }
                )
                // Preview + apply button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(customColorLong.toInt()))
                    )
                    Button(
                        onClick = { onColorSelected(Color(customColorLong.toInt())) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text("Apply Color", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Gradient tab — 5 curated presets (no custom builder for now)
// ---------------------------------------------------------------------------

@Composable
private fun GradientTab(
    currentBackground: BackgroundState?,
    onGradientSelected: (List<Color>) -> Unit
) {
    val activeColors = (currentBackground as? BackgroundState.Gradient)?.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Tap a gradient to apply it",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
        ) {
            items(GRADIENT_PRESETS) { preset ->
                val isSelected = preset.colors == activeColors
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.6f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(preset.colors))
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                                else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onGradientSelected(preset.colors) }
                    )
                    Text(
                        text = preset.name,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Video tab — image from gallery + video from gallery
// ---------------------------------------------------------------------------

@Composable
private fun VideoTab(
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Import media from your gallery",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Static image background
            MediaPickerCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(FeatherIcons.Image, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(28.dp)) },
                title = "Photo",
                subtitle = "Still image background",
                onClick = onPickImage
            )
            // Looping video background
            MediaPickerCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(FeatherIcons.Film, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(28.dp)) },
                title = "Video",
                subtitle = "Loops behind your voice",
                onClick = onPickVideo
            )
        }
    }
}

@Composable
private fun MediaPickerCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

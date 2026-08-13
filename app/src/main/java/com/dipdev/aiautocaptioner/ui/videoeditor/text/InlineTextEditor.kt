package com.dipdev.aiautocaptioner.ui.videoeditor.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.engine.BundledFonts
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun InlineTextEditor(
    overlay: TextOverlayEntity,
    containerSize: IntSize,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    val canvasWidth = containerSize.width.toFloat()

    var textFieldValue by remember(overlay.id) {
        mutableStateOf(
            TextFieldValue(
                text = overlay.text,
                selection = TextRange(overlay.text.length)
            )
        )
    }

    LaunchedEffect(overlay.text, overlay.id) {
        if (overlay.text != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = overlay.text,
                selection = TextRange(overlay.text.length)
            )
        }
    }

    // Hide the keyboard and clear focus when this editor leaves the composition
    // (i.e. when editing is committed / cancelled).
    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(overlay.id) {
        delay(100.milliseconds)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val typeface = remember(overlay.fontAssetPath) {
        if (overlay.fontAssetPath.isEmpty()) {
            android.graphics.Typeface.DEFAULT
        } else {
            BundledFonts.getAssetTypeface(context, overlay.fontAssetPath) ?: android.graphics.Typeface.DEFAULT
        }
    }

    val fontFamily = remember(typeface) {
        FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
    }

    val align = when (overlay.textAlignment) {
        "LEFT" -> TextAlign.Left
        "RIGHT" -> TextAlign.Right
        else -> TextAlign.Center
    }

    val bgAlpha = when (overlay.backgroundStyle) {
        "NONE" -> 0f
        else -> overlay.backgroundOpacity.coerceIn(0f, 1f)
    }

    val fontPx = textFontPx(overlay.fontSize, canvasWidth)
    val fontSize = density.pxToTextUnit(fontPx)

    val boxWidthPx = textBoxWidthPx(overlay.textWidth ?: DEFAULT_TEXT_WIDTH_FRACTION, canvasWidth)
    val widthMod = with(density) { Modifier.width(boxWidthPx.toDp()) }

    val hp = textHPaddingPx(overlay.fontSize, canvasWidth)
    val vp = textVPaddingPx(overlay.fontSize, canvasWidth)
    val corner = textCornerRadiusPx(overlay.fontSize, canvasWidth)
    val bgPadding = with(density) {
        Modifier.padding(horizontal = hp.toDp(), vertical = vp.toDp())
    }
    val shape = with(density) { RoundedCornerShape(size = corner.toDp()) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                onTextChange(it.text)
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .then(widthMod)
                .wrapContentHeight(),
            textStyle = TextStyle(
                color = Color(overlay.textColorArgb),
                fontSize = fontSize,
                fontFamily = fontFamily,
                textAlign = align,
                platformStyle = PlatformTextStyle(
                    emojiSupportMatch = androidx.compose.ui.text.EmojiSupportMatch.Default
                )
            ),
            cursorBrush = SolidColor(AccentRose),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(overlay.backgroundColorArgb).copy(alpha = bgAlpha),
                            shape = shape
                        )
                        .then(bgPadding),
                    contentAlignment = when (overlay.textAlignment) {
                        "LEFT" -> Alignment.CenterStart
                        "RIGHT" -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                ) {
                    if (overlay.text.isEmpty()) {
                        Text(
                            text = "Type something...",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = fontSize,
                            fontFamily = fontFamily
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun FontStyleCarousel(
    selectedAssetPath: String,
    onFontChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .heightIn(max = 260.dp)
            .background(Color(0xF216171B), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Fonts",
            color = Color.White.copy(alpha = 0.65f),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(BundledFonts.all, key = { it.assetPath + it.displayName }) { font ->
                FontStyleChip(
                    name = font.displayName,
                    assetPath = font.assetPath,
                    isSelected = font.assetPath == selectedAssetPath,
                    onClick = { onFontChange(font.assetPath) }
                )
            }
        }
    }
}

@Composable
private fun FontStyleChip(
    name: String,
    assetPath: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val typeface = remember(assetPath) {
        if (assetPath.isEmpty()) android.graphics.Typeface.DEFAULT
        else BundledFonts.getAssetTypeface(context, assetPath) ?: android.graphics.Typeface.DEFAULT
    }
    val fontFamily = remember(typeface) { FontFamily(androidx.compose.ui.text.font.Typeface(typeface)) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                if (isSelected) AccentRose.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        Text(
            "Aa",
            color = Color.White,
            fontFamily = fontFamily,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            name,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.68f),
            fontFamily = fontFamily,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
        )
    }
}

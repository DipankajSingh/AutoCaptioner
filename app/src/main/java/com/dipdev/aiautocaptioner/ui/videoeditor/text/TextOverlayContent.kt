package com.dipdev.aiautocaptioner.ui.videoeditor.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.engine.BundledFonts

@Composable
fun TextOverlayContent(
    overlay: TextOverlayEntity,
    canvasWidth: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

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

    val widthFraction = overlay.textWidth ?: DEFAULT_TEXT_WIDTH_FRACTION
    val boxWidthPx = textBoxWidthPx(widthFraction, canvasWidth)
    val widthMod = with(density) { if (boxWidthPx > 0f) Modifier.width(boxWidthPx.toDp()) else Modifier }

    val fontSize = with(density) { density.pxToTextUnit(textFontPx(overlay.fontSize, canvasWidth)) }

    val hp = textHPaddingPx(overlay.fontSize, canvasWidth)
    val vp = textVPaddingPx(overlay.fontSize, canvasWidth)
    val corner = textCornerRadiusPx(overlay.fontSize, canvasWidth)
    val bgPadding = with(density) {
        Modifier.padding(horizontal = hp.toDp(), vertical = vp.toDp())
    }
    val shape = with(density) { androidx.compose.foundation.shape.RoundedCornerShape(size = corner.toDp()) }

    Box(
        modifier = modifier
            .then(widthMod)
            .background(
                color = Color(overlay.backgroundColorArgb).copy(alpha = bgAlpha),
                shape = shape
            )
            .then(bgPadding)
    ) {
        Text(
            text = overlay.text,
            color = Color(overlay.textColorArgb),
            fontSize = fontSize,
            fontFamily = fontFamily,
            textAlign = align,
            style = TextStyle(
                platformStyle = PlatformTextStyle(
                    emojiSupportMatch = androidx.compose.ui.text.EmojiSupportMatch.Default
                )
            )
        )
    }
}

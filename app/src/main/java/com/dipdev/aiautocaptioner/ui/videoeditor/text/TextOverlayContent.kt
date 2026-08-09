package com.dipdev.aiautocaptioner.ui.videoeditor.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.engine.BundledFonts

@Composable
fun TextOverlayContent(
    overlay: TextOverlayEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Resolve font from BundledFonts
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

    Box(modifier = modifier.padding(16.dp)) {
        Text(
            text = overlay.text,
            color = Color(overlay.textColorArgb),
            fontFamily = fontFamily,
            fontSize = 32.sp, // Note: Scaled later by graphicsLayer in OverlayRenderer
            textAlign = TextAlign.Center,
            style = TextStyle(
                platformStyle = PlatformTextStyle(
                    emojiSupportMatch = androidx.compose.ui.text.EmojiSupportMatch.Default
                )
            )
        )
    }
}

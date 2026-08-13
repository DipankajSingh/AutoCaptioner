package com.dipdev.aiautocaptioner.engine.style

import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.GradientDirection
import com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode


data class StyleUiCapabilities(
    val showBackgroundStyleControl: Boolean,
    val showBackgroundDetailSliders: Boolean,
    val showKaraokeControls: Boolean,
    val showPerWordAnimations: Boolean,
    val showLayoutSliders: Boolean,
    val showGlowControls: Boolean,
    val showGradientControls: Boolean
)

object StyleCapabilityResolver {
    fun resolve(style: CaptionStyleEntity): StyleUiCapabilities {
        val isWordByWord = style.displayMode == DisplayMode.WORD_BY_WORD
        val isKaraoke = style.displayMode in setOf(DisplayMode.LINE_HIGHLIGHT, DisplayMode.KARAOKE_FILL)
        val isBackgroundHighlight = style.karaokeHighlightMode == KaraokeHighlightMode.BACKGROUND_HIGHLIGHT
        
        val canHaveBackground = !isWordByWord && !isBackgroundHighlight
        
        return StyleUiCapabilities(
            showBackgroundStyleControl = canHaveBackground,
            showBackgroundDetailSliders = canHaveBackground && style.backgroundType != BackgroundType.NONE,
            showKaraokeControls = isKaraoke,
            showPerWordAnimations = isWordByWord || style.displayMode == DisplayMode.TYPEWRITER,
            showLayoutSliders = !isWordByWord,
            showGlowControls = style.glowEnabled,
            showGradientControls = style.gradientDirection != GradientDirection.NONE
        )
    }
}

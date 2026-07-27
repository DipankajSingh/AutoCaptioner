package com.dipdev.aiautocaptioner.engine.style

import com.dipdev.aiautocaptioner.data.db.entity.*
import java.util.UUID

/**
 * Single-responsibility factory for generating caption style presets with
 * cohesive defaults, eliminating verbose configuration boilerplate in repositories.
 */
object PresetFactory {

    fun create(
        name: String,
        fontFamily: String = "Montserrat",
        fontWeight: Int = 700,
        fontSize: Float = 48f,
        textColor: Long = 0xFFFFFFFF,
        highlightColor: Long = 0xFFFFD700,
        outlineColor: Long = 0xFF000000,
        outlineWidth: Float = 3f,
        hasBg: Boolean = false,
        backgroundColor: Long = 0xFF000000,
        isKaraoke: Boolean = false,
        isWordByWord: Boolean = false,
        isTypewriter: Boolean = false,
        positionY: Float = 0.85f,
        customizer: (CaptionStyleEntity) -> CaptionStyleEntity = { it }
    ): CaptionStyleEntity {
        val baseMode = when {
            isKaraoke -> DisplayMode.KARAOKE_FILL
            isWordByWord -> DisplayMode.WORD_BY_WORD
            isTypewriter -> DisplayMode.TYPEWRITER
            else -> DisplayMode.PHRASE
        }

        val maxWords = when {
            isWordByWord -> 1
            isKaraoke -> 4
            isTypewriter -> 6
            else -> 6
        }

        val lines = if (isWordByWord) 1 else 2

        val bgType = if (hasBg) BackgroundType.BOX else BackgroundType.NONE

        val enterAnim = when {
            isTypewriter -> AnimationType.TYPEWRITER
            isWordByWord -> AnimationType.FADE
            else -> AnimationType.NONE
        }

        val exitAnim = when {
            isWordByWord -> AnimationType.FADE
            else -> AnimationType.NONE
        }

        val base = CaptionStyleEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            isDefault = true,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            textColor = textColor,
            highlightColor = highlightColor,
            outlineColor = outlineColor,
            outlineWidth = outlineWidth,
            backgroundType = bgType,
            backgroundColor = backgroundColor,
            displayMode = baseMode,
            wordEnterAnimation = enterAnim,
            wordExitAnimation = exitAnim,
            maxWordsPerLine = maxWords,
            maxLines = lines,
            positionX = 0.5f,
            positionY = positionY
        )
        return customizer(base)
    }
}

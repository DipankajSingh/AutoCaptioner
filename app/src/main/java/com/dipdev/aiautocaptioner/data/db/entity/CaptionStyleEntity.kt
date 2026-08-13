package com.dipdev.aiautocaptioner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caption_styles")
data class CaptionStyleEntity(

    @PrimaryKey
    val id: String,

    val name: String,


    val isDefault: Boolean = false,


    val fontFamily: String = "Montserrat",

    val fontWeight: Int = 700,


    @ColumnInfo(defaultValue = "0.0")
    val textThickness: Float = 0f,


    val fontSize: Float = 48f,

    val isItalic: Boolean = false,

    val letterSpacing: Float = 0f,


    val textColor: Long = 0xFFFFFFFF,


    val highlightColor: Long = 0xFFFFD700,


    val secondaryColor: Long = 0xFFFFFFFF,
    val outlineColor: Long = 0xFF000000,

    val outlineWidth: Float = 3f,

    val shadowColor: Long = 0x80000000,
    val shadowRadius: Float = 4f,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 2f,

    // ---- BACKGROUND ----

    val backgroundType: BackgroundType = BackgroundType.NONE,

    val backgroundColor: Long = 0xFF000000,
    val backgroundOpacity: Float = 0.5f,

    val backgroundCornerRadius: Float = 8f,

    val backgroundPaddingH: Float = 16f,
    val backgroundPaddingV: Float = 8f,


    val positionX: Float = 0.5f,
    val positionY: Float = 0.85f,

    val alignment: TextAlignment = TextAlignment.CENTER,

    val maxWordsPerLine: Int = 5,
    
    val maxLines: Int = 2,
    
    val removePunctuation: Boolean = false,

    val textTransform: TextTransform = TextTransform.NONE,

    val lineHeight: Float = 1.2f,

    val textOpacity: Float = 1f,

    val outlineOnly: Boolean = false,

    val gradientDirection: GradientDirection = GradientDirection.NONE,

    val glowEnabled: Boolean = false,
    val glowColor: Long = 0xFFFFD700,
    val glowRadius: Float = 8f,


    val displayMode: DisplayMode = DisplayMode.WORD_BY_WORD,


    val wordEnterAnimation: AnimationType = AnimationType.FADE,

    val wordExitAnimation: AnimationType = AnimationType.FADE,

    val emphasisAnimation: AnimationType = AnimationType.SCALE_POP,

    val animationDurationMs: Int = 150,


    val karaokeHighlightMode: KaraokeHighlightMode = KaraokeHighlightMode.COLOR_CHANGE,

    val karaokeFillColor: Long = 0xFFFFD700,

    val activeWordBgColor: Long = 0xFFFFC107,

    val activeWordTextColor: Long = 0xFF000000,

    val activeWordCornerRadius: Float = 14f,

    @ColumnInfo(defaultValue = "999")
    val sortOrder: Int = 999
)

enum class BackgroundType {
    NONE,
    BOX,
    PILL,
    FULL_LINE,
    PER_WORD
}

enum class DisplayMode {
    WORD_BY_WORD,
    LINE_HIGHLIGHT,
    KARAOKE_FILL,
    PHRASE,
    TYPEWRITER
}

enum class KaraokeHighlightMode {
    COLOR_CHANGE,
    FILL_LEFT_RIGHT,
    SCALE_UP,
    UNDERLINE,
    BACKGROUND_HIGHLIGHT
}

enum class AnimationType {
    NONE,
    FADE,
    SLIDE_UP,
    SLIDE_DOWN,
    SCALE_POP,
    BOUNCE,
    ELASTIC,
    TYPEWRITER,
    SHAKE,
    FLIP
}

enum class GradientDirection {
    NONE,
    LEFT_RIGHT,
    TOP_BOTTOM,
    DIAGONAL
}

enum class TextTransform {
    NONE,
    UPPERCASE,
    LOWERCASE,
    TITLE_CASE,
    SENTENCE_CASE
}

enum class TextAlignment {
    START,
    CENTER,
    END
}
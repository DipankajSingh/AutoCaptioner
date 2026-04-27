package com.dipdev.autocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caption_styles")
data class CaptionStyleEntity(

    @PrimaryKey
    val id: String,

    // Human readable name shown in the style picker
    // Example: "Bold Pop", "Minimal", "Neon Glow"
    val name: String,

    // true = one of our built-in presets (can't be deleted by user)
    // false = user created/modified style
    val isDefault: Boolean = false,

    // ---- FONT ----

    // Font family name — must match an actual font in the app
    // We'll bundle custom fonts in assets/fonts/
    val fontFamily: String = "Montserrat",

    // CSS-style font weight: 400=Regular, 700=Bold, 900=Black
    val fontWeight: Int = 700,

    // Text size in SP (scale-independent pixels)
    // SP automatically scales with user's system font size setting
    val fontSize: Float = 48f,

    val isItalic: Boolean = false,

    // Extra space between letters — negative = tighter, positive = wider
    val letterSpacing: Float = 0f,

    // ---- COLORS ----
    // Colors stored as Long (ARGB format)
    // Example: 0xFFFFFFFF = fully opaque white
    // We use Long instead of Int because Color values can exceed Int range

    // Main text color (inactive/default words)
    val textColor: Long = 0xFFFFFFFF,

    // Color of the currently active/highlighted word in karaoke mode
    // This is what creates the karaoke effect — active word = different color
    val highlightColor: Long = 0xFFFFD700, // gold by default

    // Optional second color for gradient effects on text
    val secondaryColor: Long = 0xFFFFFFFF,

    // Color of the outline drawn around each letter
    // Outline makes text readable on any background color
    val outlineColor: Long = 0xFF000000,  // black outline

    // Thickness of the outline in pixels
    val outlineWidth: Float = 3f,

    // Shadow behind the text for depth effect
    val shadowColor: Long = 0x80000000,   // semi-transparent black
    val shadowRadius: Float = 4f,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 2f,

    // ---- BACKGROUND ----

    // What kind of background to draw behind the text
    val backgroundType: BackgroundType = BackgroundType.NONE,

    // Background color and transparency
    val backgroundColor: Long = 0xFF000000,
    val backgroundOpacity: Float = 0.5f,  // 0.0 = invisible, 1.0 = fully opaque

    // Rounded corners on the background box
    val backgroundCornerRadius: Float = 8f,

    // Padding between text and background edges
    val backgroundPaddingH: Float = 16f,  // horizontal (left/right)
    val backgroundPaddingV: Float = 8f,   // vertical (top/bottom)

    // ---- POSITION ----

    // Position as fraction of video dimensions (0.0 to 1.0)
    // Example: positionX=0.5, positionY=0.85 = centered, near bottom
    // Using relative values means position works on any video resolution
    val positionX: Float = 0.5f,
    val positionY: Float = 0.85f,

    // Text alignment within the caption block
    val alignment: TextAlignment = TextAlignment.CENTER,

    // Maximum number of words shown per line before wrapping
    val maxWordsPerLine: Int = 5,
    
    // Maximum number of lines shown vertically before cutting off text
    val maxLines: Int = 2,

    // ---- DISPLAY MODE ----

    // How captions are displayed — core behavior of the app
    val displayMode: DisplayMode = DisplayMode.WORD_BY_WORD,

    // ---- ANIMATIONS ----

    // Animation played when a word appears on screen
    val wordEnterAnimation: AnimationType = AnimationType.FADE,

    // Animation played when a word disappears
    val wordExitAnimation: AnimationType = AnimationType.FADE,

    // Animation played when an emphasized word is active
    val emphasisAnimation: AnimationType = AnimationType.SCALE_POP,

    // How long each animation takes in milliseconds
    val animationDurationMs: Int = 150,

    // ---- KARAOKE SPECIFIC ----

    // Visual style of the karaoke highlighting effect
    val karaokeHighlightMode: KaraokeHighlightMode = KaraokeHighlightMode.COLOR_CHANGE,

    // Color that fills the word left-to-right in FILL_LEFT_RIGHT mode
    val karaokeFillColor: Long = 0xFFFFD700 // gold
)

// How the background is drawn behind the caption text
enum class BackgroundType {
    NONE,           // no background — text only
    BOX,            // rectangle behind entire caption block
    PILL,           // rounded rectangle (pill shape) per word or line
    FULL_LINE,      // full-width bar behind each line
    PER_WORD        // individual background behind each word
}

// Core display behavior — how captions appear on screen
enum class DisplayMode {
    WORD_BY_WORD,   // one word appears at a time — classic CapCut style
    LINE_HIGHLIGHT, // full line shown, active word highlighted
    KARAOKE_FILL,   // color fills word from left to right as it's spoken
    PHRASE,         // show entire phrase/segment at once, no word highlighting
    TYPEWRITER      // letters appear one by one as the word is spoken
}

// Style of the karaoke word highlighting effect
enum class KaraokeHighlightMode {
    COLOR_CHANGE,         // active word simply changes to highlight color
    FILL_LEFT_RIGHT,      // color sweeps across the word left to right
    SCALE_UP,             // active word gets slightly bigger
    UNDERLINE,            // underline appears beneath active word
    BACKGROUND_HIGHLIGHT  // background color appears behind active word
}

// Available animations for word enter/exit/emphasis
enum class AnimationType {
    NONE,           // instant appear/disappear
    FADE,           // smooth opacity transition
    SLIDE_UP,       // word slides up from below
    SLIDE_DOWN,     // word slides down from above
    SCALE_POP,      // word pops in from small to normal size
    BOUNCE,         // word bounces as it appears
    ELASTIC,        // word overshoots then settles (spring effect)
    TYPEWRITER,     // letters appear one at a time
    SHAKE,          // word shakes horizontally
    FLIP            // word flips in like a card
}

// Text alignment within the caption block
enum class TextAlignment {
    START,    // left aligned
    CENTER,   // center aligned (default)
    END       // right aligned
}
package com.dipdev.aiautocaptioner.engine

import com.dipdev.aiautocaptioner.data.db.entity.AnimationType
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode

/**
 * Single source of truth for all per-display-mode rules.
 *
 * Encapsulates:
 *  - Which UI controls should be visible for each display mode
 *  - How many words are visible in the display window
 *  - What animations are forced or suppressed per mode
 *  - How past words are treated visually
 *
 * Every `when(displayMode)` check in the codebase should eventually
 * route through this object, making behavior changes one-location edits.
 */
object DisplayModeBehavior {

    // ── UI Control Visibility ──────────────────────────────────────────────

    enum class StyleControl {
        FONT_FAMILY,
        FONT_SIZE,
        FONT_WEIGHT,
        IS_ITALIC,
        LETTER_SPACING,
        MAX_WORDS_PER_LINE,
        MAX_LINES,
        ALIGNMENT,
        LINE_HEIGHT,
        POSITION_X,
        POSITION_Y,
        TEXT_COLOR,
        HIGHLIGHT_COLOR,
        SECONDARY_COLOR,
        OUTLINE_COLOR,
        OUTLINE_WIDTH,
        OUTLINE_ONLY,
        SHADOW,
        GRADIENT,
        GLOW,
        BACKGROUND_TYPE,
        BACKGROUND_COLOR,
        BACKGROUND_PADDING,
        BACKGROUND_OPACITY,
        KARAOKE_HIGHLIGHT_MODE,
        WORD_ENTER_ANIMATION,
        WORD_EXIT_ANIMATION,
        ANIMATION_SPEED,
        TEXT_TRANSFORM,
        REMOVE_PUNCTUATION,
        TEXT_OPACITY
    }

    /**
     * Whether the given [control] should be shown in the UI for [mode].
     *
     * Rules derived from real-world app behavior (CapCut, TikTok, VN):
     *  - WORD_BY_WORD: single word at a time → no multi-word layout controls
     *  - PHRASE: no per-word highlighting → no karaoke controls
     *  - TYPEWRITER: letters accumulate → some controls suppressed
     */
    fun isControlVisible(control: StyleControl, mode: DisplayMode): Boolean = when (control) {

        // ── Layout controls: only relevant for multi-word modes ────────────
        StyleControl.MAX_WORDS_PER_LINE -> mode in setOf(
            DisplayMode.PHRASE,
            DisplayMode.LINE_HIGHLIGHT,
            DisplayMode.KARAOKE_FILL,
            DisplayMode.TYPEWRITER
        )
        StyleControl.MAX_LINES -> mode in setOf(
            DisplayMode.PHRASE,
            DisplayMode.LINE_HIGHLIGHT,
            DisplayMode.KARAOKE_FILL,
            DisplayMode.TYPEWRITER
        )
        StyleControl.ALIGNMENT -> mode != DisplayMode.WORD_BY_WORD
        StyleControl.LINE_HEIGHT -> mode != DisplayMode.WORD_BY_WORD
        StyleControl.POSITION_X -> mode != DisplayMode.WORD_BY_WORD

        // ── Karaoke highlight: only for modes that highlight within a line ──
        StyleControl.KARAOKE_HIGHLIGHT_MODE -> mode in setOf(
            DisplayMode.LINE_HIGHLIGHT,
            DisplayMode.KARAOKE_FILL
        )

        // ── Word animations: only WORD_BY_WORD has per-word enter/exit ─────
        StyleControl.WORD_ENTER_ANIMATION -> mode == DisplayMode.WORD_BY_WORD
        StyleControl.WORD_EXIT_ANIMATION -> mode == DisplayMode.WORD_BY_WORD

        // ── Background: not useful for single-word display ─────────────────
        StyleControl.BACKGROUND_TYPE -> mode != DisplayMode.WORD_BY_WORD
        StyleControl.BACKGROUND_COLOR -> mode != DisplayMode.WORD_BY_WORD
        StyleControl.BACKGROUND_PADDING -> mode != DisplayMode.WORD_BY_WORD
        StyleControl.BACKGROUND_OPACITY -> mode != DisplayMode.WORD_BY_WORD

        // ── Everything else is always visible ──────────────────────────────
        else -> true
    }

    // ── Animation Rules ────────────────────────────────────────────────────

    /**
     * When the user switches to [mode], the enter animation should be forced
     * to this value. Returns null if no forced value (user's choice is kept).
     */
    fun forcedEnterAnimation(mode: DisplayMode): AnimationType? = when (mode) {
        DisplayMode.KARAOKE_FILL -> AnimationType.NONE
        DisplayMode.PHRASE       -> AnimationType.NONE
        DisplayMode.TYPEWRITER   -> AnimationType.TYPEWRITER
        else                     -> null
    }

    /**
     * When the user switches to [mode], the exit animation should be forced
     * to this value. Returns null if no forced value.
     */
    fun forcedExitAnimation(mode: DisplayMode): AnimationType? = when (mode) {
        DisplayMode.KARAOKE_FILL -> AnimationType.NONE
        DisplayMode.PHRASE       -> AnimationType.NONE
        DisplayMode.TYPEWRITER   -> AnimationType.NONE
        DisplayMode.LINE_HIGHLIGHT -> AnimationType.NONE
        else                     -> null
    }

    /**
     * Whether words in this mode have per-word animation applied.
     * false for modes where the entire block appears/disappears as a unit.
     */
    fun hasWordAnimations(mode: DisplayMode): Boolean = when (mode) {
        DisplayMode.WORD_BY_WORD -> true
        DisplayMode.TYPEWRITER   -> true
        else                     -> false
    }

    /**
     * Opacity multiplier for past (spoken) words in [mode].
     *
     *  - PHRASE: CapCut dims past words to ~60% opacity within the visible block
     *  - LINE_HIGHLIGHT / KARAOKE_FILL: past words stay at full opacity (highlight moves forward)
     *  - WORD_BY_WORD: past words are REMOVED (not visible at all)
     *  - TYPEWRITER: past words stay at full opacity (they accumulate)
     */
    fun pastWordOpacity(mode: DisplayMode): Float = when (mode) {
        DisplayMode.PHRASE         -> 0.6f
        DisplayMode.LINE_HIGHLIGHT -> 1.0f
        DisplayMode.KARAOKE_FILL   -> 1.0f
        DisplayMode.WORD_BY_WORD   -> 0f  // not rendered at all
        DisplayMode.TYPEWRITER     -> 1.0f
    }

    /**
     * Default animation duration (ms) for each mode.
     * Faster modes (WORD_BY_WORD pop) need snappy animations.
     * Slower modes (PHRASE fade) can use longer durations.
     */
    fun defaultAnimationDuration(mode: DisplayMode): Int = when (mode) {
        DisplayMode.WORD_BY_WORD -> 150
        DisplayMode.TYPEWRITER   -> 80
        DisplayMode.LINE_HIGHLIGHT -> 200
        DisplayMode.KARAOKE_FILL   -> 200
        DisplayMode.PHRASE         -> 200
    }
}

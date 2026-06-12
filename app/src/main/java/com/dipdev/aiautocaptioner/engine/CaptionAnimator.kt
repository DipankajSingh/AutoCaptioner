package com.dipdev.aiautocaptioner.engine

import com.dipdev.aiautocaptioner.data.db.entity.*
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// CaptionAnimator
//
// Pure animation math — no Canvas, no Paint, no Android framework dependencies.
// Responsible for computing a WordTransform for any word at any point in time.
// ─────────────────────────────────────────────────────────────────────────────
object CaptionAnimator {

    // ── Public data types ─────────────────────────────────────────────────────

    /**
     * A word with its timing info, passed to the animator.
     * Decoupled from [CaptionWordEntity] so the renderer can also
     * construct fallback words when no timestamps exist.
     */
    data class TimedWord(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val isActive: Boolean,
        val isPast: Boolean,
        val isEmphasized: Boolean,
        val emphasisType: EmphasisType
    )

    /**
     * The per-frame transform for a single word.
     * All fields default to identity (no transform) so callers only need to
     * check the fields that actually deviate from normal.
     */
    data class WordTransform(
        val alpha: Float = 1f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val translateX: Float = 0f,
        val translateY: Float = 0f,
        /** 0..1 — fraction of the word's characters/width to reveal (TYPEWRITER) */
        val clipFraction: Float = 1f,
        /** When non-null, overrides the word's fill colour (COLOR_POP emphasis) */
        val colorOverride: Int? = null
    )

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * Compute the [WordTransform] for [word] at [posMs].
     *
     * @param posMs       Current playback position in milliseconds.
     * @param word        The word and its timing info.
     * @param style       Caption style (contains animation type, duration, etc.).
     * @param animMs      Animation duration in ms (pre-clamped by caller).
     */
    fun computeWordTransform(
        posMs: Long,
        word: TimedWord,
        style: CaptionStyleEntity,
        animMs: Long,
    ): WordTransform {
        // Enter progress: 0→1 over [startTimeMs, startTimeMs + animMs]
        val enterRaw = ((posMs - word.startTimeMs).toFloat() / animMs).coerceIn(0f, 1f)
        // Exit progress:  0→1 over [endTimeMs, endTimeMs + animMs]
        val exitRaw  = ((posMs - word.endTimeMs).toFloat() / animMs).coerceIn(0f, 1f)

        val isEntering = !word.isActive && !word.isPast
        val isExiting  = word.isPast && style.displayMode == DisplayMode.WORD_BY_WORD

        val enter = if (isEntering || word.isActive) enterRaw else 1f
        val exit  = if (isExiting) exitRaw else 0f

        // Base enter transform
        val et = applyAnim(style.wordEnterAnimation, enter, entering = true)
        // Base exit transform (evaluate backward so 1f = fully visible, 0f = exited)
        val xt = if (isExiting) applyAnim(style.wordExitAnimation, 1f - exit, entering = false) else RawTransform()

        val alpha  = et.alpha * (if (isExiting) {
            if (style.wordExitAnimation == AnimationType.NONE) (1f - AnimationUtils.easeInCubic(exit)).coerceIn(0f, 1f) else xt.alpha
        } else 1f)
        var scaleX = et.scaleX * (if (isExiting) xt.scaleX else 1f)
        var scaleY = et.scaleY * (if (isExiting) xt.scaleY else 1f)
        var tx     = et.translateX + (if (isExiting) xt.translateX else 0f)
        var ty     = et.translateY + (if (isExiting) xt.translateY else 0f)
        val clip   = et.clipFraction

        // KARAOKE SCALE_UP — boost scale on the active word
        if (word.isActive && style.karaokeHighlightMode == KaraokeHighlightMode.SCALE_UP) {
            scaleX *= 1.12f
            scaleY *= 1.12f
        }

        // Emphasis oscillations (continuous sin wave while the word is being spoken)
        var colorOverride: Int? = null
        if (word.isActive && word.isEmphasized) {
            val phase = (posMs % 600L).toFloat() / 600f * 2f * PI.toFloat()
            when (word.emphasisType) {
                EmphasisType.BOUNCE    -> ty -= sin(phase) * 12f
                EmphasisType.SCALE     -> { val s = 1f + 0.12f * sin(phase); scaleX *= s; scaleY *= s }
                EmphasisType.SHAKE     -> tx += sin(phase * 3f) * 8f
                EmphasisType.COLOR_POP -> colorOverride = AnimationUtils.blendColor(
                    style.textColor.toInt(), style.highlightColor.toInt(),
                    (sin(phase) + 1f) / 2f
                )
                EmphasisType.NONE -> {}
            }
        }

        return WordTransform(
            alpha         = alpha.coerceIn(0f, 1f),
            scaleX        = scaleX.coerceAtLeast(0f),
            scaleY        = scaleY.coerceAtLeast(0f),
            translateX    = tx,
            translateY    = ty,
            clipFraction  = clip,
            colorOverride = colorOverride
        )
    }

    // ── Internal animation type resolver ──────────────────────────────────────

    private data class RawTransform(
        val alpha: Float = 1f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val translateX: Float = 0f,
        val translateY: Float = 0f,
        val clipFraction: Float = 1f
    )

    private fun applyAnim(type: AnimationType, p: Float, entering: Boolean): RawTransform {
        val e = AnimationUtils.easeOutCubic(p)
        val dir = if (entering) 1f else -1f
        return when (type) {
            AnimationType.NONE       -> RawTransform()
            AnimationType.FADE       -> RawTransform(alpha = e)
            AnimationType.SLIDE_UP   -> RawTransform(alpha = e, translateY = (1f - e) * 40f * dir)
            AnimationType.SLIDE_DOWN -> RawTransform(alpha = e, translateY = -(1f - e) * 40f * dir)
            AnimationType.SCALE_POP  -> RawTransform(alpha = e, scaleX = e.coerceAtLeast(0.01f), scaleY = e.coerceAtLeast(0.01f))
            AnimationType.BOUNCE     -> {
                val s = AnimationUtils.bounceOut(p).coerceAtLeast(0.01f)
                RawTransform(alpha = if (p > 0.05f) 1f else p * 20f, scaleX = s, scaleY = s)
            }
            AnimationType.ELASTIC    -> {
                val s = AnimationUtils.elasticOut(p).coerceAtLeast(0.01f)
                RawTransform(alpha = if (p > 0.05f) 1f else p * 20f, scaleX = s, scaleY = s)
            }
            AnimationType.TYPEWRITER -> RawTransform(clipFraction = p)
            AnimationType.SHAKE      -> RawTransform(alpha = e, translateX = sin(p * PI.toFloat() * 5f) * (1f - p) * 20f)
            AnimationType.FLIP       -> {
                val sx = abs(cos(p * PI.toFloat())).coerceAtLeast(0.01f)
                RawTransform(alpha = if (p > 0.5f) 1f else p * 2f, scaleX = sx)
            }
        }
    }

    // ── Visibility filter ─────────────────────────────────────────────────────

    /**
     * Returns which [words] should be included in the draw pass for [displayMode].
     * WORD_BY_WORD additionally includes words in their enter/exit animation window.
     */
    fun filterVisible(
        words: List<TimedWord>,
        displayMode: DisplayMode,
        posMs: Long,
        animMs: Long
    ): List<TimedWord> = when (displayMode) {
        DisplayMode.PHRASE,
        DisplayMode.LINE_HIGHLIGHT,
        DisplayMode.KARAOKE_FILL  -> words
        DisplayMode.TYPEWRITER    -> words.filter { it.startTimeMs <= posMs + animMs }
        DisplayMode.WORD_BY_WORD  -> words.filter {
            it.isActive ||
            (it.isPast && (posMs - it.endTimeMs) < animMs) ||
            (!it.isPast && (it.startTimeMs - posMs) < animMs)
        }
    }
}

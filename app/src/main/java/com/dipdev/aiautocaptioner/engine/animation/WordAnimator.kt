package com.dipdev.aiautocaptioner.engine.animation

import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle

/**
 * Transform result for a word at a specific point in time.
 * Drives all visual modifications: position, scale, opacity, color.
 */
data class WordTransform(
    val alpha: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    /** 0..1 — fraction of characters to reveal (TYPEWRITER). */
    val clipFraction: Float = 1f,
    /** When non-null, overrides the word's fill color (COLOR_POP emphasis). */
    val colorOverride: Int? = null
)

/**
 * Interface for per-word animation evaluators.
 *
 * Each implementation computes how a word should look at a given point
 * in its lifecycle (entering, active, exiting). New animations are added
 * by implementing this interface — no changes to core rendering code.
 *
 * Implementations must be deterministic: given identical inputs, the output
 * must be identical. Never use System.currentTimeMillis().
 */
interface WordAnimator {
    /** Unique string ID (used in CaptionStyleEntity and JSON configs). */
    val id: String

    /**
     * Compute the transform for a word at the given lifecycle progress.
     *
     * @param progress  0..1 progress through the animation phase
     *                  (0 = start of enter/exit, 1 = fully entered/exited)
     * @param lifecycle Which phase the word is in
     * @param baseScale videoHeight / 1920f — scales dp-like values to px
     */
    fun computeTransform(
        progress: Float,
        lifecycle: WordLifecycle,
        baseScale: Float
    ): WordTransform
}

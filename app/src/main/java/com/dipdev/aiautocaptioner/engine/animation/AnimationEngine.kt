package com.dipdev.aiautocaptioner.engine.animation

import com.dipdev.aiautocaptioner.data.db.entity.AnimationType
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType
import com.dipdev.aiautocaptioner.engine.AnimationUtils
import com.dipdev.aiautocaptioner.engine.timing.TimingEngine
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle
import com.dipdev.aiautocaptioner.engine.timing.WordState
import com.dipdev.aiautocaptioner.engine.animation.animators.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Registry + computation engine for per-word animations.
 *
 * Maps AnimationType enum values to WordAnimator implementations.
 * New animations are added by:
 *   1. Creating a WordAnimator implementation
 *   2. Registering it in the init block below
 *
 * No changes to CaptionRenderer or any other core code needed.
 */
object AnimationEngine {

    private val registry = mutableMapOf<String, WordAnimator>()

    init {
        register(NoneAnimator())
        register(FadeAnimator())
        register(SlideUpAnimator())
        register(SlideDownAnimator())
        register(ScalePopAnimator())
        register(BounceAnimator())
        register(ElasticAnimator())
        register(TypeWriterAnimator())
        register(ShakeAnimator())
        register(FlipAnimator())
        register(SpringPopAnimator())
    }

    fun register(animator: WordAnimator) {
        registry[animator.id] = animator
    }

    fun get(id: String): WordAnimator = registry[id]
        ?: registry["none"]
        ?: throw IllegalStateException("NoneAnimator not registered")

    /**
     * Resolve the WordAnimator for an [AnimationType] enum value.
     */
    fun resolve(type: AnimationType): WordAnimator = when (type) {
        AnimationType.NONE       -> get("none")
        AnimationType.FADE       -> get("fade")
        AnimationType.SLIDE_UP   -> get("slide_up")
        AnimationType.SLIDE_DOWN -> get("slide_down")
        AnimationType.SCALE_POP  -> get("scale_pop")
        AnimationType.BOUNCE     -> get("bounce")
        AnimationType.ELASTIC    -> get("elastic")
        AnimationType.TYPEWRITER -> get("typewriter")
        AnimationType.SHAKE      -> get("shake")
        AnimationType.FLIP       -> get("flip")
    }

    /**
     * Compute the final WordTransform for a word at the current playback position.
     *
     * Combines:
     *  1. Enter animation (progress 0→1 as word appears)
     *  2. Exit animation (progress 0→1 as word disappears, WORD_BY_WORD only)
     *  3. Karaoke scale-up for active word (SCALE_UP highlight mode)
     *  4. Emphasis oscillations (BOUNCE, SCALE, SHAKE, COLOR_POP)
     *
     * This replaces the old computeWordTransform in CaptionAnimator.
     */
    fun computeWordTransform(
        posMs: Long,
        word: WordState,
        style: CaptionStyleEntity,
        animMs: Long,
        baseScale: Float
    ): WordTransform {
        val wordDurationMs = word.endTimeMs - word.startTimeMs
        val effectiveExitOverlap = TimingEngine.calculateExitOverlap(wordDurationMs, animMs)

        // Enter progress: 0→1 over [startTimeMs, startTimeMs + animMs]
        val enterRaw = ((posMs - word.startTimeMs).toFloat() / animMs).coerceIn(0f, 1f)
        // Exit progress: 0→1 over [endTimeMs, endTimeMs + effectiveExitOverlap]
        val exitRaw = ((posMs - word.endTimeMs).toFloat() / effectiveExitOverlap).coerceIn(0f, 1f)

        val isEntering = word.lifecycle == WordLifecycle.ENTERING || word.lifecycle == WordLifecycle.UPCOMING
        val isExiting = word.lifecycle == WordLifecycle.EXITING

        val enter = if (isEntering || word.lifecycle == WordLifecycle.ACTIVE) enterRaw else 1f
        val exit = if (isExiting) exitRaw else 0f

        // Compute enter transform
        val enterAnimator = resolve(style.wordEnterAnimation)
        val et = enterAnimator.computeTransform(enter, WordLifecycle.ENTERING, baseScale)

        // Compute exit transform (only for WORD_BY_WORD mode)
        val xt = if (isExiting) {
            val exitAnimator = resolve(style.wordExitAnimation)
            exitAnimator.computeTransform(1f - exit, WordLifecycle.EXITING, baseScale)
        } else {
            WordTransform()
        }

        // Combine enter + exit
        val alpha = et.alpha * (if (isExiting) {
            if (style.wordExitAnimation == AnimationType.NONE) {
                (1f - AnimationUtils.easeInCubic(exit)).coerceIn(0f, 1f)
            } else {
                xt.alpha
            }
        } else 1f)

        var scaleX = et.scaleX * (if (isExiting) xt.scaleX else 1f)
        var scaleY = et.scaleY * (if (isExiting) xt.scaleY else 1f)
        var tx = et.translateX + (if (isExiting) xt.translateX else 0f)
        var ty = et.translateY + (if (isExiting) xt.translateY else 0f)
        var clip = et.clipFraction

        // Typewriter: override clipFraction for ACTIVE lifecycle
        // to reveal letters progressively over the word's speaking duration
        if (word.lifecycle == WordLifecycle.ACTIVE &&
            (style.displayMode == DisplayMode.TYPEWRITER || style.wordEnterAnimation == AnimationType.TYPEWRITER)
        ) {
            val wordDur = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)
            clip = ((posMs - word.startTimeMs).toFloat() / wordDur).coerceIn(0f, 1f)
        }

        // Karaoke dynamic word emphasis (TikTok / CapCut kinetic pop style)
        // Strictly guarded to karaoke and line highlight modes so other presets are 100% untouched
        if (word.lifecycle == WordLifecycle.ACTIVE &&
            (style.displayMode == DisplayMode.KARAOKE_FILL || style.displayMode == DisplayMode.LINE_HIGHLIGHT)
        ) {
            when (style.karaokeHighlightMode) {
                com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode.SCALE_UP -> {
                    scaleX *= 1.15f
                    scaleY *= 1.15f
                }
                com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode.FILL_LEFT_RIGHT -> {
                    if (style.displayMode == DisplayMode.KARAOKE_FILL) {
                        // Kinetic pop curve: gives an organic scale bounce as the highlight sweeps across
                        val wordDur = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)
                        val progress = ((posMs - word.startTimeMs).toFloat() / wordDur).coerceIn(0f, 1f)
                        val pop = 1f + 0.08f * kotlin.math.sin(progress * Math.PI.toFloat())
                        scaleX *= pop
                        scaleY *= pop
                    }
                }
                else -> {}
            }
        }

        // Emphasis oscillations
        var colorOverride: Int? = null
        if (word.lifecycle == WordLifecycle.ACTIVE && word.isEmphasized) {
            val phase = (posMs % 600L).toFloat() / 600f * 2f * PI.toFloat()
            when (word.emphasisType) {
                EmphasisType.BOUNCE -> ty -= sin(phase) * 12f * baseScale
                EmphasisType.SCALE -> {
                    val s = 1f + 0.12f * sin(phase)
                    scaleX *= s; scaleY *= s
                }
                EmphasisType.SHAKE -> tx += sin(phase * 3f) * 8f * baseScale
                EmphasisType.COLOR_POP -> colorOverride = AnimationUtils.blendColor(
                    style.textColor.toInt(), style.highlightColor.toInt(),
                    (sin(phase) + 1f) / 2f
                )
                EmphasisType.NONE -> {}
            }
        }

        return WordTransform(
            alpha = alpha.coerceIn(0f, 1f),
            scaleX = scaleX.coerceAtLeast(0f),
            scaleY = scaleY.coerceAtLeast(0f),
            translateX = tx,
            translateY = ty,
            clipFraction = clip,
            colorOverride = colorOverride
        )
    }

}

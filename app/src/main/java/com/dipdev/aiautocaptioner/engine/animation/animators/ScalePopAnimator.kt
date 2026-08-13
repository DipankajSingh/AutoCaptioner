package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle
import com.dipdev.aiautocaptioner.engine.AnimationUtils

/**
 * Scale pop using ease-out cubic (simpler than spring, faster).
 * Word grows from small to full size with a pop feel.
 */
class ScalePopAnimator : WordAnimator {
    override val id = "scale_pop"

    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        val e = AnimationUtils.easeOutCubic(progress)
        val s = e.coerceAtLeast(0.01f)
        return when (lifecycle) {
            WordLifecycle.ENTERING -> WordTransform(
                alpha = e,
                scaleX = s,
                scaleY = s
            )
            WordLifecycle.EXITING -> {
                val exitE = AnimationUtils.easeInCubic(1f - progress)
                WordTransform(
                    alpha = exitE,
                    scaleX = exitE.coerceAtLeast(0.01f),
                    scaleY = exitE.coerceAtLeast(0.01f)
                )
            }
            else -> WordTransform()
        }
    }
}

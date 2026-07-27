package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle
import kotlin.math.PI
import kotlin.math.sin

/** Word slides up from below. */
class SlideUpAnimator : WordAnimator {
    override val id = "slide_up"

    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        val e = com.dipdev.aiautocaptioner.engine.AnimationUtils.easeOutCubic(progress)
        return when (lifecycle) {
            WordLifecycle.ENTERING -> WordTransform(
                alpha = e,
                translateY = (1f - e) * 40f * baseScale
            )
            WordLifecycle.EXITING -> {
                val exitE = com.dipdev.aiautocaptioner.engine.AnimationUtils.easeInCubic(1f - progress)
                WordTransform(
                    alpha = exitE,
                    translateY = -(1f - exitE) * 40f * baseScale
                )
            }
            else -> WordTransform()
        }
    }
}

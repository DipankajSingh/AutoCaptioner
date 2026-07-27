package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle
import com.dipdev.aiautocaptioner.engine.AnimationUtils

/** Word slides down from above. */
class SlideDownAnimator : WordAnimator {
    override val id = "slide_down"

    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        val e = AnimationUtils.easeOutCubic(progress)
        return when (lifecycle) {
            WordLifecycle.ENTERING -> WordTransform(
                alpha = e,
                translateY = -(1f - e) * 40f * baseScale
            )
            WordLifecycle.EXITING -> {
                val exitE = AnimationUtils.easeInCubic(1f - progress)
                WordTransform(
                    alpha = exitE,
                    translateY = (1f - exitE) * 40f * baseScale
                )
            }
            else -> WordTransform()
        }
    }
}

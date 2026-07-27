package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle
import com.dipdev.aiautocaptioner.engine.AnimationUtils
import kotlin.math.PI
import kotlin.math.sin

/** Word shakes horizontally with damped oscillation. */
class ShakeAnimator : WordAnimator {
    override val id = "shake"

    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        val e = AnimationUtils.easeOutCubic(progress)
        return when (lifecycle) {
            WordLifecycle.ENTERING -> WordTransform(
                alpha = e,
                translateX = sin(progress * PI.toFloat() * 5f) * (1f - progress) * 20f * baseScale
            )
            WordLifecycle.EXITING -> {
                val exitP = 1f - progress
                val exitE = AnimationUtils.easeOutCubic(exitP)
                WordTransform(
                    alpha = exitE,
                    translateX = sin(exitP * PI.toFloat() * 5f) * exitP * 20f * baseScale
                )
            }
            else -> WordTransform()
        }
    }
}

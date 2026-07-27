package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle
import com.dipdev.aiautocaptioner.engine.AnimationUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Word flips in like a card (horizontal scale via cosine). */
class FlipAnimator : WordAnimator {
    override val id = "flip"

    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        return when (lifecycle) {
            WordLifecycle.ENTERING -> {
                val sx = abs(cos(progress * PI.toFloat())).coerceAtLeast(0.01f)
                val a = if (progress > 0.5f) 1f else progress * 2f
                WordTransform(alpha = a, scaleX = sx)
            }
            WordLifecycle.EXITING -> {
                val exitP = 1f - progress
                val sx = abs(cos(exitP * PI.toFloat())).coerceAtLeast(0.01f)
                WordTransform(alpha = exitP, scaleX = sx)
            }
            else -> WordTransform()
        }
    }
}

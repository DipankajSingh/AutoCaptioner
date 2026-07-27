package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle
import com.dipdev.aiautocaptioner.engine.AnimationUtils

/** Word overshoots then settles — spring effect. */
class ElasticAnimator : WordAnimator {
    override val id = "elastic"

    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        return when (lifecycle) {
            WordLifecycle.ENTERING -> {
                val s = AnimationUtils.elasticOut(progress).coerceAtLeast(0.01f)
                val a = if (progress > 0.05f) 1f else progress * 20f
                WordTransform(alpha = a, scaleX = s, scaleY = s)
            }
            WordLifecycle.EXITING -> {
                val exitP = 1f - progress
                val s = AnimationUtils.elasticOut(exitP).coerceAtLeast(0.01f)
                WordTransform(alpha = exitP, scaleX = s, scaleY = s)
            }
            else -> WordTransform()
        }
    }
}

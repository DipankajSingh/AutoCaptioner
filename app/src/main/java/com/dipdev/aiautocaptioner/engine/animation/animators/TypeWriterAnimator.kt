package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle

/** Letters reveal one at a time (clipFraction drives rendering). */
class TypeWriterAnimator : WordAnimator {
    override val id = "typewriter"

    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        return when (lifecycle) {
            WordLifecycle.ENTERING -> WordTransform(clipFraction = progress)
            WordLifecycle.ACTIVE   -> WordTransform(clipFraction = 1f)
            WordLifecycle.EXITING  -> WordTransform(clipFraction = 1f - progress)
            else -> WordTransform(clipFraction = 0f)
        }
    }
}

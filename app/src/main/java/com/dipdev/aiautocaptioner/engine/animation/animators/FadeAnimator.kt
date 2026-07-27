package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle
import com.dipdev.aiautocaptioner.engine.AnimationUtils

/** Smooth opacity transition. */
class FadeAnimator : WordAnimator {
    override val id = "fade"
    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        val alpha = AnimationUtils.easeOutCubic(progress)
        return WordTransform(alpha = alpha)
    }
}

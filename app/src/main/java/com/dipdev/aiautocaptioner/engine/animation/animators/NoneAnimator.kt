package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle

/** No animation — instant appear/disappear. */
class NoneAnimator : WordAnimator {
    override val id = "none"
    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float) =
        WordTransform()
}

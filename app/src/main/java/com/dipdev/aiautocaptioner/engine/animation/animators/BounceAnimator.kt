package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.SpringUtils
import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle
import com.dipdev.aiautocaptioner.engine.AnimationUtils

/**
 * Bounce animation — spring with lower stiffness and damping for visible oscillation.
 * Matches CapCut's "Bounce" preset and TikTok's signature word pop.
 *
 * Uses damped spring for natural motion instead of the old sine-based approach.
 */
class BounceAnimator : WordAnimator {
    override val id = "bounce"

    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        return when (lifecycle) {
            WordLifecycle.ENTERING -> {
                val s = SpringUtils.scale(fromScale = 0.6f, stiffness = 180f, damping = 12f, t = progress)
                val a = if (progress > 0.05f) 1f else progress * 20f
                WordTransform(alpha = a, scaleX = s, scaleY = s)
            }
            WordLifecycle.EXITING -> {
                val exitProgress = 1f - progress
                val s = AnimationUtils.easeOutCubic(exitProgress).coerceAtLeast(0.01f)
                WordTransform(alpha = exitProgress, scaleX = s, scaleY = s)
            }
            else -> WordTransform()
        }
    }
}

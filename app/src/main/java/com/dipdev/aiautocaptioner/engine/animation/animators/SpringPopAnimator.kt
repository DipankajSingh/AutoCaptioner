package com.dipdev.aiautocaptioner.engine.animation.animators

import com.dipdev.aiautocaptioner.engine.animation.SpringUtils
import com.dipdev.aiautocaptioner.engine.animation.WordAnimator
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle

/**
 * Spring-based pop-in — the signature CapCut/TikTok animation.
 *
 * Word scales from [fromScale] to 1.0 using damped spring physics.
 * Matches the "Pop" and "Bounce" animations in CapCut's dynamic captions.
 *
 * @param fromScale Starting scale (0 = invisible, 0.8 = slight pop feel)
 * @param stiffness Spring stiffness — higher = snappier (default 280)
 * @param damping   Friction — higher = less bounce (default 18)
 */
class SpringPopAnimator(
    private val fromScale: Float = 0.8f,
    private val stiffness: Float = 280f,
    private val damping: Float = 18f
) : WordAnimator {
    override val id = "spring_pop"

    override fun computeTransform(progress: Float, lifecycle: WordLifecycle, baseScale: Float): WordTransform {
        return when (lifecycle) {
            WordLifecycle.ENTERING -> {
                val s = SpringUtils.scale(fromScale, stiffness, damping, progress)
                val a = SpringUtils.alpha(progress)
                WordTransform(scaleX = s, scaleY = s, alpha = a)
            }
            WordLifecycle.EXITING -> {
                val exitProgress = 1f - progress
                val s = SpringUtils.scale(fromScale, stiffness, damping, exitProgress)
                WordTransform(scaleX = s, scaleY = s, alpha = exitProgress)
            }
            else -> WordTransform()
        }
    }
}

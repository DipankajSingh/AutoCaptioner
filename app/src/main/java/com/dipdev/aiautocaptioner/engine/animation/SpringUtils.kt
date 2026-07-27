package com.dipdev.aiautocaptioner.engine.animation

import kotlin.math.*

/**
 * Damped spring physics for natural motion.
 *
 * Models a mass-spring-damper system:
 *   F = -kx - cv
 * where k = stiffness, c = damping, x = displacement, v = velocity.
 *
 * Used for the "pop" / "bounce" animations that CapCut and TikTok use.
 */
object SpringUtils {

    /**
     * Damped spring position at normalized time t (0..1).
     *
     * Returns the position of a mass on a spring, starting displaced
     * and settling at 1.0. The curve overshoots for underdamped systems
     * (low damping) and approaches monotonically for overdamped systems.
     *
     * @param stiffness Spring constant — higher = snappier, faster oscillation.
     *   Typical range: 100–400. CapCut "Pop" ≈ 280, "Bounce" ≈ 180.
     * @param damping   Friction coefficient — higher = less overshoot.
     *   Typical range: 10–30. Underdamped when damping < 2*sqrt(stiffness).
     * @param t         Normalized time 0..1 (mapped from raw time by caller).
     */
    fun position(stiffness: Float = 280f, damping: Float = 18f, t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f

        val omega = sqrt(stiffness)
        val zeta = damping / (2f * omega)

        return when {
            // Underdamped — oscillates (most presets use this)
            zeta < 1f -> {
                val omegaD = omega * sqrt(1f - zeta * zeta)
                val decay = exp(-zeta * omega * t)
                1f - decay * (cos(omegaD * t) + (zeta * omega / omegaD) * sin(omegaD * t))
            }
            // Critically damped — fastest non-oscillating approach
            zeta == 1f -> {
                val decay = exp(-omega * t)
                1f - decay * (1f + omega * t)
            }
            // Overdamped — slow approach, no overshoot
            else -> {
                val r1 = -omega * (zeta + sqrt(zeta * zeta - 1f))
                val r2 = -omega * (zeta - sqrt(zeta * zeta - 1f))
                val a = r2 / (r2 - r1)
                val b = r1 / (r1 - r2)
                1f - a * exp(r1 * t) - b * exp(r2 * t)
            }
        }
    }

    /**
     * Convenience: spring scale factor (starts at [fromScale], settles at 1.0).
     */
    fun scale(
        fromScale: Float = 0.8f,
        stiffness: Float = 280f,
        damping: Float = 18f,
        t: Float
    ): Float {
        return fromScale + (1f - fromScale) * position(stiffness, damping, t)
    }

    /**
     * Convenience: spring alpha (fades in quickly, settles at 1.0).
     * Uses a faster spring so the word is mostly visible early in the animation.
     */
    fun alpha(t: Float): Float {
        return position(stiffness = 400f, damping = 24f, t).coerceIn(0f, 1f)
    }
}

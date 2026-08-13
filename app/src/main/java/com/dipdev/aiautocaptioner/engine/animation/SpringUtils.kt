package com.dipdev.aiautocaptioner.engine.animation

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt


object SpringUtils {


    fun position(stiffness: Float = 280f, damping: Float = 18f, t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f

        val omega = sqrt(stiffness)
        val zeta = damping / (2f * omega)

        return when {
            zeta < 1f -> {
                val omegaD = omega * sqrt(1f - zeta * zeta)
                val decay = exp(-zeta * omega * t)
                1f - decay * (cos(omegaD * t) + (zeta * omega / omegaD) * sin(omegaD * t))
            }
            zeta == 1f -> {
                val decay = exp(-omega * t)
                1f - decay * (1f + omega * t)
            }
            else -> {
                val r1 = -omega * (zeta + sqrt(zeta * zeta - 1f))
                val r2 = -omega * (zeta - sqrt(zeta * zeta - 1f))
                val a = r2 / (r2 - r1)
                val b = r1 / (r1 - r2)
                1f - a * exp(r1 * t) - b * exp(r2 * t)
            }
        }
    }

    fun scale(
        fromScale: Float = 0.8f,
        stiffness: Float = 280f,
        damping: Float = 18f,
        t: Float
    ): Float {
        return fromScale + (1f - fromScale) * position(stiffness, damping, t)
    }

    fun alpha(t: Float): Float {
        return position(stiffness = 400f, damping = 24f, t).coerceIn(0f, 1f)
    }
}

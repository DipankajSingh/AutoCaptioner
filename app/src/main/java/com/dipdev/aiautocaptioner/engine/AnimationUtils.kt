package com.dipdev.aiautocaptioner.engine

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

object AnimationUtils {

    fun easeOutCubic(t: Float): Float {
        val c = 1f - t
        return 1f - c * c * c
    }

    fun easeInCubic(t: Float): Float = t * t * t

    fun elasticOut(t: Float): Float {
        if (t == 0f || t == 1f) return t
        val p = 0.3f
        return 2f.pow(-10f * t) * sin((t - p / 4f) * (2f * PI.toFloat()) / p) + 1f
    }

    /**
     * Linearly interpolate between two ARGB colours.
     * @param t 0f = c1, 1f = c2
     */
    fun blendColor(c1: Int, c2: Int, t: Float): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun lerp(a: Int, b: Int) = (a + (b - a) * t).toInt().coerceIn(0, 255)
        return (lerp(ch(c1, 24), ch(c2, 24)) shl 24) or
                (lerp(ch(c1, 16), ch(c2, 16)) shl 16) or
                (lerp(ch(c1, 8), ch(c2, 8)) shl 8) or
                lerp(ch(c1, 0), ch(c2, 0))
    }
}

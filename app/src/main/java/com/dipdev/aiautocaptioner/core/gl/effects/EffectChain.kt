package com.dipdev.aiautocaptioner.core.gl.effects

/**
 * Ordered chain of [GlEffect] passes. Empty today by design — this feature ships with no
 * filters/effects. When one is added later, it's appended here; [RendererThread]'s draw
 * loop already calls through this chain (currently a no-op pass-through when [effects] is
 * empty), so no capture, encoding, or threading code needs to change.
 */
class EffectChain {
    private val effects = mutableListOf<GlEffect>()

    fun add(effect: GlEffect) {
        effect.onCreate()
        effects.add(effect)
    }

    val isEmpty: Boolean get() = effects.isEmpty()

    fun release() {
        effects.forEach { it.onRelease() }
        effects.clear()
    }
}

package com.dipdev.aiautocaptioner.core.gl.effects

/**
 * A single GL post-processing pass, applied after [com.dipdev.aiautocaptioner.core.gl.GlFrameRenderer]
 * has produced an oriented, cropped frame. This interface does not exist to be used yet —
 * it exists so that when effects are added later, they plug into [EffectChain] without
 * touching [com.dipdev.aiautocaptioner.core.camera.Camera2Session],
 * [com.dipdev.aiautocaptioner.core.gl.RendererThread], or any capture/recording code.
 *
 * Intentionally unimplemented today — no effect exists yet, and [EffectChain] is empty.
 */
interface GlEffect {
    /** One-time GL resource setup (compiling shaders, allocating textures). Called with a current EGL context. */
    fun onCreate()

    /**
     * Applies this effect. Implementations read from [inputTextureId] (a regular 2D texture,
     * not OES — the OES-to-2D conversion happens once in the chain's first pass) and render
     * into whatever framebuffer/surface is currently bound.
     */
    fun draw(inputTextureId: Int, viewportWidth: Int, viewportHeight: Int)

    fun onRelease()
}

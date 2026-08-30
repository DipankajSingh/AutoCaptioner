package com.dipdev.aiautocaptioner.core.gl

import android.view.Surface

/**
 * A renderable destination: either the live preview (TextureView) or, from stage 2 onward,
 * a MediaCodec encoder's input surface. Both are "just a Surface" from EGL's point of view,
 * which is exactly why the same draw call in [RendererThread] can target either one.
 *
 * [ownsSurface] controls whether [release] also calls `surface.release()`. Pass true for a
 * Surface we constructed ourselves (e.g. wrapping a SurfaceTexture) and false for one we
 * don't own the lifecycle of (a MediaCodec encoder releases its own input surface).
 */
class WindowSurface(
    eglCore: EglCore,
    private val surface: Surface,
    private val ownsSurface: Boolean
) {
    private val eglCoreRef = eglCore
    private val eglSurface = eglCore.createWindowSurface(surface)

    fun makeCurrent() = eglCoreRef.makeCurrent(eglSurface)

    fun swapBuffers(): Boolean = eglCoreRef.swapBuffers(eglSurface)

    fun setPresentationTime(nanos: Long) = eglCoreRef.setPresentationTime(eglSurface, nanos)

    fun release() {
        eglCoreRef.releaseSurface(eglSurface)
        if (ownsSurface) surface.release()
    }
}

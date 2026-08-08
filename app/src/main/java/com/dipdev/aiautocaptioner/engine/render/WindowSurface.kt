package com.dipdev.aiautocaptioner.engine.render

import android.opengl.EGLSurface
import android.view.Surface

/**
 * Recordable EGL window surface.
 */
class WindowSurface(
    private val eglCore: EglCore,
    private var surface: Surface?,
    private val releaseSurface: Boolean
) {
    private var eglSurface: EGLSurface? = null

    init {
        createWindowSurface(surface!!)
    }

    private fun createWindowSurface(surface: Any) {
        check(eglSurface == null) { "surface already created" }
        eglSurface = eglCore.createWindowSurface(surface)
    }

    /**
     * Releases any resources associated with the EGL surface (and, if configured to do so,
     * with the Surface as well).
     *
     * Does not require that the surface's EGL context be current.
     */
    fun release() {
        if (eglSurface != null) {
            eglCore.releaseSurface(eglSurface!!)
            eglSurface = null
        }
        if (surface != null) {
            if (releaseSurface) {
                surface!!.release()
            }
            surface = null
        }
    }

    /**
     * Makes our EGL context and surface current.
     */
    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface!!)
    }

    /**
     * Calls eglSwapBuffers. Use this to "publish" the current frame.
     */
    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface!!)
    }

    /**
     * Sends the presentation time stamp to EGL. Time is expressed in nanoseconds.
     */
    fun setPresentationTime(nsecs: Long) {
        eglCore.setPresentationTime(eglSurface!!, nsecs)
    }
}

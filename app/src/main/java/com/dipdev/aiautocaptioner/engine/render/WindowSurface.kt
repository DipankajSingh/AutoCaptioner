package com.dipdev.aiautocaptioner.engine.render

import android.opengl.EGLSurface
import android.view.Surface

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


    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface!!)
    }

    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface!!)
    }


    fun setPresentationTime(nsecs: Long) {
        eglCore.setPresentationTime(eglSurface!!, nsecs)
    }
}

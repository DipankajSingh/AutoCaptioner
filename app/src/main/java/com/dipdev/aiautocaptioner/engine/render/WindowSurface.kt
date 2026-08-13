package com.dipdev.aiautocaptioner.engine.render

import android.opengl.EGLSurface
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.O)
class WindowSurface(
    private val eglCore: EglCore,
    private var surface: Surface?,
    private val releaseSurface: Boolean
) {
    private var eglSurface: EGLSurface? = null

    init {
        createWindowSurface(surface!!)
    }

    @RequiresApi(Build.VERSION_CODES.O)
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

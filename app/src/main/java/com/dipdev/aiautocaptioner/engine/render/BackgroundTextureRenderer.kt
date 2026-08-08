package com.dipdev.aiautocaptioner.engine.render

import android.graphics.Color
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Handles rendering solid color or gradient backgrounds using OpenGL ES 2.0.
 */
class BackgroundTextureRenderer {

    private val vertexShaderCode = """
        attribute vec4 aPosition;
        varying vec2 vPosition;
        void main() {
            gl_Position = aPosition;
            // Map y from [-1, 1] to [1, 0] for gradient interpolation (top to bottom)
            vPosition = vec2(aPosition.x, (1.0 - aPosition.y) * 0.5);
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vPosition;
        uniform vec4 uTopColor;
        uniform vec4 uBottomColor;
        void main() {
            gl_FragColor = mix(uTopColor, uBottomColor, vPosition.y);
        }
    """.trimIndent()

    private var program: Int = 0
    private var maPositionHandle: Int = 0
    private var muTopColorHandle: Int = 0
    private var muBottomColorHandle: Int = 0

    private val vertexBuffer: FloatBuffer

    // A full-screen quad (z=0)
    private val vertexData = floatArrayOf(
        -1.0f, -1.0f, 0.0f,
         1.0f, -1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f,
         1.0f,  1.0f, 0.0f
    )

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertexData).position(0)

        initGL()
    }

    private fun initGL() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e("BackgroundRenderer", "Could not link program: " + GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }

        maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        muTopColorHandle = GLES20.glGetUniformLocation(program, "uTopColor")
        muBottomColorHandle = GLES20.glGetUniformLocation(program, "uBottomColor")
    }

    /**
     * Draws a solid color background filling the viewport.
     */
    fun drawSolidColor(color: Int) {
        drawGradient(color, color)
    }

    /**
     * Draws a linear gradient from top to bottom.
     */
    fun drawGradient(topColor: Int, bottomColor: Int) {
        GLES20.glUseProgram(program)

        val rT = Color.red(topColor) / 255f
        val gT = Color.green(topColor) / 255f
        val bT = Color.blue(topColor) / 255f
        val aT = Color.alpha(topColor) / 255f

        val rB = Color.red(bottomColor) / 255f
        val gB = Color.green(bottomColor) / 255f
        val bB = Color.blue(bottomColor) / 255f
        val aB = Color.alpha(bottomColor) / 255f

        GLES20.glUniform4f(muTopColorHandle, rT, gT, bT, aT)
        GLES20.glUniform4f(muBottomColorHandle, rB, gB, bB, aB)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, vertexBuffer)
        GLES20.glEnableVertexAttribArray(maPositionHandle)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(maPositionHandle)
        GLES20.glUseProgram(0)
    }

    fun release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e("BackgroundRenderer", "Could not compile shader $type: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}

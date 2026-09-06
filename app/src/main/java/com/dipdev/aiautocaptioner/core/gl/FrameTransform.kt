package com.dipdev.aiautocaptioner.core.gl

import android.opengl.Matrix

/**
 * Computes the model (MVP) matrix that crops the camera's OES texture output to
 * [targetAspect], optionally mirroring for the front-camera selfie preview.
 *
 * RESPONSIBILITY SPLIT — two separate matrices are applied per frame (see [GlFrameRenderer]):
 *  1. `uTexMatrix`  = `SurfaceTexture.getTransformMatrix()` — provided by the Android
 *     framework. This matrix handles the raw OES buffer layout: the implicit Y-axis flip
 *     of OES textures, any hardware stride padding, and — critically — the sensor's
 *     physical rotation. By the time the vertex shader applies `uTexMatrix` to the texture
 *     coordinates, the sampled image is already upright.
 *  2. `uMvpMatrix`  = the matrix produced by this class — applied to the *vertex* positions
 *     (not tex-coords). Its only jobs are: center-crop to [targetAspect] and optionally
 *     mirror horizontally for the front-camera preview. It must NOT apply any rotation
 *     because that is already done by `uTexMatrix`.
 *
 * WHY `sensorOrientation` IS STILL A PARAMETER — even though we don't rotate vertices,
 * we do need it to determine the "upright" dimensions of the image (i.e., after the
 * texMatrix has un-rotated it) so the crop-scale math uses the correct axis:
 *  - sensorOrientation 90 or 270 → the raw buffer is landscape (width > height) but the
 *    upright image is portrait, so uprightWidth = sourceHeight, uprightHeight = sourceWidth.
 *  - sensorOrientation 0 or 180 → raw buffer is already portrait-ish; no axis swap needed.
 *
 * MATRIX ORDER — `Matrix.scaleM` POST-multiplies (`m = m * X`), so the vertex is
 * transformed in the REVERSE of call order. We want applied to the vertex: crop-scale first,
 * then mirror. So calls are issued: mirror first, then scale.
 *
 * VERIFICATION: confirmed correct on RMX1825 (back camera, sensorOrientation=90).
 * Front camera must be verified independently before shipping — front-lens
 * sensorOrientation frequently differs from back.
 */
object FrameTransform {

    val IDENTITY: FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    /**
     * @param sensorOrientation from `CameraCharacteristics.SENSOR_ORIENTATION` for the active lens
     * @param sourceWidth  the SurfaceTexture's native buffer width  (pre-rotation, landscape on most devices)
     * @param sourceHeight the SurfaceTexture's native buffer height (pre-rotation)
     * @param targetAspect desired output aspect ratio (width / height), e.g. 9f/16f for portrait
     * @param mirrorHorizontally true only for the *preview* draw of the front camera;
     *   the encoder draw (stage 2) must always pass false so the saved file is not mirrored
     */
    fun compute(
        sensorOrientation: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        targetAspect: Float,
        mirrorHorizontally: Boolean
    ): FloatArray {
        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix, 0)

        // Mirror issued first so it is applied LAST to the vertex (post-multiply order).
        if (mirrorHorizontally) {
            Matrix.scaleM(matrix, 0, -1f, 1f, 1f)
        }

        // Determine the upright dimensions of the image after the texMatrix has un-rotated it.
        val rotationSwapsDimensions = sensorOrientation == 90 || sensorOrientation == 270
        val uprightWidth  = if (rotationSwapsDimensions) sourceHeight else sourceWidth
        val uprightHeight = if (rotationSwapsDimensions) sourceWidth  else sourceHeight
        val uprightAspect = uprightWidth.toFloat() / uprightHeight.toFloat()

        if (uprightAspect > targetAspect) {
            // Upright source is wider than target → crop left/right (scale X up so edges bleed off).
            Matrix.scaleM(matrix, 0, uprightAspect / targetAspect, 1f, 1f)
        } else {
            // Upright source is taller than target → crop top/bottom (scale Y up so edges bleed off).
            Matrix.scaleM(matrix, 0, 1f, targetAspect / uprightAspect, 1f)
        }

        // No rotateM — rotation is already handled by the SurfaceTexture texMatrix. See class doc.

        return matrix
    }
}

package com.dipdev.aiautocaptioner.core.gl

import android.opengl.Matrix

/**
 * Computes the model matrix that takes the camera's raw OES buffer (whatever orientation and
 * aspect the sensor natively outputs) and maps it onto a full-screen quad such that the
 * result is upright and cropped (center-crop, not squashed) to [targetAspect].
 *
 * ASSUMPTIONS — both hold for this feature (portrait-locked recording on phone form factors):
 *  - the Activity is portrait-locked, and
 *  - the device's natural orientation is portrait (true for effectively all phones; this
 *    feature does not target portrait-primary tablets).
 * Under those assumptions the display rotation relative to natural orientation is always 0°,
 * which collapses the general Camera2 orientation formula down to: the buffer just needs to
 * be rotated by `sensorOrientation` degrees to be upright.
 *
 * MATRIX ORDER — worth documenting explicitly since it's easy to get backwards:
 * `Matrix.rotateM`/`scaleM` POST-multiply (`m = m * X`), which means the vertex ends up
 * transformed in the REVERSE of call order (`m*v = A*(B*(C*v))` for calls issued A, B, C —
 * so C, the last call, is applied to the vertex FIRST). We want, applied to the vertex in
 * this order: rotate to upright -> crop-scale (computed in upright-space dimensions) ->
 * optional mirror. So the calls are issued in the opposite order: mirror, then scale, then
 * rotate last.
 *
 * ROTATION DIRECTION — a second, separate gotcha from matrix order: Android's
 * `CameraCharacteristics.SENSOR_ORIENTATION` is documented as "the angle the image needs to
 * be rotated CLOCKWISE to show correctly." `android.opengl.Matrix.rotateM`, however, follows
 * the standard right-hand-rule convention, where a POSITIVE angle rotates COUNTER-clockwise
 * (as seen by the viewer looking at the screen). The two conventions are opposite-handed, so
 * the sensor's clockwise degrees must be negated before being passed to `rotateM`. Getting
 * this backwards doesn't just flip the direction — for a 90°/270° sensor orientation it
 * produces an image rotated a full 180° from correct, which is exactly the "wrong rotation"
 * symptom this fix addresses.
 *
 * VERIFICATION NOTE: `sensorOrientation` must be queried per-lens from
 * `CameraCharacteristics.SENSOR_ORIENTATION` (it is NOT guaranteed equal for front vs. back —
 * query whichever lens is active, see [com.dipdev.aiautocaptioner.core.camera.Camera2Session.sensorOrientation]).
 * The formula here matches the documented Camera2 contract, but confirm visually on at least
 * one physical device per facing before shipping — a very small number of OEM camera HALs are
 * known to report this value inconsistently.
 */
object FrameTransform {

    val IDENTITY: FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    /**
     * @param sensorOrientation from `CameraCharacteristics.SENSOR_ORIENTATION` for the active lens
     * @param sourceWidth / sourceHeight the SurfaceTexture's native buffer size (pre-rotation)
     * @param targetAspect desired output aspect (width / height) after rotation, e.g. 9f/16f
     * @param mirrorHorizontally true only for the *preview* draw of the front camera; the
     *   encoder-surface draw (stage 2) should always pass false so the saved file is not mirrored
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

        // Calls issued in reverse of intended vertex-application order — see class doc.
        if (mirrorHorizontally) {
            Matrix.scaleM(matrix, 0, -1f, 1f, 1f)
        }

        val rotationSwapsDimensions = sensorOrientation == 90 || sensorOrientation == 270
        val uprightWidth = if (rotationSwapsDimensions) sourceHeight else sourceWidth
        val uprightHeight = if (rotationSwapsDimensions) sourceWidth else sourceHeight
        val uprightAspect = uprightWidth.toFloat() / uprightHeight.toFloat()

        if (uprightAspect > targetAspect) {
            // Upright source is relatively wider than target -> crop left/right.
            Matrix.scaleM(matrix, 0, uprightAspect / targetAspect, 1f, 1f)
        } else {
            // Upright source is relatively taller than target -> crop top/bottom.
            Matrix.scaleM(matrix, 0, 1f, targetAspect / uprightAspect, 1f)
        }

        // Negated: see "ROTATION DIRECTION" in the class doc above.
        Matrix.rotateM(matrix, 0, -sensorOrientation.toFloat(), 0f, 0f, 1f)

        return matrix
    }
}

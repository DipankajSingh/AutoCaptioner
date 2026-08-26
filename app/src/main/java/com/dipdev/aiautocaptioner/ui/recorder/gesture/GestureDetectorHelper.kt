package com.dipdev.aiautocaptioner.ui.recorder.gesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.ui.recorder.camera.FrameAnalyzer
import java.io.ByteArrayOutputStream

class GestureDetectorHelper(
    val context: Context,
    private val crashReporter: CrashReporter,
    private val gestureListener: GestureListener?
) : FrameAnalyzer {

    private @Volatile var gestureRecognizer: GestureRecognizer? = null
    private var lastPalmDetectionTime = 0L

    init {
        setupGestureRecognizer()
    }

    fun close() {
        gestureRecognizer?.close()
        gestureRecognizer = null
    }

    private fun setupGestureRecognizer() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("gesture_recognizer.task")
                .setDelegate(Delegate.GPU)
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
                .setNumHands(1)
                .build()

            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
        } catch (e: Throwable) {
            crashReporter.recordException(e)
            gestureListener?.onError(e.message ?: "Failed to initialize gesture recognizer")
        }
    }

    override fun analyze(image: Image, rotationDegrees: Int) {
        val recognizer = gestureRecognizer ?: return
        val bitmap = image.toBitmap() ?: return

        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotationDegrees)
            .build()

        val mpImage = BitmapImageBuilder(bitmap).build()
        val frameTime = SystemClock.uptimeMillis()

        recognizer.recognizeAsync(mpImage, imageProcessingOptions, frameTime)
    }

    private fun Image.toBitmap(): Bitmap? {
        try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
            val bytes = out.toByteArray()

            val opts = android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: Exception) {
            return null
        }
    }

    private fun returnLivestreamResult(
        result: GestureRecognizerResult,
        inputImage: com.google.mediapipe.framework.image.MPImage
    ) {
        inputImage.close()

        if (result.gestures().isNotEmpty()) {
            val topGesture = result.gestures().first().first()
            if (topGesture.categoryName() == "Open_Palm") {
                val now = SystemClock.uptimeMillis()
                if (now - lastPalmDetectionTime > 2000L) {
                    lastPalmDetectionTime = now
                    gestureListener?.onPalmDetected()
                }
            }
        }
    }

    private fun returnLivestreamError(error: RuntimeException) {
        gestureListener?.onError(error.message ?: "Gesture recognition error")
    }

    interface GestureListener {
        fun onPalmDetected()
        fun onError(error: String)
    }
}

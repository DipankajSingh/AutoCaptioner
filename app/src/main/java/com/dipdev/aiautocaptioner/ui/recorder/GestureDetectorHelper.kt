package com.dipdev.aiautocaptioner.ui.recorder

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

class GestureDetectorHelper(
    val context: Context,
    gestureListener: GestureListener?
) : ImageAnalysis.Analyzer {

    private var gestureRecognizer: GestureRecognizer? = null
    private var lastPalmDetectionTime = 0L
    @Volatile private var isProcessing = false
    private val gestureListener: GestureListener? = gestureListener

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
                .setDelegate(Delegate.CPU)
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
                .setNumHands(1)
                .build()

            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
        } catch (e: Exception) {
            gestureListener?.onError(e.message ?: "Failed to initialize gesture recognizer")
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing || gestureRecognizer == null) {
            imageProxy.close()
            return
        }

        isProcessing = true
        var bitmapBuffer: android.graphics.Bitmap? = null
        try {
            bitmapBuffer = imageProxy.toBitmap()

            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
                .build()

            val mpImage = BitmapImageBuilder(bitmapBuffer).build()
            val frameTime = SystemClock.uptimeMillis()

            gestureRecognizer?.recognizeAsync(mpImage, imageProcessingOptions, frameTime)
        } catch (_: Exception) {
            isProcessing = false
            bitmapBuffer?.recycle()
        } finally {
            imageProxy.close()
        }
    }

    private fun returnLivestreamResult(
        result: GestureRecognizerResult,
        inputImage: com.google.mediapipe.framework.image.MPImage
    ) {
        isProcessing = false
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
        isProcessing = false
        gestureListener?.onError(error.message ?: "Gesture recognition error")
    }

    interface GestureListener {
        fun onPalmDetected()
        fun onError(error: String)
    }
}

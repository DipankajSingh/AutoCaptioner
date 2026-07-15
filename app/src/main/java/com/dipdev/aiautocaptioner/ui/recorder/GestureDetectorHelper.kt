package com.dipdev.aiautocaptioner.ui.recorder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
    val gestureListener: GestureListener?
) : ImageAnalysis.Analyzer {

    private var gestureRecognizer: GestureRecognizer? = null
    // Add debounce to avoid rapid continuous firing
    private var lastPalmDetectionTime = 0L

    init {
        setupGestureRecognizer()
    }

    fun clearGestureRecognizer() {
        gestureRecognizer?.close()
        gestureRecognizer = null
    }

    private fun setupGestureRecognizer() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("gesture_recognizer.task")
            .setDelegate(Delegate.GPU)
        
        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
            
            val options = optionsBuilder.build()
            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
        } catch (e: Exception) {
            gestureListener?.onError(e.message ?: "An unknown error occurred")
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        val bitmapBuffer = imageProxy.toBitmap()
        
        // Pass rotation via ImageProcessingOptions instead of manually allocating a new rotated Bitmap
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
            .build()
            
        val mpImage = BitmapImageBuilder(bitmapBuffer).build()
        val frameTime = SystemClock.uptimeMillis()
        
        gestureRecognizer?.recognizeAsync(mpImage, imageProcessingOptions, frameTime)
        imageProxy.close()
    }

    private fun returnLivestreamResult(
        result: GestureRecognizerResult,
        inputImage: com.google.mediapipe.framework.image.MPImage
    ) {
        if (result.gestures().isNotEmpty()) {
            val topGesture = result.gestures().first().first()
            if (topGesture.categoryName() == "Open_Palm") {
                val now = SystemClock.uptimeMillis()
                // Require 2 seconds between detections to avoid spamming
                if (now - lastPalmDetectionTime > 2000L) {
                    lastPalmDetectionTime = now
                    gestureListener?.onPalmDetected()
                }
            }
        }
    }

    private fun returnLivestreamError(error: RuntimeException) {
        gestureListener?.onError(error.message ?: "An unknown error occurred")
    }

    interface GestureListener {
        fun onPalmDetected()
        fun onError(error: String)
    }
}

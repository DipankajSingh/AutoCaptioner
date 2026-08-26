package com.dipdev.aiautocaptioner.ui.recorder.gesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Handler
import android.os.HandlerThread
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
    private @Volatile var isClosed = false
    private var lastPalmDetectionTime = 0L

    private val analysisThread = HandlerThread("GestureAnalysisThread").also { it.start() }
    private val analysisHandler = Handler(analysisThread.looper)

    private var nv21Buffer: ByteArray? = null
    private var bufferedWidth = 0
    private var bufferedHeight = 0
    private val pendingBitmaps = java.util.concurrent.ConcurrentLinkedQueue<Bitmap>()

    init {
        setupGestureRecognizer()
    }

    fun close() {
        isClosed = true
        val recognizerToClose = gestureRecognizer
        gestureRecognizer = null
        analysisHandler.post {
            recognizerToClose?.close()
        }
        analysisThread.quitSafely()
        pendingBitmaps.forEach { it.recycle() }
        pendingBitmaps.clear()
        nv21Buffer = null
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
        if (isClosed) return
        val recognizer = gestureRecognizer ?: return

        analysisHandler.post {
            if (isClosed) return@post
            val bitmap = image.toBitmap() ?: return@post

            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()

            val mpImage = BitmapImageBuilder(bitmap).build()
            val frameTime = SystemClock.uptimeMillis()
            pendingBitmaps.add(bitmap)

            recognizer.recognizeAsync(mpImage, imageProcessingOptions, frameTime)
        }
    }

    private fun Image.toBitmap(): Bitmap? {
        try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            yBuffer.rewind()
            uBuffer.rewind()
            vBuffer.rewind()

            val nv21Size = width * height * 3 / 2
            if (nv21Buffer == null || bufferedWidth != width || bufferedHeight != height) {
                nv21Buffer = ByteArray(nv21Size)
                bufferedWidth = width
                bufferedHeight = height
            }
            val nv21 = nv21Buffer!!

            yBuffer.position(0)
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, row * width, width)
            }

            val uvHeight = height / 2
            val uvWidth = width / 2
            var pos = width * height
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uvOffset = row * uvRowStride + col * uvPixelStride
                    nv21[pos++] = vBuffer.get(uvOffset)
                    nv21[pos++] = uBuffer.get(uvOffset)
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream(width * height / 2)
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
        pendingBitmaps.poll()?.recycle()

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

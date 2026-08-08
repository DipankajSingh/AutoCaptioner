package com.dipdev.aiautocaptioner.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Decodes video frames from a [Uri] in real time for use as a recording background.
 *
 * Architecture:
 *  - [MediaExtractor] demuxes the video track from the file.
 *  - [MediaCodec] (hardware decoder) decodes frames to an [ImageReader] surface.
 *  - A background coroutine feeds samples to the codec and drains decoded [Image]s.
 *  - Decoded frames are converted to [Bitmap] and stored in [latestFrame].
 *  - The [FacelessVideoRecorder.videoDrawLoop] calls [getLatestFrame] every frame.
 *
 * The video is looped automatically when the end of the stream is reached.
 */
class VideoFrameExtractor(
    private val context: Context,
    private val uri: Uri,
    private val targetWidth: Int,
    private val targetHeight: Int
) {
    private val latestFrame = AtomicReference<Bitmap?>(null)
    private val isRunning = AtomicBoolean(false)
    private var decoder: MediaCodec? = null
    private var extractor: MediaExtractor? = null
    private var imageReader: ImageReader? = null
    private var scope = CoroutineScope(Dispatchers.IO)
    private var decodeJob: Job? = null
    private var videoDurationUs = 0L
    private var mime = ""

    /**
     * Starts the decode loop. Must be called before [getLatestFrame].
     * Safe to call from any thread.
     */
    fun start() {
        if (isRunning.getAndSet(true)) return
        decodeJob = scope.launch { runDecodeLoop() }
    }

    /**
     * Returns the most recently decoded frame, or null if none is available yet.
     */
    fun getLatestFrame(): Bitmap? = latestFrame.get()

    /**
     * Stops decoding and releases all resources. Must be called when done.
     */
    fun release() {
        isRunning.set(false)
        decodeJob?.cancel()
        scope.cancel()
        try { decoder?.stop() } catch (_: Exception) {}
        try { decoder?.release() } catch (_: Exception) {}
        try { extractor?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        latestFrame.getAndSet(null)?.recycle()
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private suspend fun runDecodeLoop() {
        try {
            setupExtractorAndDecoder()
            decodeFrames()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupExtractorAndDecoder() {
        val ext = MediaExtractor()
        ext.setDataSource(context, uri, null)
        extractor = ext

        var trackIndex = -1
        for (i in 0 until ext.trackCount) {
            val fmt = ext.getTrackFormat(i)
            val m = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (m.startsWith("video/")) {
                trackIndex = i
                mime = m
                break
            }
        }
        if (trackIndex < 0) return

        ext.selectTrack(trackIndex)
        val format = ext.getTrackFormat(trackIndex)
        videoDurationUs = try {
            format.getLong(MediaFormat.KEY_DURATION)
        } catch (_: Exception) { 0L }

        // Override output dimensions in the format so the decoder produces frames at
        // the exact size we need — avoids an extra Bitmap scale step.
        format.setInteger(MediaFormat.KEY_WIDTH, targetWidth)
        format.setInteger(MediaFormat.KEY_HEIGHT, targetHeight)

        val reader = ImageReader.newInstance(targetWidth, targetHeight, ImageFormat.YUV_420_888, 3)
        imageReader = reader

        val dec = MediaCodec.createDecoderByType(mime)
        dec.configure(format, reader.surface, null, 0)
        dec.start()
        decoder = dec
    }

    private suspend fun decodeFrames() {
        val dec = decoder ?: return
        val ext = extractor ?: return

        val bufInfo = MediaCodec.BufferInfo()
        var inputDone = false

        while (isRunning.get()) {
            // Feed input samples
            if (!inputDone) {
                val inputIdx = dec.dequeueInputBuffer(5000)
                if (inputIdx >= 0) {
                    val buf = dec.getInputBuffer(inputIdx)!!
                    val sampleSize = ext.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        // End of stream — queue EOS flag and loop back to start
                        dec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        dec.queueInputBuffer(inputIdx, 0, sampleSize, ext.sampleTime, 0)
                        ext.advance()
                    }
                }
            }

            // Drain output
            val outputIdx = dec.dequeueOutputBuffer(bufInfo, 5000)
            when {
                outputIdx >= 0 -> {
                    // render=true pushes the frame to the ImageReader surface
                    dec.releaseOutputBuffer(outputIdx, true)
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val bmp = image.toArgbBitmap()
                        image.close()
                        if (bmp != null) {
                            latestFrame.getAndSet(bmp)?.recycle()
                        }
                    }
                }
                outputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    delay(8)
                }
                bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || inputDone -> {
                    // Loop: flush decoder and seek extractor back to beginning
                    dec.flush()
                    ext.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    inputDone = false
                }
            }
        }
    }

    /**
     * Converts a [YUV_420_888][ImageFormat.YUV_420_888] [Image] to an ARGB [Bitmap].
     *
     * Route: YUV planes → NV21 byte array → [YuvImage] → JPEG → [BitmapFactory].
     * This is CPU work (~2–5 ms on mid-range devices at 720p), which is acceptable
     * since we target 30 fps and have a dedicated IO coroutine for this.
     */
    private fun Image.toArgbBitmap(): Bitmap? {
        return try {
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]
            val yBuf = yPlane.buffer
            val uBuf = uPlane.buffer
            val vBuf = vPlane.buffer
            val ySize = yBuf.remaining()
            val uSize = uBuf.remaining()
            val vSize = vBuf.remaining()

            // Build NV21 from YUV_420_888
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuf.get(nv21, 0, ySize)
            // NV21 is Y then VU interleaved; planes[2] is V, planes[1] is U
            vBuf.get(nv21, ySize, vSize)
            uBuf.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 88, out)
            val jpegBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (_: Exception) {
            null
        }
    }
}

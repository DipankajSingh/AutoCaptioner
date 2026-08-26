package com.dipdev.aiautocaptioner.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.ui.recorder.model.AspectRatio
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoCropper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashReporter: CrashReporter
) {
    companion object {
        private const val TAG = "VideoCropper"
        private const val TIMEOUT_US = 10_000L
    }

    suspend fun cropToAspectRatio(
        inputFile: File,
        outputFile: File,
        targetAspectRatio: AspectRatio
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            cropVideoInternal(inputFile, outputFile, targetAspectRatio)
        } catch (e: Exception) {
            Log.e(TAG, "Crop failed", e)
            crashReporter.recordException(e)
            false
        }
    }

    private fun cropVideoInternal(
        inputFile: File,
        outputFile: File,
        targetRatio: AspectRatio
    ): Boolean {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(inputFile.absolutePath)
            val srcWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return false
            val srcHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return false
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            retriever.release()

            val effectiveWidth: Int
            val effectiveHeight: Int
            if (rotation == 90 || rotation == 270) {
                effectiveWidth = srcHeight
                effectiveHeight = srcWidth
            } else {
                effectiveWidth = srcWidth
                effectiveHeight = srcHeight
            }

            val ratioValue = targetRatio.width.toFloat() / targetRatio.height.toFloat()
            val srcRatio = effectiveWidth.toFloat() / effectiveHeight.toFloat()

            val cropW: Int
            val cropH: Int
            if (ratioValue > srcRatio) {
                cropW = effectiveWidth
                cropH = (effectiveWidth / ratioValue).toInt()
            } else {
                cropH = effectiveHeight
                cropW = (effectiveHeight * ratioValue).toInt()
            }

            val finalCropW = cropW - (cropW % 2)
            val finalCropH = cropH - (cropH % 2)

            if (finalCropW == effectiveWidth && finalCropH == effectiveHeight) {
                if (inputFile.absolutePath != outputFile.absolutePath) {
                    inputFile.copyTo(outputFile, overwrite = true)
                }
                return true
            }

            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    trackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (trackIndex < 0 || inputFormat == null) {
                extractor.release()
                return false
            }
            extractor.selectTrack(trackIndex)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val encoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, finalCropW, finalCropH).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val encoderSurface = encoder.createInputSurface()
            encoder.start()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            val cropLeft = (effectiveWidth - finalCropW) / 2
            val cropTop = (effectiveHeight - finalCropH) / 2

            val rect = Rect(0, 0, finalCropW, finalCropH)

            while (!decoderDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val decoderIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (decoderIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        decoder.releaseOutputBuffer(decoderIndex, false)
                    } else if (bufferInfo.size > 0) {
                        val image = decoder.getOutputImage(decoderIndex)
                        if (image != null) {
                            val fullBitmap = imageToBitmap(image, effectiveWidth, effectiveHeight)
                            image.close()

                            if (fullBitmap != null) {
                                val cropped = Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, finalCropW, finalCropH)
                                val canvas = encoderSurface.lockCanvas(rect)
                                canvas.drawColor(android.graphics.Color.BLACK)
                                canvas.drawBitmap(cropped, null, rect, null)
                                encoderSurface.unlockCanvasAndPost(canvas)
                                if (cropped !== fullBitmap) cropped.recycle()
                                fullBitmap.recycle()
                            }
                        }
                        decoder.releaseOutputBuffer(decoderIndex, false)
                    } else {
                        decoder.releaseOutputBuffer(decoderIndex, false)
                    }

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        encoder.signalEndOfInputStream()
                        decoderDone = true
                    }
                }

                drainEncoder(encoder, muxer, bufferInfo, muxerTrackIndex, muxerStarted).let {
                    muxerTrackIndex = it.first
                    muxerStarted = it.second
                }
            }

            drainEncoder(encoder, muxer, bufferInfo, muxerTrackIndex, muxerStarted)

            if (muxerStarted) {
                muxer.stop()
            }

            encoder.release()
            decoder.release()
            extractor.release()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Crop pipeline error", e)
            crashReporter.recordException(e)
            return false
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        trackIndex: Int,
        started: Boolean
    ): Pair<Int, Boolean> {
        var idx = trackIndex
        var isStarted = started
        while (true) {
            val encoderIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                encoderIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                encoderIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!isStarted) {
                        idx = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        isStarted = true
                    }
                }
                encoderIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(encoderIndex)
                    if (outputBuffer != null && bufferInfo.size > 0 && isStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(idx, outputBuffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encoderIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        return Pair(idx, isStarted)
    }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap? {
        return try {
            val planes = image.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            val nv21 = ByteArray(width * height * 3 / 2)
            var pos = 0

            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }

            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val uvIndex = row * uvRowStride + col * uvPixelStride
                    nv21[pos++] = vBuffer.get(uvIndex)
                    nv21[pos++] = uBuffer.get(uvIndex)
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert image to bitmap: ${e.message}")
            null
        }
    }
}

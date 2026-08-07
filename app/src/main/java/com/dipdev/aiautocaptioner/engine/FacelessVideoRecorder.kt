package com.dipdev.aiautocaptioner.engine

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class FacelessVideoRecorder {

    private val TAG = "FacelessVideoRecorder"

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var isMuxerStarted = false

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var muxer: MediaMuxer? = null

    private var inputSurface: Surface? = null
    private var audioRecord: AudioRecord? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1

    private var videoJob: Job? = null
    private var audioJob: Job? = null
    private var videoEncoderJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.Default)

    private var videoWidth = 1080
    private var videoHeight = 1920
    private var videoFps = 30
    private var videoBitrate = 4_000_000
    private var audioBitrate = 128_000
    private var audioEnabled = true
    private var sampleRate = 44100
    private val TIMEOUT_USEC = 10000L

    private var onCompleteCallback: ((File) -> Unit)? = null
    private var onErrorCallback: ((Exception) -> Unit)? = null
    private var onAmplitudeCallback: ((Float) -> Unit)? = null
    private var outputFile: File? = null

    fun isPaused(): Boolean = isPaused.get()

    @SuppressLint("MissingPermission")
    fun start(
        width: Int = 1080,
        height: Int = 1920,
        fps: Int = 30,
        videoBitrate: Int = 4_000_000,
        audioBitrate: Int = 128_000,
        backgroundBitmap: Bitmap?,
        backgroundColor: Int?,
        gradientColors: List<Int>?,
        scale: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        muted: Boolean = false,
        outputFile: File,
        onComplete: (File) -> Unit,
        onError: (Exception) -> Unit,
        onAmplitude: ((Float) -> Unit)? = null
    ) {
        if (isRecording.getAndSet(true)) {
            onError(IllegalStateException("Already recording"))
            return
        }

        this.videoWidth = width
        this.videoHeight = height
        this.videoFps = fps
        this.videoBitrate = videoBitrate
        this.audioBitrate = audioBitrate
        this.audioEnabled = !muted
        this.onCompleteCallback = onComplete
        this.onErrorCallback = onError
        this.onAmplitudeCallback = onAmplitude
        this.outputFile = outputFile

        scope = CoroutineScope(Dispatchers.Default)

        try {
            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight)
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate)
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoFps)
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoCodec?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoCodec?.createInputSurface()
            videoCodec?.start()

            if (audioEnabled) {
                sampleRate = 44100
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
                audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)
                audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

                audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                audioCodec?.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                audioCodec?.start()

                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
                var bufferSize = minBufferSize * 4
                if (bufferSize < 16384) bufferSize = 16384
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                audioRecord?.startRecording()
            }

            videoJob = scope.launch(Dispatchers.IO) { videoDrawLoop(backgroundBitmap, backgroundColor, gradientColors, scale, offsetX, offsetY) }
            videoEncoderJob = scope.launch { videoEncodeLoop() }
            if (audioEnabled) {
                audioJob = scope.launch { audioEncodeLoop() }
            }

        } catch (e: Exception) {
            isRecording.set(false)
            releaseResources()
            scope.cancel()
            onError(e)
        }
    }

    fun pause() {
        if (!isRecording.get() || isPaused.get()) return
        isPaused.set(true)
        videoCodec?.setParameters(Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 1)
        })
    }

    fun resume() {
        if (!isRecording.get() || !isPaused.get()) return
        isPaused.set(false)
        videoCodec?.setParameters(Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0)
            putLong(MediaCodec.PARAMETER_KEY_SUSPEND_TIME, 0L)
        })
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) return
        val wasPaused = isPaused.get()

        scope.launch {
            // Let the encoder flush any suspended frames before we tear down.
            if (wasPaused) {
                videoCodec?.setParameters(Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0)
                    putLong(MediaCodec.PARAMETER_KEY_SUSPEND_TIME, 0L)
                })
                delay(50)
            }

            withTimeoutOrNull(1000) { videoJob?.join() }
            // Signal the encoder that no more input will arrive so it can drain to EOS
            // (instead of being cut off by codec.stop(), which truncates the tail frames).
            try {
                videoCodec?.signalEndOfInputStream()
            } catch (_: Exception) {}
            withTimeoutOrNull(3000) { videoEncoderJob?.join() }
            withTimeoutOrNull(3000) { audioJob?.join() }

            videoJob?.cancel()
            videoEncoderJob?.cancel()
            audioJob?.cancel()

            releaseResources()

            if (isMuxerStarted && outputFile != null) {
                onCompleteCallback?.invoke(outputFile!!)
            } else {
                onErrorCallback?.invoke(IllegalStateException("Recording finished without a valid output (muxer never started)"))
            }
            scope.cancel()
        }
    }

    private suspend fun videoDrawLoop(
        bitmap: Bitmap?,
        color: Int?,
        gradientColors: List<Int>?,
        scale: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        val frameDurationMs = 1000L / videoFps
        val rect = Rect(0, 0, videoWidth, videoHeight)
        var lastFrameTime = System.currentTimeMillis()

        var gradientPaint: android.graphics.Paint? = null
        if (gradientColors != null && gradientColors.size >= 2) {
            gradientPaint = android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, videoHeight.toFloat(),
                    gradientColors.toIntArray(),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
        }

        val bitmapMatrix = android.graphics.Matrix()
        if (bitmap != null) {
            val scaleX = videoWidth.toFloat() / bitmap.width
            val scaleY = videoHeight.toFloat() / bitmap.height
            val baseScale = Math.min(scaleX, scaleY)
            val dx = (videoWidth - bitmap.width * baseScale) / 2f
            val dy = (videoHeight - bitmap.height * baseScale) / 2f

            bitmapMatrix.postScale(baseScale, baseScale)
            bitmapMatrix.postTranslate(dx, dy)
            bitmapMatrix.postScale(scale, scale, videoWidth / 2f, videoHeight / 2f)
            bitmapMatrix.postTranslate(offsetX, offsetY)
        }

        try {
            while (isRecording.get()) {
                if (isPaused.get()) {
                    delay(16)
                    lastFrameTime = System.currentTimeMillis()
                    continue
                }

                val canvas = inputSurface?.lockCanvas(null)
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)
                    if (bitmap != null) {
                        canvas.drawBitmap(bitmap, bitmapMatrix, null)
                    } else if (gradientPaint != null) {
                        canvas.drawRect(rect, gradientPaint)
                    } else if (color != null) {
                        canvas.drawColor(color)
                    }
                    inputSurface?.unlockCanvasAndPost(canvas)
                }

                val elapsed = System.currentTimeMillis() - lastFrameTime
                val sleepTime = frameDurationMs - elapsed
                if (sleepTime > 0) {
                    delay(sleepTime)
                }
                lastFrameTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video draw error", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun videoEncodeLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            var eosReceived = false
            var stopTimeOut = 0
            while (!eosReceived) {
                val encoderStatus = videoCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC) ?: break
                if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = videoCodec?.outputFormat ?: break
                    synchronized(this) {
                        videoTrackIndex = muxer?.addTrack(newFormat) ?: -1
                        checkMuxerStart()
                    }
                } else if (encoderStatus >= 0) {
                    val encodedData = videoCodec?.getOutputBuffer(encoderStatus)
                    if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        if (bufferInfo.size != 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            synchronized(this) {
                                if (isMuxerStarted) {
                                    muxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                                }
                            }
                        }
                    }
                    videoCodec?.releaseOutputBuffer(encoderStatus, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eosReceived = true
                        break
                    }
                } else if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!isRecording.get()) {
                        stopTimeOut++
                        if (stopTimeOut > 100) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video encode error", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun audioEncodeLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        val audioBuffer = ByteArray(4096)
        val silenceBuffer = ByteArray(4096)
        var audioPts = 0L
        var lastAmplitudeEmitMs = 0L
        val bytesPerFrame = sampleRate * 2L

        try {
            var eosReceived = false
            var isEosSent = false
            var stopTimeOut = 0
            while (!eosReceived) {
                if (!isEosSent) {
                    val inputBufferIndex = audioCodec?.dequeueInputBuffer(TIMEOUT_USEC) ?: -1
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = audioCodec?.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()

                        var readBytes = 0
                        if (isRecording.get()) {
                            readBytes = if (isPaused.get()) {
                                // Keep the audio track alive and continuous across a pause by
                                // encoding silence instead of terminating the stream with EOS.
                                silenceBuffer.size
                            } else {
                                audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                            }
                        }

                        if (readBytes > 0) {
                            if (isPaused.get()) {
                                inputBuffer?.put(silenceBuffer, 0, readBytes)
                            } else {
                                var sum = 0.0
                                for (i in 0 until readBytes step 2) {
                                    val sample = (audioBuffer[i].toInt() and 0xFF) or (audioBuffer[i + 1].toInt() shl 8)
                                    val shortSample = sample.toShort()
                                    sum += shortSample * shortSample
                                }
                                val rms = Math.sqrt(sum / (readBytes / 2.0))
                                val amplitude = if (rms.isNaN()) 0f else (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                                val now = System.currentTimeMillis()
                                if (now - lastAmplitudeEmitMs >= 100) {
                                    lastAmplitudeEmitMs = now
                                    onAmplitudeCallback?.invoke(amplitude)
                                }
                                inputBuffer?.put(audioBuffer, 0, readBytes)
                            }
                            val ptsUs = (audioPts * 1000000L) / bytesPerFrame
                            audioCodec?.queueInputBuffer(inputBufferIndex, 0, readBytes, ptsUs, 0)
                            audioPts += readBytes
                        } else {
                            audioCodec?.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEosSent = true
                        }
                    }
                }

                var encoderStatus = audioCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC) ?: -1
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (isEosSent) {
                        stopTimeOut++
                        if (stopTimeOut > 100) break
                    }
                }
                while (encoderStatus >= 0 || encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = audioCodec?.outputFormat ?: break
                        synchronized(this) {
                            audioTrackIndex = muxer?.addTrack(newFormat) ?: -1
                            checkMuxerStart()
                        }
                    } else if (encoderStatus >= 0) {
                        val encodedData = audioCodec?.getOutputBuffer(encoderStatus)
                        if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (bufferInfo.size != 0) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                synchronized(this) {
                                    if (isMuxerStarted) {
                                        muxer?.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                                    }
                                }
                            }
                        }
                        audioCodec?.releaseOutputBuffer(encoderStatus, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            eosReceived = true
                            return
                        }
                    }
                    encoderStatus = audioCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC) ?: -1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio encode error", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun checkMuxerStart() {
        if (!isMuxerStarted && videoTrackIndex >= 0 && (!audioEnabled || audioTrackIndex >= 0)) {
            muxer?.start()
            isMuxerStarted = true
        }
    }

    private fun releaseResources() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null

        try { videoCodec?.stop() } catch (_: Exception) {}
        try { videoCodec?.release() } catch (_: Exception) {}
        videoCodec = null

        try { audioCodec?.stop() } catch (_: Exception) {}
        try { audioCodec?.release() } catch (_: Exception) {}
        audioCodec = null

        if (isMuxerStarted) {
            try { muxer?.stop() } catch (_: Exception) {}
        }
        try { muxer?.release() } catch (_: Exception) {}
        muxer = null
        isMuxerStarted = false

        videoTrackIndex = -1
        audioTrackIndex = -1

        try { inputSurface?.release() } catch (_: Exception) {}
        inputSurface = null

        isPaused.set(false)
    }
}

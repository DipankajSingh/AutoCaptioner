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
import android.util.Log
import android.view.Surface
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class FacelessVideoRecorder {

    private val TAG = "FacelessVideoRecorder"

    private val isRecording = AtomicBoolean(false)
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
    private val scope = CoroutineScope(Dispatchers.Default)

    private val VIDEO_WIDTH = 1080
    private val VIDEO_HEIGHT = 1920
    private val VIDEO_FPS = 30
    private val TIMEOUT_USEC = 10000L

    private var onCompleteCallback: ((File) -> Unit)? = null
    private var onErrorCallback: ((Exception) -> Unit)? = null
    private var onAmplitudeCallback: ((Float) -> Unit)? = null
    private var outputFile: File? = null

    @SuppressLint("MissingPermission")
    fun start(
        backgroundBitmap: Bitmap?,
        backgroundColor: Int?,
        gradientColors: List<Int>?,
        scale: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        outputFile: File,
        onComplete: (File) -> Unit,
        onError: (Exception) -> Unit,
        onAmplitude: ((Float) -> Unit)? = null
    ) {
        if (isRecording.getAndSet(true)) {
            onError(IllegalStateException("Already recording"))
            return
        }

        this.onCompleteCallback = onComplete
        this.onErrorCallback = onError
        this.onAmplitudeCallback = onAmplitude
        this.outputFile = outputFile

        try {
            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Setup Video Codec
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT)
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoCodec?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoCodec?.createInputSurface()
            videoCodec?.start()

            // Setup Audio Codec
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioCodec?.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioCodec?.start()

            // Setup AudioRecord
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
            var bufferSize = minBufferSize * 4
            if (bufferSize < 16384) bufferSize = 16384
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            audioRecord?.startRecording()

            // Start threads
            videoJob = scope.launch { videoDrawLoop(backgroundBitmap, backgroundColor, gradientColors, scale, offsetX, offsetY) }
            videoEncoderJob = scope.launch { videoEncodeLoop() }
            audioJob = scope.launch { audioEncodeLoop() }

        } catch (e: Exception) {
            isRecording.set(false)
            releaseResources()
            onError(e)
        }
    }

    fun stop() {
        if (!isRecording.get()) return
        isRecording.set(false)
        
        scope.launch {
            withTimeoutOrNull(1000) { videoJob?.join() }
            withTimeoutOrNull(2000) { videoEncoderJob?.join() }
            withTimeoutOrNull(2000) { audioJob?.join() }

            releaseResources()
            outputFile?.let { onCompleteCallback?.invoke(it) }
        }
    }

    private fun videoDrawLoop(bitmap: Bitmap?, color: Int?, gradientColors: List<Int>?, scale: Float = 1f, offsetX: Float = 0f, offsetY: Float = 0f) {
        val frameDurationMs = 1000L / VIDEO_FPS
        val rect = android.graphics.Rect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT)
        var frameCount = 0L
        
        var gradientPaint: android.graphics.Paint? = null
        if (gradientColors != null && gradientColors.size >= 2) {
            gradientPaint = android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, VIDEO_HEIGHT.toFloat(),
                    gradientColors.toIntArray(),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
        }
        
        val bitmapMatrix = android.graphics.Matrix()
        if (bitmap != null) {
            // Replicate ContentScale.Fit logic:
            val scaleX = VIDEO_WIDTH.toFloat() / bitmap.width
            val scaleY = VIDEO_HEIGHT.toFloat() / bitmap.height
            val baseScale = Math.min(scaleX, scaleY)
            val dx = (VIDEO_WIDTH - bitmap.width * baseScale) / 2f
            val dy = (VIDEO_HEIGHT - bitmap.height * baseScale) / 2f
            
            bitmapMatrix.postScale(baseScale, baseScale)
            bitmapMatrix.postTranslate(dx, dy)
            
            // Apply user transform (scale originates from center in UI)
            bitmapMatrix.postScale(scale, scale, VIDEO_WIDTH / 2f, VIDEO_HEIGHT / 2f)
            bitmapMatrix.postTranslate(offsetX, offsetY)
        }
        
        try {
            while (isRecording.get()) {
                val startTime = System.currentTimeMillis()
                
                val canvas = inputSurface?.lockCanvas(null)
                if (canvas != null) {
                    canvas.drawColor(android.graphics.Color.BLACK) // base background
                    if (bitmap != null) {
                        canvas.drawBitmap(bitmap, bitmapMatrix, null)
                    } else if (gradientPaint != null) {
                        canvas.drawRect(rect, gradientPaint)
                    } else if (color != null) {
                        canvas.drawColor(color)
                    }
                    inputSurface?.unlockCanvasAndPost(canvas)
                }
                
                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = frameDurationMs - elapsed
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime)
                }
                frameCount++
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
                        if (stopTimeOut > 100) break // fail-safe (1 sec)
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
        var audioPts = 0L
        var audioPtsUsBase = -1L
        
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
                        
                        val readBytes = if (isRecording.get()) audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0 else 0
                        if (readBytes > 0) {
                            var sum = 0.0
                            for (i in 0 until readBytes step 2) {
                                val sample = (audioBuffer[i].toInt() and 0xFF) or (audioBuffer[i+1].toInt() shl 8)
                                val shortSample = sample.toShort()
                                sum += shortSample * shortSample
                            }
                            val rms = Math.sqrt(sum / (readBytes / 2.0))
                            val amplitude = if (rms.isNaN()) 0f else (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                            onAmplitudeCallback?.invoke(amplitude)

                            inputBuffer?.put(audioBuffer, 0, readBytes)
                            if (audioPtsUsBase < 0L) {
                                audioPtsUsBase = System.nanoTime() / 1000L
                            }
                            val ptsUs = audioPtsUsBase + (audioPts * 1000000L / (44100L * 2L))
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
                        if (stopTimeOut > 100) break // fail-safe
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
        if (!isMuxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
            muxer?.start()
            isMuxerStarted = true
        }
    }

    private fun releaseResources() {
        try { audioRecord?.stop() } catch (e: Exception) {}
        try { audioRecord?.release() } catch (e: Exception) {}
        audioRecord = null

        try { videoCodec?.stop() } catch (e: Exception) {}
        try { videoCodec?.release() } catch (e: Exception) {}
        videoCodec = null

        try { audioCodec?.stop() } catch (e: Exception) {}
        try { audioCodec?.release() } catch (e: Exception) {}
        audioCodec = null

        if (isMuxerStarted) {
            try { muxer?.stop() } catch (e: Exception) {}
        }
        try { muxer?.release() } catch (e: Exception) {}
        muxer = null
        isMuxerStarted = false

        videoTrackIndex = -1
        audioTrackIndex = -1

        try { inputSurface?.release() } catch (e: Exception) {}
        inputSurface = null
    }
}

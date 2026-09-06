package com.dipdev.aiautocaptioner.core.media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Records PCM audio from the microphone and encodes it to AAC, writing output to [MuxerWrapper].
 *
 * Runs its capture/encode loop on a dedicated [HandlerThread] — completely decoupled from the
 * GL/renderer thread. The caller interacts via [start], [pause], [resume], and [stop].
 *
 * Pause/resume: the AudioRecord capture loop is stopped/restarted. The MediaCodec encoder
 * keeps running; the gap in audio samples appears as silence-free cut in the stream (because
 * no samples are presented during the paused interval). PTS continuity is maintained by
 * [RecordingSession] via the [onPausedNanosUpdated] callback — audio PTS is computed from
 * the system clock offset by total paused duration.
 *
 * Thread safety: [pause] and [resume] post runnables to the audio thread's handler to avoid
 * races with the capture loop. [stop] blocks until the encoder thread drains to EOS.
 */
class AudioEncoderCore(private val muxer: MuxerWrapper) {

    private val thread = HandlerThread("RecorderAudioThread").apply { start() }
    private val handler = Handler(thread.looper)

    private lateinit var audioRecord: AudioRecord
    private lateinit var codec: MediaCodec

    @Volatile private var isCapturing = false
    @Volatile private var isStopped = false

    private var trackIndex = -1
    private var muxerTrackAdded = false

    /** Nanoseconds already spent paused; updated by [RecordingSession] on resume. */
    @Volatile var pausedNanos: Long = 0L

    private var startTimeNanos: Long = 0L

    /**
     * Initialises AudioRecord and MediaCodec, then starts the capture loop.
     * Returns once the encoder's format is ready (i.e., the muxer track is added).
     */
    fun start() {
        handler.post { initAndCapture() }
    }

    /** Suspends the capture loop without stopping the codec. */
    fun pause() {
        handler.post { isCapturing = false }
    }

    /** Resumes the capture loop. */
    fun resume() {
        handler.post { isCapturing = true }
    }

    /**
     * Signals EOS, drains remaining audio, and releases all resources.
     * Blocks until the audio thread finishes (safe to call from a coroutine via [suspendCancellableCoroutine]).
     */
    fun stop() {
        handler.post {
            isStopped = true
            isCapturing = false
        }
        // Give the audio thread time to drain and exit.
        thread.quitSafely()
        thread.join()
    }

    // -----------------------------------------------------------------------------------------
    // Internal — runs on AudioThread
    // -----------------------------------------------------------------------------------------

    private fun initAndCapture() {
        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, ENCODING
        )
        val bufSize = minBufSize * 2   // 2× for headroom

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            ENCODING,
            bufSize
        )

        val format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE_BPS)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufSize)
        }

        codec = MediaCodec.createEncoderByType(MIME_TYPE)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        audioRecord.startRecording()
        isCapturing = true
        startTimeNanos = System.nanoTime()

        val pcmBuffer = ByteArray(bufSize)

        while (!isStopped) {
            if (!isCapturing) {
                Thread.sleep(10)
                continue
            }

            // Feed PCM into codec input buffers.
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                inputBuffer.clear()
                val bytesRead = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                if (bytesRead > 0) {
                    inputBuffer.put(pcmBuffer, 0, bytesRead)
                    val ptsUs = ((System.nanoTime() - startTimeNanos - pausedNanos) / 1000L)
                        .coerceAtLeast(0L)
                    codec.queueInputBuffer(inputBufferIndex, 0, bytesRead, ptsUs, 0)
                } else {
                    codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, 0)
                }
            }

            // Drain encoded output.
            drainCodec(endOfStream = false)
        }

        // Signal EOS and drain remaining samples.
        val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()
            val ptsUs = ((System.nanoTime() - startTimeNanos - pausedNanos) / 1000L)
                .coerceAtLeast(0L)
            codec.queueInputBuffer(inputBufferIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainCodec(endOfStream = true)

        audioRecord.stop()
        audioRecord.release()
        codec.stop()
        codec.release()
    }

    private fun drainCodec(endOfStream: Boolean) {
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = if (endOfStream) DRAIN_TIMEOUT_US else 0L

        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerTrackAdded) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxerTrackAdded = true
                    }
                }
                outputBufferIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null &&
                        bufferInfo.size > 0 &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                        muxer.isStarted
                    ) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (isEos) return
                }
            }
        }
    }

    companion object {
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_COUNT = 2
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_BITRATE_BPS = 128_000
        private const val TIMEOUT_US = 10_000L
        private const val DRAIN_TIMEOUT_US = 10_000L
    }
}

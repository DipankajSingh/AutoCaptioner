package com.dipdev.aiautocaptioner.core.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import com.dipdev.aiautocaptioner.core.camera.RecordingAspectRatio
import com.dipdev.aiautocaptioner.core.camera.RecordingQuality

/**
 * Wraps [MediaCodec] in AVC (H.264) surface-input encode mode.
 *
 * The encoder is configured with an **input Surface** rather than byte buffers — this lets
 * [com.dipdev.aiautocaptioner.core.gl.RendererThread] draw directly into the encoder via an
 * EGL [android.opengl.EGLSurface], at the correct resolution and with the correct crop already
 * applied by [com.dipdev.aiautocaptioner.core.gl.FrameTransform]. No intermediate copy or
 * CPU round-trip is needed.
 *
 * Output encoded buffers are drained synchronously via [drainEncoder]; call it from the same
 * thread that's driving [com.dipdev.aiautocaptioner.core.gl.RendererThread]'s frame loop, or
 * from [RecordingSession]'s control flow after signalling EOS.
 *
 * Bitrate mode: prefers [MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ] (constant quality,
 * encoder controls bitrate per-frame in real time) if the hardware encoder supports it.
 * Falls back to [MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR] with the quality tier's
 * [RecordingQuality.targetBitrateBps] as the target.
 */
class VideoEncoderCore(
    quality: RecordingQuality,
    aspectRatio: RecordingAspectRatio,
    private val muxer: MuxerWrapper
) {
    private val codec: MediaCodec
    private var trackIndex = -1
    private var muxerTrackAdded = false

    /** The Surface that [com.dipdev.aiautocaptioner.core.gl.RendererThread] draws into. */
    val inputSurface: Surface

    init {
        val size = aspectRatio.resolve(quality)
        val format = MediaFormat.createVideoFormat(MIME_TYPE, size.width, size.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECS)

            // Query encoder capabilities WITHOUT instantiating a codec.
            // The old code did MediaCodec.createEncoderByType(MIME_TYPE).name and then lost
            // the reference — leaking a hardware codec slot. On devices with a low concurrent
            // codec limit (e.g. MediaTek RMX1825) this stalled the GL frame loop, freezing
            // the preview as soon as recording started.
            val encoderCaps = try {
                android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
                    .codecInfos
                    .firstOrNull { info ->
                        info.isEncoder &&
                        info.supportedTypes.any { it.equals(MIME_TYPE, ignoreCase = true) }
                    }
                    ?.getCapabilitiesForType(MIME_TYPE)
                    ?.encoderCapabilities
            } catch (_: Exception) { null }

            val supportsCq = encoderCaps
                ?.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ) == true

            if (supportsCq) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)
            } else {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                setInteger(MediaFormat.KEY_BIT_RATE, quality.targetBitrateBps)
            }
        }

        codec = MediaCodec.createEncoderByType(MIME_TYPE)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
    }

    /**
     * Drains encoded output buffers from the codec and writes them to [muxer].
     *
     * @param endOfStream pass `true` on the final drain after signalling EOS on [inputSurface]
     *   via [android.opengl.EGL14.eglSwapBuffers] with a sentinel timestamp, or by calling
     *   [MediaCodec.signalEndOfInputStream].
     */
    fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) {
            codec.signalEndOfInputStream()
        }

        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, DRAIN_TIMEOUT_US)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break   // No output ready and not EOS — come back next frame.
                    // EOS: keep polling until the codec confirms it.
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerTrackAdded) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxerTrackAdded = true
                    }
                }
                outputBufferIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputBufferIndex)
                        ?: run {
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            continue
                        }

                    // Skip codec config buffers (SPS/PPS) — the muxer handles these internally.
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && muxer.isStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }

                    val isEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (isEos) break
                }
            }
        }
    }

    /**
     * Signals end-of-stream, drains remaining frames, and releases all resources.
     * [inputSurface] is released by [MediaCodec.release] — do not release it separately.
     */
    fun release() {
        try {
            drainEncoder(endOfStream = true)
        } catch (_: Exception) { /* best-effort */ }
        codec.stop()
        codec.release()
        inputSurface.release()
    }

    companion object {
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL_SECS = 1
        private const val DRAIN_TIMEOUT_US = 10_000L
    }
}

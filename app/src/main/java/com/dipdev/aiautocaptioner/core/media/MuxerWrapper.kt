package com.dipdev.aiautocaptioner.core.media

import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import java.io.File
import java.nio.ByteBuffer

/**
 * Thread-safe wrapper around [MediaMuxer] (MP4 output).
 *
 * [MediaMuxer.start] can only be called once both the audio and video track formats are
 * known — the codec signals its actual output format via [MediaCodec.INFO_OUTPUT_FORMAT_CHANGED]
 * before the first encoded frame arrives. This class accumulates track formats via [addTrack]
 * and starts the muxer automatically once both are registered.
 *
 * All public methods are synchronized, so [VideoEncoderCore] and [AudioEncoderCore] can safely
 * call [writeSampleData] from their respective threads without additional locking.
 */
class MuxerWrapper(outputFile: File) {

    private val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var trackCount = 0

    /** True once both tracks are added and [MediaMuxer.start] has been called. */
    @Volatile var isStarted: Boolean = false
        private set

    /**
     * Registers a track format from [MediaCodec.INFO_OUTPUT_FORMAT_CHANGED].
     * The muxer is started automatically once both audio and video tracks are added.
     *
     * @return the assigned track index, to be used in [writeSampleData]
     */
    @Synchronized
    fun addTrack(format: MediaFormat): Int {
        val index = muxer.addTrack(format)
        trackCount++
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("video/")) videoTrackIndex = index
        else if (mime.startsWith("audio/")) audioTrackIndex = index

        // Start only when both tracks are registered. Writing samples before start() crashes.
        if (videoTrackIndex != -1 && audioTrackIndex != -1) {
            muxer.start()
            isStarted = true
        }
        return index
    }

    /**
     * Writes an encoded sample. Safe to call from any thread.
     * No-op if the muxer hasn't started yet (both formats not yet received).
     */
    @Synchronized
    fun writeSampleData(trackIndex: Int, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isStarted) return
        muxer.writeSampleData(trackIndex, buffer, bufferInfo)
    }

    /**
     * Stops and releases the muxer. Must be called after both encoders have reached EOS.
     * Safe to call only once.
     */
    @Synchronized
    fun stop() {
        if (isStarted) {
            muxer.stop()
            isStarted = false
        }
        muxer.release()
    }
}

package com.dipdev.aiautocaptioner.core.media

import android.os.Handler
import android.os.Looper
import com.dipdev.aiautocaptioner.core.camera.RecordingAspectRatio
import com.dipdev.aiautocaptioner.core.camera.RecordingQuality
import com.dipdev.aiautocaptioner.core.gl.RendererThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Top-level orchestrator for a single recording take.
 *
 * Owns [VideoEncoderCore], [AudioEncoderCore], and [MuxerWrapper]. Wires the encoder's input
 * Surface into [RendererThread] so that each camera frame is drawn into the encoder in addition
 * to the preview. Manages pause/resume PTS bookkeeping so the output file has monotonic
 * timestamps and no frozen-frame artifacts across pause events.
 *
 * State machine: Idle → Started → (Paused ⇄ Started) → Stopped
 *
 * Duration cap: automatically stops the recording at [MAX_DURATION_MS] (3 minutes) and invokes
 * [onDurationCapReached] so the UI can react (e.g. navigate to the editor).
 *
 * Usage:
 * ```kotlin
 * val session = RecordingSession(file, quality, ratio, rendererThread) { onCapped() }
 * session.start()
 * // ... user interaction ...
 * session.pause()
 * session.resume()
 * val outputFile = session.stop()  // returns the finalized mp4
 * ```
 */
class RecordingSession(
    private val outputFile: File,
    private val quality: RecordingQuality,
    private val aspectRatio: RecordingAspectRatio,
    private val rendererThread: RendererThread,
    private val onDurationCapReached: () -> Unit
) {
    private lateinit var muxer: MuxerWrapper
    private lateinit var videoEncoder: VideoEncoderCore
    private lateinit var audioEncoder: AudioEncoderCore

    @Volatile private var pauseStartNanos = 0L
    @Volatile private var totalPausedNanos = 0L
    @Volatile private var startTimeMs = 0L

    val isPaused: Boolean get() = pauseStartNanos != 0L

    /** Elapsed recording time in milliseconds (paused time excluded). */
    val elapsedMs: Long
        get() {
            val raw = System.currentTimeMillis() - startTimeMs
            val pausedMs = if (isPaused) (System.nanoTime() - pauseStartNanos) / 1_000_000L else 0L
            return (raw - pausedMs - totalPausedNanos / 1_000_000L).coerceAtLeast(0L)
        }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val durationCheckRunnable = object : Runnable {
        override fun run() {
            if (elapsedMs >= MAX_DURATION_MS) {
                mainHandler.post { onDurationCapReached() }
            } else {
                mainHandler.postDelayed(this, DURATION_CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Initialises the pipeline and starts recording.
     * Must be called from a coroutine (switches to IO for encoder setup).
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        muxer = MuxerWrapper(outputFile)
        videoEncoder = VideoEncoderCore(quality, aspectRatio, muxer)
        audioEncoder = AudioEncoderCore(muxer)

        val size = aspectRatio.resolve(quality)
        // Wire the encoder's input Surface into the renderer — from this point, every frame
        // is drawn to both the preview surface and the encoder surface.
        rendererThread.setEncoderSurface(videoEncoder.inputSurface, size.width, size.height)
        rendererThread.setRecordingActive(true)

        audioEncoder.start()
        startTimeMs = System.currentTimeMillis()

        mainHandler.postDelayed(durationCheckRunnable, DURATION_CHECK_INTERVAL_MS)
    }

    /**
     * Pauses recording. Audio capture stops; video frames are still rendered to the preview
     * but are not forwarded to the encoder surface (controlled by [RendererThread]).
     */
    fun pause() {
        if (isPaused) return
        pauseStartNanos = System.nanoTime()
        rendererThread.setRecordingActive(false)
        audioEncoder.pause()
        mainHandler.removeCallbacks(durationCheckRunnable)
    }

    /**
     * Resumes a paused recording. Accumulated pause duration is pushed to both the renderer
     * thread (for video PTS) and the audio encoder (for audio PTS).
     */
    fun resume() {
        if (!isPaused) return
        val pauseDuration = System.nanoTime() - pauseStartNanos
        totalPausedNanos += pauseDuration
        pauseStartNanos = 0L

        rendererThread.setPausedDurationNanos(totalPausedNanos)
        audioEncoder.pausedNanos = totalPausedNanos
        rendererThread.setRecordingActive(true)
        audioEncoder.resume()

        mainHandler.postDelayed(durationCheckRunnable, DURATION_CHECK_INTERVAL_MS)
    }

    /**
     * Stops recording, drains encoders, finalizes the muxer, and returns the output file.
     * Must be called from a coroutine (switches to IO for encoder drain).
     */
    suspend fun stop(): File = withContext(Dispatchers.IO) {
        mainHandler.removeCallbacks(durationCheckRunnable)

        rendererThread.setRecordingActive(false)

        // Drain video encoder first (signals EOS into the codec via the GL surface swap mechanism).
        videoEncoder.release()

        // Stop audio encoder (drain + release).
        audioEncoder.stop()

        // Finalize the file.
        muxer.stop()

        // Detach encoder surface from renderer.
        rendererThread.setEncoderSurface(null, 0, 0)

        outputFile
    }

    companion object {
        /** Hard recording cap — 3 minutes, matching YouTube Shorts / TikTok / Reels. */
        const val MAX_DURATION_MS = 3 * 60 * 1_000L
        private const val DURATION_CHECK_INTERVAL_MS = 100L
    }
}

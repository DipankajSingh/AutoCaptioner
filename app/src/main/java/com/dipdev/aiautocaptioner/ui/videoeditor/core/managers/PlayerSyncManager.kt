package com.dipdev.aiautocaptioner.ui.videoeditor.core.managers

import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.model.Clip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerSyncManager(
    private val scope: CoroutineScope,
    private val onIsPlayingChanged: (Boolean) -> Unit,
    private val onTimelinePositionChanged: (Long) -> Unit,
    private val getClips: () -> List<Clip>
) {
    private var progressJob: Job? = null
    var boundPlayer: Player? = null
        private set

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onIsPlayingChanged(isPlaying)
            if (isPlaying) {
                startProgressSync()
            } else {
                stopProgressSync()
                updateProgress()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updateProgress()
        }
    }

    fun bindPlayer(player: Player) {
        if (boundPlayer == player) return
        boundPlayer?.removeListener(playerListener)
        boundPlayer = player
        player.addListener(playerListener)
        onIsPlayingChanged(player.isPlaying)
        if (player.isPlaying) {
            startProgressSync()
        } else {
            updateProgress()
        }
    }

    fun unbind() {
        boundPlayer?.removeListener(playerListener)
        boundPlayer = null
        stopProgressSync()
    }

    private fun startProgressSync() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                updateProgress()
                delay(16L) // Update ~60fps while playing
            }
        }
    }

    private fun stopProgressSync() {
        progressJob?.cancel()
        progressJob = null
    }

    fun updateProgress() {
        val player = boundPlayer ?: return
        val windowIndex = player.currentMediaItemIndex
        val posInWindow = player.currentPosition
        
        var accumulated = 0L
        val clips = getClips()
        
        // Compute merged clips dynamically to calculate absolute timeline ms
        val mergedClips = mutableListOf<Clip>()
        var current: Clip? = null
        for (c in clips) {
            if (current == null) {
                current = c
            } else {
                if (current.endTrimMs == c.startTrimMs) {
                    current = current.copy(endTrimMs = c.endTrimMs)
                } else {
                    mergedClips.add(current)
                    current = c
                }
            }
        }
        if (current != null) mergedClips.add(current)
        
        for (i in 0 until windowIndex.coerceAtMost(mergedClips.size)) {
            accumulated += (mergedClips[i].endTrimMs - mergedClips[i].startTrimMs)
        }
        
        onTimelinePositionChanged(accumulated + posInWindow)
    }
}

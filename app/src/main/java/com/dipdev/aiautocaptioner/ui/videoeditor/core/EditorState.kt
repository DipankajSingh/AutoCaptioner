package com.dipdev.aiautocaptioner.ui.videoeditor.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.data.model.mergeContiguousClips
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun rememberEditorState(
    clips: List<Clip>,
    originalVideoPath: String,
    onDurationUpdated: (Long) -> Unit
): EditorState {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = false
        }
    }

    val state = remember(player) {
        EditorState(player, coroutineScope, onDurationUpdated)
    }

    DisposableEffect(player) {
        onDispose {
            state.stopProgressSync()
            player.release()
        }
    }

    // Compute merged clips dynamically to calculate absolute timeline ms
    val mergedClips = remember(clips) {
        mergeContiguousClips(clips)
    }

    // Keep track of the previous media items to prevent unnecessary ExoPlayer restarts
    var previousMediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // Sync ExoPlayer playlist with clips
    LaunchedEffect(mergedClips, state.isDragging, originalVideoPath) {
        if (!state.isDragging && originalVideoPath.isNotEmpty() && mergedClips.isNotEmpty()) {
            val mediaItems = mergedClips.map { clip ->
                MediaItem.Builder()
                    .setUri(originalVideoPath.toUri().toString())
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startTrimMs)
                            .setEndPositionMs(clip.endTrimMs)
                            .build()
                    )
                    .build()
            }

            val changed = mediaItems.size != previousMediaItems.size ||
                mediaItems.zip(previousMediaItems).any { (new, old) ->
                    new.clippingConfiguration.startPositionMs != old.clippingConfiguration.startPositionMs ||
                    new.clippingConfiguration.endPositionMs != old.clippingConfiguration.endPositionMs ||
                    new.localConfiguration?.uri != old.localConfiguration?.uri
                }

            if (changed) {
                previousMediaItems = mediaItems
                // Try to preserve timeline position across playlist updates
                val oldWindowIndex = player.currentMediaItemIndex
                val oldPos = player.currentPosition
                val wasPlaying = player.playWhenReady
                
                player.setMediaItems(mediaItems)
                player.prepare()

                if (mediaItems.isNotEmpty()) {
                    player.seekTo(oldWindowIndex.coerceIn(0, mediaItems.size - 1), oldPos)
                }
                player.playWhenReady = wasPlaying
            }
        }
    }

    // Expose mergedClips to the state so that the polling loop can use them to calculate timeline Ms
    state.mergedClips = mergedClips
    
    return state
}

@Stable
class EditorState(
    val player: ExoPlayer,
    private val coroutineScope: CoroutineScope,
    private val onDurationUpdated: (Long) -> Unit
) {
    var isDragging by mutableStateOf(false)
    var currentTimelineMs by mutableStateOf(0L)
    var isPlaying by mutableStateOf(false)
    
    private var hasEmittedOriginalDuration = false
    
    // Updated by the composable remember function when clips change
    var mergedClips: List<Clip> = emptyList()

    private var progressJob: kotlinx.coroutines.Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = player.duration
                    if (duration > 0 && !hasEmittedOriginalDuration) {
                        hasEmittedOriginalDuration = true
                        onDurationUpdated(duration)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlayingParam: Boolean) {
                isPlaying = isPlayingParam
                if (isPlayingParam) {
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
        })
    }

    fun startProgressSync() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (isActive) {
                updateProgress()
                delay(16L) // ~60fps updates
            }
        }
    }

    fun stopProgressSync() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateProgress() {
        val windowIndex = player.currentMediaItemIndex
        val posInWindow = player.currentPosition
        
        val safeWindowIndex = windowIndex.coerceIn(0, maxOf(0, mergedClips.size - 1))
        
        var accumulated = 0L
        for (i in 0 until safeWindowIndex) {
            accumulated += (mergedClips[i].endTrimMs - mergedClips[i].startTrimMs)
        }
        
        // If windowIndex is out of bounds, clip posInWindow so we don't exceed duration
        val safePosInWindow = if (windowIndex >= mergedClips.size) 0L else posInWindow
        
        currentTimelineMs = accumulated + safePosInWindow
    }
}

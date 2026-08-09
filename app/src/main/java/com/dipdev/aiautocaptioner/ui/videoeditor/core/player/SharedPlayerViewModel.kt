package com.dipdev.aiautocaptioner.ui.videoeditor.core.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Navigation-graph-scoped ViewModel that owns the single ExoPlayer instance
 * shared between EditorScreen and StyleScreen.
 *
 * Both screens obtain this via:
 *   val parentEntry = remember(backStackEntry) {
 *       navController.getBackStackEntry(Screen.ProjectEditorGraph.route)
 *   }
 *   val sharedPlayerViewModel: SharedPlayerViewModel = hiltViewModel(parentEntry)
 *
 * The player is created once per project editor session and released when the
 * entire nested graph is popped from the back stack.
 */
@HiltViewModel
class SharedPlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    /** The video path the current player was initialised with. */
    private var loadedPath: String = ""

    private var isSuspendedForExport: Boolean = false
    private var savedPositionMs: Long = 0L
    private var savedMediaItemIndex: Int = 0
    private var savedPlayWhenReady: Boolean = false
    private var savedPlaylist: List<androidx.media3.common.MediaItem> = emptyList()

    /**
     * Initialise the player for [videoPath]. No-op if already initialised for the same path.
     * Safe to call from multiple screens on every recomposition.
     */
    fun initPlayer(videoPath: String) {
        if (videoPath.isEmpty()) return
        if (isSuspendedForExport) {
            resumePlayerFromExport()
            return
        }
        if (loadedPath == videoPath && _player.value != null) return

        // Release previous player if the path changed
        _player.value?.release()

        loadedPath = videoPath
        _player.value = ExoPlayer.Builder(appContext).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoPath))
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = false
            prepare()
        }
    }

    /**
     * Releases active ExoPlayer video surface and codec decoding buffers when navigating to Export.
     * Prevents mobile hardware video codec resource depletion (CodecException: NO_MEMORY) during Media3 Transformer rendering.
     */
    fun suspendPlayerForExport() {
        val exoPlayer = _player.value ?: return
        if (isSuspendedForExport) return
        isSuspendedForExport = true
        savedPositionMs = exoPlayer.currentPosition
        savedMediaItemIndex = exoPlayer.currentMediaItemIndex
        savedPlayWhenReady = exoPlayer.playWhenReady
        savedPlaylist = List(exoPlayer.mediaItemCount) { i -> exoPlayer.getMediaItemAt(i) }

        exoPlayer.pause()
        exoPlayer.clearVideoSurface()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    /**
     * Re-initializes media buffers and state when returning from Export screen.
     */
    fun resumePlayerFromExport() {
        val exoPlayer = _player.value ?: return
        if (!isSuspendedForExport) return
        isSuspendedForExport = false

        if (savedPlaylist.isNotEmpty()) {
            exoPlayer.setMediaItems(savedPlaylist, savedMediaItemIndex, savedPositionMs)
        } else if (loadedPath.isNotEmpty()) {
            exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(loadedPath))
            exoPlayer.seekTo(savedPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = savedPlayWhenReady

        savedPlaylist = emptyList()
        savedMediaItemIndex = 0
        savedPositionMs = 0L
    }

    /** Pause playback — call from lifecycle observers (ON_STOP / onDispose). */
    fun pauseForBackground() {
        _player.value?.pause()
    }

    override fun onCleared() {
        _player.value?.release()
        _player.value = null
    }
}

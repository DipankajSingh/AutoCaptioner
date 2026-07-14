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
 *       navController.getBackStackEntry("project_editor_graph/$projectId")
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

    /**
     * Initialise the player for [videoPath]. No-op if already initialised for the same path.
     * Safe to call from multiple screens on every recomposition.
     */
    fun initPlayer(videoPath: String) {
        if (videoPath.isEmpty()) return
        if (loadedPath == videoPath && _player.value != null) return

        // Release previous player if the path changed
        _player.value?.release()

        loadedPath = videoPath
        _player.value = ExoPlayer.Builder(appContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = false
        }
    }

    /** Pause playback — call from lifecycle observers (ON_STOP / onDispose). */
    fun pauseForBackground() {
        _player.value?.pause()
    }

    override fun onCleared() {
        super.onCleared()
        _player.value?.release()
        _player.value = null
    }
}

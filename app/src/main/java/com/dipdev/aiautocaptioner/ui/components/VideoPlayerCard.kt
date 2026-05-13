package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * A self-contained ExoPlayer video card.
 *
 * Creates, configures and releases an [ExoPlayer] instance tied to the
 * Compose lifecycle. Use [showControls] to toggle the playback control bar.
 *
 * @param path         Absolute file path or content URI string.
 * @param modifier     Applied to the [AndroidView].
 * @param loop         When true, repeats playback indefinitely.
 * @param autoPlay     When true, starts playing as soon as the player is ready.
 * @param showControls When true, the Media3 control bar is visible.
 * @param cornerRadius Corner radius for the clip shape.
 */
@Composable
fun VideoPlayerCard(
    path: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    loop: Boolean = true,
    autoPlay: Boolean = true,
    showControls: Boolean = true,
    cornerRadius: Dp = 4.dp
) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(path))
            repeatMode    = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            playWhenReady = autoPlay
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = showControls
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        update  = { view -> view.player = player },
        modifier = modifier.clip(RoundedCornerShape(cornerRadius))
    )
}

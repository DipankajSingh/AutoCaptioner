

package com.dipdev.aiautocaptioner.ui.components

import androidx.annotation.OptIn
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
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.media3.common.util.UnstableApi

/**
 * A reusable Video Player component that binds to an external Media3 [Player].
 *
 * @param player       The Media3 Player instance.
 * @param modifier     Applied to the container.
 * @param showControls When true, the Media3 control bar is visible.
 * @param cornerRadius Corner radius for the clip shape.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerCard(
    modifier: Modifier = Modifier,
    player: Player?,
    showControls: Boolean = false,
    cornerRadius: Dp = 4.dp
) {
    Box(modifier = modifier.clip(RoundedCornerShape(cornerRadius))) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = showControls
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            update  = { view -> 
                view.player = player 
                view.useController = showControls
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Transparent overlay for tap-to-play/pause if controls are disabled
        if (!showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        player?.let { p ->
                            p.playWhenReady = !p.playWhenReady
                        }
                    }
            )
        }
    }
}

/**
 * A self-contained ExoPlayer video card that manages its own player.
 *
 * @param path         Absolute file path or content URI string.
 * @param modifier     Applied to the container.
 * @param loop         When true, repeats playback indefinitely.
 * @param autoPlay     When true, starts playing as soon as the player is ready.
 * @param showControls When true, the Media3 control bar is visible.
 * @param cornerRadius Corner radius for the clip shape.
 */
@Composable
fun VideoPlayerCard(
    modifier: Modifier = Modifier,
    path: String,
    loop: Boolean = true,
    autoPlay: Boolean = true,
    showControls: Boolean = true,
    cornerRadius: Dp = 4.dp
) {
    val context = LocalContext.current
    val player = remember(path) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(path))
            repeatMode    = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            playWhenReady = autoPlay
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    VideoPlayerCard(
        player = player,
        modifier = modifier,
        showControls = showControls,
        cornerRadius = cornerRadius
    )
}

package com.boomstream.sdk.player.internal

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.boomstream.sdk.player.PlayerEvent
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Translates ExoPlayer [Player.Listener] callbacks into public [PlayerEvent] emissions.
 *
 * Extracted as a named class (rather than an anonymous object) so unit tests can call its
 * methods directly without an Android runtime — pass controllable lambdas for
 * [currentPositionMs], [durationMs], and [playbackState].
 *
 * **CSO constraint:** no ExoPlayer or Media3 types appear in the emitted [PlayerEvent] fields —
 * only primitive `Long` / `Boolean` values are carried.
 */
@OptIn(UnstableApi::class)
internal class PlayerEventEmitter(
    private val events: MutableSharedFlow<PlayerEvent>,
    private val currentPositionMs: () -> Long,
    private val durationMs: () -> Long,
    private val playbackState: () -> Int,
) : Player.Listener {

    private var loadedEmitted = false

    /** Reset per-item loaded guard on new media (call from [Player.Listener.onMediaItemTransition] and from [load]). */
    internal fun resetForNewMediaItem() {
        loadedEmitted = false
    }

    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_READY -> {
                if (!loadedEmitted) {
                    loadedEmitted = true
                    events.tryEmit(PlayerEvent.Loaded(durationMs()))
                }
            }
            Player.STATE_ENDED -> events.tryEmit(PlayerEvent.Ended)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            events.tryEmit(PlayerEvent.Playing(currentPositionMs()))
        } else if (playbackState() != Player.STATE_ENDED) {
            events.tryEmit(PlayerEvent.Paused(currentPositionMs()))
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            onSeeked(newPosition.positionMs)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        resetForNewMediaItem()
    }

    /**
     * Called when a seek completes.  Extracted as a public-within-module helper so tests can
     * verify seek event emission without constructing [Player.PositionInfo].
     */
    internal fun onSeeked(positionMs: Long) {
        events.tryEmit(PlayerEvent.Seeked(positionMs))
    }
}

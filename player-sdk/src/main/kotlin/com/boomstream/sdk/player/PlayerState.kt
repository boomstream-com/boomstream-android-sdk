package com.boomstream.sdk.player

/**
 * Represents the current playback / loading state of [BoomstreamPlayer] and [BoomstreamPlayerView].
 *
 * Observe via [BoomstreamPlayerView.stateFlow] or the `onState` callback parameter of the
 * [BoomstreamPlayer] Composable.
 *
 * Transitions:
 * ```
 * Idle ──► Loading ──► Ready        (authenticated single/playlist)
 *                  └──► PosterOnly  (unauthenticated — no mediaData in config)
 *                  └──► Error
 * Ready ──► Ended
 * Ready / Loading ──► Error         (playback error or config fetch failure)
 * ```
 */
sealed class PlayerState {

    /** Initial state — player has not started loading yet. */
    object Idle : PlayerState()

    /** Config is being fetched from `play.boomstream.com/{mediaCode}/config` or the
     *  player is buffering before first frame. */
    object Loading : PlayerState()

    /**
     * Config resolved successfully and playback has started (or is paused by the user).
     *
     * @param title     Title of the currently playing item.
     * @param isPlaylist `true` when the media code resolved to a playlist (`mediaData` array).
     * @param playlistIndex Zero-based index of the currently active item (always 0 for single media).
     * @param playlistSize  Total items in the playlist (always 1 for single media).
     * @param isLive    `true` when the currently playing item is a live stream. Consumers should
     *                  hide the timeline/scrubbing UI when this is `true`. Updated on every
     *                  playlist item transition, so it reflects the current item.
     * @param systemMessage Optional dismissible informational overlay shown at the top of the player.
     *   Currently set to the `playing_record` message when offline-live recordings are playing.
     *   The UI layer is responsible for offering a dismiss control (e.g. click-to-hide).
     */
    data class Ready(
        val title: String,
        val isPlaylist: Boolean,
        val playlistIndex: Int = 0,
        val playlistSize: Int = 1,
        val isLive: Boolean = false,
        val systemMessage: String? = null,
    ) : PlayerState()

    /**
     * Config endpoint returned a degraded (unauthenticated) response, or access is explicitly
     * restricted for the current viewer. Only a poster/thumbnail is shown; no video playback
     * is possible.
     *
     * @param posterUrl URL of the best-quality poster image, or `null` if none was available.
     * @param message   Non-null when the server provided an `accessRestricted` message (PPV /
     *                  subscription gate / preview expiry). Rendered as a system-message overlay
     *                  at the top of the poster. `null` for plain unauthenticated responses.
     */
    data class PosterOnly(val posterUrl: String?, val message: String? = null) : PlayerState()

    /**
     * A non-recoverable error occurred (network failure, HTTP error, or ExoPlayer error).
     *
     * @param message Human-readable description. **Do not display raw to end-users** — localise
     *   in your UI layer.
     */
    data class Error(val message: String) : PlayerState()

    /** All items in the playlist (or the single item) have finished playing. */
    object Ended : PlayerState()
}

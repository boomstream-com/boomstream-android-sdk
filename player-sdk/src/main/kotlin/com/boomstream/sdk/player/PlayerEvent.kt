package com.boomstream.sdk.player

/**
 * Discrete playback events emitted by [BoomstreamPlayerController.events].
 *
 * All numeric fields carry only position/duration/flag data — no URLs, tokens, or keys.
 * This invariant is enforced by the CSO reflection gate in `ConstraintOneReflectionTest`.
 */
sealed class PlayerEvent {

    /** Media is ready and duration is known. Emitted once per media item on first STATE_READY. */
    data class Loaded(val durationMs: Long) : PlayerEvent()

    /** Playback transitioned to the playing state (play() called or playWhenReady). */
    data class Playing(val positionMs: Long) : PlayerEvent()

    /** Playback was paused by the user or the host. Not emitted when playback ends naturally. */
    data class Paused(val positionMs: Long) : PlayerEvent()

    /** All items in the queue have finished playing. */
    object Ended : PlayerEvent()

    /**
     * Periodic progress tick while the player is playing (optional stream — primary consumers
     * should prefer [BoomstreamPlayerController.progressFlow] which is a StateFlow and does not
     * miss the latest value on late subscription).
     */
    data class Progress(val positionMs: Long, val durationMs: Long, val percent: Float) : PlayerEvent()

    /** A programmatic or user-initiated seek completed. */
    data class Seeked(val positionMs: Long) : PlayerEvent()

    /** The fullscreen state changed via [BoomstreamPlayerController.setFullScreen] or [BoomstreamPlayerController.toggleFullScreen]. */
    data class FullScreenChanged(val isFullScreen: Boolean) : PlayerEvent()

    /** The active video quality changed via [BoomstreamPlayerController.selectQuality] or [BoomstreamPlayerController.selectAuto]. */
    data class QualityChanged(val quality: VideoQuality) : PlayerEvent()
}

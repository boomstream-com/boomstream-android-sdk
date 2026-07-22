package com.boomstream.sdk.player

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Public contract for programmatic player control and event observation.
 *
 * Obtain an instance from:
 * - [BoomstreamPlayerView.controller] — for the View-based integration path.
 * - [rememberBoomstreamPlayerController] — for the Compose integration path.
 *
 * **CSO constraint #1:** this interface returns only primitives, data classes, and Flow types.
 * No `androidx.media3.*` types appear in the public signature.
 *
 * **Thread safety:** control methods must be called from the main thread (mirrors the
 * ExoPlayer threading contract). Flow collection is safe from any thread.
 */
interface BoomstreamPlayerController {

    /** Hot stream of discrete playback events. `replay = 0` — late subscribers miss past events. */
    val events: SharedFlow<PlayerEvent>

    /**
     * Current playback progress, updated every ~300 ms while [PlayerState.Ready] and playing.
     * Updated on pause/seek. `durationMs = -1` until [PlayerEvent.Loaded] arrives.
     */
    val progressFlow: StateFlow<PlaybackProgress>

    /** Mirror of [BoomstreamPlayerView.stateFlow] for consumers who only hold the controller. */
    val state: StateFlow<PlayerState>

    /** Returns the current playback position in milliseconds, or `0` if not yet loaded. */
    fun getCurrentPosition(): Long

    /**
     * Returns the total duration in milliseconds, or `-1` when the duration is unknown
     * (before [PlayerEvent.Loaded], or for live streams with indeterminate length).
     */
    fun getDuration(): Long

    /** Resumes playback. No-op if not yet loaded. */
    fun play()

    /** Pauses playback. No-op if not yet loaded. */
    fun pause()

    /** Seeks to [positionMs]. No-op if not yet loaded. */
    fun seekTo(positionMs: Long)

    /**
     * Seeks to [percent] × duration. [percent] is clamped to `[0f..1f]`.
     * No-op if duration is unknown.
     */
    fun seekToPercent(percent: Float)

    /** Sets the audio volume. [percent] is clamped to `[0..100]`. */
    fun setVolume(percent: Int)

    /** Mutes the audio (equivalent to `setVolume(0)`). */
    fun mute()

    /** Restores the audio to full volume (equivalent to `setVolume(100)`). */
    fun unmute()

    /** Advances to the next playlist item. No-op on single-item media. */
    fun next()

    /** Returns to the previous playlist item. No-op on single-item media. */
    fun previous()

    /**
     * Programmatically sets the fullscreen state and emits [PlayerEvent.FullScreenChanged].
     * The SDK does not perform any orientation or window-flag changes — the host Activity owns that.
     */
    fun setFullScreen(isFullScreen: Boolean)

    /** Toggles the current fullscreen state and emits [PlayerEvent.FullScreenChanged]. */
    fun toggleFullScreen()

    /**
     * Available video quality renditions discovered from the HLS master manifest.
     * Empty until the first track-ready event; resets to empty on each new [load].
     * Renditions are sorted highest-resolution first.
     */
    val availableQualities: StateFlow<List<VideoQuality>>

    /**
     * Currently selected quality. [VideoQuality.Auto] by default and after each [load].
     * Updated synchronously when [selectQuality] or [selectAuto] is called.
     */
    val currentQuality: StateFlow<VideoQuality>

    /**
     * Locks playback to [quality]. The rendition change takes effect on the next segment
     * boundary without requiring a reload.
     * Emits [PlayerEvent.QualityChanged].
     * No-op if the player is not yet loaded.
     */
    fun selectQuality(quality: VideoQuality)

    /**
     * Clears any quality override and returns to adaptive bitrate selection.
     * Emits [PlayerEvent.QualityChanged] with [VideoQuality.Auto].
     * No-op if the player is not yet loaded.
     */
    fun selectAuto()
}

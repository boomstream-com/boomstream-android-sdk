package com.boomstream.sdk.player

/**
 * A snapshot of the current playback position and duration.
 *
 * Emitted by [BoomstreamPlayerController.progressFlow] every ~300 ms while the player is
 * actively playing (the polling loop freezes when paused — no redundant updates).
 *
 * @param positionMs  Current playback position in milliseconds.
 * @param durationMs  Total duration in milliseconds, or **-1** when the duration is not yet
 *                    known (e.g. before the first [PlayerEvent.Loaded] or for live streams
 *                    whose duration is indeterminate).
 * @param percent     Playback progress in the range `[0f..1f]`.  Always `0f` when
 *                    [durationMs] ≤ 0.
 */
data class PlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
    val percent: Float,
)

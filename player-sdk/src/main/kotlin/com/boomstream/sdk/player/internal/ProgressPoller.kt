package com.boomstream.sdk.player.internal

import com.boomstream.sdk.player.PlaybackProgress
import kotlinx.coroutines.delay

/**
 * Suspending loop that emits [PlaybackProgress] at [intervalMs] intervals while [isPlaying]
 * returns `true`.  When paused the loop keeps running but skips emission — no coroutine
 * cancellation and recreation on every pause/resume cycle.
 *
 * [durationMs] should return `-1` when the duration is unknown; [percent] is then `0f`.
 *
 * Extracted as a standalone `suspend fun` so it can be unit-tested without an Android
 * runtime by injecting fake clocks via [kotlinx.coroutines.test.runTest].
 */
internal suspend fun progressPollLoop(
    intervalMs: Long = 300L,
    isPlaying: () -> Boolean,
    currentPositionMs: () -> Long,
    durationMs: () -> Long,
    onProgress: (PlaybackProgress) -> Unit,
) {
    while (true) {
        delay(intervalMs)
        if (isPlaying()) {
            val pos = currentPositionMs()
            val dur = durationMs()
            val percent = if (dur <= 0L) 0f else (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            onProgress(PlaybackProgress(pos, dur, percent))
        }
    }
}

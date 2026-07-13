package com.boomstream.sdk.player

import com.boomstream.sdk.player.internal.progressPollLoop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [progressPollLoop] — verifies emission cadence and pause/resume behaviour.
 *
 * Uses [kotlinx.coroutines.test.runTest] + [advanceTimeBy] so `delay()` inside the
 * loop is skipped by the test scheduler, making the tests instant and deterministic.
 */
class ProgressFlowEmissionTest {

    // ── Emission cadence ──────────────────────────────────────────────────────

    @Test
    fun `emits every 300ms while playing`() = runTest {
        val emissions = mutableListOf<PlaybackProgress>()

        backgroundScope.launch {
            progressPollLoop(
                intervalMs = 300L,
                isPlaying = { true },
                currentPositionMs = { 5_000L },
                durationMs = { 120_000L },
                onProgress = { emissions.add(it) },
            )
        }

        advanceTimeBy(950L) // 3 complete 300 ms intervals: t=300, t=600, t=900
        assertEquals("Expected 3 emissions in 950 ms", 3, emissions.size)

        advanceTimeBy(300L) // fourth interval
        assertEquals("Expected 4 emissions in 1250 ms", 4, emissions.size)
    }

    @Test
    fun `emitted PlaybackProgress has correct fields`() = runTest {
        val emissions = mutableListOf<PlaybackProgress>()
        val position = 30_000L
        val duration = 120_000L

        backgroundScope.launch {
            progressPollLoop(
                intervalMs = 300L,
                isPlaying = { true },
                currentPositionMs = { position },
                durationMs = { duration },
                onProgress = { emissions.add(it) },
            )
        }

        advanceTimeBy(310L)
        assertEquals(1, emissions.size)

        val p = emissions[0]
        assertEquals(position, p.positionMs)
        assertEquals(duration, p.durationMs)
        assertEquals(0.25f, p.percent, 0.001f) // 30_000 / 120_000 = 0.25
    }

    // ── Pause/resume behaviour ────────────────────────────────────────────────

    @Test
    fun `does NOT emit while paused`() = runTest {
        val emissions = mutableListOf<PlaybackProgress>()
        var isPlaying = true

        backgroundScope.launch {
            progressPollLoop(
                intervalMs = 300L,
                isPlaying = { isPlaying },
                currentPositionMs = { 10_000L },
                durationMs = { 60_000L },
                onProgress = { emissions.add(it) },
            )
        }

        advanceTimeBy(950L) // 3 emissions while playing
        assertEquals(3, emissions.size)

        isPlaying = false
        advanceTimeBy(900L) // 3 more intervals — but paused, so no new emissions
        assertEquals("No new emissions expected while paused", 3, emissions.size)
    }

    @Test
    fun `resumes emitting after un-pause without loop restart`() = runTest {
        val emissions = mutableListOf<PlaybackProgress>()
        var isPlaying = true

        backgroundScope.launch {
            progressPollLoop(
                intervalMs = 300L,
                isPlaying = { isPlaying },
                currentPositionMs = { 10_000L },
                durationMs = { 60_000L },
                onProgress = { emissions.add(it) },
            )
        }

        advanceTimeBy(310L)         // 1 emission
        assertEquals(1, emissions.size)

        isPlaying = false
        advanceTimeBy(900L)         // 3 skipped intervals
        assertEquals(1, emissions.size) // still 1

        isPlaying = true
        advanceTimeBy(310L)         // 1 new emission
        assertEquals(2, emissions.size)
    }

    // ── Unknown duration ──────────────────────────────────────────────────────

    @Test
    fun `percent is 0f when durationMs is -1`() = runTest {
        val emissions = mutableListOf<PlaybackProgress>()

        backgroundScope.launch {
            progressPollLoop(
                intervalMs = 300L,
                isPlaying = { true },
                currentPositionMs = { 5_000L },
                durationMs = { -1L }, // unknown — simulates live/pre-Loaded
                onProgress = { emissions.add(it) },
            )
        }

        advanceTimeBy(310L)
        assertEquals(1, emissions.size)

        val p = emissions[0]
        assertEquals(-1L, p.durationMs)
        assertEquals(0f, p.percent, 0f)
    }

    // ── percent clamping ──────────────────────────────────────────────────────

    @Test
    fun `percent is clamped to 1f when position exceeds duration`() = runTest {
        val emissions = mutableListOf<PlaybackProgress>()

        backgroundScope.launch {
            progressPollLoop(
                intervalMs = 300L,
                isPlaying = { true },
                currentPositionMs = { 200_000L }, // beyond duration
                durationMs = { 120_000L },
                onProgress = { emissions.add(it) },
            )
        }

        advanceTimeBy(310L)
        assertEquals(1, emissions.size)
        assertTrue("percent should be ≤ 1f", emissions[0].percent <= 1f)
        assertEquals(1f, emissions[0].percent, 0f)
    }
}

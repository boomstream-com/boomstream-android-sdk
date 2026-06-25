package com.boomstream.sdk.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DownloadState] sealed class.
 *
 * Verifies that each subclass carries the right data and that simple equality / type checks
 * work as expected.
 */
class DownloadStateTest {

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `Downloading carries progress in 0-1 range`() {
        val state = DownloadState.Downloading(progress = 0.42f)
        assertEquals(0.42f, state.progress, 0.001f)
    }

    @Test
    fun `Downloading with 1f represents complete fraction`() {
        val state = DownloadState.Downloading(progress = 1f)
        assertEquals(1f, state.progress, 0.001f)
    }

    @Test
    fun `Completed is a singleton-like object`() {
        val a: DownloadState = DownloadState.Completed
        val b: DownloadState = DownloadState.Completed
        assertTrue(a === b)
    }

    @Test
    fun `NotDownloaded is a singleton-like object`() {
        val a: DownloadState = DownloadState.NotDownloaded
        val b: DownloadState = DownloadState.NotDownloaded
        assertTrue(a === b)
    }

    @Test
    fun `Failed carries the error message`() {
        val state = DownloadState.Failed("Connection reset")
        assertEquals("Connection reset", state.message)
    }

    @Test
    fun `Queued is distinguishable from Downloading`() {
        val queued: DownloadState = DownloadState.Queued
        assertFalse(queued is DownloadState.Downloading)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `Downloading equality is value-based`() {
        val a = DownloadState.Downloading(0.5f)
        val b = DownloadState.Downloading(0.5f)
        assertEquals(a, b)
    }

    @Test
    fun `Downloading with different progress values are not equal`() {
        val a = DownloadState.Downloading(0.3f)
        val b = DownloadState.Downloading(0.7f)
        assertFalse(a == b)
    }

    @Test
    fun `Failed with different messages are not equal`() {
        val a = DownloadState.Failed("timeout")
        val b = DownloadState.Failed("no network")
        assertFalse(a == b)
    }

    @Test
    fun `DownloadState when exhaustive covers all branches`() {
        val states: List<DownloadState> = listOf(
            DownloadState.Queued,
            DownloadState.Downloading(0.1f),
            DownloadState.Completed,
            DownloadState.Failed("x"),
            DownloadState.Removing,
            DownloadState.NotDownloaded,
        )
        val labels = states.map { state ->
            when (state) {
                is DownloadState.Queued -> "queued"
                is DownloadState.Downloading -> "downloading"
                is DownloadState.Completed -> "completed"
                is DownloadState.Failed -> "failed"
                is DownloadState.Removing -> "removing"
                is DownloadState.NotDownloaded -> "not_downloaded"
            }
        }
        assertEquals(
            listOf("queued", "downloading", "completed", "failed", "removing", "not_downloaded"),
            labels,
        )
    }
}

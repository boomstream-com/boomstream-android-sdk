package com.boomstream.sdk.player

import androidx.media3.common.Player
import com.boomstream.sdk.player.internal.PlayerEventEmitter
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PlayerEventEmitter] — verifies that ExoPlayer [Player.Listener] callbacks
 * map to the correct [PlayerEvent] variants.
 *
 * Tests run on the JVM without an Android runtime.  [PlayerEventEmitter] accepts lambdas for
 * position/duration/state so we can inject controlled values.
 *
 * Pattern: always call [runCurrent] once after [backgroundScope.launch] to let the collector
 * subscribe before emitting — [MutableSharedFlow] with `replay=0` drops events when no
 * subscriber exists yet.
 */
class PlayerEventMappingTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeEmitter(
        events: MutableSharedFlow<PlayerEvent>,
        positionMs: Long = 0L,
        durationMs: Long = -1L,
        playbackState: Int = Player.STATE_IDLE,
    ) = PlayerEventEmitter(
        events = events,
        currentPositionMs = { positionMs },
        durationMs = { durationMs },
        playbackState = { playbackState },
    )

    // ── STATE_READY → Loaded ──────────────────────────────────────────────────

    @Test
    fun `STATE_READY emits Loaded with correct durationMs`() = runTest {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        val collected = mutableListOf<PlayerEvent>()
        backgroundScope.launch { events.collect { collected.add(it) } }
        runCurrent() // let collector subscribe before first emission

        val e = PlayerEventEmitter(
            events = events,
            currentPositionMs = { 0L },
            durationMs = { 120_000L },
            playbackState = { Player.STATE_READY },
        )
        e.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()

        assertEquals(1, collected.size)
        val loaded = collected[0] as? PlayerEvent.Loaded
        assertEquals(120_000L, loaded?.durationMs)
    }

    @Test
    fun `STATE_READY emits Loaded only once per media item`() = runTest {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        val collected = mutableListOf<PlayerEvent>()
        backgroundScope.launch { events.collect { collected.add(it) } }
        runCurrent()

        val e = PlayerEventEmitter(
            events = events,
            currentPositionMs = { 0L },
            durationMs = { 60_000L },
            playbackState = { Player.STATE_READY },
        )

        e.onPlaybackStateChanged(Player.STATE_READY)
        e.onPlaybackStateChanged(Player.STATE_READY) // rebuffer → READY again
        runCurrent()

        // Only one Loaded event despite two STATE_READY callbacks.
        assertEquals(1, collected.filterIsInstance<PlayerEvent.Loaded>().size)
    }

    @Test
    fun `Loaded emitted again after media item transition`() = runTest {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        val collected = mutableListOf<PlayerEvent>()
        backgroundScope.launch { events.collect { collected.add(it) } }
        runCurrent()

        val e = PlayerEventEmitter(
            events = events,
            currentPositionMs = { 0L },
            durationMs = { 60_000L },
            playbackState = { Player.STATE_READY },
        )

        e.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()
        assertEquals(1, collected.filterIsInstance<PlayerEvent.Loaded>().size)

        // Simulate playlist advance: resets the loaded guard.
        e.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        e.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()

        assertEquals(2, collected.filterIsInstance<PlayerEvent.Loaded>().size)
    }

    // ── STATE_ENDED → Ended ───────────────────────────────────────────────────

    @Test
    fun `STATE_ENDED emits Ended`() = runTest {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        val collected = mutableListOf<PlayerEvent>()
        backgroundScope.launch { events.collect { collected.add(it) } }
        runCurrent()

        makeEmitter(events).onPlaybackStateChanged(Player.STATE_ENDED)
        runCurrent()

        assertEquals(1, collected.size)
        assertTrue(collected[0] is PlayerEvent.Ended)
    }

    // ── isPlaying → Playing / Paused ─────────────────────────────────────────

    @Test
    fun `onIsPlayingChanged true emits Playing with current position`() = runTest {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        val collected = mutableListOf<PlayerEvent>()
        backgroundScope.launch { events.collect { collected.add(it) } }
        runCurrent()

        val e = PlayerEventEmitter(
            events = events,
            currentPositionMs = { 5_000L },
            durationMs = { 120_000L },
            playbackState = { Player.STATE_READY },
        )
        e.onIsPlayingChanged(true)
        runCurrent()

        assertEquals(1, collected.size)
        assertEquals(PlayerEvent.Playing(5_000L), collected[0])
    }

    @Test
    fun `onIsPlayingChanged false emits Paused when not ended`() = runTest {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        val collected = mutableListOf<PlayerEvent>()
        backgroundScope.launch { events.collect { collected.add(it) } }
        runCurrent()

        val e = PlayerEventEmitter(
            events = events,
            currentPositionMs = { 30_000L },
            durationMs = { 120_000L },
            playbackState = { Player.STATE_READY },
        )
        e.onIsPlayingChanged(false)
        runCurrent()

        assertEquals(1, collected.size)
        assertEquals(PlayerEvent.Paused(30_000L), collected[0])
    }

    @Test
    fun `onIsPlayingChanged false does NOT emit Paused when playback ended`() = runTest {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        val collected = mutableListOf<PlayerEvent>()
        backgroundScope.launch { events.collect { collected.add(it) } }
        runCurrent()

        val e = PlayerEventEmitter(
            events = events,
            currentPositionMs = { 120_000L },
            durationMs = { 120_000L },
            playbackState = { Player.STATE_ENDED },
        )
        e.onIsPlayingChanged(false) // fires when STATE_ENDED stops the clock
        runCurrent()

        assertTrue("No Paused event expected on natural end", collected.none { it is PlayerEvent.Paused })
    }

    // ── Seek → Seeked ─────────────────────────────────────────────────────────

    @Test
    fun `onSeeked emits Seeked with target position`() = runTest {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        val collected = mutableListOf<PlayerEvent>()
        backgroundScope.launch { events.collect { collected.add(it) } }
        runCurrent()

        makeEmitter(events).onSeeked(42_000L)
        runCurrent()

        assertEquals(1, collected.size)
        assertEquals(PlayerEvent.Seeked(42_000L), collected[0])
    }

    // ── resetForNewMediaItem ──────────────────────────────────────────────────

    @Test
    fun `resetForNewMediaItem allows Loaded to fire again`() = runTest {
        val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        val collected = mutableListOf<PlayerEvent>()
        backgroundScope.launch { events.collect { collected.add(it) } }
        runCurrent()

        val e = makeEmitter(events, durationMs = 90_000L)
        e.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()
        assertEquals(1, collected.size)

        e.resetForNewMediaItem()
        e.onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()
        assertEquals(2, collected.size)
        assertEquals(2, collected.filterIsInstance<PlayerEvent.Loaded>().size)
    }
}

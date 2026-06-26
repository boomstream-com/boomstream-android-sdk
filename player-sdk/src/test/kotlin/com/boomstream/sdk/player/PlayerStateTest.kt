package com.boomstream.sdk.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateTest {

    // ── Identity / equality ────────────────────────────────────────────────────

    @Test
    fun `Idle is a singleton object`() {
        assertTrue(PlayerState.Idle === PlayerState.Idle)
    }

    @Test
    fun `Loading is a singleton object`() {
        assertTrue(PlayerState.Loading === PlayerState.Loading)
    }

    @Test
    fun `Ready equality depends on all fields`() {
        val a = PlayerState.Ready("Title", isPlaylist = false)
        val b = PlayerState.Ready("Title", isPlaylist = false)
        val c = PlayerState.Ready("Other", isPlaylist = false)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `PosterOnly with null url is distinct from non-null`() {
        val withNull = PlayerState.PosterOnly(null)
        val withUrl = PlayerState.PosterOnly("https://cdn.example.com/poster.jpg")
        assertNotEquals(withNull, withUrl)
        assertNull(withNull.posterUrl)
    }

    @Test
    fun `PosterOnly message defaults to null for unauthenticated access`() {
        val state = PlayerState.PosterOnly("https://cdn.example.com/poster.jpg")
        assertNull("No message for plain unauthenticated response", state.message)
    }

    @Test
    fun `PosterOnly carries accessRestricted message when set`() {
        val state = PlayerState.PosterOnly(
            posterUrl = "https://cdn.example.com/poster.jpg",
            message = "Доступ ограничен",
        )
        assertEquals("Доступ ограничен", state.message)
        assertEquals("https://cdn.example.com/poster.jpg", state.posterUrl)
    }

    @Test
    fun `PosterOnly with message is distinct from PosterOnly without message`() {
        val plain = PlayerState.PosterOnly("https://cdn.example.com/poster.jpg")
        val restricted = PlayerState.PosterOnly("https://cdn.example.com/poster.jpg", "Access denied")
        assertNotEquals(plain, restricted)
    }

    @Test
    fun `Error carries the message`() {
        val error = PlayerState.Error("Network timeout")
        assertEquals("Network timeout", error.message)
    }

    @Test
    fun `Ended is a singleton object`() {
        assertTrue(PlayerState.Ended === PlayerState.Ended)
    }

    // ── Ready playlist fields ──────────────────────────────────────────────────

    @Test
    fun `Ready defaults to single media (not playlist)`() {
        val state = PlayerState.Ready("My Video", isPlaylist = false)
        assertFalse(state.isPlaylist)
        assertEquals(0, state.playlistIndex)
        assertEquals(1, state.playlistSize)
    }

    @Test
    fun `Ready playlist tracks index and size`() {
        val state = PlayerState.Ready(
            title = "Part 2",
            isPlaylist = true,
            playlistIndex = 1,
            playlistSize = 5,
        )
        assertTrue(state.isPlaylist)
        assertEquals(1, state.playlistIndex)
        assertEquals(5, state.playlistSize)
    }

    @Test
    fun `Ready copy updates playlist index`() {
        val initial = PlayerState.Ready("Item 1", isPlaylist = true, playlistSize = 3)
        val advanced = initial.copy(title = "Item 2", playlistIndex = 1)
        assertEquals(1, advanced.playlistIndex)
        assertEquals(3, advanced.playlistSize)
    }

    // ── isLive field ──────────────────────────────────────────────────────────

    @Test
    fun `Ready defaults isLive to false`() {
        val state = PlayerState.Ready("My Video", isPlaylist = false)
        assertFalse(state.isLive)
    }

    @Test
    fun `Ready with isLive true carries the flag`() {
        val state = PlayerState.Ready("Live Stream", isPlaylist = false, isLive = true)
        assertTrue(state.isLive)
    }

    @Test
    fun `Ready isLive distinguishes live from vod in equality`() {
        val vod = PlayerState.Ready("Show", isPlaylist = false, isLive = false)
        val live = PlayerState.Ready("Show", isPlaylist = false, isLive = true)
        assertNotEquals(vod, live)
    }

    @Test
    fun `Ready copy can update isLive for playlist item transition`() {
        val initial = PlayerState.Ready("Ep 1", isPlaylist = true, playlistSize = 2, isLive = false)
        val transitioned = initial.copy(title = "Live item", playlistIndex = 1, isLive = true)
        assertTrue(transitioned.isLive)
        assertEquals(1, transitioned.playlistIndex)
        assertEquals(2, transitioned.playlistSize)
    }

    // ── systemMessage field (BOO-703 playing_record overlay) ─────────────────

    @Test
    fun `Ready systemMessage defaults to null`() {
        val state = PlayerState.Ready("Recording 1", isPlaylist = true)
        assertNull("systemMessage must be null by default", state.systemMessage)
    }

    @Test
    fun `Ready carries playing_record system message`() {
        val state = PlayerState.Ready(
            title = "Recording 1",
            isPlaylist = true,
            systemMessage = "Playing recording",
        )
        assertEquals("Playing recording", state.systemMessage)
    }

    @Test
    fun `Ready with systemMessage is distinct from Ready without`() {
        val withMsg = PlayerState.Ready("Rec", isPlaylist = true, systemMessage = "Playing recording")
        val withoutMsg = PlayerState.Ready("Rec", isPlaylist = true)
        assertNotEquals(withMsg, withoutMsg)
    }

    @Test
    fun `Ready copy preserves systemMessage across playlist item transitions`() {
        val initial = PlayerState.Ready(
            title = "Rec 1",
            isPlaylist = true,
            playlistSize = 3,
            systemMessage = "Playing recording",
        )
        val transitioned = initial.copy(title = "Rec 2", playlistIndex = 1)
        assertEquals("Playing recording", transitioned.systemMessage)
        assertEquals(1, transitioned.playlistIndex)
    }

    @Test
    fun `Ready copy can clear systemMessage when dismissed`() {
        val withMsg = PlayerState.Ready("Rec 1", isPlaylist = true, systemMessage = "Playing recording")
        val dismissed = withMsg.copy(systemMessage = null)
        assertNull(dismissed.systemMessage)
        assertEquals("Rec 1", dismissed.title)
    }

    // ── Nav-button visibility mapping ─────────────────────────────────────────

    @Test
    fun `navButtonsVisible returns false for single-video Ready`() {
        assertFalse(
            "Single video must hide prev/next controls (drives setShowPreviousButton/setShowNextButton=false)",
            PlayerState.Ready("Video", isPlaylist = false).navButtonsVisible(),
        )
    }

    @Test
    fun `navButtonsVisible returns true for playlist Ready`() {
        assertTrue(
            "Playlist must show prev/next controls (drives setShowPreviousButton/setShowNextButton=true)",
            PlayerState.Ready("Part 1", isPlaylist = true, playlistIndex = 0, playlistSize = 3).navButtonsVisible(),
        )
    }

    @Test
    fun `navButtonsVisible returns false for all non-Ready states`() {
        val nonReadyStates: List<PlayerState> = listOf(
            PlayerState.Idle,
            PlayerState.Loading,
            PlayerState.Ended,
            PlayerState.PosterOnly(null),
            PlayerState.Error("err"),
        )
        nonReadyStates.forEach { state ->
            assertFalse(
                "${state::class.simpleName} must not show nav controls",
                state.navButtonsVisible(),
            )
        }
    }

    // ── CSO constraint #1 smoke — ExoPlayer not in public API ─────────────────

    private companion object {
        private const val EXO_PLAYER_FQN = "androidx.media3.exoplayer.ExoPlayer"

        // Public file-classes + classes that must NOT expose ExoPlayer.
        // Add new public surfaces here — Constraint #1 reflection test will cover them automatically.
        private val SCANNED_CLASSES = listOf(
            "com.boomstream.sdk.player.BoomstreamPlayerKt",
            "com.boomstream.sdk.player.BoomstreamPlayerView",
        )
    }

    private fun java.lang.reflect.Type.containsExoPlayer(): Boolean = when (this) {
        is Class<*> -> name == EXO_PLAYER_FQN ||
            (isArray && (componentType?.containsExoPlayer() ?: false))
        is java.lang.reflect.ParameterizedType -> {
            (rawType as? Class<*>)?.name == EXO_PLAYER_FQN ||
                actualTypeArguments.any { it.containsExoPlayer() }
        }
        is java.lang.reflect.GenericArrayType -> genericComponentType.containsExoPlayer()
        is java.lang.reflect.WildcardType ->
            upperBounds.any { it.containsExoPlayer() } || lowerBounds.any { it.containsExoPlayer() }
        is java.lang.reflect.TypeVariable<*> -> bounds.any { it.containsExoPlayer() }
        else -> false
    }

    @Test
    fun `no scanned class exposes ExoPlayer via method return or generic return type`() {
        SCANNED_CLASSES.forEach { className ->
            val cls = Class.forName(className)
            cls.methods.forEach { method ->
                assertFalse(
                    "$className.${method.name}: return type must not expose ExoPlayer (CSO constraint #1 / BOO-596)",
                    method.returnType.name == EXO_PLAYER_FQN,
                )
                assertFalse(
                    "$className.${method.name}: generic return type must not contain ExoPlayer (CSO constraint #1 / BOO-596)",
                    method.genericReturnType.containsExoPlayer(),
                )
            }
        }
    }

    @Test
    fun `no scanned class exposes ExoPlayer via declared or public field`() {
        SCANNED_CLASSES.forEach { className ->
            val cls = Class.forName(className)
            cls.declaredFields.forEach { field ->
                assertFalse(
                    "$className.${field.name}: declared field must not expose ExoPlayer (CSO constraint #1 / BOO-596)",
                    field.type.name == EXO_PLAYER_FQN,
                )
                assertFalse(
                    "$className.${field.name}: declared field generic type must not contain ExoPlayer (CSO constraint #1 / BOO-596)",
                    field.genericType.containsExoPlayer(),
                )
            }
            cls.fields.forEach { field ->
                assertFalse(
                    "$className.${field.name}: public field must not expose ExoPlayer (CSO constraint #1 / BOO-596)",
                    field.type.name == EXO_PLAYER_FQN,
                )
                assertFalse(
                    "$className.${field.name}: public field generic type must not contain ExoPlayer (CSO constraint #1 / BOO-596)",
                    field.genericType.containsExoPlayer(),
                )
            }
        }
    }
}

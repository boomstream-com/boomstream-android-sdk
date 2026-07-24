package com.boomstream.sdk.player

import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Reflection-based contract test for CSO constraint #1 (BOO-596, hardened via BOO-637 Obs 3).
 *
 * Walks every public method and public field of the player-sdk's integrator-facing classes and
 * asserts that none of them returns or exposes a type assignable from
 * `androidx.media3.exoplayer.ExoPlayer`.
 *
 * This is defense-in-depth against a future refactor accidentally promoting `BoomstreamMediaPlayer.
 * exoPlayer` (currently `internal`) to a public surface — such a refactor would compile cleanly
 * and pass `:player-sdk:assembleRelease`, silently breaking the constraint. This test fails the
 * build first.
 *
 * **Out of scope.** This test catches `getPlayer(): ExoPlayer`-class regressions. It does **not**
 * catch the BOO-637 Obs 1 view-tree escape (`BoomstreamPlayerView.getChildAt(0)` returning a
 * `View` that happens to be `androidx.media3.ui.PlayerView`), because `getChildAt` returns
 * `View`, not `ExoPlayer`. That route is addressed by the unsupported-introspection KDoc on
 * `BoomstreamPlayerView` and by the constructor-immutability of the OkHttp client in
 * `BoomstreamDataSourceFactory` (CSO constraint #2). The two layers are complementary.
 */
class PublicApiReflectionTest {

    private val targetClasses: List<Class<*>> = listOf(
        BoomstreamPlayerView::class.java,
        AdvancedPlayerOptions::class.java,
        PlayerState::class.java,
        PlayerState.Idle::class.java,
        PlayerState.Loading::class.java,
        PlayerState.Ready::class.java,
        PlayerState.PosterOnly::class.java,
        PlayerState.Error::class.java,
        PlayerState.Ended::class.java,
        // New public types added for the Player API.
        PlaybackProgress::class.java,
        PlayerEvent::class.java,
        PlayerEvent.Loaded::class.java,
        PlayerEvent.Playing::class.java,
        PlayerEvent.Paused::class.java,
        PlayerEvent.Ended::class.java,
        PlayerEvent.Progress::class.java,
        PlayerEvent.Seeked::class.java,
        PlayerEvent.FullScreenChanged::class.java,
        PlayerEvent.QualityChanged::class.java,
        // BoomstreamPlayerController is an interface — check its declared methods.
        BoomstreamPlayerController::class.java,
        // VideoQuality — media3-free quality model (CSO constraint #1 gate).
        VideoQuality::class.java,
        VideoQuality.Auto::class.java,
        VideoQuality.Resolution::class.java,
        // Top-level @Composable fun BoomstreamPlayer(...) compiles to BoomstreamPlayerKt.
        Class.forName("com.boomstream.sdk.player.BoomstreamPlayerKt"),
        // BoomstreamPlayerStyle — media3-free styling model (CSO constraint #1 gate).
        BoomstreamPlayerStyle::class.java,
    )

    private val exoPlayerClass: Class<*> =
        Class.forName("androidx.media3.exoplayer.ExoPlayer")

    @Test
    fun `Constraint #1 — no public method on player-sdk surface returns ExoPlayer-assignable type`() {
        val violations = mutableListOf<String>()
        for (cls in targetClasses) {
            cls.methods
                .filter { !it.isSynthetic && Modifier.isPublic(it.modifiers) }
                .forEach { m ->
                    if (exoPlayerClass.isAssignableFrom(m.returnType)) {
                        violations += "${cls.name}.${m.name}() returns ${m.returnType.name}"
                    }
                }
        }
        check(violations.isEmpty()) {
            "CSO constraint #1 violation (BOO-596 / BOO-637 Obs 3): one or more public methods " +
                "on the player-sdk integrator surface return a type assignable from " +
                "androidx.media3.exoplayer.ExoPlayer:\n  " + violations.joinToString("\n  ")
        }
    }

    @Test
    fun `Constraint #1 — no public field on player-sdk surface exposes ExoPlayer-assignable type`() {
        val violations = mutableListOf<String>()
        for (cls in targetClasses) {
            cls.fields
                .filter { Modifier.isPublic(it.modifiers) }
                .forEach { f ->
                    if (exoPlayerClass.isAssignableFrom(f.type)) {
                        violations += "${cls.name}.${f.name} : ${f.type.name}"
                    }
                }
        }
        check(violations.isEmpty()) {
            "CSO constraint #1 violation (BOO-596 / BOO-637 Obs 3): one or more public fields " +
                "on the player-sdk integrator surface expose a type assignable from " +
                "androidx.media3.exoplayer.ExoPlayer:\n  " + violations.joinToString("\n  ")
        }
    }
}

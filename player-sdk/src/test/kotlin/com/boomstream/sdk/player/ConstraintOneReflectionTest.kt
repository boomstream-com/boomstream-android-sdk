package com.boomstream.sdk.player

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Reflection-based guard for CSO constraint #1 (BOO-596 §1).
 *
 * Mirrors the pattern of [BoomstreamDataSourceFactoryTest] which enforces constraint #2 via
 * reflection. A future refactor that accidentally adds a public method or property returning
 * [androidx.media3.exoplayer.ExoPlayer] on any public `BoomstreamPlayer*` class would break
 * these tests before reaching CI.
 *
 * These tests run on the JVM without an Android environment — they inspect class metadata
 * via reflection only (no Android APIs are invoked at runtime).
 */
class ConstraintOneReflectionTest {

    /**
     * No public member declared on [BoomstreamPlayerView] may have a return type (method) or
     * field type assignable from [androidx.media3.exoplayer.ExoPlayer].
     *
     * The View-based player is the higher-risk surface: it extends `FrameLayout` and holds an
     * internal `PlayerView` child, which in turn holds the `ExoPlayer` reference. Extracting it
     * via view-tree traversal is the adversarial path described in BOO-633 Observation 1.
     */
    @Test
    fun `BoomstreamPlayerView exposes no ExoPlayer in public API — CSO constraint 1`() {
        assertNoExoPlayerExposure(BoomstreamPlayerView::class.java)
    }

    /**
     * No public member declared on [AdvancedPlayerOptions] may expose [ExoPlayer].
     *
     * [AdvancedPlayerOptions] is the whitelisted tuning surface *instead of* raw ExoPlayer
     * access, so it must never inadvertently re-introduce an ExoPlayer return type.
     */
    @Test
    fun `AdvancedPlayerOptions exposes no ExoPlayer in public API — CSO constraint 1`() {
        assertNoExoPlayerExposure(AdvancedPlayerOptions::class.java)
    }

    /**
     * [PlaybackProgress] must not expose any Media3 / ExoPlayer types.
     * Its fields are primitive-only (Long, Float) by design.
     */
    @Test
    fun `PlaybackProgress exposes no ExoPlayer in public API — CSO constraint 1`() {
        assertNoExoPlayerExposure(PlaybackProgress::class.java)
    }

    /**
     * [PlayerEvent] sealed class and all its nested variants must not expose ExoPlayer types.
     * Each variant carries only primitive or Boolean fields.
     */
    @Test
    fun `PlayerEvent sealed class exposes no ExoPlayer in public API — CSO constraint 1`() {
        assertNoExoPlayerExposure(PlayerEvent::class.java)
        assertNoExoPlayerExposure(PlayerEvent.Loaded::class.java)
        assertNoExoPlayerExposure(PlayerEvent.Playing::class.java)
        assertNoExoPlayerExposure(PlayerEvent.Paused::class.java)
        assertNoExoPlayerExposure(PlayerEvent.Ended::class.java)
        assertNoExoPlayerExposure(PlayerEvent.Progress::class.java)
        assertNoExoPlayerExposure(PlayerEvent.Seeked::class.java)
        assertNoExoPlayerExposure(PlayerEvent.FullScreenChanged::class.java)
    }

    /**
     * [BoomstreamPlayerController] is a public interface — none of its declared methods may
     * return a type assignable from ExoPlayer.
     */
    @Test
    fun `BoomstreamPlayerController exposes no ExoPlayer in public API — CSO constraint 1`() {
        assertNoExoPlayerExposure(BoomstreamPlayerController::class.java)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun assertNoExoPlayerExposure(cls: Class<*>) {
        val exoPlayerClass = Class.forName("androidx.media3.exoplayer.ExoPlayer")

        val violations = mutableListOf<String>()

        // Check public methods declared directly in this class (not inherited).
        // Kotlin `val exoPlayer: ExoPlayer` compiles to a private field + public getter;
        // the getter appears in declaredMethods and is caught here.
        for (method in cls.declaredMethods) {
            if (Modifier.isPublic(method.modifiers) &&
                exoPlayerClass.isAssignableFrom(method.returnType)
            ) {
                violations += "${cls.simpleName}.${method.name}(): ${method.returnType.simpleName}"
            }
        }

        // Check public fields declared directly in this class (not inherited).
        // Catches Java-style public fields and any future annotation-generated exposure.
        for (field in cls.declaredFields) {
            if (Modifier.isPublic(field.modifiers) &&
                exoPlayerClass.isAssignableFrom(field.type)
            ) {
                violations += "${cls.simpleName}.${field.name}: ${field.type.simpleName}"
            }
        }

        assertTrue(
            "CSO constraint #1 (BOO-596 §1): '${cls.simpleName}' must not expose ExoPlayer " +
                "on its public API surface. Violations found: $violations. " +
                "Use AdvancedPlayerOptions for permissible tuning instead.",
            violations.isEmpty(),
        )
    }
}

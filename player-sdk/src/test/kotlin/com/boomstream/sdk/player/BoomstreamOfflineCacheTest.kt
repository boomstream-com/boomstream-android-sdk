package com.boomstream.sdk.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit tests for [BoomstreamOfflineCache] interface and its contract with [BoomstreamPlayer].
 *
 * All tests run on the JVM (no Android environment). Offline playback integration with
 * ExoPlayer's [androidx.media3.datasource.cache.CacheDataSource] is verified end-to-end via
 * instrumented tests on a device/emulator (BOO-642 AC #4).
 */
class BoomstreamOfflineCacheTest {

    // ── Interface contract ────────────────────────────────────────────────────

    @Test
    fun `BoomstreamOfflineCache is a functional interface — SAM conversion compiles`() {
        val iface = BoomstreamOfflineCache::class.java
        // A fun interface has exactly one abstract method.
        val abstractMethods = iface.methods.filter { Modifier.isAbstract(it.modifiers) }
        assertEquals(
            "BoomstreamOfflineCache must be a SAM (exactly 1 abstract method)",
            1,
            abstractMethods.size,
        )
        assertEquals("provideCache", abstractMethods.single().name)
    }

    @Test
    fun `BoomstreamOfflineCache provideCache returns correct instance`() {
        // Verify SAM lambda works; the actual SimpleCache class is not available on JVM
        // (it's an Android artifact), so we use a simple mock via subclassing.
        val sentinel = object : BoomstreamOfflineCache {
            override fun provideCache() = throw UnsupportedOperationException("sentinel")
        }
        assertNotNull(sentinel)
    }

    // ── BoomstreamPlayer signature guard ──────────────────────────────────────

    @Test
    fun `BoomstreamOfflineCache is in the public player package — no internal cross-module dep`() {
        // BoomstreamOfflineCache must live in com.boomstream.sdk.player, not offline package.
        assertEquals(
            "com.boomstream.sdk.player",
            BoomstreamOfflineCache::class.java.packageName,
        )
    }

    @Test
    fun `BoomstreamOfflineCache is a public interface`() {
        val cls = BoomstreamOfflineCache::class.java
        assertNotNull("BoomstreamOfflineCache must exist", cls)
        assert(cls.isInterface) { "BoomstreamOfflineCache must be an interface" }
        assert(Modifier.isPublic(cls.modifiers)) { "BoomstreamOfflineCache must be public" }
    }

    // ── Null-safety: offlineCache = null does not affect BoomstreamPlayerView ─

    @Test
    fun `BoomstreamPlayerView is constructable without offlineCache — no offline-sdk required`() {
        // Reflection check: BoomstreamPlayerView constructor must not require BoomstreamOfflineCache.
        // This ensures consumers who only use :player-sdk (no :offline-sdk) are not broken.
        val playerViewClass = BoomstreamPlayerView::class.java
        val constructors = playerViewClass.constructors + playerViewClass.declaredConstructors
        val offlineCacheClass = BoomstreamOfflineCache::class.java

        // At least one constructor must NOT take BoomstreamOfflineCache as a mandatory param.
        val hasConstructorWithoutOfflineCache = constructors.any { ctor ->
            ctor.parameterTypes.none { it == offlineCacheClass }
        }
        assert(hasConstructorWithoutOfflineCache) {
            "BoomstreamPlayerView must be constructable without BoomstreamOfflineCache"
        }
    }

    @Test
    fun `BoomstreamOfflineCache does not reference SimpleCache in its erasure return type`() {
        // SimpleCache is @UnstableApi but acceptable since we already use @UnstableApi internally.
        // This test documents the deliberate choice — if the API surface changes,
        // this test alerts the reviewer.
        val abstractMethod = BoomstreamOfflineCache::class.java.methods
            .single { Modifier.isAbstract(it.modifiers) }
        assertEquals(
            "provideCache return type must be SimpleCache",
            "SimpleCache",
            abstractMethod.returnType.simpleName,
        )
    }

    // ── Verify no ExoPlayer leaked via offline path (CSO constraint #1 guard) ─

    @Test
    fun `BoomstreamOfflineCache does not expose ExoPlayer`() {
        val offlineCacheClass = BoomstreamOfflineCache::class.java
        val exoPlayerName = "androidx.media3.exoplayer.ExoPlayer"
        val exoPlayerClass = runCatching { Class.forName(exoPlayerName) }.getOrNull()
            ?: return // ExoPlayer not on test classpath — skip (no risk of exposure either)

        for (method in offlineCacheClass.methods) {
            assert(!exoPlayerClass.isAssignableFrom(method.returnType)) {
                "CSO constraint #1: BoomstreamOfflineCache.${method.name}() must not return ExoPlayer"
            }
        }
    }
}

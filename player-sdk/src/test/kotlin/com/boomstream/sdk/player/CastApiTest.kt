package com.boomstream.sdk.player

import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Verifies the Cast v1 public API shape (BOO-835).
 *
 * Does not instantiate [BoomstreamPlayerView] or [BoomstreamPlayer] (requires Android runtime);
 * instead tests the interface contract via reflection and the default behaviour of the
 * Compose-path controller returned by [rememberBoomstreamPlayerController]'s factory.
 */
class CastApiTest {

    // ── BoomstreamPlayerController interface ─────────────────────────────────

    @Test
    fun `BoomstreamPlayerController declares isCasting StateFlow`() {
        // Kotlin JVM getter naming for is-prefixed properties can be either "isCasting" or
        // "getIsCasting" depending on return type — match by name pattern and return type.
        val method = BoomstreamPlayerController::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .firstOrNull { m ->
                StateFlow::class.java.isAssignableFrom(m.returnType) &&
                    (m.name == "isCasting" || m.name == "getIsCasting")
            }
        assertNotNull(
            "BoomstreamPlayerController must declare a public isCasting property returning StateFlow",
            method,
        )
    }

    @Test
    fun `BoomstreamPlayerController declares castDeviceName StateFlow`() {
        val method = BoomstreamPlayerController::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .firstOrNull { m ->
                StateFlow::class.java.isAssignableFrom(m.returnType) &&
                    (m.name == "getCastDeviceName" || m.name == "castDeviceName")
            }
        assertNotNull(
            "BoomstreamPlayerController must declare a public castDeviceName property returning StateFlow",
            method,
        )
    }

    // ── BoomstreamComposableController defaults ──────────────────────────────

    @Test
    fun `BoomstreamComposableController isCasting defaults to false`() {
        val controller = com.boomstream.sdk.player.internal.BoomstreamComposableController()
        assertFalse("isCasting must default to false before any player is attached", controller.isCasting.value)
    }

    @Test
    fun `BoomstreamComposableController castDeviceName defaults to null`() {
        val controller = com.boomstream.sdk.player.internal.BoomstreamComposableController()
        assertNull("castDeviceName must default to null before any player is attached", controller.castDeviceName.value)
    }

    // ── CSO constraint: no Cast SDK types on public API ──────────────────────

    @Test
    fun `BoomstreamPlayerController does not expose CastPlayer-assignable types`() {
        assertNoCastPlayerExposure(BoomstreamPlayerController::class.java)
    }

    @Test
    fun `BoomstreamCastOptionsProvider does not expose CastPlayer-assignable types on its public surface`() {
        assertNoCastPlayerExposure(BoomstreamCastOptionsProvider::class.java)
    }

    private fun assertNoCastPlayerExposure(cls: Class<*>) {
        val castPlayerClass = try {
            Class.forName("androidx.media3.cast.CastPlayer")
        } catch (_: ClassNotFoundException) {
            return  // library not on classpath in this test variant — skip
        }

        val violations = mutableListOf<String>()
        for (method in cls.declaredMethods) {
            if (Modifier.isPublic(method.modifiers) &&
                castPlayerClass.isAssignableFrom(method.returnType)
            ) {
                violations += "${cls.simpleName}.${method.name}(): ${method.returnType.simpleName}"
            }
        }
        for (field in cls.declaredFields) {
            if (Modifier.isPublic(field.modifiers) &&
                castPlayerClass.isAssignableFrom(field.type)
            ) {
                violations += "${cls.simpleName}.${field.name}: ${field.type.simpleName}"
            }
        }

        assert(violations.isEmpty()) {
            "CSO constraint #1 (BOO-835): '${cls.simpleName}' must not expose CastPlayer " +
                "on its public API surface. Violations: $violations"
        }
    }
}

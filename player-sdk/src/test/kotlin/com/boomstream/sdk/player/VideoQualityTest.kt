package com.boomstream.sdk.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Test
import java.lang.reflect.Modifier

class VideoQualityTest {

    // ── Model behaviour ───────────────────────────────────────────────────────

    @Test
    fun `Auto is a singleton — identity equality`() {
        assertSame(VideoQuality.Auto, VideoQuality.Auto)
    }

    @Test
    fun `Resolution default label is height-p`() {
        assertEquals("720p", VideoQuality.Resolution(720).label)
        assertEquals("1080p", VideoQuality.Resolution(1080).label)
        assertEquals("480p", VideoQuality.Resolution(480).label)
    }

    @Test
    fun `Resolution custom label overrides default`() {
        val q = VideoQuality.Resolution(height = 1080, label = "HD")
        assertEquals("HD", q.label)
    }

    @Test
    fun `Resolution with unknown bitrate stores minus-one`() {
        val q = VideoQuality.Resolution(height = 720)
        assertEquals(-1L, q.bitrate)
    }

    @Test
    fun `Resolution structural equality on identical fields`() {
        val a = VideoQuality.Resolution(720, 2_000_000L)
        val b = VideoQuality.Resolution(720, 2_000_000L)
        assertEquals(a, b)
    }

    @Test
    fun `Resolution structural inequality on different height`() {
        val a = VideoQuality.Resolution(720)
        val b = VideoQuality.Resolution(1080)
        assertNotEquals(a, b)
    }

    @Test
    fun `Resolution structural inequality on different bitrate`() {
        val a = VideoQuality.Resolution(720, 1_000_000L)
        val b = VideoQuality.Resolution(720, 2_000_000L)
        assertNotEquals(a, b)
    }

    // ── CSO constraint #1 — no media3 types in public API ────────────────────

    @Test
    fun `VideoQuality has no public members returning media3 types`() {
        val media3Prefix = "androidx.media3"
        listOf(
            VideoQuality::class.java,
            VideoQuality.Auto::class.java,
            VideoQuality.Resolution::class.java,
        ).forEach { cls ->
            cls.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) }
                .forEach { m ->
                    assertFalse(
                        "CSO constraint #1: ${cls.simpleName}.${m.name}() must not return " +
                            "a media3 type, but returns ${m.returnType.name}",
                        m.returnType.name.startsWith(media3Prefix),
                    )
                }
            cls.declaredFields
                .filter { Modifier.isPublic(it.modifiers) }
                .forEach { f ->
                    assertFalse(
                        "CSO constraint #1: ${cls.simpleName}.${f.name} must not be a " +
                            "media3 type, but is ${f.type.name}",
                        f.type.name.startsWith(media3Prefix),
                    )
                }
        }
    }
}

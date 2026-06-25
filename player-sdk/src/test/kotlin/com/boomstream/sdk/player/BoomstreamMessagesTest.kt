package com.boomstream.sdk.player

import com.boomstream.sdk.player.internal.BoomstreamMessages
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [BoomstreamMessages] — the internal locale-keyed message table.
 *
 * Covers: known keys in all supported locales, English fallback for unknown locale,
 * caller-supplied fallback when key is missing, and key-as-last-resort.
 */
class BoomstreamMessagesTest {

    // ── Known keys ────────────────────────────────────────────────────────────

    @Test
    fun `resolve access_restricted in Russian`() {
        assertEquals(
            "Доступ к контенту ограничен",
            BoomstreamMessages.resolve("access_restricted", locale = "ru"),
        )
    }

    @Test
    fun `resolve access_restricted in English`() {
        assertEquals(
            "Access to this content is restricted",
            BoomstreamMessages.resolve("access_restricted", locale = "en"),
        )
    }

    @Test
    fun `resolve stream_offline in Russian`() {
        assertEquals(
            "Трансляция оффлайн",
            BoomstreamMessages.resolve("stream_offline", locale = "ru"),
        )
    }

    @Test
    fun `resolve stream_offline in English`() {
        assertEquals(
            "Stream is offline",
            BoomstreamMessages.resolve("stream_offline", locale = "en"),
        )
    }

    @Test
    fun `resolve playing_record in Russian`() {
        assertEquals(
            "Играет запись",
            BoomstreamMessages.resolve("playing_record", locale = "ru"),
        )
    }

    @Test
    fun `resolve no_network_offline in Russian`() {
        assertEquals(
            "Нет сети и оффлайн контент не загружен",
            BoomstreamMessages.resolve("no_network_offline", locale = "ru"),
        )
    }

    // ── English fallback for unsupported locale ───────────────────────────────

    @Test
    fun `unknown locale falls back to English`() {
        assertEquals(
            "Access to this content is restricted",
            BoomstreamMessages.resolve("access_restricted", locale = "de"),
        )
    }

    @Test
    fun `null locale uses English table for known key`() {
        assertEquals(
            "Stream is offline",
            BoomstreamMessages.resolve("stream_offline", locale = null),
        )
    }

    // ── Caller-supplied fallback (server translate field) ─────────────────────

    @Test
    fun `unknown key with caller fallback returns fallback`() {
        assertEquals(
            "Some server text",
            BoomstreamMessages.resolve("unknown_future_key", locale = "ru", fallback = "Some server text"),
        )
    }

    @Test
    fun `unknown key without fallback returns the key itself`() {
        assertEquals(
            "new_unknown_key",
            BoomstreamMessages.resolve("new_unknown_key", locale = "en"),
        )
    }

    // ── accessRestricted integration: prefer locale, fallback to translate ────

    @Test
    fun `locale ru with known key ignores server translate`() {
        val serverTranslate = "Access to this content is restricted."
        val result = BoomstreamMessages.resolve("access_restricted", locale = "ru", fallback = serverTranslate)
        assertEquals("Доступ к контенту ограничен", result)
    }

    @Test
    fun `unknown key with null locale uses server translate`() {
        val serverTranslate = "Access to this content is restricted."
        val result = BoomstreamMessages.resolve("unknown_future_key", locale = null, fallback = serverTranslate)
        assertEquals(serverTranslate, result)
    }
}

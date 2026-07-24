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

    // ── Settings sheet keys ───────────────────────────────────────────────────

    @Test
    fun `resolve settings_speed in Russian`() {
        assertEquals("Скорость", BoomstreamMessages.resolve("settings_speed", locale = "ru"))
    }

    @Test
    fun `resolve settings_speed in English`() {
        assertEquals("Speed", BoomstreamMessages.resolve("settings_speed", locale = "en"))
    }

    @Test
    fun `resolve settings_audio in Russian`() {
        assertEquals("Аудио", BoomstreamMessages.resolve("settings_audio", locale = "ru"))
    }

    @Test
    fun `resolve settings_audio in English`() {
        assertEquals("Audio", BoomstreamMessages.resolve("settings_audio", locale = "en"))
    }

    @Test
    fun `resolve settings_quality in Russian`() {
        assertEquals("Качество", BoomstreamMessages.resolve("settings_quality", locale = "ru"))
    }

    @Test
    fun `resolve settings_quality in English`() {
        assertEquals("Quality", BoomstreamMessages.resolve("settings_quality", locale = "en"))
    }

    @Test
    fun `resolve settings_speed_normal in Russian`() {
        assertEquals("Обычная (1×)", BoomstreamMessages.resolve("settings_speed_normal", locale = "ru"))
    }

    @Test
    fun `resolve settings_speed_normal in English`() {
        assertEquals("Normal (1×)", BoomstreamMessages.resolve("settings_speed_normal", locale = "en"))
    }

    @Test
    fun `resolve settings_quality_auto in Russian`() {
        assertEquals("Авто", BoomstreamMessages.resolve("settings_quality_auto", locale = "ru"))
    }

    @Test
    fun `resolve settings_quality_auto in English`() {
        assertEquals("Auto", BoomstreamMessages.resolve("settings_quality_auto", locale = "en"))
    }

    @Test
    fun `settings keys fall back to English for unknown locale`() {
        assertEquals("Speed", BoomstreamMessages.resolve("settings_speed", locale = "de"))
        assertEquals("Audio", BoomstreamMessages.resolve("settings_audio", locale = "de"))
        assertEquals("Quality", BoomstreamMessages.resolve("settings_quality", locale = "de"))
    }

    @Test
    fun `resolve subtitles_title in Russian`() {
        assertEquals("Субтитры", BoomstreamMessages.resolve("subtitles_title", locale = "ru"))
    }

    @Test
    fun `resolve subtitles_title in English`() {
        assertEquals("Subtitles", BoomstreamMessages.resolve("subtitles_title", locale = "en"))
    }

    @Test
    fun `resolve subtitles_off in Russian`() {
        assertEquals("Выкл.", BoomstreamMessages.resolve("subtitles_off", locale = "ru"))
    }

    @Test
    fun `resolve subtitles_off in English`() {
        assertEquals("Off", BoomstreamMessages.resolve("subtitles_off", locale = "en"))
    }

    @Test
    fun `subtitles keys fall back to English for unknown locale`() {
        assertEquals("Subtitles", BoomstreamMessages.resolve("subtitles_title", locale = "de"))
        assertEquals("Off", BoomstreamMessages.resolve("subtitles_off", locale = "de"))
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

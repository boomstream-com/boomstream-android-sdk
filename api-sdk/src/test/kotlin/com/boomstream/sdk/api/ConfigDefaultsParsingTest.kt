package com.boomstream.sdk.api

import com.boomstream.sdk.api.internal.ApiFactory
import com.boomstream.sdk.api.model.ConfigResponse
import com.boomstream.sdk.api.model.effectivePosters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for `defaults.posters` parsing and `effectivePosters` resolver.
 *
 * API contract verified 2026-06-21 against `https://play.boomstream.com/Il4lNOfL/config`:
 * `defaults.posters` uses the same `{width, height, link}` shape as top-level `posters`;
 * `height` is typically 0 for defaults (CDN sizes by width only).
 */
class ConfigDefaultsParsingTest {

    private val json = ApiFactory.json

    // ── defaults.posters parsing ──────────────────────────────────────────────

    @Test
    fun `parse config with defaults posters`() {
        val raw = """
            {
              "code": "Il4lNOfL",
              "posters": [],
              "defaults": {
                "posters": [
                  { "width": 720,  "height": 0, "link": "https://cdn.example.com/size:720/img.jpg" },
                  { "width": 1280, "height": 0, "link": "https://cdn.example.com/size:1280/img.jpg" },
                  { "width": 1920, "height": 0, "link": "https://cdn.example.com/size:1920/img.jpg" }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertNotNull("defaults should be parsed", response.defaults)
        assertEquals(3, response.defaults!!.posters.size)
        assertEquals(720, response.defaults!!.posters[0].width)
        assertEquals(0, response.defaults!!.posters[0].height)
        assertEquals("https://cdn.example.com/size:720/img.jpg", response.defaults!!.posters[0].link)
        assertEquals(1920, response.defaults!!.posters[2].width)
    }

    @Test
    fun `defaults absent in config — defaults field is null`() {
        val raw = """{ "code": "abc123", "posters": [] }"""
        val response = json.decodeFromString<ConfigResponse>(raw)
        assertNull("defaults should be null when absent from JSON", response.defaults)
    }

    @Test
    fun `defaults with empty posters array parses correctly`() {
        val raw = """{ "code": "abc123", "posters": [], "defaults": { "posters": [] } }"""
        val response = json.decodeFromString<ConfigResponse>(raw)
        assertNotNull(response.defaults)
        assertEquals(0, response.defaults!!.posters.size)
    }

    // ── effectivePosters resolver ─────────────────────────────────────────────

    @Test
    fun `effectivePosters — non-empty posters → returns posters, ignores defaults`() {
        val raw = """
            {
              "code": "test01",
              "posters": [
                { "width": 1280, "height": 720, "link": "https://cdn.example.com/primary.jpg" }
              ],
              "defaults": {
                "posters": [
                  { "width": 720, "height": 0, "link": "https://cdn.example.com/fallback.jpg" }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertEquals(1, response.effectivePosters.size)
        assertEquals("https://cdn.example.com/primary.jpg", response.effectivePosters[0].link)
    }

    @Test
    fun `effectivePosters — empty posters → returns defaults posters`() {
        val raw = """
            {
              "code": "test02",
              "posters": [],
              "defaults": {
                "posters": [
                  { "width": 720,  "height": 0, "link": "https://cdn.example.com/size:720/fallback.jpg" },
                  { "width": 1920, "height": 0, "link": "https://cdn.example.com/size:1920/fallback.jpg" }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertEquals(2, response.effectivePosters.size)
        assertEquals("https://cdn.example.com/size:720/fallback.jpg", response.effectivePosters[0].link)
        assertEquals(1920, response.effectivePosters[1].width)
    }

    @Test
    fun `effectivePosters — empty posters and no defaults → returns empty list`() {
        val raw = """{ "code": "test03", "posters": [] }"""
        val response = json.decodeFromString<ConfigResponse>(raw)
        assertEquals(0, response.effectivePosters.size)
    }

    @Test
    fun `effectivePosters — empty posters and defaults with empty posters → returns empty list`() {
        val raw = """{ "code": "test04", "posters": [], "defaults": { "posters": [] } }"""
        val response = json.decodeFromString<ConfigResponse>(raw)
        assertEquals(0, response.effectivePosters.size)
    }
}

package com.boomstream.sdk.api

import com.boomstream.sdk.api.internal.ApiFactory
import com.boomstream.sdk.api.model.ConfigResponse
import com.boomstream.sdk.api.model.mediaDataPlaylist
import com.boomstream.sdk.api.model.mediaDataSingle
import com.boomstream.sdk.api.model.isPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for JSON parsing of the config endpoint response.
 *
 * Covers the polymorphic `mediaData` field and `mediaType` discriminator:
 * - single-media (`mediaType="media"`, `mediaData` object)
 * - playlist (`mediaType="playlist"`, `mediaData` array)
 * - unauthenticated (`mediaData` null / absent)
 * - edge case: `mediaType="media"` with array `mediaData` → not a playlist (BOO-684)
 */
class MediaDataParsingTest {

    private val json = ApiFactory.json

    // ── Single-media (object) ────────────────────────────────────────────────

    @Test
    fun `parse authenticated single-media response`() {
        val raw = """
            {
              "code": "Il4lNOfL",
              "language": "en",
              "posters": [],
              "mediaData": {
                "code": "Il4lNOfL",
                "title": "Demo Video",
                "duration": 120,
                "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS92aWRlby5tM3U4" },
                "posters": [{ "width": 640, "height": 360, "link": "https://example.com/poster.jpg" }]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertFalse("should not be playlist", response.isPlaylist)
        assertNotNull("mediaDataSingle should not be null", response.mediaDataSingle)
        assertNull("mediaDataPlaylist should be null", response.mediaDataPlaylist)

        val media = response.mediaDataSingle!!
        assertEquals("Il4lNOfL", media.code)
        assertEquals("Demo Video", media.title)
        assertEquals(120, media.duration)
        assertNotNull(media.links)
        assertEquals(1, media.posters.size)
        assertEquals("https://example.com/poster.jpg", media.posters[0].link)
    }

    // ── Playlist (array) ────────────────────────────────────────────────────

    @Test
    fun `parse playlist response — mediaType playlist with array mediaData`() {
        val raw = """
            {
              "code": "playlist01",
              "language": "en",
              "mediaType": "playlist",
              "posters": [],
              "mediaData": [
                {
                  "code": "item001",
                  "title": "Episode 1",
                  "duration": 1800
                },
                {
                  "code": "item002",
                  "title": "Episode 2",
                  "duration": 2400
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertTrue("should be playlist", response.isPlaylist)
        assertNull("mediaDataSingle should be null for playlist", response.mediaDataSingle)
        assertNotNull("mediaDataPlaylist should not be null", response.mediaDataPlaylist)

        val items = response.mediaDataPlaylist!!
        assertEquals(2, items.size)
        assertEquals("item001", items[0].code)
        assertEquals("Episode 1", items[0].title)
        assertEquals("item002", items[1].code)
        assertEquals(2400, items[1].duration)
    }

    @Test
    fun `single video with array mediaData is NOT a playlist when mediaType is media (BOO-684)`() {
        // Edge case: mediaData is an array but mediaType is "media" — must not be treated
        // as a playlist (no prev/next controls). mediaType is authoritative (BOO-684).
        val raw = """
            {
              "code": "singlevid1",
              "mediaType": "media",
              "mediaData": [
                {
                  "code": "singlevid1",
                  "title": "Only Video",
                  "duration": 300
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertFalse("mediaType=media must not be treated as playlist even if mediaData is array", response.isPlaylist)
        assertNull("mediaDataSingle is null when mediaData is array", response.mediaDataSingle)
        assertNotNull("mediaDataPlaylist still parses from array shape", response.mediaDataPlaylist)
        assertEquals(1, response.mediaDataPlaylist!!.size)
    }

    // ── Unauthenticated (null mediaData) ────────────────────────────────────

    @Test
    fun `parse unauthenticated response — mediaData absent`() {
        val raw = """
            {
              "code": "VVwbS8LD",
              "language": "en",
              "posters": [
                { "width": 1280, "height": 720, "link": "https://cdn.example.com/poster.jpg" }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertFalse("should not be playlist", response.isPlaylist)
        assertNull("mediaDataSingle should be null for unauth", response.mediaDataSingle)
        assertNull("mediaDataPlaylist should be null for unauth", response.mediaDataPlaylist)
        assertEquals(1, response.posters.size)
        assertEquals("https://cdn.example.com/poster.jpg", response.posters[0].link)
    }

    @Test
    fun `parse unauthenticated response — mediaData explicitly null`() {
        val raw = """
            {
              "code": "VVwbS8LD",
              "posters": [{ "width": 320, "height": 180, "link": "https://cdn.example.com/thumb.jpg" }],
              "mediaData": null
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertNull(response.mediaDataSingle)
        assertNull(response.mediaDataPlaylist)
        assertEquals(1, response.posters.size)
    }

    // ── Error envelope ───────────────────────────────────────────────────────

    @Test
    fun `parse server error envelope`() {
        val raw = """
            {
              "code": "INVALID",
              "error": { "code": 404, "message": "Media not found", "translate": "not_found" }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertNotNull(response.error)
        assertEquals(404, response.error!!.code)
        assertEquals("Media not found", response.error!!.message)
        assertNull(response.mediaDataSingle)
    }

    // ── Live content (isLive discriminator) ─────────────────────────────────
    // Verified 2026-06-20 against real codes vWktqOGl and H4pNmmAU:
    //   top-level isLive=true, mediaData.isLive=true, mediaType="media" (NOT "live")

    @Test
    fun `parse live single-media — isLive true in mediaData and top-level`() {
        val raw = """
            {
              "code": "vWktqOGl",
              "isLive": true,
              "mediaType": "media",
              "mediaData": {
                "code": "vWktqOGl",
                "title": "Demo Live Broadcast",
                "mediaType": "media",
                "isLive": true,
                "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS9saXZlLm0zdTg=" }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertTrue("top-level isLive must be true", response.isLive)
        assertFalse("live single mediaType=media is NOT a playlist", response.isPlaylist)

        val media = response.mediaDataSingle!!
        assertTrue("mediaData.isLive must be true", media.isLive)
        assertEquals("Demo Live Broadcast", media.title)
        assertEquals("media", media.mediaType)
    }

    @Test
    fun `parse vod single-media — isLive defaults to false`() {
        val raw = """
            {
              "code": "Il4lNOfL",
              "isLive": false,
              "mediaType": "media",
              "mediaData": {
                "code": "Il4lNOfL",
                "title": "Regular Video",
                "duration": 300
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertFalse("top-level isLive should be false for VOD", response.isLive)
        val media = response.mediaDataSingle!!
        assertFalse("mediaData.isLive should be false for VOD", media.isLive)
    }

    @Test
    fun `parse playlist with mixed live and vod items — each item carries its own isLive`() {
        val raw = """
            {
              "code": "mixedPL",
              "mediaType": "playlist",
              "mediaData": [
                {
                  "code": "vodItem",
                  "title": "Regular Episode",
                  "duration": 1800,
                  "isLive": false
                },
                {
                  "code": "liveItem",
                  "title": "Live Stream",
                  "isLive": true,
                  "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS9saXZlLm0zdTg=" }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)
        val items = response.mediaDataPlaylist!!

        assertEquals(2, items.size)
        assertFalse("first item is VOD — isLive false", items[0].isLive)
        assertTrue("second item is live — isLive true", items[1].isLive)
    }

    // ── accessRestricted field ───────────────────────────────────────────────

    @Test
    fun `parse accessRestricted response — field present as object with message and translate`() {
        // Wire contract confirmed 2026-06-22: server sends an object with localisation key
        // and server-side fallback translation (not a plain string as in earlier BOO-694 prototype).
        val raw = """
            {
              "code": "ppvContent",
              "posters": [{ "width": 1280, "height": 720, "link": "https://cdn.example.com/poster.jpg" }],
              "accessRestricted": {
                "message": "access_restricted",
                "translate": "Access to this content is restricted."
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertNotNull("accessRestricted must be present", response.accessRestricted)
        assertEquals("access_restricted", response.accessRestricted!!.message)
        assertEquals("Access to this content is restricted.", response.accessRestricted!!.translate)
        assertNull(response.mediaDataSingle)
    }

    @Test
    fun `parse accessRestricted — message key and translate are independent fields`() {
        val raw = """
            {
              "code": "subGate",
              "accessRestricted": {
                "message": "access_restricted",
                "translate": "Доступ к контенту ограничен"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        val ar = response.accessRestricted!!
        assertEquals("access_restricted", ar.message)
        assertEquals("Доступ к контенту ограничен", ar.translate)
    }

    @Test
    fun `parse normal response — accessRestricted absent means null`() {
        val raw = """
            {
              "code": "Il4lNOfL",
              "mediaData": {
                "code": "Il4lNOfL",
                "title": "Demo Video",
                "duration": 120,
                "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS92aWRlby5tM3U4" }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)

        assertNull("No accessRestricted in normal authenticated response", response.accessRestricted)
    }

    // ── isPublish + source (live offline state) ──────────────────────────────
    // Offline state is determined solely by isPublish (BOO-705 regression fix).
    // source=false/true is encoder-source metadata, not an availability indicator.
    // Wire contract verified 2026-06-22 against play.boomstream.com/vWktqOGl/config:
    //   isPublish=true, source=false → published broadcast → ONLINE, must play.

    @Test
    fun `parse live with isPublish=true and source=false — isLiveOffline false (BOO-705 regression)`() {
        // Regression: vWktqOGl config has isPublish=true, source=false (JSON boolean).
        // OLD (buggy): isLiveOffline=true because hasActiveSource=false was part of the check.
        // NEW (fixed): isLiveOffline=false — only isPublish drives the offline flag.
        val raw = """
            {
              "code": "vWktqOGl",
              "isLive": true,
              "mediaType": "media",
              "mediaData": {
                "code": "vWktqOGl",
                "title": "Demo Live Broadcast",
                "isLive": true,
                "isPublish": true,
                "source": false,
                "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS9saXZlLm0zdTg=" }
              }
            }
        """.trimIndent()

        val media = json.decodeFromString<ConfigResponse>(raw).mediaDataSingle!!

        assertTrue("isLive must be true", media.isLive)
        assertTrue("isPublish must be true", media.isPublish)
        assertFalse("isLiveOffline must be false — isPublish=true, source is not an availability indicator", media.isLiveOffline)
    }

    @Test
    fun `parse live with isPublish=true and source=true — isLiveOffline false`() {
        // isPublish=true, source="url" (external RTMP source configured).
        // isLiveOffline=false because isPublish=true — source field plays no role.
        val raw = """
            {
              "code": "extSrcLive",
              "isLive": true,
              "mediaType": "media",
              "mediaData": {
                "code": "extSrcLive",
                "title": "External Source Live",
                "isLive": true,
                "isPublish": true,
                "source": "rtmps://ingest.boomstream.com/live/key",
                "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS9saXZlLm0zdTg=" }
              }
            }
        """.trimIndent()

        val media = json.decodeFromString<ConfigResponse>(raw).mediaDataSingle!!

        assertTrue("isLive must be true", media.isLive)
        assertTrue("isPublish must be true", media.isPublish)
        assertFalse("isLiveOffline must be false — isPublish=true regardless of source URL", media.isLiveOffline)
    }

    @Test
    fun `parse live with isPublish=false — isLiveOffline true regardless of source`() {
        val raw = """
            {
              "code": "liveCode",
              "isLive": true,
              "mediaType": "media",
              "mediaData": {
                "code": "liveCode",
                "title": "Unpublished Live",
                "isLive": true,
                "isPublish": false
              }
            }
        """.trimIndent()

        val media = json.decodeFromString<ConfigResponse>(raw).mediaDataSingle!!

        assertTrue("isLive must be true", media.isLive)
        assertFalse("isPublish must be false", media.isPublish)
        assertTrue("isLiveOffline — not published", media.isLiveOffline)
    }

    @Test
    fun `parse vod — isPublish defaults false but isLiveOffline false because isLive false`() {
        val raw = """
            {
              "code": "vod01",
              "mediaData": {
                "code": "vod01",
                "title": "Regular VOD",
                "duration": 300,
                "isLive": false
              }
            }
        """.trimIndent()

        val media = json.decodeFromString<ConfigResponse>(raw).mediaDataSingle!!

        assertFalse("isLive must be false for VOD", media.isLive)
        assertFalse("isLiveOffline must be false for VOD", media.isLiveOffline)
    }

    // ── records field (offline-live recordings) ──────────────────────────────

    @Test
    fun `parse live offline with records — records list populated`() {
        val raw = """
            {
              "code": "livecode1",
              "isLive": true,
              "mediaType": "media",
              "mediaData": {
                "code": "livecode1",
                "title": "My Live",
                "isLive": true,
                "isPublish": false,
                "records": [
                  {
                    "code": "rec001",
                    "title": "Recording 1",
                    "duration": 3600,
                    "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS9yZWMxLm0zdTg=" },
                    "posters": [{ "width": 1280, "height": 720, "link": "https://cdn.example.com/rec1.jpg" }]
                  },
                  {
                    "code": "rec002",
                    "title": "Recording 2",
                    "duration": 1800,
                    "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS9yZWMyLm0zdTg=" }
                  }
                ]
              }
            }
        """.trimIndent()

        val media = json.decodeFromString<ConfigResponse>(raw).mediaDataSingle!!

        assertTrue("isLiveOffline must be true", media.isLiveOffline)
        assertEquals(2, media.records.size)

        val rec1 = media.records[0]
        assertEquals("rec001", rec1.code)
        assertEquals("Recording 1", rec1.title)
        assertEquals(3600, rec1.duration)
        assertNotNull(rec1.links)
        assertEquals(1, rec1.posters.size)

        val rec2 = media.records[1]
        assertEquals("rec002", rec2.code)
        assertEquals(1800, rec2.duration)
    }

    @Test
    fun `parse live offline without records — records empty by default`() {
        val raw = """
            {
              "code": "offlinelive",
              "isLive": true,
              "mediaType": "media",
              "mediaData": {
                "code": "offlinelive",
                "title": "Offline Live",
                "isLive": true,
                "isPublish": false
              }
            }
        """.trimIndent()

        val media = json.decodeFromString<ConfigResponse>(raw).mediaDataSingle!!

        assertTrue("isLiveOffline must be true", media.isLiveOffline)
        assertTrue("records must be empty when field absent", media.records.isEmpty())
    }

    @Test
    fun `parse live offline with empty records array — records empty`() {
        val raw = """
            {
              "code": "norecsli",
              "isLive": true,
              "mediaType": "media",
              "mediaData": {
                "code": "norecsli",
                "title": "No Recs Live",
                "isLive": true,
                "isPublish": false,
                "records": []
              }
            }
        """.trimIndent()

        val media = json.decodeFromString<ConfigResponse>(raw).mediaDataSingle!!

        assertTrue(media.records.isEmpty())
    }

    // ── Unknown fields tolerance ─────────────────────────────────────────────

    @Test
    fun `unknown fields in mediaData are ignored`() {
        val raw = """
            {
              "code": "TEST01",
              "mediaData": {
                "code": "TEST01",
                "title": "Test",
                "duration": 60,
                "UNKNOWN_FUTURE_FIELD": "value",
                "nested_unknown": { "foo": "bar" }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ConfigResponse>(raw)
        val media = response.mediaDataSingle

        assertNotNull(media)
        assertEquals("TEST01", media!!.code)
        assertEquals("Test", media.title)
    }
}

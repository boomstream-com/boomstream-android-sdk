package com.boomstream.sdk.api

import com.boomstream.sdk.api.internal.ApiFactory
import com.boomstream.sdk.api.internal.model.LiveFolderApiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for JSON parsing of the `POST /api/live/folder` response.
 *
 * Fixtures are taken from the real Boomstream API response (live-verified 2026-06-20).
 * Auth: `Authorization: Bearer <apikey>`. `ver: "1.2"` in body.
 * Real response shape: `{countTotal, Folders:null, Medias:[{Title,Code,Poster:{Url}|null,...}], Status:"Success"}`
 * Note: live items have no `Duration` or `MediaStatus` fields.
 */
class LiveListingParsingTest {

    private val json = ApiFactory.json

    @Test
    fun `parse real api response with live items`() {
        // Fixture from live POST /api/live/folder (2026-06-20), trimmed to key fields.
        val raw = """
            {
              "countTotal": 8,
              "Folders": null,
              "Medias": [
                {
                  "Title": "test3",
                  "Code": "H4pNmmAU",
                  "OnlineStatus": "False",
                  "Type": "live",
                  "Poster": null
                },
                {
                  "Title": "Demo Live Broadcast",
                  "Code": "vWktqOGl",
                  "OnlineStatus": "True",
                  "Type": "record_live",
                  "Poster": {
                    "Code": "NQJWz6o2-a28",
                    "Width": "1280",
                    "Height": "720",
                    "Url": "https://bs-cdn.boomstream.com/balancer/NQJWz6o2-a28.jpg"
                  }
                }
              ],
              "Status": "Success"
            }
        """.trimIndent()

        val response = json.decodeFromString<LiveFolderApiResponse>(raw)

        assertEquals(8, response.countTotal)
        assertEquals(2, response.medias.size)
        assertNull(response.status.takeIf { it == "Failed" })

        val first = response.medias[0]
        assertEquals("H4pNmmAU", first.code)
        assertEquals("test3", first.title)
        assertNull(first.poster)

        val second = response.medias[1]
        assertEquals("vWktqOGl", second.code)
        assertEquals("Demo Live Broadcast", second.title)
        assertNotNull(second.poster)
        assertEquals("https://bs-cdn.boomstream.com/balancer/NQJWz6o2-a28.jpg", second.poster?.url)
    }

    @Test
    fun `parse empty medias array`() {
        val raw = """{"countTotal": 0, "Medias": [], "Status": "Success"}"""
        val response = json.decodeFromString<LiveFolderApiResponse>(raw)
        assertTrue(response.medias.isEmpty())
    }

    @Test
    fun `medias absent defaults to empty list`() {
        val raw = """{"countTotal": 0}"""
        val response = json.decodeFromString<LiveFolderApiResponse>(raw)
        assertTrue(response.medias.isEmpty())
    }

    @Test
    fun `parse api error response`() {
        val raw = """{"Status":"Failed","Message":"Method not found"}"""
        val response = json.decodeFromString<LiveFolderApiResponse>(raw)
        assertEquals("Failed", response.status)
        assertEquals("Method not found", response.message)
        assertTrue(response.medias.isEmpty())
    }

    @Test
    fun `unknown fields in live item are ignored`() {
        val raw = """
            {
              "countTotal": 1,
              "Medias": [
                {
                  "Code": "abc123",
                  "Title": "My Stream",
                  "OnlineStatus": "True",
                  "RecordStatus": "False",
                  "Concurrent": "5",
                  "RTMP": {"Server": "rtmp://live.boomstream.com/live", "Key": "abc123-key"},
                  "Poster": {"Code": "abc-a1", "Width": "1920", "Height": "1080", "Url": "https://cdn.example.com/p.jpg"}
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<LiveFolderApiResponse>(raw)
        assertEquals(1, response.medias.size)
        val item = response.medias[0]
        assertEquals("abc123", item.code)
        assertEquals("My Stream", item.title)
        assertEquals("https://cdn.example.com/p.jpg", item.poster?.url)
    }

    @Test
    fun `item with missing optional fields uses defaults`() {
        val raw = """{"Medias": [{"Code": "MinCode"}]}"""
        val response = json.decodeFromString<LiveFolderApiResponse>(raw)
        val item = response.medias[0]
        assertEquals("MinCode", item.code)
        assertEquals("", item.title)
        assertNull(item.poster)
    }
}

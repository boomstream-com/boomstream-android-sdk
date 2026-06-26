package com.boomstream.sdk.api

import com.boomstream.sdk.api.internal.ApiFactory
import com.boomstream.sdk.api.internal.model.FolderApiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for JSON parsing of the `POST /api/media/folder` response.
 *
 * Fixtures are taken from the real Boomstream API response (live-verified 2026-06-19).
 * Auth: `Authorization: Bearer <apikey>` (board-confirmed). `ver: "1.2"` in body.
 * Real response shape: `{countTotal, Folders:[...], Medias:[{Code,Title,Duration,MediaStatus,Poster:{Url}}]}`
 */
class FolderListingParsingTest {

    private val json = ApiFactory.json

    @Test
    fun `parse real api response with medias and folders`() {
        // Fixture from live POST /api/media/folder (2026-06-19)
        val raw = """
            {
              "countTotal": 46,
              "Folders": [
                {
                  "code": "p76oybzr",
                  "title": "Demo Folder",
                  "fileCount": "15",
                  "fileSize": "20019512435",
                  "added": "2023-10-11 17:29:47",
                  "duration": 8964
                }
              ],
              "Medias": [
                {
                  "Title": "Demo Video.MP4",
                  "Code": "nKg3scvB",
                  "Type": "video",
                  "Width": "1974",
                  "Height": "1080",
                  "FileSize": "49156264",
                  "FileName": "Demo Video.MP4",
                  "MediaStatus": "Done",
                  "Duration": 46,
                  "DurationMillisecond": "46533",
                  "Description": null,
                  "CreatedAt": "2026-03-07 15:56:35",
                  "PlayerLink": "https://play.boomstream.com/nKg3scvB",
                  "Poster": {
                    "Code": "dxf9qvcn-a1",
                    "Width": "1974",
                    "Height": "1080",
                    "Url": "https://bs-cdn.boomstream.com/balancer/dxf9qvcn-a1.jpg"
                  }
                },
                {
                  "Title": "Second Clip",
                  "Code": "EfGh5678",
                  "Type": "video",
                  "Duration": 300,
                  "Poster": null
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<FolderApiResponse>(raw)

        assertEquals(46, response.countTotal)
        assertEquals(2, response.medias.size)

        val first = response.medias[0]
        assertEquals("nKg3scvB", first.code)
        assertEquals("Demo Video.MP4", first.title)
        assertEquals(46, first.duration)
        assertEquals("Done", first.mediaStatus)
        assertNotNull(first.poster)
        assertEquals("https://bs-cdn.boomstream.com/balancer/dxf9qvcn-a1.jpg", first.poster?.url)

        val second = response.medias[1]
        assertEquals("EfGh5678", second.code)
        assertEquals("Second Clip", second.title)
        assertEquals(300, second.duration)
        assertNull(second.poster)
    }

    @Test
    fun `parse empty medias array`() {
        val raw = """{"countTotal": 0, "Medias": []}"""
        val response = json.decodeFromString<FolderApiResponse>(raw)
        assertTrue(response.medias.isEmpty())
    }

    @Test
    fun `medias absent defaults to empty list`() {
        val raw = """{"countTotal": 0}"""
        val response = json.decodeFromString<FolderApiResponse>(raw)
        assertTrue(response.medias.isEmpty())
    }

    @Test
    fun `parse api error response`() {
        val raw = """{"Status":"Failed","Message":"Method not found"}"""
        val response = json.decodeFromString<FolderApiResponse>(raw)
        assertEquals("Failed", response.status)
        assertEquals("Method not found", response.message)
        assertTrue(response.medias.isEmpty())
    }

    @Test
    fun `unknown fields in response are ignored`() {
        val raw = """
            {
              "countTotal": 1,
              "Folders": [{"code": "FolderA", "title": "Archive"}],
              "Medias": [
                {
                  "Code": "XxYy9900",
                  "Title": "Future Field Test",
                  "Duration": 60,
                  "Type": "video",
                  "FileSize": "12345",
                  "Transcodes": [{"Code": "t1", "Title": "Original"}],
                  "PlayerLink": "https://play.boomstream.com/XxYy9900",
                  "Poster": {"Code": "p1", "Width": "1920", "Height": "1080", "Url": "https://cdn.example.com/p1.jpg"}
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<FolderApiResponse>(raw)

        assertEquals(1, response.medias.size)
        val item = response.medias[0]
        assertEquals("XxYy9900", item.code)
        assertEquals("Future Field Test", item.title)
        assertEquals(60, item.duration)
        assertEquals("https://cdn.example.com/p1.jpg", item.poster?.url)
    }

    @Test
    fun `item with missing optional fields uses defaults`() {
        val raw = """{"Medias": [{"Code": "MinCode"}]}"""
        val response = json.decodeFromString<FolderApiResponse>(raw)

        assertEquals(1, response.medias.size)
        val item = response.medias[0]
        assertEquals("MinCode", item.code)
        assertEquals("", item.title)
        assertEquals(0, item.duration)
        assertNull(item.poster)
    }

    @Test
    fun `parse multiple items preserves order`() {
        val raw = """
            {
              "countTotal": 3,
              "Medias": [
                {"Code": "first", "Title": "A"},
                {"Code": "second", "Title": "B"},
                {"Code": "third", "Title": "C"}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<FolderApiResponse>(raw)

        assertEquals(listOf("first", "second", "third"), response.medias.map { it.code })
    }

    @Test
    fun `poster url is extracted correctly`() {
        val raw = """
            {
              "Medias": [
                {
                  "Code": "abc",
                  "Title": "With Poster",
                  "Poster": {"Code": "abc-p1", "Width": "1280", "Height": "720", "Url": "https://cdn.example.com/thumb.jpg"}
                }
              ]
            }
        """.trimIndent()
        val response = json.decodeFromString<FolderApiResponse>(raw)
        assertEquals("https://cdn.example.com/thumb.jpg", response.medias[0].poster?.url)
    }

    @Test
    fun `poster with missing url field defaults to null`() {
        val raw = """{"Medias": [{"Code": "abc", "Poster": {"Code": "p1"}}]}"""
        val response = json.decodeFromString<FolderApiResponse>(raw)
        assertNull(response.medias[0].poster?.url)
    }
}

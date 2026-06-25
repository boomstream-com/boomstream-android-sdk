package com.boomstream.sdk.api

import com.boomstream.sdk.api.internal.ApiFactory
import com.boomstream.sdk.api.internal.model.PlaylistListApiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for JSON parsing of the `POST /api/playlist/list` response.
 *
 * Fixtures are taken from the real Boomstream API response (live-verified 2026-06-20).
 * Auth: `Authorization: Bearer <apikey>`. `ver: "1.2"` in body.
 * Real response shape: `{Items:[{Code,Name,Duration:"<ms>",Poster:{Url}|null,AmountFiles,...}], Status:"Success"}`.
 * Note: `Duration` is milliseconds as a string. `Name` (not `Title`). `Poster` follows the same `{Url}` shape as other endpoints.
 */
class PlaylistListingParsingTest {

    private val json = ApiFactory.json

    @Test
    fun `parse real api response with playlist items`() {
        // Fixture from live POST /api/playlist/list (2026-06-20), trimmed to key fields.
        val raw = """
            {
              "Items": [
                {
                  "Code": "uLmWi9IB",
                  "Name": "Commercials",
                  "Duration": "244000",
                  "AmountFiles": 4,
                  "AddedDate": "2013-09-25 11:39:27",
                  "Description": ""
                },
                {
                  "Code": "nCmuI9Zb",
                  "Name": "Плейлист",
                  "Duration": "122000",
                  "AmountFiles": 2,
                  "AddedDate": "2016-04-22 14:54:53",
                  "Description": "Пример описания."
                }
              ],
              "Status": "Success"
            }
        """.trimIndent()

        val response = json.decodeFromString<PlaylistListApiResponse>(raw)

        assertEquals(2, response.items.size)

        val first = response.items[0]
        assertEquals("uLmWi9IB", first.code)
        assertEquals("Commercials", first.name)
        assertEquals("244000", first.durationMs)

        val second = response.items[1]
        assertEquals("nCmuI9Zb", second.code)
        assertEquals("Плейлист", second.name)
        assertEquals("122000", second.durationMs)
    }

    @Test
    fun `duration string is preserved as-is for caller to parse`() {
        val raw = """{"Items":[{"Code":"x","Name":"Test","Duration":"73000"}]}"""
        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        assertEquals("73000", response.items[0].durationMs)
    }

    @Test
    fun `zero duration string`() {
        val raw = """{"Items":[{"Code":"x","Name":"Empty","Duration":"0"}]}"""
        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        assertEquals("0", response.items[0].durationMs)
    }

    @Test
    fun `parse empty items array`() {
        val raw = """{"Items": [], "Status": "Success"}"""
        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        assertTrue(response.items.isEmpty())
    }

    @Test
    fun `items absent defaults to empty list`() {
        val raw = """{"Status": "Success"}"""
        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        assertTrue(response.items.isEmpty())
    }

    @Test
    fun `parse api error response`() {
        val raw = """{"Status":"Failed","Message":"Method not found"}"""
        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        assertEquals("Failed", response.status)
        assertEquals("Method not found", response.message)
        assertTrue(response.items.isEmpty())
    }

    @Test
    fun `unknown fields in playlist item are ignored`() {
        val raw = """
            {
              "Items": [
                {
                  "Code": "abc",
                  "Name": "My List",
                  "Duration": "10000",
                  "AmountFiles": 1,
                  "AddedDate": "2024-01-01 00:00:00",
                  "Description": "Ignored",
                  "FutureField": "also ignored"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        assertEquals(1, response.items.size)
        val item = response.items[0]
        assertEquals("abc", item.code)
        assertEquals("My List", item.name)
        assertEquals("10000", item.durationMs)
    }

    @Test
    fun `item with missing optional fields uses defaults`() {
        val raw = """{"Items": [{"Code": "MinCode"}]}"""
        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        val item = response.items[0]
        assertEquals("MinCode", item.code)
        assertEquals("", item.name)
        assertEquals("0", item.durationMs)
        assertEquals(null, item.poster)
    }

    @Test
    fun `poster url is parsed from Poster object`() {
        val raw = """
            {"Items":[{"Code":"p1","Name":"With Poster","Duration":"5000","Poster":{"Url":"https://boomstream.com/poster.jpg"}}]}
        """.trimIndent()
        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        val item = response.items[0]
        assertEquals("https://boomstream.com/poster.jpg", item.poster?.url)
    }

    @Test
    fun `poster absent defaults to null`() {
        val raw = """{"Items":[{"Code":"p2","Name":"No Poster","Duration":"3000"}]}"""
        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        assertEquals(null, response.items[0].poster)
    }

    @Test
    fun `poster with null Url is parsed as null url`() {
        val raw = """{"Items":[{"Code":"p3","Name":"Null Url","Duration":"1000","Poster":{"Url":null}}]}"""
        val response = json.decodeFromString<PlaylistListApiResponse>(raw)
        assertEquals(null, response.items[0].poster?.url)
    }
}

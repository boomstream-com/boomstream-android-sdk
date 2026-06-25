package com.boomstream.sdk.api

import com.boomstream.sdk.api.error.BoomstreamApiError
import com.boomstream.sdk.api.internal.ApiFactory
import com.boomstream.sdk.api.model.ConfigResponse
import com.boomstream.sdk.api.model.mediaDataSingle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

/**
 * Unit tests for [BoomstreamConfigClient] using a [MockWebServer].
 *
 * Tests verify HTTP error mapping and successful response parsing without
 * making real network calls.
 */
class BoomstreamConfigClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: BoomstreamConfigClient

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockServer.url("/"))
            .addConverterFactory(ApiFactory.json.asConverterFactory("application/json".toMediaType()))
            .build()

        client = BoomstreamConfigClient(
            service = retrofit.create(com.boomstream.sdk.api.internal.BoomstreamConfigService::class.java)
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `getConfig returns success on 200`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "code": "Il4lNOfL",
                      "mediaData": {
                        "code": "Il4lNOfL",
                        "title": "Test Video",
                        "duration": 300,
                        "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS92aWRlby5tM3U4" },
                        "posters": [{ "width": 1280, "height": 720, "link": "https://cdn.example.com/poster.jpg" }]
                      }
                    }
                    """.trimIndent()
                )
        )

        val result = client.getConfig("Il4lNOfL")

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("Il4lNOfL", response.code)

        val media = response.mediaDataSingle
        assertNotNull(media)
        assertEquals("Test Video", media!!.title)
        assertEquals(300, media.duration)
        assertNotNull(media.links)
    }

    @Test
    fun `getConfig returns unauthenticated config on 200 without mediaData`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "code": "VVwbS8LD",
                      "posters": [{ "width": 640, "height": 360, "link": "https://cdn.example.com/poster.jpg" }]
                    }
                    """.trimIndent()
                )
        )

        val result = client.getConfig("VVwbS8LD")

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertTrue("mediaDataSingle should be null for unauth", response.mediaDataSingle == null)
        assertEquals(1, response.posters.size)
    }

    @Test
    fun `getConfig returns NotFound on 404`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(404))

        val result = client.getConfig("NOTEXIST")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected NotFound error", error is BoomstreamApiError.NotFound)
    }

    @Test
    fun `getConfig returns Unauthorized on 401`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(401))

        val result = client.getConfig("Il4lNOfL")

        assertTrue(result.isFailure)
        assertTrue("expected Unauthorized error", result.exceptionOrNull() is BoomstreamApiError.Unauthorized)
    }

    @Test
    fun `getConfig returns Unknown on 500`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = client.getConfig("Il4lNOfL")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected Unknown error", error is BoomstreamApiError.Unknown)
        assertEquals(500, (error as BoomstreamApiError.Unknown).httpStatus)
    }

    @Test
    fun `getConfig returns Network error on connection failure`() = runTest {
        mockServer.shutdown()

        val result = client.getConfig("Il4lNOfL")

        assertTrue(result.isFailure)
        assertTrue("expected Network error", result.exceptionOrNull() is BoomstreamApiError.Network)
    }

    // ── forceRefresh cache bypass (BOO-703) ───────────────────────────────────

    private val singleMediaBody = """
        {
          "code": "Il4lNOfL",
          "mediaData": {
            "code": "Il4lNOfL",
            "title": "Test Video",
            "duration": 300,
            "links": { "hls": "aHR0cHM6Ly9leGFtcGxlLmNvbS92aWRlby5tM3U4" }
          }
        }
    """.trimIndent()

    @Test
    fun `getConfig without forceRefresh returns cached response on second call`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(singleMediaBody))

        val r1 = client.getConfig("Il4lNOfL")
        assertTrue(r1.isSuccess)

        // Second call — cache hit, no new request queued.
        val r2 = client.getConfig("Il4lNOfL")
        assertTrue(r2.isSuccess)

        // Only one HTTP request should have been made.
        assertEquals(1, mockServer.requestCount)
    }

    @Test
    fun `getConfig with forceRefresh=true bypasses cache and hits server again`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(singleMediaBody))
        val r1 = client.getConfig("Il4lNOfL")
        assertTrue(r1.isSuccess)

        // Second call with forceRefresh — must hit the server (new response needed).
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(singleMediaBody))
        val r2 = client.getConfig("Il4lNOfL", forceRefresh = true)
        assertTrue(r2.isSuccess)

        // Two HTTP requests made.
        assertEquals(2, mockServer.requestCount)
    }

    @Test
    fun `getConfig with forceRefresh=true updates cache on success`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(singleMediaBody))
        client.getConfig("Il4lNOfL", forceRefresh = true)

        // Subsequent non-forced call should use the updated cache — no new request.
        val r = client.getConfig("Il4lNOfL")
        assertTrue(r.isSuccess)
        assertEquals(1, mockServer.requestCount)
    }
}

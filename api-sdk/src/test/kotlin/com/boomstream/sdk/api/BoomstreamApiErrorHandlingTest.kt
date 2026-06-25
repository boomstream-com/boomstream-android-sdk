package com.boomstream.sdk.api

import com.boomstream.sdk.api.error.BoomstreamApiError
import com.boomstream.sdk.api.internal.ApiFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that [BoomstreamApi] methods correctly map `{"Status":"Failed","Message":"..."}` API
 * responses to [BoomstreamApiError.ApiError]. Exercises the full stack via MockWebServer —
 * OkHttp client → Retrofit → response parsing → error branch in [BoomstreamApi].
 */
class BoomstreamApiErrorHandlingTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: BoomstreamApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val service = ApiFactory.createApiService(
            apiKey = "test-key",
            userAgent = "Test/1.0",
            connectTimeoutSeconds = 5,
            readTimeoutSeconds = 5,
            additionalInterceptors = emptyList(),
            baseUrl = mockWebServer.url("/").toString(),
        )
        api = BoomstreamApi(service)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `listFolder returns ApiError when Status is Failed`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"Status":"Failed","Message":"Invalid API key"}"""),
        )

        val result = api.listFolder()

        assertTrue("expected failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected BoomstreamApiError.ApiError, got ${error?.javaClass}", error is BoomstreamApiError.ApiError)
        assertEquals("Invalid API key", (error as BoomstreamApiError.ApiError).apiMessage)
    }

    @Test
    fun `listFolder returns ApiError with fallback message when Message absent`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"Status":"Failed"}"""),
        )

        val result = api.listFolder()

        assertTrue("expected failure", result.isFailure)
        assertTrue(result.exceptionOrNull() is BoomstreamApiError.ApiError)
    }

    @Test
    fun `listFolder maps mediaStatus to FolderMediaItem`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "countTotal": 2,
                      "Medias": [
                        {"Code": "abc123", "Title": "Done Video", "Duration": 60, "MediaStatus": "Done"},
                        {"Code": "def456", "Title": "No Status Video", "Duration": 30}
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val result = api.listFolder()

        assertTrue("expected success", result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(2, items.size)
        assertEquals("Done", items[0].mediaStatus)
        assertNull(items[1].mediaStatus)
    }

    @Test
    fun `listLive returns ApiError when Status is Failed`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"Status":"Failed","Message":"Invalid API key"}"""),
        )

        val result = api.listLive()

        assertTrue("expected failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected BoomstreamApiError.ApiError", error is BoomstreamApiError.ApiError)
        assertEquals("Invalid API key", (error as BoomstreamApiError.ApiError).apiMessage)
    }

    @Test
    fun `listPlaylists returns ApiError when Status is Failed`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"Status":"Failed","Message":"Unauthorized"}"""),
        )

        val result = api.listPlaylists()

        assertTrue("expected failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected BoomstreamApiError.ApiError", error is BoomstreamApiError.ApiError)
        assertEquals("Unauthorized", (error as BoomstreamApiError.ApiError).apiMessage)
    }

    @Test
    fun `listLive returns empty list on success with no items`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"countTotal":0,"Medias":[],"Status":"Success"}"""),
        )

        val result = api.listLive()

        assertTrue("expected success", result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `listPlaylists returns items on success`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"Items":[{"Code":"abc","Name":"My List","Duration":"122000"}],"Status":"Success"}""",
                ),
        )

        val result = api.listPlaylists()

        assertTrue("expected success", result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals("abc", items[0].code)
        assertEquals("My List", items[0].name)
        assertEquals(122, items[0].durationSeconds)
    }
}

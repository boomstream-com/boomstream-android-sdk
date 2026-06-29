package com.boomstream.sdk.api.integration

import com.boomstream.sdk.api.BoomstreamConfigClient
import com.boomstream.sdk.api.internal.ApiFactory
import com.boomstream.sdk.api.model.mediaDataSingle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

/**
 * Integration tests against the real `play.boomstream.com` config endpoint.
 *
 * These tests require network access and are skipped automatically when
 * the environment variable `BOOMSTREAM_INTEGRATION_TESTS=true` is not set
 * (to prevent unintended network calls in unit-test CI runs).
 *
 * Run manually or in a dedicated integration-test CI stage:
 * ```
 * BOOMSTREAM_INTEGRATION_TESTS=true ./gradlew :api-sdk:test --tests "*.integration.*"
 * ```
 */
class ConfigEndpointIntegrationTest {

    private val isEnabled = System.getenv("BOOMSTREAM_INTEGRATION_TESTS") == "true"

    private fun buildClient(apiKey: String = ""): BoomstreamConfigClient {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://play.boomstream.com/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(ApiFactory.json.asConverterFactory("application/json".toMediaType()))
            .build()
        return BoomstreamConfigClient(
            service = retrofit.create(com.boomstream.sdk.api.internal.BoomstreamConfigService::class.java)
        )
    }

    /**
     * Auth case: `Il4lNOfL` — expects mediaData with HLS links.
     *
     * Requires env var `BOOMSTREAM_API_KEY` to be set with a valid key.
     */
    @Test
    fun `auth config returns mediaData with hls link`() = runTest {
        Assume.assumeTrue("Integration tests disabled", isEnabled)

        val apiKey = System.getenv("BOOMSTREAM_API_KEY")
        Assume.assumeFalse("BOOMSTREAM_API_KEY not set", apiKey.isNullOrBlank())

        val client = buildClient(apiKey!!)
        val result = client.getConfig("Il4lNOfL")

        assertTrue("expected success", result.isSuccess)
        val response = result.getOrThrow()
        assertNotNull("expected mediaDataSingle", response.mediaDataSingle)
        assertNotNull("expected HLS link", response.mediaDataSingle?.links)
    }

    /**
     * Unauth case: `VVwbS8LD` — expects response with posters only (no mediaData).
     */
    @Test
    fun `unauth config returns posters only without mediaData`() = runTest {
        Assume.assumeTrue("Integration tests disabled", isEnabled)

        val client = buildClient(apiKey = "")
        val result = client.getConfig("VVwbS8LD")

        assertTrue("expected success (degraded, not error)", result.isSuccess)
        val response = result.getOrThrow()
        assertNull("expected null mediaDataSingle for unauth", response.mediaDataSingle)
        assertTrue("expected at least one poster", response.posters.isNotEmpty())
    }
}

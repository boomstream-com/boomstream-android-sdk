package com.boomstream.sdk.player

import com.boomstream.sdk.player.internal.BoomstreamDataSourceFactory
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests for [BoomstreamDataSourceFactory].
 *
 * These tests run on the JVM without an Android environment (no Robolectric needed) because
 * [BoomstreamDataSourceFactory] is a pure Kotlin/OkHttp class.
 *
 * CSO constraint #2 (BOO-596): verifies that no `HttpLoggingInterceptor` is registered in the
 * OkHttp client, regardless of build variant.
 *
 * BOO-700: verifies User-Agent format — base string and optional allowClearKeyDRMtoken suffix.
 */
class BoomstreamDataSourceFactoryTest {

    @Test
    fun `factory creates a non-null DataSource`() {
        val factory = BoomstreamDataSourceFactory()
        val source = factory.createDataSource()
        assertNotNull("createDataSource() must return a non-null DataSource", source)
    }

    @Test
    fun `OkHttp client has no HttpLoggingInterceptor — CSO constraint 2`() {
        val factory = BoomstreamDataSourceFactory(allowClearKeyDRMtoken = null)

        val okHttpClientField: Field = BoomstreamDataSourceFactory::class.java
            .getDeclaredField("okHttpClient")
            .apply { isAccessible = true }

        val client = okHttpClientField.get(factory) as OkHttpClient

        val loggingInterceptorName = "okhttp3.logging.HttpLoggingInterceptor"
        val allInterceptors = client.interceptors + client.networkInterceptors
        val hasLogging = allInterceptors.any { interceptor ->
            interceptor.javaClass.name == loggingInterceptorName ||
                interceptor.javaClass.superclass?.name == loggingInterceptorName
        }

        assertFalse(
            "BoomstreamDataSourceFactory must not register HttpLoggingInterceptor " +
                "(CSO constraint #2, BOO-596). Found logging interceptor in OkHttp client.",
            hasLogging,
        )
    }

    @Test
    fun `multiple createDataSource calls return independent sources`() {
        val factory = BoomstreamDataSourceFactory()
        val s1 = factory.createDataSource()
        val s2 = factory.createDataSource()
        assertNotNull(s1)
        assertNotNull(s2)
        assertFalse("createDataSource() must return new instances", s1 === s2)
    }

    // ── User-Agent format (BOO-700, BOO-714) ─────────────────────────────────

    private fun userAgentOf(factory: BoomstreamDataSourceFactory): String {
        val field: Field = BoomstreamDataSourceFactory::class.java
            .getDeclaredField("userAgent")
            .apply { isAccessible = true }
        return field.get(factory) as String
    }

    @Test
    fun `User-Agent without token matches SDK version format`() {
        val ua = userAgentOf(BoomstreamDataSourceFactory(allowClearKeyDRMtoken = null))
        assertTrue(
            "Expected 'Boomstream Android SDK v<version>' but got '$ua'",
            ua.matches(Regex("Boomstream Android SDK v.+")),
        )
        assertFalse("UA must not end with a trailing space when no token is set", ua.endsWith(" "))
    }

    @Test
    fun `User-Agent with allowClearKeyDRMtoken appends token after space`() {
        val token = "test-drm-token-12345"
        val ua = userAgentOf(BoomstreamDataSourceFactory(allowClearKeyDRMtoken = token))
        assertTrue(
            "Expected UA to end with ' $token' but got '$ua'",
            ua.endsWith(" $token"),
        )
        assertTrue(
            "Expected UA prefix 'Boomstream Android SDK v' but got '$ua'",
            ua.startsWith("Boomstream Android SDK v"),
        )
    }

    @Test
    fun `User-Agent with blank allowClearKeyDRMtoken omits token suffix`() {
        val ua = userAgentOf(BoomstreamDataSourceFactory(allowClearKeyDRMtoken = "   "))
        assertFalse("Blank token must not produce trailing space", ua.endsWith(" "))
        assertEquals(
            userAgentOf(BoomstreamDataSourceFactory(allowClearKeyDRMtoken = null)),
            ua,
        )
    }
}

package com.boomstream.sdk.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BoomstreamOfflineException] typed exceptions.
 */
class BoomstreamOfflineExceptionTest {

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `NoHlsUrl carries the media code`() {
        val ex = BoomstreamOfflineException.NoHlsUrl("Il4lNOfL")
        assertEquals("Il4lNOfL", ex.mediaCode)
        assertTrue("message should reference the media code", ex.message?.contains("Il4lNOfL") == true)
    }

    @Test
    fun `DownloadFailed carries the reason`() {
        val ex = BoomstreamOfflineException.DownloadFailed("HTTP 503")
        assertEquals("HTTP 503", ex.reason)
        assertTrue("message should reference the reason", ex.message?.contains("HTTP 503") == true)
    }

    @Test
    fun `Cancelled carries the media code`() {
        val ex = BoomstreamOfflineException.Cancelled("Il4lNOfL")
        assertEquals("Il4lNOfL", ex.mediaCode)
        assertTrue("message should reference the media code", ex.message?.contains("Il4lNOfL") == true)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `NoHlsUrl is a subtype of BoomstreamOfflineException`() {
        val ex: BoomstreamOfflineException = BoomstreamOfflineException.NoHlsUrl("x")
        assertTrue(ex is BoomstreamOfflineException.NoHlsUrl)
    }

    @Test
    fun `DownloadFailed is a subtype of Exception`() {
        val ex = BoomstreamOfflineException.DownloadFailed("oops")
        assertNotNull(ex.message)
        assertTrue(ex is Exception)
    }

    @Test
    fun `All subtypes have non-null messages`() {
        val exceptions: List<BoomstreamOfflineException> = listOf(
            BoomstreamOfflineException.NoHlsUrl("code"),
            BoomstreamOfflineException.DownloadFailed("reason"),
            BoomstreamOfflineException.Cancelled("code"),
        )
        exceptions.forEach { ex ->
            assertNotNull("message must not be null for ${ex::class.simpleName}", ex.message)
        }
    }
}

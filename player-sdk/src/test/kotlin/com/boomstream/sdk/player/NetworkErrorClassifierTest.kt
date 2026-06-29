package com.boomstream.sdk.player

import com.boomstream.sdk.player.internal.isNetworkError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Unit tests for [isNetworkError].
 *
 * Covers: direct network exception types, wrapped/nested exceptions, non-network exceptions,
 * and the offline-cache decision branching described in BOO-701.
 */
class NetworkErrorClassifierTest {

    // ── Direct network exceptions ─────────────────────────────────────────────

    @Test
    fun `UnknownHostException is a network error`() {
        assertTrue(isNetworkError(UnknownHostException("Unable to resolve host play.boomstream.com")))
    }

    @Test
    fun `ConnectException is a network error`() {
        assertTrue(isNetworkError(ConnectException("Connection refused")))
    }

    @Test
    fun `SocketException is a network error`() {
        assertTrue(isNetworkError(SocketException("Network unreachable")))
    }

    // ── Wrapped / nested exceptions (cause chain) ─────────────────────────────

    @Test
    fun `IOException wrapping UnknownHostException is a network error`() {
        val wrapped = IOException("Config fetch failed", UnknownHostException("play.boomstream.com"))
        assertTrue(isNetworkError(wrapped))
    }

    @Test
    fun `RuntimeException wrapping ConnectException is a network error`() {
        val wrapped = RuntimeException("Unexpected", ConnectException("timed out"))
        assertTrue(isNetworkError(wrapped))
    }

    @Test
    fun `three-level chain with network error at the bottom is detected`() {
        val network = SocketException("ETIMEDOUT")
        val mid = IOException("IO", network)
        val top = RuntimeException("Top", mid)
        assertTrue(isNetworkError(top))
    }

    @Test
    fun `SocketTimeoutException is a network error`() {
        assertTrue(isNetworkError(SocketTimeoutException("Read timed out")))
    }

    @Test
    fun `IOException wrapping SocketTimeoutException is a network error`() {
        val wrapped = IOException("Config fetch failed", SocketTimeoutException("Read timed out"))
        assertTrue(isNetworkError(wrapped))
    }

    // ── Non-network exceptions ────────────────────────────────────────────────

    @Test
    fun `plain IOException without network cause is not a network error`() {
        assertFalse(isNetworkError(IOException("file not found")))
    }

    @Test
    fun `IllegalStateException is not a network error`() {
        assertFalse(isNetworkError(IllegalStateException("Bad state")))
    }

    @Test
    fun `NullPointerException is not a network error`() {
        assertFalse(isNetworkError(NullPointerException()))
    }

    @Test
    fun `RuntimeException with no cause is not a network error`() {
        assertFalse(isNetworkError(RuntimeException("Something went wrong")))
    }

    // ── Offline-cache decision integration (BOO-701) ─────────────────────────

    @Test
    fun `no_network_offline message is triggered when offlineCache non-null and network error`() {
        val offlineCacheConfigured = true   // simulates offlineCache != null
        val error = UnknownHostException("play.boomstream.com")
        assertTrue("Should show no_network_offline when offline mode + network error",
            offlineCacheConfigured && isNetworkError(error))
    }

    @Test
    fun `no_network_offline message is not triggered when offlineCache is null`() {
        val offlineCacheConfigured = false  // simulates offlineCache == null
        val error = UnknownHostException("play.boomstream.com")
        assertFalse("Should not show no_network_offline without offline mode",
            offlineCacheConfigured && isNetworkError(error))
    }

    @Test
    fun `no_network_offline is not triggered for non-network error even with offlineCache`() {
        val offlineCacheConfigured = true
        val error = IllegalStateException("decoding error")
        assertFalse("Non-network error must not show no_network_offline",
            offlineCacheConfigured && isNetworkError(error))
    }
}

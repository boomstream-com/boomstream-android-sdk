package com.boomstream.sdk.api

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`


/**
 * Unit tests for User-Agent composition logic in [BoomstreamSdk] (via [BoomstreamOptions]).
 *
 * AC5 coverage (BOO-771):
 *  - Only [BoomstreamOptions.userAgentToken] set → SDK forms token-derived UA on the config client.
 *  - Only [BoomstreamOptions.userAgent] set → explicit UA wins (legacy path, deprecated).
 *  - Both set → [BoomstreamOptions.userAgent] wins (explicit override).
 *  - Neither set → legacy default `"BoomstreamSDK/1.0"`.
 *
 * Also verifies:
 *  - [BoomstreamConfigClient.userAgentToken] carries the init-time token for the player-sdk
 *    fallback path (no need to repeat the token in every `load()` call).
 *  - [BoomstreamOptions.toString] redacts both sensitive fields.
 */
@Suppress("DEPRECATION")
class UserAgentCompositionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        resetSingleton()
    }

    @After
    fun tearDown() {
        resetSingleton()
    }

    // ── BoomstreamOptions UA composition ─────────────────────────────────────

    @Test
    fun `userAgentToken only — config client UA contains token after SDK version`() {
        val token = "my-ua-allow-token"
        val sdk = Boomstream.init(context, options = BoomstreamOptions(userAgentToken = token))

        val ua = configClientUserAgent(sdk)
        assertTrue(
            "Expected UA to contain token '$token' but got: $ua",
            ua.endsWith(" $token"),
        )
        assertTrue(
            "Expected UA to start with 'Boomstream Android SDK v' but got: $ua",
            ua.startsWith("Boomstream Android SDK v"),
        )
        assertFalse("UA must not be the legacy default when token is set", ua == "BoomstreamSDK/1.0")
    }

    @Test
    fun `userAgent only — explicit UA used verbatim, legacy path`() {
        val customUa = "BoomstreamSDK/1.0 MyApp/2.3"
        val sdk = Boomstream.init(context, options = BoomstreamOptions(userAgent = customUa))

        assertEquals(customUa, configClientUserAgent(sdk))
    }

    @Test
    fun `both userAgentToken and userAgent — userAgent wins as explicit override`() {
        val token = "token-that-should-not-win"
        val explicit = "BoomstreamSDK/1.0 ExplicitWins/1.0"
        val sdk = Boomstream.init(
            context,
            options = BoomstreamOptions(userAgentToken = token, userAgent = explicit),
        )

        assertEquals(
            "explicit userAgent must override token-derived UA",
            explicit,
            configClientUserAgent(sdk),
        )
        assertFalse(
            "token-derived UA must not appear when userAgent is set",
            configClientUserAgent(sdk).contains(token),
        )
    }

    @Test
    fun `neither field set — legacy default UA used`() {
        val sdk = Boomstream.init(context, options = BoomstreamOptions())

        assertEquals("BoomstreamSDK/1.0", configClientUserAgent(sdk))
    }

    // ── BoomstreamConfigClient.userAgentToken passthrough ────────────────────

    @Test
    fun `configClient carries userAgentToken for player-sdk fallback`() {
        val token = "segment-client-token"
        val sdk = Boomstream.init(context, options = BoomstreamOptions(userAgentToken = token))

        assertEquals(
            "configClient.userAgentToken must carry the init-time token",
            token,
            sdk.configClient.userAgentToken,
        )
    }

    @Test
    fun `configClient userAgentToken is null when no token configured`() {
        val sdk = Boomstream.init(context, options = BoomstreamOptions())
        assertNull(sdk.configClient.userAgentToken)
    }

    @Test
    fun `configClient userAgentToken is null when only deprecated userAgent is set`() {
        val sdk = Boomstream.init(context, options = BoomstreamOptions(userAgent = "BoomstreamSDK/1.0 Legacy"))
        assertNull(
            "userAgentToken must be null when only deprecated userAgent is used",
            sdk.configClient.userAgentToken,
        )
    }

    // ── BoomstreamOptions.toString redaction ─────────────────────────────────

    @Test
    fun `toString redacts userAgentToken`() {
        val opts = BoomstreamOptions(userAgentToken = "super-secret-token")
        val str = opts.toString()
        assertFalse("toString must not expose userAgentToken value", str.contains("super-secret-token"))
        assertTrue("toString must show redacted marker for userAgentToken", str.contains("userAgentToken=***"))
    }

    @Test
    fun `toString redacts userAgent`() {
        val opts = BoomstreamOptions(userAgent = "BoomstreamSDK/1.0 secret-embedded")
        val str = opts.toString()
        assertFalse("toString must not expose userAgent value", str.contains("secret-embedded"))
        assertTrue("toString must show redacted marker for userAgent", str.contains("userAgent=***"))
    }

    @Test
    fun `toString shows null for unset fields`() {
        val opts = BoomstreamOptions()
        val str = opts.toString()
        assertTrue(str.contains("userAgentToken=null"))
        assertTrue(str.contains("userAgent=null"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the effective User-Agent string computed by [BoomstreamSdk] at init time.
     * Uses the `internal` [BoomstreamSdk.effectiveUserAgent] property — accessible from
     * the same module's test sources without deep OkHttp reflection.
     */
    private fun configClientUserAgent(sdk: BoomstreamSdk): String = sdk.effectiveUserAgent

    private fun resetSingleton() {
        val field = Boomstream::class.java.getDeclaredField("_instance")
        field.isAccessible = true
        field.set(Boomstream, null)
    }
}

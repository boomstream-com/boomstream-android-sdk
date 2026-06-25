package com.boomstream.sdk.api

import android.content.Context
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Unit tests for [Boomstream.init] nullable-apiKey contract.
 *
 * Covers:
 *  - init without apiKey does not throw and exposes [Boomstream.configClient];
 *  - access to [Boomstream.api] in player/offline-only mode throws a human-readable
 *    [IllegalStateException];
 *  - init with a non-null, non-blank apiKey exposes [Boomstream.api];
 *  - init with an empty apiKey (typical misconfiguration) still throws
 *    [IllegalArgumentException].
 *
 * Test isolation: the [Boomstream] singleton holds a `@Volatile private var _instance`;
 * we reset it via reflection in [tearDown] so each test starts from a clean slate.
 */
class BoomstreamInitTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        // Boomstream.init calls context.applicationContext — return the same mock.
        `when`(context.applicationContext).thenReturn(context)
        resetSingleton()
    }

    @After
    fun tearDown() {
        resetSingleton()
    }

    @Test
    fun `init without apiKey does not throw and exposes configClient`() {
        Boomstream.init(context, apiKey = null)
        assertNotNull(Boomstream.configClient)
    }

    @Test
    fun `init without apiKey makes api throw IllegalStateException with explicit message`() {
        Boomstream.init(context, apiKey = null)

        val ex = assertThrows(IllegalStateException::class.java) {
            Boomstream.api
        }
        val message = ex.message ?: ""
        assertTrue(
            "expected message to explain api-sdk requirement, got: $message",
            message.contains("api-sdk requires"),
        )
        assertTrue(
            "expected message to mention player/offline-only mode, got: $message",
            message.contains("player/offline-only mode"),
        )
    }

    @Test
    fun `init with non-blank apiKey exposes both configClient and api`() {
        Boomstream.init(context, apiKey = "valid-key")

        assertNotNull(Boomstream.configClient)
        assertNotNull(Boomstream.api)
    }

    @Test
    fun `init with blank apiKey throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            Boomstream.init(context, apiKey = "")
        }
    }

    private fun resetSingleton() {
        val field = Boomstream::class.java.getDeclaredField("_instance")
        field.isAccessible = true
        field.set(Boomstream, null)
    }
}

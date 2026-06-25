package com.boomstream.sdk.api

import android.content.Context
import com.boomstream.sdk.api.internal.ApiFactory

/**
 * Main entry point for the Boomstream Android SDK.
 *
 * Call [init] exactly once from `Application.onCreate()` before using any other SDK features.
 *
 * The API key is **optional**. Pass it when you need the authenticated Boomstream API
 * (folder/playlist/live listings via [Boomstream.api]). Omit it (or pass `null`) when you
 * only use the player and/or offline downloads — those features go through
 * [Boomstream.configClient], which calls the unauthenticated
 * `play.boomstream.com/{mediaCode}/config` endpoint.
 *
 * Full SDK (player + offline + API):
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         // API key comes from BuildConfig, injected from local.properties or CI env.
 *         // Never hardcode the key value in source code.
 *         Boomstream.init(this, BuildConfig.BOOMSTREAM_API_KEY)
 *     }
 * }
 * ```
 *
 * Player- and/or offline-only (no API key needed):
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Boomstream.init(this) // apiKey defaults to null
 *     }
 * }
 * ```
 */
object Boomstream {

    @Volatile private var _instance: BoomstreamSdk? = null

    /**
     * Initialises the SDK. Must be called exactly once from `Application.onCreate()`.
     *
     * Throws [IllegalStateException] if called more than once — reinitialisation is not
     * supported. To use a different configuration in tests, create a [BoomstreamSdk] instance
     * directly instead of going through this singleton.
     *
     * @param context Application context (used for Android-specific internals).
     * @param apiKey Your Boomstream API key, or `null` for player/offline-only mode. When
     *   `null`, [Boomstream.configClient] still works (it calls the unauthenticated
     *   `play.boomstream.com/{mediaCode}/config` endpoint), but accessing [Boomstream.api]
     *   throws [IllegalStateException]. When non-null, the value must not be blank —
     *   an empty string is treated as a misconfiguration (typical cause: `BuildConfig`
     *   constant left unset) and rejected with [IllegalArgumentException]. Read the value
     *   from `BuildConfig.BOOMSTREAM_API_KEY` (set via `local.properties` locally or a CI
     *   secret). **Never hardcode this value.**
     * @param options Additional configuration (timeouts, custom User-Agent, extra interceptors).
     */
    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        apiKey: String? = null,
        options: BoomstreamOptions = BoomstreamOptions(),
    ): BoomstreamSdk {
        require(apiKey == null || apiKey.isNotBlank()) { "apiKey must be null or non-blank" }
        check(_instance == null) {
            "Boomstream SDK already initialised. Call init() exactly once in Application.onCreate()."
        }
        return BoomstreamSdk(apiKey = apiKey, options = options, context = context.applicationContext)
            .also { _instance = it }
    }

    /**
     * The initialised SDK instance.
     *
     * @throws IllegalStateException if [init] has not been called.
     */
    val instance: BoomstreamSdk
        get() = _instance ?: error("Boomstream SDK not initialised. Call Boomstream.init() in Application.onCreate().")

    /** Shortcut to the [BoomstreamConfigClient] from the current [instance]. */
    val configClient: BoomstreamConfigClient get() = instance.configClient

    /**
     * Shortcut to the [BoomstreamApi] from the current [instance].
     *
     * @throws IllegalStateException if the SDK was initialised in player/offline-only mode
     *   (without an API key). Re-initialise via [init] with a non-null `apiKey` to enable
     *   the authenticated API.
     */
    val api: BoomstreamApi get() = instance.api
}

/**
 * Fully-configured SDK object returned by [Boomstream.init].
 *
 * Prefer the [Boomstream] singleton for application code. Use this class directly in
 * tests or when you need multiple isolated SDK instances (e.g. integration test suites).
 *
 * @param apiKey Boomstream API key, or `null` for player/offline-only mode. When `null`,
 *   only [configClient] is available; touching [api] throws [IllegalStateException].
 */
class BoomstreamSdk internal constructor(
    apiKey: String?,
    options: BoomstreamOptions,
    @Suppress("UNUSED_PARAMETER") context: Context,
) {
    /** Client for `{configBaseUrl}/{mediaCode}/config` (default: `https://play.boomstream.com/`). */
    val configClient: BoomstreamConfigClient = BoomstreamConfigClient(
        service = ApiFactory.createConfigService(
            userAgent = options.userAgent,
            connectTimeoutSeconds = options.connectTimeoutSeconds,
            readTimeoutSeconds = options.readTimeoutSeconds,
            additionalInterceptors = options.additionalInterceptors,
            baseUrl = options.configBaseUrl,
        )
    )

    private val _api: BoomstreamApi? = apiKey?.let {
        BoomstreamApi(
            service = ApiFactory.createApiService(
                apiKey = it,
                userAgent = options.userAgent,
                connectTimeoutSeconds = options.connectTimeoutSeconds,
                readTimeoutSeconds = options.readTimeoutSeconds,
                additionalInterceptors = options.additionalInterceptors,
                baseUrl = options.apiBaseUrl,
            )
        )
    }

    /**
     * Client for the Boomstream media API (base URL configured via [BoomstreamOptions.apiBaseUrl]).
     *
     * @throws IllegalStateException if this instance was created without an API key
     *   (player/offline-only mode).
     */
    val api: BoomstreamApi
        get() = _api ?: error(
            "api-sdk requires Boomstream.init(apiKey = ...). " +
                "Currently initialised in player/offline-only mode."
        )
}

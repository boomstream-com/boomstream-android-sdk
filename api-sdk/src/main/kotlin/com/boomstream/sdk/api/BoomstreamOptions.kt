package com.boomstream.sdk.api

import okhttp3.Interceptor

/**
 * Configuration options for the Boomstream SDK.
 *
 * Pass a customised instance to [Boomstream.init] to override defaults.
 *
 * @param userAgentToken Token appended to the SDK `User-Agent` header for both the config
 *   endpoint (`play.boomstream.com/{mediaCode}/config`) **and** HLS segment/manifest requests.
 *   When non-null, the UA becomes:
 *   `"Boomstream Android SDK v<SDK_VERSION> <userAgentToken>"`.
 *   The media server validates this token to enable native Clear Key DRM / `ua_allow` playback.
 *
 *   Setting this is equivalent to the old workaround of setting [userAgent] to
 *   `"Boomstream Android SDK v<version> $token"` AND passing the same value as
 *   `allowClearKeyDRMtoken` to every `load()` call.  With `userAgentToken` you set the token
 *   once and both clients use it automatically.
 *
 *   **This value carries sensitive key material — do not log or externally expose it.**
 *   Pass `null` (default) for plain playback.
 * @param userAgent **Deprecated.** Fully-custom `User-Agent` string sent with every request.
 *   When non-null, it **overrides** the token-derived UA even if [userAgentToken] is also set —
 *   use this only when you need complete control over the UA string.
 *   Migrate to [userAgentToken] for the common `ua_allow` access-token use-case.
 *   **Will be removed in 2.0.**
 * @param connectTimeoutSeconds TCP connect timeout in seconds. Default 15.
 * @param readTimeoutSeconds HTTP read timeout in seconds. Default 30.
 * @param additionalInterceptors OkHttp [Interceptor]s added by the integrator on top of
 *   the SDK's own chain. Intended for debug logging or custom telemetry interceptors
 *   provided by `okhttp3:logging-interceptor` or similar.
 *
 *   **Headers visible to these interceptors** (injected by the SDK before yours run):
 *   - Config requests (`play.boomstream.com`): `User-Agent` only — no Authorization header is sent.
 *   - API requests (`boomstream.com/api/`): `Authorization: Bearer <api-key>` and `User-Agent`; body contains only `ver` and request parameters — no secrets in body.
 *   The Bearer token only travels on `boomstream.com/api/` — never log API-path requests at `HEADERS` level in release builds.
 *   Restrict any logging interceptor to `BuildConfig.DEBUG`-gated code:
 *   ```
 *   val interceptors = buildList {
 *       if (BuildConfig.DEBUG) {
 *           add(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
 *       }
 *   }
 *   Boomstream.init(this, BuildConfig.BOOMSTREAM_API_KEY,
 *       BoomstreamOptions(additionalInterceptors = interceptors))
 *   ```
 *   The SDK itself never registers a logging interceptor (CSO-mandated constraint #2).
 * @param apiBaseUrl Base URL for the Boomstream media API (`api/media/folder`, etc.).
 *   Defaults to `"https://boomstream.com/"`. Override to point at a staging or
 *   local-dev server. Read from `local.properties` via `BuildConfig.BOOMSTREAM_API_URL`
 *   in the example-app (falls back to the production URL when the property is absent).
 * @param configBaseUrl Base URL for the Boomstream config service (`{mediaCode}/config`).
 *   Defaults to `"https://play.boomstream.com/"`. Override to point at a staging or
 *   local-dev server. Read from `local.properties` via `BuildConfig.BOOMSTREAM_CONFIG_URL`
 *   in the example-app (falls back to the production URL when the property is absent).
 */
class BoomstreamOptions(
    val userAgentToken: String? = null,
    @Deprecated(
        "Use userAgentToken to pass a Clear Key / ua_allow access token. " +
            "When both are set, userAgent wins as an explicit override. Will be removed in 2.0.",
        level = DeprecationLevel.WARNING,
    )
    val userAgent: String? = null,
    val connectTimeoutSeconds: Long = 15L,
    val readTimeoutSeconds: Long = 30L,
    val additionalInterceptors: List<Interceptor> = emptyList(),
    val apiBaseUrl: String = "https://boomstream.com/",
    val configBaseUrl: String = "https://play.boomstream.com/",
) {
    override fun toString(): String =
        "BoomstreamOptions(userAgentToken=${if (userAgentToken != null) "***" else "null"}, " +
            "userAgent=${if (userAgent != null) "***" else "null"}, " +
            "connectTimeoutSeconds=$connectTimeoutSeconds, " +
            "readTimeoutSeconds=$readTimeoutSeconds, " +
            "additionalInterceptors=${additionalInterceptors.size} interceptor(s))"
}
